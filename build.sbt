import Settings.Libraries
import Common.*
import Settings.Libraries.*
import Settings.LibraryVersions
import AppsCommon.*
import NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.*

name := "navigate"

Global / onChangedBuildSource := ReloadOnSourceChanges

Global / semanticdbEnabled := true

// Gemini repository
ThisBuild / resolvers ++= Seq(
  "Gemini Repository".at("https://github.com/gemini-hlsw/maven-repo/raw/master/releases"),
  "JCenter".at("https://jcenter.bintray.com/")
)

Global / resolvers ++= Resolver.sonatypeOssRepos("public")

// Uncomment for local testing
// ThisBuild / resolvers += "Local Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2/local"

ThisBuild / dockerExposedPorts ++= Seq(9090, 9091) // Must match deployed app.conf web-server.port
ThisBuild / dockerBaseImage := "eclipse-temurin:17-jre"

val mainCond                 = "github.ref == 'refs/heads/main'"
val geminiRepoCond           = "startsWith(github.repository, 'gemini')"
def allConds(conds: String*) = conds.mkString("(", " && ", ")")

lazy val dockerHubLogin =
  WorkflowStep.Run(
    List(
      "echo ${{ secrets.DOCKER_HUB_TOKEN }} | docker login --username nlsoftware --password-stdin"
    ),
    name = Some("Login to Docker Hub")
  )

lazy val sbtDockerPublish =
  WorkflowStep.Sbt(
    List("deploy/docker:publish"),
    name = Some("Build and Publish Docker image")
  )

ThisBuild / githubWorkflowAddedJobs +=
  WorkflowJob(
    "deploy",
    "Build and publish Docker image",
    githubWorkflowJobSetup.value.toList :::
      dockerHubLogin ::
      sbtDockerPublish ::
      Nil,
    scalas = List(scalaVersion.value),
    javas = githubWorkflowJavaVersions.value.toList.take(1),
    cond = Some(allConds(mainCond, geminiRepoCond))
  )

enablePlugins(GitBranchPrompt)

// Custom commands to facilitate web development
val startNavigateAllCommands = List(
  "navigate_web_server/reStart"
)
val stopNavigateAllCommands  = List(
  "navigate_web_server/reStop"
)

addCommandAlias("startNavigateAll", startNavigateAllCommands.mkString(";", ";", ""))
addCommandAlias("stopNavigateAll", stopNavigateAllCommands.mkString(";", ";", ""))

ThisBuild / scalafixDependencies += "edu.gemini" % "lucuma-schemas_3" % LibraryVersions.lucumaSchemas

//////////////
// Projects
//////////////

ThisBuild / crossScalaVersions := Seq("3.7.1")
ThisBuild / evictionErrorLevel := Level.Info

