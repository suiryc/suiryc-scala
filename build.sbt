import sbt._
import Keys._

lazy val versions = Map[String, String](
  "akka"          -> "2.6.15",
  "config"        -> "1.4.1",
  "javafx"        -> "12.0.1",
  "logback"       -> "1.2.3",
  "monix"         -> "3.4.0",
  "scala"         -> "2.13.6",
  "scala-logging" -> "3.9.3",
  "scalatest"     -> "3.2.9",
  "scopt"         -> "4.0.1",
  "spray-json"    -> "1.3.6",
  "suiryc-scala"  -> "0.0.4-SNAPSHOT"
)

lazy val commonSettings = Seq(
  organization := "suiryc",
  version := versions("suiryc-scala"),
  scalaVersion := versions("scala"),

  // For scalac options: https://docs.scala-lang.org/overviews/compiler-options/index.html
  // Notes:
  // 'UTF-8' encoding is the default.
  scalacOptions ++= Seq(
    // Explain type errors in more detail.
    "-explaintypes",
    // Emit warning and location for usages of features that should be imported explicitly.
    "-feature",
    // Enable additional warnings where generated code depends on assumptions.
    "-unchecked",
    // Fail the compilation if there are any warnings.
    "-Werror",
    // Warn when dead code is identified.
    "-Wdead-code",
    // Warn when more than one implicit parameter section is defined.
    "-Wextra-implicit",
    // Warn when numerics are widened.
    "-Wnumeric-widen",
    // Enable all unused warnings.
    "-Wunused",
    // Warn when non-Unit expression results are unused.
    "-Wvalue-discard",
    // Wrap field accessors to throw an exception on uninitialized access.
    "-Xcheckinit",
    // Enable all recommended warnings.
    "-Xlint"
  ),
  resolvers += Resolver.mavenLocal,
  publishMavenStyle := true,
  publishTo := Some(Resolver.mavenLocal)
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
    Compile / publishArtifact := false
  )

lazy val jfxPlatform = {
  val osName = System.getProperty("os.name", "").toLowerCase
  if (osName.startsWith("mac")) "mac"
  else if (osName.startsWith("win")) "win"
  else "linux"
}
