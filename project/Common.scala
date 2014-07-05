import sbt._
import Keys._


object Common {

  val localMavenPath = Path.userHome.absolutePath + "/.m2/repository"

  val versions = Map[String, String](
    "java" -> "1.8",
    "akka" -> "2.3.3",
    "config" -> "1.2.1",
    "grizzled" -> "1.0.2",
    "logback" -> "1.1.2",
    "suiryc-scala" -> "0.0.2-SNAPSHOT",
    "maven-compiler-plugin" -> "3.1",
    "maven-surefire-plugin" -> "2.17",
    "scala-maven-plugin" -> "3.1.6"
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
      resolvers += "Local Maven Repository" at "file://" + localMavenPath,
      publishMavenStyle := true,
      publishTo := Some(Resolver.file("file", new File(localMavenPath)))
    )

  val pomExtra = (
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
          <source>{ versions("java") }</source>
          <target>{ versions("java") }</target>
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

