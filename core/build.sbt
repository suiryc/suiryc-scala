Common.settings

name := "suiryc-scala-core"

scalacOptions += "-target:jvm-1.7"

libraryDependencies ++= Seq(
  "org.clapper" %% "grizzled-slf4j" % Common.versions("grizzled") % "provided",
  "com.typesafe" % "config" % Common.versions("config") % "provided"
)

