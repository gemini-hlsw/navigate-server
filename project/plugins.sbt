resolvers ++= Resolver.sonatypeOssRepos("public")

addDependencyTreePlugin
addSbtPlugin("edu.gemini"     % "sbt-lucuma-app"      % "0.11.17")
// sbt revolver lets launching applications from the sbt console
addSbtPlugin("io.spray"       % "sbt-revolver"        % "0.10.0")
// Support making distributions
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")

// Extract metadata from sbt and make it available to the code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
