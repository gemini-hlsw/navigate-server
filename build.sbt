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
val startEngageAllCommands = List(
  "engage_web_server/reStart"
)
val stopEngageAllCommands  = List(
  "engage_web_server/reStop"
)

addCommandAlias("startEngageAll", startEngageAllCommands.mkString(";", ";", ""))
addCommandAlias("stopEngageAll", stopEngageAllCommands.mkString(";", ";", ""))

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / updateOptions := updateOptions.value.withLatestSnapshots(false)

ThisBuild / evictionErrorLevel := Level.Info

//////////////
// Projects
//////////////

ThisBuild / crossScalaVersions := Seq("3.2.1")

lazy val root = tlCrossRootProject.aggregate(epics,
                                             stateengine,
                                             engage_web_server,
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
    libraryDependencies ++= Seq(
      UnboundId,
      JwtCore,
      JwtCirce,
      CommonsHttp,
      Log4CatsNoop.value,
      CatsEffect.value,
      Log4Cats.value,
      Http4sCirce,
      BooPickle.value,
      Http4sBoopickle,
      GrackleRoutes,
      Natchez
    ) ++
      Http4sClient ++ Http4s ++ PureConfig ++ Logging.value ++ MUnit.value ++ Grackle.value,
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
  .dependsOn(schema_util)

lazy val engage_model = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/model"))
  .enablePlugins(GitBranchPrompt)
  .settings(
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

lazy val schema_util = project
  .in(file("modules/schema-util"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      CatsEffect.value,
      Fs2,
      Log4Cats.value
    ) ++ MUnit.value ++ LucumaCore.value ++ Http4sClient ++ Grackle.value
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
  .dependsOn(engage_web_server)
  .aggregate(engage_web_server)
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
