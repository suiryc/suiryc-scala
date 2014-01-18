Common.settings

name := "suiryc-scala-log"

scalacOptions += "-target:jvm-1.7"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-core" % "1.0.11",
  "ch.qos.logback" % "logback-classic" % "1.0.11"
)

