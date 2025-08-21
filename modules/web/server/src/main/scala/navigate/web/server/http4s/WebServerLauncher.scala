// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.Parallel
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.syntax.all.*
import clue.http4s.Http4sHttpBackend
import fs2.Stream
import fs2.compression.Compression
import fs2.io.file.Files
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import natchez.Trace.Implicits.noop
import navigate.model.config.*
import navigate.server.CaServiceInit
import navigate.server.NavigateEngine
import navigate.server.NavigateFailure
import navigate.server.Systems
import navigate.web.server.OcsBuildInfo
import navigate.web.server.common.LogInitialization
import navigate.web.server.common.RedirectToHttpsRoutes
import navigate.web.server.common.StaticRoutes
import navigate.web.server.common.baseDir
import navigate.web.server.config.*
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.Server
import org.http4s.server.middleware.Logger as Http4sLogger
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigObjectSource
import pureconfig.ConfigSource

import java.nio.file.Files as JavaFiles
import java.util.Locale
import scala.concurrent.duration.*

object WebServerLauncher extends IOApp with LogInitialization {
  private val ProxyRoute: Uri.Path = Uri.Path.empty / "db"

  private given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("navigate")

  // Try to load configs for deployment and staging and fall back to the common one in the class path
  private def config[F[_]: {Sync, Logger}]: F[ConfigObjectSource] =
    for
      confDir    <- baseDir[F].map(_.resolve("conf"))
      secretsConf = confDir.resolve("local").resolve("secrets.conf")
      systemsConf = confDir.resolve("local").resolve("systems.conf")
      site        = sys.env.get("SITE").getOrElse("develop")
      siteConf    = confDir.resolve(site).resolve("site.conf")
      _          <- Logger[F].info("Loading configuration:")
      _          <- Logger[F].info:
                      s" - $systemsConf (present: ${JavaFiles.exists(systemsConf)}), with fallback:"
      _          <- Logger[F].info:
                      s" - $secretsConf (present: ${JavaFiles.exists(secretsConf)}), with fallback:"
      _          <- Logger[F].info(s" - $siteConf (present: ${JavaFiles.exists(siteConf)}), with fallback:")
      _          <- Logger[F].info(s" - <resources>/base.conf")
    yield ConfigSource
      .file(systemsConf)
      .optional
      .withFallback:
        ConfigSource
          .file(secretsConf)
          .optional
          .withFallback:
            ConfigSource.file(siteConf).optional.withFallback(ConfigSource.resources("base.conf"))

  def makeContext[F[_]: Network](tls: TLSConfig): F[TLSContext[F]] =
    Network[F].tlsContext.fromKeyStoreFile(
      tls.keyStore,
      tls.keyStorePwd.toCharArray(),
      tls.certPwd.toCharArray()
    )

  /** Resource that yields the running web server */
  def webServer[F[_]: {Async, Files, Compression, Network}](
    conf:   NavigateConfiguration,
    topics: TopicManager[F],
    se:     NavigateEngine[F]
  ): Resource[F, Server] = {
    val ssl: F[Option[TLSContext[F]]] = conf.webServer.tls.traverse(makeContext[F])

    def router(wsBuilder: WebSocketBuilder2[F], proxyService: HttpRoutes[F]) = Router[F](
      "/"                 -> new StaticRoutes().service,
      "/navigate"         -> new GraphQlRoutes(
        conf,
        se,
        topics.loggingEvents,
        topics.guideState,
        topics.guidersQuality,
        topics.telescopeState,
        topics.acquisitionAdjustment,
        topics.targetAdjustment,
        topics.originAdjustment,
        topics.pointingAdjustment,
        topics.logBuffer
      )
        .service(wsBuilder),
      ProxyRoute.toString -> proxyService
    )

    def loggedRoutes(wsBuilder: WebSocketBuilder2[F], proxyService: HttpRoutes[F]) =
      Http4sLogger.httpRoutes(logHeaders = false, logBody = false)(
        router(wsBuilder, proxyService)
      )

    def builder(proxyService: HttpRoutes[F]) =
      EmberServerBuilder
        .default[F]
        .withHost(conf.webServer.host)
        .withPort(conf.webServer.port)
        .withHttpWebSocketApp(wsb => loggedRoutes(wsb, proxyService).orNotFound)

    for
      proxyService <- ProxyBuilder.buildService[F](conf.webServer.proxyBaseUri, ProxyRoute)
      server       <-
        ssl.toResource
          .flatMap(_.fold(builder(proxyService))(builder(proxyService).withTLS(_)).build)
    yield server
  }

