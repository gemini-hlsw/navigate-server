import Settings.Libraries
import Settings.Plugins
import Common._
import Settings.Libraries._
import Settings.LibraryVersions
import AppsCommon._
import NativePackagerHelper._
import com.typesafe.sbt.packager.docker._

//lazy val attoVersion                 = "0.8.0"
//lazy val catsVersion                 = "2.1.1"
//lazy val collCompatVersion           = "2.1.6"
//lazy val monocleVersion              = "2.0.5"
//lazy val catsTestkitScalaTestVersion = "1.0.1"
//lazy val scalaJavaTimeVersion        = "2.0.0"
//lazy val jtsVersion                  = "0.0.9"
//lazy val svgdotjsVersion             = "0.0.1"

name := "engage"

Global / onChangedBuildSource := ReloadOnSourceChanges

Global / semanticdbEnabled := true

// Gemini repository
ThisBuild / resolvers ++= Seq(
  "Gemini Repository".at("https://github.com/gemini-hlsw/maven-repo/raw/master/releases"),
  "JCenter".at("https://jcenter.bintray.com/")
)

Global / resolvers ++= Resolver.sonatypeOssRepos("public")

enablePlugins(GitBranchPrompt)

// Custom commands to facilitate web development
val startEngageAllCommands   = List(
  "engage_web_server/reStart",
  "engage_web_client/Compile/fastOptJS/startWebpackDevServer",
  "~engage_web_client/fastOptJS"
)
val restartEngageWDSCommands = List(
  "engage_web_client/Compile/fastOptJS/stopWebpackDevServer",
  "engage_web_client/Compile/fastOptJS/startWebpackDevServer",
  "~engage_web_client/fastOptJS"
)
val stopEngageAllCommands    = List(
  "engage_web_server/reStop",
  "engage_web_client/Compile/fastOptJS/stopWebpackDevServer"
)

addCommandAlias("startEngageAll", startEngageAllCommands.mkString(";", ";", ""))
addCommandAlias("restartEngageWDS", restartEngageWDSCommands.mkString(";", ";", ""))
addCommandAlias("stopEngageAll", stopEngageAllCommands.mkString(";", ";", ""))

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / updateOptions := updateOptions.value.withLatestSnapshots(false)

ThisBuild / evictionErrorLevel := Level.Info

//////////////
// Projects
//////////////

lazy val root = tlCrossRootProject.aggregate(epics,
                                             stateengine,
                                             engage_web_server,
                                             engage_web_client,
                                             engage_model,
                                             app_engage_server
)

lazy val epics = project
  .in(file("modules/epics"))
  .settings(
    name                     := "epics",
    libraryDependencies ++= Seq(
      Cats.value,
      CatsEffect.value,
      Mouse.value,
      Fs2,
      EpicsCA,
      EpicsJCA % Test
    ) ++ MUnit.value ++ LucumaCore.value,
    Test / parallelExecution := false
  )

lazy val stateengine = project
  .in(file("modules/stateengine"))
  .settings(
    name := "stateengine",
    libraryDependencies ++= Seq(
      Libraries.Cats.value,
      Libraries.CatsEffect.value,
      Libraries.Mouse.value,
      Libraries.Fs2,
      Libraries.CatsLaws.value
    ) ++ Libraries.MUnit.value
  )

