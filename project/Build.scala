import sbt._
import Keys._


object SuirycScalaRootBuild
  extends Build
{

  lazy val base = file(".").getCanonicalFile

  lazy val copyPom = TaskKey[Unit]("copy-pom")

  def copyPomTask(base: File) = copyPom <<= (makePom, streams) map { (pom, s) =>
    val dest = base / "pom.xml"
    s.log.info(s"Copy pom: $dest")
    IO.copyFile(pom, dest)
  }

  val extCompile = compile <<= (compile in Compile) dependsOn(copyPom)

  lazy val coreFile = file("core")
  lazy val core = project.in(coreFile).settings(
    copyPomTask(coreFile), extCompile,
    pomExtra := Common.pomExtra
  )

  lazy val logFile = file("log")
  lazy val log = project.in(logFile).dependsOn(core).settings(
    copyPomTask(logFile), extCompile,
    pomExtra := Common.pomExtra
  )

  lazy val javaFXFile = file("javafx")
  lazy val javaFX = project.in(javaFXFile).dependsOn(core, log).settings(
    copyPomTask(javaFXFile), extCompile,
    pomExtra := Common.pomExtra
  )

  lazy val root = Project(
    id = "suiryc-scala",
    base = base,
    aggregate = Seq(core, log, javaFX),
    settings = Project.defaultSettings ++ Common.settings ++ Seq(
      libraryDependencies := Seq.empty,
      publishArtifact in Compile := false,
      pomExtra := (
  <modules>
    <module>core</module>
    <module>log</module>
    <module>javafx</module>
  </modules>
      )
    ) ++ Seq(copyPomTask(base), extCompile)
  )

}
