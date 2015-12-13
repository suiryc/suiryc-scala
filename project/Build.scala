import sbt._
import Keys._


object SuirycScalaRootBuild
  extends Build
{

  lazy val base = file(".").getCanonicalFile

  lazy val coreFile = file("core")
  lazy val core = project.in(coreFile)

  lazy val logFile = file("log")
  lazy val log = project.in(logFile).dependsOn(core)

  lazy val javaFXFile = file("javafx")
  lazy val javaFX = project.in(javaFXFile).dependsOn(core, log)

  lazy val root = Project(
    id = "suiryc-scala",
    base = base,
    aggregate = Seq(core, log, javaFX),
    settings = Common.settings ++ Seq(
      libraryDependencies := Seq.empty,
      publishArtifact in Compile := false
    )
  )

}