lazy val engage_web_server = project
  .in(file("modules/web/server"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    name                 := "engage_web_server",
    libraryDependencies ++= Seq(UnboundId,
                                JwtCore,
                                JwtCirce,
                                CommonsHttp,
                                ScalaMock,
                                Log4CatsNoop.value,
                                CatsEffect.value,
                                Log4Cats.value,
                                BooPickle.value,
                                Http4sBoopickle
    ) ++
      Http4sClient ++ Http4s ++ PureConfig ++ Logging.value ++ MUnit.value,
    // Supports launching the server in the background
    reStart / mainClass  := Some("engage.web.server.http4s.WebServerLauncher"),
    Compile / bspEnabled := false
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoObject           := "OcsBuildInfo",
    buildInfoPackage          := "engage.web.server"
  )
  .dependsOn(engage_server)
  .dependsOn(engage_model.jvm % "compile->compile;test->test")

lazy val engage_web_client = project
  .in(file("modules/web/client"))
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .disablePlugins(RevolverPlugin)
  .settings(
    // Needed for Monocle macros
    scalacOptions += "-Ymacro-annotations",
    scalacOptions ~= (_.filterNot(
      Set(
        // By necessity facades will have unused params
        "-Wunused:params",
        "-Wunused:explicits"
      )
    )),
    // Configurations for webpack
    fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    fullOptJS / webpackBundlingMode := BundlingMode.Application,
    webpackResources                := (baseDirectory.value / "src" / "webpack") * "*.js",
    webpackDevServerPort            := 9090,
    // Use a different Webpack configuration file for production and create a single bundle without source maps
    fullOptJS / webpackConfigFile   := Some(
      baseDirectory.value / "src" / "webpack" / "prod.webpack.config.js"
    ),
    fastOptJS / webpackConfigFile   := Some(
      baseDirectory.value / "src" / "webpack" / "dev.webpack.config.js"
    ),
    Test / webpackConfigFile        := Some(
      baseDirectory.value / "src" / "webpack" / "test.webpack.config.js"
    ),
    webpackEmitSourceMaps           := false,
    Test / parallelExecution        := false,
    installJsdom / version          := "16.4.0",
    Test / requireJsDomEnv          := true,
    // Use yarn as it is faster than npm
    useYarn                         := true,
    // JS dependencies via npm
    Compile / npmDependencies ++= Seq(
      "fomantic-ui-less" -> LibraryVersions.fomanticUI,
      "prop-types"       -> "15.7.2",
      "core-js"          -> "2.6.11" // Without this, core-js 3 is used, which conflicts with @babel/runtime-corejs2
    ),
    Compile / fastOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    Compile / fullOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    // NPM libs for development, mostly to let webpack do its magic
    Compile / npmDevDependencies ++= Seq(
      "postcss"                       -> "8.1.1",
      "postcss-loader"                -> "4.0.3",
      "autoprefixer"                  -> "10.0.1",
      "url-loader"                    -> "4.1.0",
      "file-loader"                   -> "6.0.0",
      "css-loader"                    -> "3.5.3",
      "style-loader"                  -> "1.2.1",
      "less"                          -> "3.9.0",
      "less-loader"                   -> "7.0.1",
      "webpack-merge"                 -> "4.2.2",
      "mini-css-extract-plugin"       -> "0.8.0",
      "webpack-dev-server-status-bar" -> "1.1.0",
      "cssnano"                       -> "4.1.10",
      "terser-webpack-plugin"         -> "3.0.6",
      "html-webpack-plugin"           -> "4.3.0",
      "css-minimizer-webpack-plugin"  -> "1.1.5",
      "favicons-webpack-plugin"       -> "4.2.0",
      "@packtracker/webpack-plugin"   -> "2.3.0"
    ),
    libraryDependencies ++= Seq(
      Cats.value,
      Mouse.value,
      CatsEffect.value,
      ScalaJSDom.value,
      JavaTimeJS.value,
      ScalaJSReactSemanticUI.value,
      ScalaJSReactVirtualized.value,
      ScalaJSReactClipboard.value,
      ScalaJSReactSortable.value,
      ScalaJSReactDraggable.value,
      GeminiLocales.value,
      LucumaUI.value,
      PPrint.value,
      TestLibs.value
    ) ++ MUnit.value ++ ReactScalaJS.value ++ Log4CatsLogLevel.value ++ Circe.value
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version),
    buildInfoObject           := "OcsBuildInfo",
    buildInfoPackage          := "engage.web.client"
  )
  .dependsOn(engage_model.js % "compile->compile;test->test")

