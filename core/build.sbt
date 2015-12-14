Common.settings

name := "suiryc-scala-core"

// TODO: how to properly handle dependencies like scopt/grizzled that are needed if calling some classes 'main' function ?
// TODO: get rid of grizzled and rely on akka logger only ?

libraryDependencies ++= Seq(
  "com.github.scopt"  %% "scopt"          % Common.versions("scopt"),
  "com.typesafe.akka" %% "akka-actor"     % Common.versions("akka")      % "provided",
  "com.typesafe"      %  "config"         % Common.versions("config")    % "provided",
  "junit"             %  "junit"          % Common.versions("junit")     % "test",
  "org.clapper"       %% "grizzled-slf4j" % Common.versions("grizzled"),
  "org.scalatest"     %% "scalatest"      % Common.versions("scalatest") % "test"
)
