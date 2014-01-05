name := "scala-misc"

organization := "suiryc"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies += "org.clapper" %% "grizzled-slf4j" % "1.0.1"

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath + "/.m2/repository")))

