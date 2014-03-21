import sbt._
import Keys._


object Common {

  val localMavenPath = Path.userHome.absolutePath + "/.m2/repository"

  val versions = Map[String, String](
    "maven-compiler-plugin" -> "3.1",
    "maven-surefire-plugin" -> "2.16",
    "scala-maven-plugin" -> "3.1.6"
  )

  val settings: Seq[Setting[_]] =
    org.scalastyle.sbt.ScalastylePlugin.Settings ++
    Seq(
      organization := "suiryc",
      version := "0.0.1-SNAPSHOT",
      scalaVersion := "2.10.3",
      scalacOptions ++= Seq("-deprecation", "-feature", "-optimize", "-unchecked", "-Yinline-warnings"),
      scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits"),
      org.scalastyle.sbt.PluginKeys.config := file("project/scalastyle-config.xml"),
      publishMavenStyle := true,
      publishTo := Some(Resolver.file("file", new File(localMavenPath)))
    )

  def pomExtra(javaVersion: String = "1.7") = (
  <properties>
    <encoding>UTF-8</encoding>
  </properties>
  <build>
    <sourceDirectory>src/main/scala</sourceDirectory>
    <testSourceDirectory>src/test/scala</testSourceDirectory>
    <plugins>
      <plugin>
        <groupId>net.alchim31.maven</groupId>
        <artifactId>scala-maven-plugin</artifactId>
        <version>{ versions("scala-maven-plugin") }</version>
        <configuration>
          <args>
            <arg>-deprecation</arg>
            <arg>-feature</arg>
            <arg>-Yinline-warnings</arg>
            <arg>-optimize</arg>
            <arg>-unchecked</arg>
          </args>
          <recompileMode>incremental</recompileMode>
        </configuration>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>testCompile</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>{ versions("maven-compiler-plugin") }</version>
        <configuration>
          <source>{ javaVersion }</source>
          <target>{ javaVersion }</target>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>{ versions("maven-surefire-plugin") }</version>
        <configuration>
          <includes>
            <include>**/*Suite.class</include>
          </includes>
        </configuration>
      </plugin>
    </plugins>
  </build>
)

}

