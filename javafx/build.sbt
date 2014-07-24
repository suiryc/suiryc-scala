Common.settings

name := "suiryc-scala-javafx"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % Common.versions("akka") % "provided",
  "org.clapper" %% "grizzled-slf4j" % Common.versions("grizzled") % "provided",
  "ch.qos.logback" % "logback-classic" % Common.versions("logback") % "provided",
  "ch.qos.logback" % "logback-core" % Common.versions("logback") % "provided"
)

