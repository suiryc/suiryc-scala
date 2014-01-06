name := "suiryc-scala-log"

organization := "suiryc"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-core" % "1.0.11",
  "ch.qos.logback" % "logback-classic" % "1.0.11"
)

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath + "/.m2/repository")))