lazy val root = tlCrossRootProject.aggregate(
  epics,
  stateengine,
  navigate_server,
  navigate_web_server,
  navigate_model,
  deploy
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

lazy val createNpmProject = taskKey[Unit]("Create NPM project for Navigate web server")
lazy val npmPublish       = taskKey[Unit]("Run npm publish")

lazy val navigate_web_server = project
  .in(file("modules/web/server"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GitBranchPrompt)
  .settings(commonSettings: _*)
  .settings(
    name                := "navigate_web_server",
    libraryDependencies ++= Seq(
      Log4CatsNoop.value,
      CatsEffect.value,
      Log4Cats.value,
      Http4sCirce,
      GrackleRoutes,
      Natchez,
      LucumaSchemas
    ) ++
      Http4sClient ++ Http4s ++ PureConfig ++ Logging.value ++ MUnit.value ++ Grackle.value,
    // Supports launching the server in the background
    reStart / mainClass := Some("navigate.web.server.http4s.WebServerLauncher"),
    // Don't include configuration files in the JAR. We want them outside, so they are editable.
    Compile / packageBin / mappings ~= {
      _.filterNot(f => f._1.getName.endsWith("logback.xml"))
    },
    createNpmProject    := {
      val npmDir = target.value / "npm"

      IO.write(
        npmDir / "package.json",
        s"""|{
            |  "name": "navigate-server-schema",
            |  "version": "0.1.0-${version.value}",
            |  "license": "${licenses.value.head._1}"
            |}
            |""".stripMargin
      )

      // Replace the import path to the schema file to match the NPM package structure
      val schemaContent = IO
        .read(
          (Compile / resourceDirectory).value / "navigate.graphql"
        )
        .replace(
          "from \"lucuma/schemas/ObservationDB.graphql\"",
          "from \"lucuma-schemas/odb\""
        )
      IO.write(
        npmDir / "navigate.graphql",
        schemaContent
      )

      streams.value.log.info(s"Created NPM project in ${npmDir}")
    },
    npmPublish          := {
      import scala.sys.process._
      val npmDir = target.value / "npm"

      val _ = createNpmProject.value
      Process(List("npm", "publish"), npmDir).!!
      streams.value.log.info(s"Published NPM package from ${npmDir}")
    }
  )
  .settings(
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](name, version, buildInfoBuildNumber),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoObject           := "OcsBuildInfo",
    buildInfoPackage          := "navigate.web.server"
  )
  .dependsOn(navigate_server)
  .dependsOn(navigate_model % "compile->compile;test->test")
  .dependsOn(schema_util)

lazy val navigate_model = project
  .in(file("modules/model"))
  .enablePlugins(GitBranchPrompt)
  .settings(
    libraryDependencies ++= Seq(
      Mouse.value,
      Http4sCore,
      CatsTime.value
    ) ++ MUnit.value ++ Monocle.value ++ LucumaCore.value ++ Circe.value
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

lazy val navigate_server = project
  .in(file("modules/server"))
  .settings(commonSettings: _*)
  .enablePlugins(CluePlugin)
  .settings(
    libraryDependencies ++= Seq(
      CatsEffect.value,
      Fs2,
      Log4Cats.value,
      Http4sCirce,
      Clue,
      ClueHttp4s,
      LucumaSSO.value,
      LucumaSchemas
    ) ++ MUnit.value ++ LucumaCore.value ++ Http4sClient
  )
  .dependsOn(navigate_model % "compile->compile;test->test")
  .dependsOn(epics)
  .dependsOn(stateengine)

// Mappings for a particular release.
lazy val deployedAppMappings = Seq(
  // Copy the resource directory, with customized configuration files, but first remove existing mappings.
  Universal / mappings ++= { // maps =>
    val siteConfigDir: File                     = (ThisProject / baseDirectory).value / "conf"
    val siteConfigMappings: Seq[(File, String)] = directory(siteConfigDir).map(path =>
      path._1 -> ("conf/" + path._1.relativeTo(siteConfigDir).get.getPath)
    )
    siteConfigMappings
  }
)

/**
 * Common settings for the Navigate instances
 */
lazy val deployedAppSettings = Seq(
  // Main class for launching
  Compile / mainClass      := Some("navigate.web.server.http4s.WebServerLauncher"),
  // Name of the launch script
  executableScriptName     := "navigate-server",
  // Don't create launchers for Windows
  makeBatScripts           := Seq.empty,
  // Specify a different name for the config file
  bashScriptConfigLocation := Some("${app_home}/../conf/launcher.args"),
  bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/$SITE/logback.xml"""",
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
    "-J-Xrunjdwp:transport=dt_socket,address=8457,server=y,suspend=n"
  )
) ++ deployedAppMappings ++ commonSettings

/**
 * Project for the navigate server app for development
 */
lazy val deploy = preventPublication(project.in(file("deploy")))
  .dependsOn(navigate_web_server)
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(GitBranchPrompt)
  .settings(deployedAppSettings: _*)
  .settings(
    description            := "Navigate server",
    Docker / packageName   := "gpp-nav-server",
    Docker / daemonUserUid := Some("3624"),
    Docker / daemonUser    := "software",
    dockerBuildOptions ++= Seq("--platform", "linux/amd64"),
    dockerUpdateLatest     := true,
    dockerUsername         := Some("noirlab")
  )
