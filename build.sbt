import sbt._
import Keys._

lazy val versions = Map[String, String](
  "akka"          -> "2.5.8",
  "config"        -> "1.3.2",
  "junit"         -> "4.12",
  "logback"       -> "1.2.3",
  "scala"         -> "2.12.4",
  "scala-logging" -> "3.7.2",
  "scalatest"     -> "3.0.4",
  "scopt"         -> "3.7.0",
  "spray-json"    -> "1.3.4",
  "suiryc-scala"  -> "0.0.2-SNAPSHOT"
)

lazy val commonSettings = Seq(
  organization := "suiryc",
  version := versions("suiryc-scala"),
  scalaVersion := versions("scala"),

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-dead-code",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused",
    "-Ywarn-unused-import"
  ),
  scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits"),
  resolvers += Resolver.mavenLocal,
  publishMavenStyle := true,
  publishTo := Some(Resolver.mavenLocal)
)


lazy val core = project.in(file("core")).
  settings(commonSettings:_*).
  settings(
    name := "suiryc-scala-core",
    libraryDependencies ++= Seq(
      "com.github.scopt"           %% "scopt"          % versions("scopt"),
      "com.typesafe"               %  "config"         % versions("config")        % "provided",
      "com.typesafe.akka"          %% "akka-actor"     % versions("akka")          % "provided",
      "com.typesafe.akka"          %% "akka-testkit"   % versions("akka")          % "test",
      "com.typesafe.scala-logging" %% "scala-logging"  % versions("scala-logging"),
      "junit"                      %  "junit"          % versions("junit")         % "test",
      "io.spray"                   %% "spray-json"     % versions("spray-json"),
      "org.scalatest"              %% "scalatest"      % versions("scalatest")     % "test"
    )
  )


lazy val log = project.in(file("log")).
  dependsOn(core).
  settings(commonSettings:_*).
  settings(
    name := "suiryc-scala-log",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"      % versions("akka")    % "provided",
      "ch.qos.logback"    %  "logback-classic" % versions("logback") % "provided",
      "ch.qos.logback"    %  "logback-core"    % versions("logback") % "provided"
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
      "com.typesafe.akka"          %% "akka-actor"      % versions("akka")          % "provided"
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
