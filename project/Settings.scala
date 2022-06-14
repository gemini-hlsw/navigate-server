import sbt._
import java.lang.{ Runtime => JRuntime }
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

/**
 * Application settings and dependencies
 */
object Settings {

  /** Library versions */
  object LibraryVersions {
    // ScalaJS libraries
    val scalaDom                = "2.1.0"
    val scalajsReact            = "2.1.1"
    val booPickle               = "1.4.0"
    val javaTimeJS              = "2.4.0"
    val scalaJSReactCommon      = "0.14.7"
    val scalaJSSemanticUI       = "0.13.1"
    val scalaJSReactVirtualized = "0.13.1"
    val scalaJSReactClipboard   = "1.5.1"
    val scalaJSReactDraggable   = "0.14.3"
    val scalaJSReactSortable    = "0.5.2"

    // Scala libraries
    val catsEffect   = "3.3.5"
    val cats         = "2.7.0"
    val mouse        = "1.0.7"
    val fs2          = "3.2.4"
    val shapeless    = "2.3.7"
    val scalaParsers = "1.1.2"
    val scalaXml     = "1.2.0"
    val catsTime     = "0.5.0"

    // Logging
    val log4Cats         = "2.2.0"
    val log4CatsLogLevel = "0.3.0"

    val http4s      = "1.0.0-M27"
    val squants     = "1.8.2"
    val commonsHttp = "2.0.2"
    val unboundId   = "3.2.1"
    val jwt         = "5.0.0"
    val slf4j       = "1.7.35"
    val log4s       = "1.10.0"
    val logback     = "1.2.11"
    val janino      = "3.1.6"
    val logstash    = "7.0"
    val pureConfig  = "0.17.1"
    val monocle     = "3.1.0"
    val circe       = "0.14.2"
    val doobie      = "0.6.0"
    val flyway      = "6.0.4"

    // test libraries
    val xmlUnit                     = "1.6"
    val jUnitInterface              = "0.13.2"
    val scalaMock                   = "5.2.0"
    lazy val munitVersion           = "0.7.29"
    lazy val munitDisciplineVersion = "1.0.9"
    lazy val munitCatsEffectVersion = "1.0.6"

    val apacheXMLRPC        = "3.1.3"
    val opencsv             = "2.3"
    val epicsService        = "1.0.7"
    val gmpCommandRecords   = "0.7.7"
    val acm                 = "0.1.1"
    val giapi               = "1.1.7"
    val giapiJmsUtil        = "0.5.7"
    val giapiJmsProvider    = "1.6.7"
    val giapiCommandsClient = "0.2.7"
    val giapiStatusService  = "0.6.7"
    val gmpStatusGateway    = "0.3.7"
    val gmpStatusDatabase   = "0.3.7"
    val gmpCmdClientBridge  = "0.6.7"
    val guava               = "31.0.1-jre"
    val prometheusClient    = "0.14.1"
    val geminiLocales       = "0.7.0"
    val pprint              = "0.6.6"
    val jaxb                = "3.0.1"

    // EPICS Libraries
    val ca  = "1.3.2"
    val jca = "2.4.6"

    // Gemini Libraries
    val gspMath = "0.1.17"
    val gspCore = "0.1.8"
    val gppUI   = "0.0.3"

    // Lucuma
    val lucumaCore    = "0.24.0"
    val lucumaUI      = "0.25.0"
    val lucumaSchemas = "0.11.0"

    val clue = "0.20.2"

    val sttp = "3.5.1"

    // Pure JS libraries
    val fomanticUI = "2.8.7"

  }

