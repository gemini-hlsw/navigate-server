import sbt.*
import java.lang.{Runtime => JRuntime}
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

/**
 * Application settings and dependencies
 */
object Settings {

  /** Library versions */
  object LibraryVersions {

    // Scala libraries
    val catsEffect = "3.6.0"
    val cats       = "2.13.0"
    val mouse      = "1.3.2"
    val fs2        = "3.12.0"
    val catsTime   = "0.5.1"

    // Logging
    val log4Cats         = "2.7.0"
    val log4CatsLogLevel = "0.3.1"

    val http4s      = "0.23.30"
    val http4sBlaze = "0.23.17"
    val slf4j       = "2.0.17"
    val log4s       = "1.10.0"
    val logback     = "1.5.18"
    val janino      = "3.1.12"
    val logstash    = "7.0"
    val pureConfig  = "0.17.8"
    val monocle     = "3.3.0"
    val circe       = "0.14.12"

    // test libraries
    val scalaMock              = "5.2.0"
    val munitCatsEffectVersion = "2.0.0"

    val gmpCommandRecords   = "0.7.7"
    val giapi               = "1.1.7"
    val giapiJmsUtil        = "0.5.7"
    val giapiJmsProvider    = "1.6.7"
    val giapiCommandsClient = "0.2.7"
    val giapiStatusService  = "0.6.7"
    val gmpStatusGateway    = "0.3.7"
    val gmpStatusDatabase   = "0.3.7"
    val gmpCmdClientBridge  = "0.6.7"
    val guava               = "31.0.1-jre"
    val geminiLocales       = "0.7.0"
    val pprint              = "0.8.1"

    // EPICS Libraries
    val ca  = "1.3.2"
    val jca = "2.4.10"

    // Lucuma
    val lucumaCore    = "0.120.0"
    val lucumaSchemas = "0.123.2"
    val navigateUi    = "0.5.10"

    val grackle = "0.24.0"

    val graphQLRoutes = "0.8.17"

    val clue = "0.35.0"

    // Natchez
    val natchez = "0.3.7"
  }

  /**
   * Global libraries
   */
  object Libraries {
    // Test Libraries
    val TestLibs         = Def.setting(
      "org.typelevel" %%% "cats-testkit-scalatest" % "2.1.5" % "test"
    )
    val MUnit            = Def.setting(
      Seq(
        "org.typelevel" %% "munit-cats-effect" % LibraryVersions.munitCatsEffectVersion % Test
      )
    )
    val ScalaMock        = "org.scalamock" %% "scalamock"    % LibraryVersions.scalaMock % "test"
    // Server side libraries
    val Cats             = Def.setting("org.typelevel" %%% "cats-core" % LibraryVersions.cats)
    val CatsLaws         = Def.setting("org.typelevel" %%% "cats-laws" % LibraryVersions.cats % "test")
    val CatsEffect       =
      Def.setting("org.typelevel" %%% "cats-effect" % LibraryVersions.catsEffect)
    val Fs2              = "co.fs2"        %% "fs2-core"     % LibraryVersions.fs2
    val Fs2IO            = "co.fs2"        %% "fs2-io"       % LibraryVersions.fs2       % "test"
    val Mouse            = Def.setting("org.typelevel" %%% "mouse" % LibraryVersions.mouse)
    val Slf4j            = "org.slf4j"      % "slf4j-api"    % LibraryVersions.slf4j
    val JuliSlf4j        = "org.slf4j"      % "jul-to-slf4j" % LibraryVersions.slf4j
    val NopSlf4j         = "org.slf4j"      % "slf4j-nop"    % LibraryVersions.slf4j
    val CatsTime         = Def.setting(
      "org.typelevel" %%% "cats-time" % LibraryVersions.catsTime % "compile->compile;test->test"
    )
    val Log4s            = Def.setting("org.log4s" %%% "log4s" % LibraryVersions.log4s)
    val Log4Cats         = Def.setting("org.typelevel" %%% "log4cats-slf4j" % LibraryVersions.log4Cats)
    val Log4CatsLogLevel = Def.setting(
      Seq(
        "org.typelevel" %%% "log4cats-core"     % LibraryVersions.log4Cats,
        "com.rpiaggio"  %%% "log4cats-loglevel" % LibraryVersions.log4CatsLogLevel
      )
    )
    val Log4CatsNoop     =
      Def.setting("org.typelevel" %%% "log4cats-noop" % LibraryVersions.log4Cats % "test")
    val Logback          = Seq(
      "ch.qos.logback"      % "logback-core"    % LibraryVersions.logback,
      "ch.qos.logback"      % "logback-classic" % LibraryVersions.logback,
      "org.codehaus.janino" % "janino"          % LibraryVersions.janino
    )
    val Logging          = Def.setting(Seq(JuliSlf4j, Log4s.value) ++ Logback)
    val PureConfig       = Seq(
      "com.github.pureconfig" %% "pureconfig-core"        % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats"        % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-http4s"      % LibraryVersions.pureConfig
    )
    val Http4s           = Seq("org.http4s" %% "http4s-dsl" % LibraryVersions.http4s,
                     "org.http4s" %% "http4s-blaze-server" % LibraryVersions.http4sBlaze
    )
    val Http4sClient     = Seq(
      "org.http4s" %% "http4s-dsl"          % LibraryVersions.http4s,
      "org.http4s" %% "http4s-ember-client" % LibraryVersions.http4s
    )
    val Http4sCore       = "org.http4s"    %% "http4s-core"  % LibraryVersions.http4s
    val Http4sCirce      = "org.http4s"    %% "http4s-circe" % LibraryVersions.http4s
    val Monocle          = Def.setting(
      Seq(
        "dev.optics" %%% "monocle-core"   % LibraryVersions.monocle,
        "dev.optics" %%% "monocle-macro"  % LibraryVersions.monocle,
        "dev.optics" %%% "monocle-unsafe" % LibraryVersions.monocle,
        "dev.optics" %%% "monocle-law"    % LibraryVersions.monocle
      )
    )
    val Circe            = Def.setting(
      Seq(
        "io.circe" %%% "circe-core"    % LibraryVersions.circe,
        "io.circe" %%% "circe-generic" % LibraryVersions.circe,
        "io.circe" %%% "circe-parser"  % LibraryVersions.circe,
        "io.circe" %%% "circe-testing" % LibraryVersions.circe % "test"
      )
    )

