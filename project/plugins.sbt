
resolvers += Resolver.sonatypeRepo("public")

addSbtPlugin("edu.gemini"         % "sbt-gsp"                  % "0.2.3")
addSbtPlugin("com.geirsson"       % "sbt-ci-release"           % "1.5.3")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.1.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("com.timushev.sbt"   % "sbt-updates"              % "0.5.1")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.18.0")
// Support making distributions
addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager"      % "1.7.3")

addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.4.2")