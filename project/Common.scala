import sbt._
import Keys._


object Common {

  val settings: Seq[Setting[_]] = Seq(
    organization := "suiryc",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.10.3",
    publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath + "/.m2/repository")))
  )

}

