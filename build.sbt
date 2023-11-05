import sbt._
import Keys._

lazy val versions = Map[String, String](
  "akka"          -> "2.6.21",
  "config"        -> "1.4.3",
  "javafx"        -> "12.0.1",
  "logback"       -> "1.4.11",
  "monix"         -> "3.4.0",
  "sbt-assembly"  -> "2.1.4",
  "scala"         -> "2.13.12",
  "scala-logging" -> "3.9.5",
  "scalatest"     -> "3.2.17",
  "scopt"         -> "4.1.0",
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


lazy val core = project.in(file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "suiryc-scala-core",
    libraryDependencies ++= Seq(
      "com.github.scopt"           %% "scopt"          % versions("scopt")         % Provided,
      "com.typesafe"               %  "config"         % versions("config")        % Provided,
      "com.typesafe.akka"          %% "akka-actor"     % versions("akka")          % Provided,
      "com.typesafe.akka"          %% "akka-slf4j"     % versions("akka")          % Provided,
      "com.typesafe.akka"          %% "akka-testkit"   % versions("akka")          % Test,
      "com.typesafe.scala-logging" %% "scala-logging"  % versions("scala-logging") % Provided,
      "io.monix"                   %% "monix"          % versions("monix")         % Provided,
      "io.spray"                   %% "spray-json"     % versions("spray-json")    % Provided,
      "org.openjfx"                %  "javafx-base"    % versions("javafx")        % Provided classifier jfxPlatform,
      "org.scalatest"              %% "scalatest"      % versions("scalatest")     % Test
    )
  )


lazy val log = project.in(file("log"))
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(
    name := "suiryc-scala-log",
    libraryDependencies ++= Seq(
      "ch.qos.logback"             %  "logback-classic" % versions("logback")       % Provided,
      "ch.qos.logback"             %  "logback-core"    % versions("logback")       % Provided,
      "com.typesafe.akka"          %% "akka-actor"      % versions("akka")          % Provided,
      "com.typesafe.scala-logging" %% "scala-logging"   % versions("scala-logging") % Provided
    )
  )


lazy val javaFX = project.in(file("javafx"))
  .dependsOn(core, log)
  .settings(commonSettings: _*)
  .settings(
    name := "suiryc-scala-javafx",
    libraryDependencies ++= Seq(
      "ch.qos.logback"             %  "logback-classic" % versions("logback")       % Provided,
      "ch.qos.logback"             %  "logback-core"    % versions("logback")       % Provided,
      "com.typesafe.akka"          %% "akka-actor"      % versions("akka")          % Provided,
      "com.typesafe.scala-logging" %% "scala-logging"   % versions("scala-logging") % Provided,
      "io.monix"                   %% "monix"           % versions("monix")         % Provided,
      "io.spray"                   %% "spray-json"      % versions("spray-json")    % Provided,
      "org.openjfx"                %  "javafx-base"     % versions("javafx")        % Provided classifier jfxPlatform,
      "org.openjfx"                %  "javafx-controls" % versions("javafx")        % Provided classifier jfxPlatform,
      "org.openjfx"                %  "javafx-graphics" % versions("javafx")        % Provided classifier jfxPlatform
    )
  )


lazy val sbtPlugins = project.in(file("sbt"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings.filterNot(_.key.key.label == "scalaVersion"): _*)
  .settings(
    name := "sbt-suiryc-scala",
    // Scala 2.12 compiler options.
    // Options that have been renamed or replaced between 2.12 and 2.13:
    //  '-deprecation' -> '-Xlint:deprecated'
    //  (most) '-Ywarn-...' -> '-W...'
    //  '-Xfatal-warnings' -> '-Werror'
    scalacOptions := Seq(
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-unchecked",
      "-Xcheckinit",
      "-Xfatal-warnings",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-extra-implicit",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-value-discard"
    ),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % versions("sbt-assembly") % Provided)
  )


// Notes: 'aggregate' can be used so that commands used on 'root' project can be executed in each subproject
// 'dependsOn' can be used so that an assembly jar can be built with all subprojects resources (and dependencies)
lazy val root = project.in(file("."))
  .aggregate(core, log, javaFX, sbtPlugins)
  .settings(commonSettings: _*)
  .settings(
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