  /**
   * Global libraries
   */
  object Libraries {
    // Test Libraries
    val TestLibs       = Def.setting(
      "org.typelevel" %%% "cats-testkit-scalatest" % "2.1.5" % "test"
    )
    val MUnit          = Def.setting(
      Seq(
        "org.scalameta" %%% "munit"               % LibraryVersions.munitVersion           % Test,
        "org.typelevel" %%% "munit-cats-effect-3" % LibraryVersions.munitCatsEffectVersion % Test,
        "org.typelevel" %%% "discipline-munit"    % LibraryVersions.munitDisciplineVersion % Test
      )
    )
    val XmlUnit        = "xmlunit" % "xmlunit" % LibraryVersions.xmlUnit % "test"
    val JUnitInterface =
      "com.novocode" % "junit-interface" % LibraryVersions.jUnitInterface % "test"
    val ScalaMock   = "org.scalamock"     %% "scalamock"          % LibraryVersions.scalaMock % "test"
    // Server side libraries
    val Cats        = Def.setting("org.typelevel" %%% "cats-core" % LibraryVersions.cats)
    val CatsEffect  =
      Def.setting("org.typelevel" %%% "cats-effect" % LibraryVersions.catsEffect)
    val Fs2         = "co.fs2"            %% "fs2-core"           % LibraryVersions.fs2
    val Fs2IO       = "co.fs2"            %% "fs2-io"             % LibraryVersions.fs2       % "test"
    val Mouse       = Def.setting("org.typelevel" %%% "mouse" % LibraryVersions.mouse)
    val Shapeless   = Def.setting("com.chuusai" %%% "shapeless" % LibraryVersions.shapeless)
    val CommonsHttp = "commons-httpclient" % "commons-httpclient" % LibraryVersions.commonsHttp
    val UnboundId   =
      "com.unboundid" % "unboundid-ldapsdk-minimal-edition" % LibraryVersions.unboundId
    val JwtCore          = "com.pauldijou" %% "jwt-core"     % LibraryVersions.jwt
    val JwtCirce         = "com.pauldijou" %% "jwt-circe"    % LibraryVersions.jwt
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
    val PrometheusClient =
      "io.prometheus" % "simpleclient_common" % LibraryVersions.prometheusClient
    val Logging          = Def.setting(Seq(JuliSlf4j, Log4s.value) ++ Logback)
    val PureConfig       = Seq(
      "com.github.pureconfig" %% "pureconfig"             % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats"        % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % LibraryVersions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-http4s"      % LibraryVersions.pureConfig
    )
    val OpenCSV          = "net.sf.opencsv" % "opencsv"          % LibraryVersions.opencsv
    val Squants          = Def.setting("org.typelevel" %%% "squants" % LibraryVersions.squants)
    val ScalaXml         =
      Def.setting("org.scala-lang.modules" %%% "scala-xml" % LibraryVersions.scalaXml)
    val Http4s           = Seq("org.http4s" %% "http4s-dsl" % LibraryVersions.http4s,
                     "org.http4s" %% "http4s-blaze-server" % LibraryVersions.http4s
    )
    val Http4sClient     = Seq(
      "org.http4s" %% "http4s-dsl"               % LibraryVersions.http4s,
      "org.http4s" %% "http4s-async-http-client" % LibraryVersions.http4s
    )
    val Http4sBoopickle  = "org.http4s"    %% "http4s-boopickle" % LibraryVersions.http4s
    val Http4sCore       = "org.http4s"    %% "http4s-core"      % LibraryVersions.http4s
    val Http4sCirce      = "org.http4s"    %% "http4s-circe"     % LibraryVersions.http4s
    val Http4sXml        = "org.http4s"    %% "http4s-scala-xml" % LibraryVersions.http4s
    val Http4sPrometheus =
      "org.http4s" %% "http4s-prometheus-metrics" % LibraryVersions.http4s
    val Monocle = Def.setting(
      Seq(
        "dev.optics" %%% "monocle-core"   % LibraryVersions.monocle,
        "dev.optics" %%% "monocle-macro"  % LibraryVersions.monocle,
        "dev.optics" %%% "monocle-unsafe" % LibraryVersions.monocle,
        "dev.optics" %%% "monocle-law"    % LibraryVersions.monocle
      )
    )
    val Circe   = Def.setting(
      Seq(
        "io.circe" %%% "circe-core"    % LibraryVersions.circe,
        "io.circe" %%% "circe-generic" % LibraryVersions.circe,
        "io.circe" %%% "circe-parser"  % LibraryVersions.circe,
        "io.circe" %%% "circe-testing" % LibraryVersions.circe % "test"
      )
    )

