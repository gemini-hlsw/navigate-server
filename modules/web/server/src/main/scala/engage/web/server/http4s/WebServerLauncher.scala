// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.Parallel
import cats.effect._
import cats.effect.std.Dispatcher
import cats.syntax.all._
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import engage.model.EngageEvent
import engage.model.config._
import engage.server.CaServiceInit
import engage.server.EngageEngine
import engage.server.EngageFailure
import engage.server.Systems
import engage.web.server.OcsBuildInfo
import engage.web.server.common.LogInitialization
import engage.web.server.common.RedirectToHttpsRoutes
import engage.web.server.common.StaticRoutes
import engage.web.server.config._
import engage.web.server.logging._
import engage.web.server.security.AuthenticationService
import fs2.Stream
import fs2.concurrent.Topic
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.Server
import org.http4s.server.middleware.{Logger => Http4sLogger}
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigObjectSource
import pureconfig.ConfigSource

import java.io.FileInputStream
import java.nio.file.{Path => FilePath}
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import scala.concurrent.duration._

object WebServerLauncher extends IOApp with LogInitialization {
  private implicit def L: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("engage")

  // Attempt to get the configuration file relative to the base dir
  def configurationFile[F[_]: Sync]: F[FilePath] =
    baseDir[F].map(_.resolve("conf").resolve("app.conf"))

  // Try to load config from the file and fall back to the common one in the class path
  def config[F[_]: Sync]: F[ConfigObjectSource] = {
    val defaultConfig = ConfigSource.resources("app.conf").pure[F]
    val fileConfig    = configurationFile.map(ConfigSource.file)

    // ConfigSource, first attempt the file or default to the classpath file
    (fileConfig, defaultConfig).mapN(_.optional.withFallback(_))
  }

  /** Configures the Authentication service */
  def authService[F[_]: Sync: Logger](
    mode: Mode,
    conf: AuthenticationConfig
  ): F[AuthenticationService[F]] =
    Sync[F].delay(AuthenticationService[F](mode, conf))

  def makeContext[F[_]: Sync](tls: TLSConfig): F[SSLContext] = Sync[F].delay {
    val ksStream   = new FileInputStream(tls.keyStore.toFile.getAbsolutePath)
    val ks         = KeyStore.getInstance("JKS")
    ks.load(ksStream, tls.keyStorePwd.toCharArray)
    ksStream.close()
    val trustStore = StoreInfo(tls.keyStore.toFile.getAbsolutePath, tls.keyStorePwd)

    val tmf = {
      val ksStream = new FileInputStream(trustStore.path)

      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, tls.keyStorePwd.toCharArray)
      ksStream.close()

      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

      tmf.init(ks)
      tmf.getTrustManagers
    }

    val kmf = KeyManagerFactory.getInstance(
      Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
        .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
    )

    kmf.init(ks, tls.certPwd.toCharArray)

