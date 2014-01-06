import sbt._
import Keys._

object SuirycScalaBuild
  extends Build
{

  lazy val core = project in file("core")

  lazy val log = project in file("log") dependsOn(core)

  lazy val root = Project(
    id = "suiryc-scala",
    base = file("."),
    aggregate = Seq(core, log),
    settings = Project.defaultSettings ++ Seq(
      publish := { },
      publishLocal := { }
    )
  )

}

