enablePlugins(JavaAppPackaging, DockerPlugin)

name := "claportal"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.8"

maintainer := "Travis Finch <fincht@vmware.com>"

dockerExposedPorts in Docker := Seq(9000)

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  javaWs
)

libraryDependencies ++= Seq(
  "mysql" % "mysql-connector-java" % "5.1.39",
  "org.pegdown" % "pegdown" % "1.6.0",
  "org.ocpsoft.prettytime" % "prettytime" % "3.2.7.Final",
  "com.typesafe.play" %% "play-mailer" % "2.4.1",
  "it.innove" % "play2-pdf" % "1.1.3"
)
