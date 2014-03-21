import sbt._
import Keys._


object SuirycScalaRootBuild
  extends Build
{

  lazy val copyPom = TaskKey[Unit]("copy-pom")

  def copyPomTask(base: File) = copyPom <<= makePom map { pom =>
    IO.copyFile(pom, base / "pom.xml")
  }

  lazy val base = file(".").getCanonicalFile

  lazy val coreFile = file("core")
  lazy val core = project.in(coreFile).settings(
    copyPomTask(coreFile),
    pomExtra := Common.pomExtra("1.7")
  )

  lazy val logFile = file("log")
  lazy val log = project.in(logFile).dependsOn(core).settings(
    copyPomTask(logFile),
    pomExtra := Common.pomExtra("1.7")
  )

  lazy val javaFXFile = file("javafx")
  lazy val javaFX = project.in(javaFXFile).dependsOn(core).settings(
    copyPomTask(javaFXFile),
    pomExtra := Common.pomExtra("1.8")
  )

  lazy val root = Project(
    id = "suiryc-scala",
    base = base,
    aggregate = Seq(core, log, javaFX),
    settings = Project.defaultSettings ++ Seq(
      libraryDependencies := Seq.empty,
      publishMavenStyle := true,
      publishArtifact in Compile := false,
      publishTo := Some(Resolver.file("file", new File(Common.localMavenPath))),
      pomExtra := (
  <modules>
    <module>core</module>
    <module>log</module>
    <module>javafx</module>
  </modules>
      )
    ) ++ Seq(copyPomTask(base))
  )

}
