resolvers ++= Resolver.sonatypeOssRepos("public")

addDependencyTreePlugin
addSbtPlugin("edu.gemini"     % "sbt-lucuma-app"      % "0.12.13")
// sbt revolver lets launching applications from the sbt console
addSbtPlugin("io.spray"       % "sbt-revolver"        % "0.10.0")
// Support making distributions
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")

// Extract metadata from sbt and make it available to the code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
//
// Generate code for GraphQL queries
addSbtPlugin("edu.gemini"   % "sbt-clue"      % "0.43.1")