    // GIAPI Libraries
    val GmpCommandsRecords =
      "edu.gemini.gmp" % "gmp-commands-records" % LibraryVersions.gmpCommandRecords
    val GiapiJmsUtil     = "edu.gemini.aspen" % "giapi-jms-util" % LibraryVersions.giapiJmsUtil
    val GiapiJmsProvider =
      "edu.gemini.jms" % "jms-activemq-provider" % LibraryVersions.giapiJmsProvider
    val Giapi               = "edu.gemini.aspen" % "giapi" % LibraryVersions.giapi
    val GiapiCommandsClient =
      "edu.gemini.aspen.gmp" % "gmp-commands-jms-client" % LibraryVersions.giapiCommandsClient
    val GiapiStatusService =
      "edu.gemini.aspen" % "giapi-status-service" % LibraryVersions.giapiStatusService
    val GmpStatusGateway =
      "edu.gemini.aspen.gmp" % "gmp-status-gateway" % LibraryVersions.gmpStatusGateway
    val GmpStatusDatabase =
      "edu.gemini.aspen.gmp" % "gmp-statusdb" % LibraryVersions.gmpStatusDatabase
    val GmpCmdJmsBridge =
      "edu.gemini.aspen.gmp" % "gmp-commands-jms-bridge" % LibraryVersions.gmpCmdClientBridge
    val Guava = "com.google.guava" % "guava" % LibraryVersions.guava

    // EPICS channel access libraries
//    val EpicsCAJ = "edu.gemini.external.osgi.com.cosylab.epics.caj" % "caj" % LibraryVersions.caj
    val EpicsJCA = "org.epics" % "jca" % LibraryVersions.jca
    val EpicsCA  = "org.epics" % "ca"  % LibraryVersions.ca

    // Lucuma libraries
    val LucumaCore    = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-core"         % LibraryVersions.lucumaCore,
        "edu.gemini" %%% "lucuma-core-testkit" % LibraryVersions.lucumaCore
      )
    )
    val LucumaSchemas = "edu.gemini" %% "lucuma-schemas" % LibraryVersions.lucumaSchemas

    val NavigateUi = "edu.gemini" % "navigate-ui" % LibraryVersions.navigateUi

    val Grackle = Def.setting(
      Seq(
        "org.typelevel" %% "grackle-core"    % LibraryVersions.grackle,
        "org.typelevel" %% "grackle-generic" % LibraryVersions.grackle,
        "org.typelevel" %% "grackle-circe"   % LibraryVersions.grackle
      )
    )

    val GrackleRoutes =
      "edu.gemini" %% "lucuma-graphql-routes" % LibraryVersions.graphQLRoutes

    val Clue          = "edu.gemini" %% "clue-core"              % LibraryVersions.clue
    val ClueHttp4s    = "edu.gemini" %% "clue-http4s-jdk-client" % LibraryVersions.clue
    val ClueGenerator = "edu.gemini" %% "clue-generator"         % LibraryVersions.clue

    val Natchez = "org.tpolecat" %% "natchez-core" % LibraryVersions.natchez
  }

  object PluginVersions {
    // Compiler plugins
    val kpVersion        = "0.11.0"
    val betterMonadicFor = "0.3.1"
  }

  object Plugins {
    val kindProjectorPlugin =
      ("org.typelevel" % "kind-projector" % PluginVersions.kpVersion).cross(CrossVersion.full)
    val betterMonadicForPlugin =
      "com.olegpy" %% "better-monadic-for" % PluginVersions.betterMonadicFor
  }

}
