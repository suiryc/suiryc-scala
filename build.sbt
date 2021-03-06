import sbt._
import Keys._

lazy val versions = Map[String, String](
  "akka"          -> "2.5.25",
  "config"        -> "1.3.4",
  "javafx"        -> "12.0.1",
  "logback"       -> "1.2.3",
  "monix"         -> "3.0.0",
  "scala"         -> "2.13.1",
  "scala-logging" -> "3.9.2",
  "scalatest"     -> "3.0.8",
  "scopt"         -> "3.7.1",
  "spray-json"    -> "1.3.5",
  "suiryc-scala"  -> "0.0.4-SNAPSHOT"
)

lazy val commonSettings = Seq(
  organization := "suiryc",
  version := versions("suiryc-scala"),
  scalaVersion := versions("scala"),

  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Werror",
    "-Xlint:_",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused:_",
    "-Ywarn-value-discard"
  ),
  resolvers += Resolver.mavenLocal,
  publishMavenStyle := true,
  publishTo := Some(Resolver.mavenLocal),
  publishArtifact in packageDoc := false
)


lazy val core = project.in(file("core")).
  settings(commonSettings:_*).
  settings(
    name := "suiryc-scala-core",
    libraryDependencies ++= Seq(
      "com.github.scopt"           %% "scopt"          % versions("scopt")         % "provided",
      "com.typesafe"               %  "config"         % versions("config")        % "provided",
      "com.typesafe.akka"          %% "akka-actor"     % versions("akka")          % "provided",
      "com.typesafe.akka"          %% "akka-slf4j"     % versions("akka")          % "provided",
      "com.typesafe.akka"          %% "akka-testkit"   % versions("akka")          % "test",
      "com.typesafe.scala-logging" %% "scala-logging"  % versions("scala-logging") % "provided",
      "io.monix"                   %% "monix"          % versions("monix")         % "provided",
      "io.spray"                   %% "spray-json"     % versions("spray-json")    % "provided",
      "org.openjfx"                %  "javafx-base"    % versions("javafx")        % "provided" classifier jfxPlatform,
      "org.scalatest"              %% "scalatest"      % versions("scalatest")     % "test"
    )
  )


lazy val log = project.in(file("log")).
  dependsOn(core).
  settings(commonSettings:_*).
  settings(
    name := "suiryc-scala-log",
    libraryDependencies ++= Seq(
      "ch.qos.logback"             %  "logback-classic" % versions("logback")       % "provided",
      "ch.qos.logback"             %  "logback-core"    % versions("logback")       % "provided",
      "com.typesafe.akka"          %% "akka-actor"      % versions("akka")          % "provided",
      "com.typesafe.scala-logging" %% "scala-logging"   % versions("scala-logging") % "provided"
    )
  )


lazy val javaFX = project.in(file("javafx")).
  dependsOn(core, log).
  settings(commonSettings:_*).
  settings(
    name := "suiryc-scala-javafx",
    libraryDependencies ++= Seq(
      "ch.qos.logback"             %  "logback-classic" % versions("logback")       % "provided",
      "ch.qos.logback"             %  "logback-core"    % versions("logback")       % "provided",
      "com.typesafe.akka"          %% "akka-actor"      % versions("akka")          % "provided",
      "com.typesafe.scala-logging" %% "scala-logging"   % versions("scala-logging") % "provided",
      "io.monix"                   %% "monix"           % versions("monix")         % "provided",
      "io.spray"                   %% "spray-json"      % versions("spray-json")    % "provided",
      "org.openjfx"                %  "javafx-base"     % versions("javafx")        % "provided" classifier jfxPlatform,
      "org.openjfx"                %  "javafx-controls" % versions("javafx")        % "provided" classifier jfxPlatform,
      "org.openjfx"                %  "javafx-graphics" % versions("javafx")        % "provided" classifier jfxPlatform
    )
  )


// Notes: 'aggregate' can be used so that commands used on 'root' project can be executed in each subproject
// 'dependsOn' can be used so that an assembly jar can be built with all subprojects resources (and dependencies)
lazy val root = project.in(file(".")).
  aggregate(core, log, javaFX).
  settings(commonSettings:_*).
  settings(
    name := "suiryc-scala",
    libraryDependencies := Seq.empty,
    publishArtifact in Compile := false
  )

lazy val jfxPlatform = {
  val osName = System.getProperty("os.name", "").toLowerCase
  if (osName.startsWith("mac")) "mac"
  else if (osName.startsWith("win")) "win"
  else "linux"
}