    // Client Side JS libraries
    val ReactScalaJS            = Def.setting(
      Seq(
        "com.github.japgolly.scalajs-react" %%% "core"               % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "extra"              % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "extra-ext-monocle3" % LibraryVersions.scalajsReact,
        "com.github.japgolly.scalajs-react" %%% "core-ext-cats"      % LibraryVersions.scalajsReact
      )
    )
    val ScalaJSDom              = Def.setting("org.scala-js" %%% "scalajs-dom" % LibraryVersions.scalaDom)
    val ScalaJSReactCommon      =
      Def.setting("io.github.cquiroz.react" %%% "common" % LibraryVersions.scalaJSReactCommon)
    val ScalaJSReactCats        =
      Def.setting("io.github.cquiroz.react" %%% "cats" % LibraryVersions.scalaJSReactCommon)
    val ScalaJSReactSemanticUI  = Def.setting(
      "io.github.cquiroz.react" %%% "react-semantic-ui" % LibraryVersions.scalaJSSemanticUI
    )
    val ScalaJSReactVirtualized = Def.setting(
      "io.github.cquiroz.react" %%% "react-virtualized" % LibraryVersions.scalaJSReactVirtualized
    )
    val ScalaJSReactDraggable   = Def.setting(
      "io.github.cquiroz.react" %%% "react-draggable" % LibraryVersions.scalaJSReactDraggable
    )
    val ScalaJSReactSortable    = Def.setting(
      "io.github.cquiroz.react" %%% "react-sortable-hoc" % LibraryVersions.scalaJSReactSortable
    )
    val ScalaJSReactClipboard   = Def.setting(
      "io.github.cquiroz.react" %%% "react-clipboard" % LibraryVersions.scalaJSReactClipboard
    )
    val BooPickle               = Def.setting("io.suzaku" %%% "boopickle" % LibraryVersions.booPickle)
    val JavaTimeJS              =
      Def.setting("io.github.cquiroz" %%% "scala-java-time" % LibraryVersions.javaTimeJS)
    val GeminiLocales           =
      Def.setting("edu.gemini" %%% "gemini-locales" % LibraryVersions.geminiLocales)
    val PPrint                  = Def.setting("com.lihaoyi" %%% "pprint" % LibraryVersions.pprint)

    val JAXB = Seq("javax.xml.bind" % "jaxb-api" % LibraryVersions.jaxb,
                   "org.glassfish.jaxb" % "jaxb-runtime" % LibraryVersions.jaxb
    )

    // GIAPI Libraries
    val EpicsService       = "edu.gemini.epics" % "epics-service" % LibraryVersions.epicsService
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

    // Gemini Libraries
//    val GspMath = Def.setting("edu.gemini" %%% "gsp-math" % LibraryVersions.gspMath)
//    val GspMathTestkit =
//      Def.setting("edu.gemini" %%% "gsp-math-testkit" % LibraryVersions.gspMath % "test")
//    val GspCoreModel = Def.setting("edu.gemini" %%% "gsp-core-model" % LibraryVersions.gspCore)
//    val GspCoreTestkit =
//      Def.setting("edu.gemini" %%% "gsp-core-testkit" % LibraryVersions.gspCore % "test")
//    val GspCoreOcs2Api = Def.setting("edu.gemini" %%% "gsp-core-ocs2-api" % LibraryVersions.gspCore)
//    val GppUI          = Def.setting("edu.gemini" %%% "gpp-ui" % LibraryVersions.gppUI)

    // Lucuma libraries
    val LucumaCore    = Def.setting(
      Seq(
        "edu.gemini" %%% "lucuma-core"         % LibraryVersions.lucumaCore,
        "edu.gemini" %%% "lucuma-core-testkit" % LibraryVersions.lucumaCore
      )
    )
    val LucumaUI      = Def.setting("edu.gemini" %%% "lucuma-ui" % LibraryVersions.lucumaUI)
    val LucumaSchemas = "edu.gemini" %% "lucuma-schemas" % LibraryVersions.lucumaSchemas

    val Clue          = "edu.gemini" %% "clue-core"              % LibraryVersions.clue
    val ClueHttp4s    = "edu.gemini" %% "clue-http4s-jdk-client" % LibraryVersions.clue
    val ClueGenerator = "edu.gemini" %% "clue-generator"         % LibraryVersions.clue

    val Sttp = Def.setting(
      Seq(
        "com.softwaremill.sttp.client3" %%% "core"  % LibraryVersions.sttp,
        "com.softwaremill.sttp.client3" %%% "circe" % LibraryVersions.sttp,
        "com.softwaremill.sttp.client3" %%% "cats"  % LibraryVersions.sttp
      )
    )
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
