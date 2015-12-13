import sbt._
import Keys._


object Common {

  val versions = Map[String, String](
    "akka" -> "2.3.3",
    "config" -> "1.2.1",
    "grizzled" -> "1.0.2",
    "logback" -> "1.1.2",
    "suiryc-scala" -> "0.0.2-SNAPSHOT"
  )

  val settings: Seq[Setting[_]] =
    org.scalastyle.sbt.ScalastylePlugin.Settings ++
    Seq(
      organization := "suiryc",
      version := versions("suiryc-scala"),
      scalaVersion := "2.11.1",
      scalacOptions ++= Seq("-deprecation", "-feature", "-optimize", "-unchecked", "-Yinline-warnings"),
      scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits"),
      org.scalastyle.sbt.PluginKeys.config := file("project/scalastyle-config.xml"),
      resolvers += Resolver.mavenLocal,
      publishMavenStyle := true,
      publishTo := Some(Resolver.mavenLocal)
    )

}