    val context = SSLContext.getInstance("TLS")
    context.init(kmf.getKeyManagers, tmf, null)
    context
  }

  /** Resource that yields the running web server */
  def webServer[F[_]: Logger: Async, I](
    conf:      EngageConfiguration,
    as:        AuthenticationService[F],
    outputs:   Topic[F, EngageEvent],
    se:        EngageEngine[F],
    clientsDb: ClientsSetDb[F]
  ): Resource[F, Server] = {

    val ssl: F[Option[SSLContext]] = conf.webServer.tls.map(makeContext[F]).sequence

    def build(all: WebSocketBuilder2[F] => HttpRoutes[F]): Resource[F, Server] = Resource.eval {
      val builder =
        BlazeServerBuilder[F]
          .bindHttp(conf.webServer.port, conf.webServer.host)
          .withHttpWebSocketApp(wsb => all(wsb).orNotFound)
      ssl.map(_.fold(builder)(builder.withSslContext)).map(_.resource)
    }.flatten

    def router(wsBuilder: WebSocketBuilder2[F]) = Router[F](
      "/"                    -> new StaticRoutes(conf.mode === Mode.Development, OcsBuildInfo.builtAtMillis).service,
      "/api/engage/commands" -> new EngageCommandRoutes(as, se).service,
      "/api"                 -> new EngageUIApiRoutes(conf.site, conf.mode, as, clientsDb, outputs)
        .service(wsBuilder)
    )

    val pingRouter = Router[F](
      "/ping" -> new PingRoutes(as).service
    )

    def loggedRoutes(wsBuilder: WebSocketBuilder2[F]) =
      pingRouter <+> Http4sLogger.httpRoutes(logHeaders = false, logBody = false)(router(wsBuilder))

    build(loggedRoutes)

  }

  def redirectWebServer[F[_]: Async](conf: WebServerConfiguration): Resource[F, Server] = {
    val router = Router[F](
      "/" -> new RedirectToHttpsRoutes[F](443, conf.externalBaseUrl).service
    )

    BlazeServerBuilder[F]
      .bindHttp(conf.insecurePort, conf.host)
      .withHttpApp(router.orNotFound)
      .resource
  }

  def printBanner[F[_]: Logger](conf: EngageConfiguration): F[Unit] = {
    val banner = """
    ______
   / ____/___  ____ _____ _____ ____
  / __/ / __ \/ __ `/ __ `/ __ `/ _ \
 / /___/ / / / /_/ / /_/ / /_/ /  __/
/_____/_/ /_/\__, /\__,_/\__, /\___/
            /____/      /____/

"""
    val msg    =
      s"""Start web server for site ${conf.site} on ${conf.mode} mode, version ${OcsBuildInfo.version}"""
    Logger[F].info(banner + msg)
  }

  // We need to manually update the configuration of the logging subsystem
  // to support capturing log messages and forward them to the clients
  def logToClients(
    out:        Topic[IO, EngageEvent],
    dispatcher: Dispatcher[IO]
  ): IO[Appender[ILoggingEvent]] = IO.apply {
    import ch.qos.logback.classic.{AsyncAppender, Logger, LoggerContext}
    import org.slf4j.LoggerFactory

    val asyncAppender = new AsyncAppender
    val appender      = new AppenderForClients(out)(dispatcher)
    Option(LoggerFactory.getILoggerFactory)
      .collect { case lc: LoggerContext =>
        lc
      }
      .foreach { ctx =>
        asyncAppender.setContext(ctx)
        appender.setContext(ctx)
        asyncAppender.addAppender(appender)
      }

    Option(LoggerFactory.getLogger("engage"))
      .collect { case l: Logger =>
        l
      }
      .foreach { l =>
        l.addAppender(asyncAppender)
        asyncAppender.start()
        appender.start()
      }
    asyncAppender
  }

  // Logger of error of last resort.
  def logError[F[_]: Logger]: PartialFunction[Throwable, F[Unit]] = {
    case e: EngageFailure =>
      Logger[F].error(e)(s"Engage global error handler ${EngageFailure.explain(e)}")
    case e: Exception     => Logger[F].error(e)("Engage global error handler")
  }

  /** Reads the configuration and launches the engage engine and web server */
  def engage[I]: IO[ExitCode] = {

    // Override the default client config
    def client(timeout: Duration): Resource[IO, Client[IO]] =
      EmberClientBuilder.default[IO].withTimeout(timeout).build

    def engineIO(
      conf:       EngageConfiguration,
      httpClient: Client[IO]
    ): Resource[IO, EngageEngine[IO]] =
      for {
        dspt <- Dispatcher.sequential[IO]
        cas  <- CaServiceInit.caInit[IO](conf.engageEngine)
        sys  <-
          Systems
            .build[IO](conf.site, httpClient, conf.engageEngine, cas)(Async[IO], dspt, Parallel[IO])
        seqE <- Resource.eval[IO, EngageEngine[IO]](
                  EngageEngine.build[IO](conf.site, sys, conf.engageEngine)
                )
      } yield seqE

    def webServerIO(
      conf: EngageConfiguration,
      out:  Topic[IO, EngageEvent],
      en:   EngageEngine[IO],
      cs:   ClientsSetDb[IO]
    ): Resource[IO, Unit] =
      for {
        as <- Resource.eval(authService[IO](conf.mode, conf.authentication))
        _  <- webServer[IO, I](conf, as, out, en, cs)
      } yield ()

    def publishStats[F[_]: Temporal](cs: ClientsSetDb[F]): Stream[F, Unit] =
      Stream.fixedRate[F](10.minute).flatMap(_ => Stream.eval(cs.report))

    val engage: Resource[IO, ExitCode] =
      for {
        _      <- Resource.eval(configLog[IO]) // Initialize log before the engine is setup
        conf   <- Resource.eval(config[IO].flatMap(loadConfiguration[IO]))
        _      <- Resource.eval(printBanner(conf))
        cli    <- client(10.seconds)
        out    <- Resource.eval(Topic[IO, EngageEvent])
        dsp    <- Dispatcher.sequential[IO]
        _      <- Resource.eval(logToClients(out, dsp))
        cs     <- Resource.eval(
                    Ref.of[IO, ClientsSetDb.ClientsSet](Map.empty).map(ClientsSetDb.apply[IO](_))
                  )
        _      <- Resource.eval(publishStats(cs).compile.drain.start)
        engine <- engineIO(conf, cli)
        _      <- webServerIO(conf, out, engine, cs)
        _      <- Resource.eval(
                    out.subscribers
                      .evalMap(l => Logger[IO].debug(s"Subscribers amount: $l").whenA(l > 1))
                      .compile
                      .drain
                      .start
                  )
        f      <- Resource.eval(
                    engine.eventStream.through(out.publish).compile.drain.onError(logError).start
                  )
        _      <- Resource.eval(f.join)        // We need to join to catch uncaught errors
      } yield ExitCode.Success

    engage.use(_ => IO.never)

  }

  /** Reads the configuration and launches Engage */
  override def run(args: List[String]): IO[ExitCode] =
    engage.guaranteeCase { oc =>
      if (oc.isSuccess) IO.unit
      else IO(Console.println(s"Exit code $oc")) // scalastyle:off console.io
    }

}
