Common.settings

name := "suiryc-scala-javafx"

libraryDependencies ++= Seq(
  "org.clapper" %% "grizzled-slf4j" % Common.versions("grizzled") % "provided",
  "suiryc" %% "suiryc-scala-core" % Common.versions("suiryc-scala") % "provided",
  "com.typesafe.akka" %% "akka-actor" % Common.versions("akka") % "provided"
)