  def redirectWebServer[F[_]: {Async, Network}](
    conf: WebServerConfiguration
  ): Resource[F, Server] = {
    val router = Router[F](
      "/" -> new RedirectToHttpsRoutes[F](443, conf.externalBaseUrl).service
    )

    EmberServerBuilder
      .default[F]
      .withHost(conf.host)
      .withPort(conf.insecurePort)
      .withHttpApp(router.orNotFound)
      .build
  }

  def printBanner[F[_]: Logger](conf: NavigateConfiguration): F[Unit] = {
    val banner = """
    _   __            _             __
   / | / /___ __   __(_)___ _____ _/ /____
  /  |/ / __ `/ | / / / __ `/ __ `/ __/ _ \
 / /|  / /_/ /| |/ / / /_/ / /_/ / /_/  __/
/_/ |_/\__,_/ |___/_/\__, /\__,_/\__/\___/
                    /____/

"""
    val msg    =
      s"""Start navigate server
        |  Site        : ${conf.site}
        |  Mode        : ${conf.mode} mode
        |  Version     : ${OcsBuildInfo.version}
        |  Web server  : ${conf.webServer.host}:${conf.webServer.port}
        |  External URL: ${conf.webServer.externalBaseUrl}
        |  ODB         : ${conf.navigateEngine.odb}
       """.stripMargin
    Logger[F].info(banner + msg)
  }

  // Logger of error of last resort.
  def logError[F[_]: Logger]: PartialFunction[Throwable, F[Unit]] = {
    case e: NavigateFailure =>
      Logger[F].error(e)(s"Navigate global error handler ${NavigateFailure.explain(e)}")
    case e: Exception       => Logger[F].error(e)("Navigate global error handler")
  }

  /** Reads the configuration and launches the navigate engine and web server */
  def navigate[I]: IO[ExitCode] = {

    // Override the default client config
    def client(timeout: Duration): Resource[IO, Client[IO]] =
      // Insecure as we call observe with self signed certificates
      TLSContext.Builder.forAsync[IO].insecureResource.flatMap { tls =>
        EmberClientBuilder
          .default[IO]
          .withTLSContext(tls)
          .withTimeout(timeout)
          .build
      }

    def engineIO(
      conf:       NavigateConfiguration,
      httpClient: Client[IO]
    ): Resource[IO, NavigateEngine[IO]] =
      for {
        backend <- Resource.pure(Http4sHttpBackend(httpClient))
        dspt    <- Dispatcher.sequential[IO]
        cas     <- CaServiceInit.caInit[IO](conf.navigateEngine)
        sys     <-
          Systems
            .build[IO](conf.site, httpClient, conf, cas)(using
              Async[IO],
              Logger[IO],
              backend,
              dspt,
              Parallel[IO]
            )
        seqE    <- Resource.eval[IO, NavigateEngine[IO]](
                     NavigateEngine.build[IO](conf.site, sys, conf.navigateEngine)
                   )
      } yield seqE

    def publishStats[F[_]: Temporal](cs: ClientsSetDb[F]): Stream[F, Unit] =
      Stream.fixedRate[F](10.minute).flatMap(_ => Stream.eval(cs.report))

    val navigate: Resource[IO, ExitCode] =
      for {
        _      <- Resource.eval(IO.delay(Locale.setDefault(Locale.ENGLISH)))
        _      <- Resource.eval(configLog[IO]) // Initialize log before the engine is setup
        conf   <- Resource.eval(config[IO].flatMap(loadConfiguration[IO]))
        _      <- Resource.eval(printBanner(conf))
        dsp    <- Dispatcher.sequential[IO]
        cli    <- client(10.seconds)
        topics <- TopicManager.create[IO](dsp)
        cs     <- Resource.eval(ClientsSetDb.create[IO])
        _      <- Resource.eval(publishStats(cs).compile.drain.start)
        engine <- engineIO(conf, cli)
        _      <- webServer[IO](conf, topics, engine)
        f      <- Resource.eval(topics.startAll(engine))
        _      <- Resource.eval(f.join)        // We need to join to catch uncaught errors
      } yield ExitCode.Success

    navigate.use(_ => IO.never)

  }

  /** Reads the configuration and launches Navigate */
  override def run(args: List[String]): IO[ExitCode] =
    navigate.guaranteeCase { oc =>
      if (oc.isSuccess) IO.unit
      else IO(Console.println(s"Exit code $oc")) // scalastyle:off console.io
    }

}
