Common.settings

name := "suiryc-scala-core"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % Common.versions("akka") % "provided",
  "com.typesafe" % "config" % Common.versions("config") % "provided",
  "org.clapper" %% "grizzled-slf4j" % Common.versions("grizzled") % "provided"
)

