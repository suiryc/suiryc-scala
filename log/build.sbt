Common.settings

name := "suiryc-scala-log"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-core" % Common.versions("logback") % "provided",
  "ch.qos.logback" % "logback-classic" % Common.versions("logback") % "provided"
)