lazy val engage_model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .enablePlugins(GitBranchPrompt)
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(
      Squants.value,
      Mouse.value,
      BooPickle.value,
      CatsTime.value
    ) ++ MUnit.value ++ Monocle.value ++ LucumaCore.value ++ Sttp.value ++ Circe.value
  )
  .jvmSettings(
    commonSettings,
    libraryDependencies += Http4sCore
  )
  .jsSettings(
    // And add a custom one
    libraryDependencies += JavaTimeJS.value,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )

lazy val engage_server = project
  .in(file("modules/server"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      CatsEffect.value,
      Fs2,
      Log4Cats.value
    ) ++ MUnit.value ++ LucumaCore.value ++ Http4sClient
  )
  .dependsOn(engage_model.jvm % "compile->compile;test->test")
  .dependsOn(epics)
  .dependsOn(stateengine)

/**
 * Project for the engage server app for development
 */
lazy val app_engage_server = preventPublication(project.in(file("app/engage-server")))
  .dependsOn(engage_web_server, engage_web_client)
  .aggregate(engage_web_server, engage_web_client)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(engageCommonSettings: _*)
  .settings(
    description          := "Engage server for local testing",
    // Put the jar files in the lib dir
    Universal / mappings += {
      val jar = (Compile / packageBin).value
      jar -> ("lib/" + jar.getName)
    },
    Universal / mappings := {
      // filter out sjs jar files. otherwise it could generate some conflicts
      val universalMappings = (Universal / mappings).value
      val filtered          = universalMappings.filter { case (_, name) =>
        !name.contains("_sjs")
      }
      filtered
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "update_smartgcal"
      f -> ("bin/" + f.getName)
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "engage-server.env"
      f -> ("systemd/" + f.getName)
    },
    Universal / mappings += {
      val f = (Compile / resourceDirectory).value / "engage-server.service"
      f -> ("systemd/" + f.getName)
    }
  )

/**
 * Common settings for the Engage instances
 */
lazy val engageCommonSettings = Seq(
  // Main class for launching
  Compile / mainClass           := Some("engage.web.server.http4s.WebServerLauncher"),
  // This is important to keep the file generation order correctly
  Universal / parallelExecution := false,
  // Depend on webpack and add the assets created by webpack
  Compile / packageBin / mappings ++= (engage_web_client / Compile / fullOptJS / webpack).value
    .map(f => f.data -> f.data.getName()),
  // Name of the launch script
  executableScriptName          := "engage-server",
  // Don't create launchers for Windows
  makeBatScripts                := Seq.empty,
  // Specify a different name for the config file
  bashScriptConfigLocation      := Some("${app_home}/../conf/launcher.args"),
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""",
  // Copy logback.xml to let users customize it on site
  Universal / mappings += {
    val f = (engage_web_server / Compile / resourceDirectory).value / "logback.xml"
    f -> ("conf/" + f.getName)
  },
  // Launch options
  Universal / javaOptions ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx1024m",
    "-J-Xms256m",
    // Support remote JMX access
    "-J-Dcom.sun.management.jmxremote",
    "-J-Dcom.sun.management.jmxremote.authenticate=false",
    "-J-Dcom.sun.management.jmxremote.port=2407",
    "-J-Dcom.sun.management.jmxremote.ssl=false",
    // Ensure the local is correctly set
    "-J-Duser.language=en",
    "-J-Duser.country=US",
    // Support remote debugging
    "-J-Xdebug",
    "-J-Xnoagent",
    "-J-XX:+HeapDumpOnOutOfMemoryError",
    // Make sure the application exits on OOM
    "-J-XX:+ExitOnOutOfMemoryError",
    "-J-XX:+CrashOnOutOfMemoryError",
    "-J-XX:HeapDumpPath=/tmp",
    "-J-Xrunjdwp:transport=dt_socket,address=8457,server=y,suspend=n",
    "-java-home ${app_home}/../jre" // This breaks builds without jre
  )
) ++ commonSettings
