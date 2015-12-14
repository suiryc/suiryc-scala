import sbt._
import Keys._

// TODO: refactor project definition; try to move everything in root build.sbt

object Common {

  val versions = Map[String, String](
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

  val settings: Seq[Setting[_]] =
    org.scalastyle.sbt.ScalastylePlugin.Settings ++
    Seq(
      organization := "suiryc",
      version := versions("suiryc-scala"),
      scalaVersion := versions("scala"),
      scalacOptions ++= Seq("-deprecation", "-feature", "-optimize", "-unchecked", "-Yinline-warnings"),
      scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits"),
      org.scalastyle.sbt.PluginKeys.config := file("project/scalastyle-config.xml"),
      resolvers += Resolver.mavenLocal,
      publishMavenStyle := true,
      publishTo := Some(Resolver.mavenLocal)
    )

}
