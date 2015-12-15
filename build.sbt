import sbt._
import Keys._

lazy val versions = Map[String, String](
  "akka"         -> "2.4.1",
  "config"       -> "1.3.0",
  "grizzled"     -> "1.0.2",
  "junit"        -> "4.12",
  "logback"      -> "1.1.3",
  "scala"        -> "2.11.7",
  "scalatest"    -> "2.2.4",
  "scopt"        -> "3.3.0",
  "suiryc-scala" -> "0.0.2-SNAPSHOT"
)

lazy val commonSettings = Seq(
  organization := "suiryc",
  version := versions("suiryc-scala"),
  scalaVersion := versions("scala"),

  scalacOptions ++= Seq(
    //"-optimize",
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
    // TODO: how to properly handle dependencies like scopt/grizzled that are needed if calling some classes 'main' function ?
    // TODO: get rid of grizzled and rely on akka logger only ?
    libraryDependencies ++= Seq(
      "com.github.scopt"  %% "scopt"          % versions("scopt"),
      "com.typesafe.akka" %% "akka-actor"     % versions("akka")      % "provided",
      "com.typesafe"      %  "config"         % versions("config")    % "provided",
      "junit"             %  "junit"          % versions("junit")     % "test",
      "org.clapper"       %% "grizzled-slf4j" % versions("grizzled"),
      "org.scalatest"     %% "scalatest"      % versions("scalatest") % "test"
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
      "com.typesafe.akka" %% "akka-actor"      % versions("akka")     % "provided",
      "org.clapper"       %% "grizzled-slf4j"  % versions("grizzled") % "provided",
      "ch.qos.logback"    %  "logback-classic" % versions("logback")  % "provided",
      "ch.qos.logback"    %  "logback-core"    % versions("logback")  % "provided"
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
