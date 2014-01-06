import sbt._
import Keys._

object SuirycScalaBuild
  extends Build
{

  lazy val core = project in file("core")

  lazy val log = project in file("log") dependsOn(core)

  lazy val root = project aggregate(core, log)

}

