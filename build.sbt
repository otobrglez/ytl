import Dependencies._
import sbt.Keys.resolvers

ThisBuild / version := "0.0.1"
ThisBuild / scalaVersion := "3.1.3"
ThisBuild / scalacOptions := Seq(
  "-explain",
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-deprecation",
  "-print-lines",
  "-source:future",
  "-language:adhocExtensions",
  "-language:implicitConversions"
  // "-Ykind-projector:underscores",
)

lazy val root = (project in file("."))
  .settings(name := "ytl")
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
  .settings(
    libraryDependencies ++= {
      zio ++ zioTest ++ asyncHttpClient ++ circe ++ influxdb ++ tsconfig ++ logging
    },
    resolvers := Dependencies.resolvers,
    Compile / mainClass := Some("com.pinkstack.ytl.StatsCollectorApp"),
    fork := true,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .settings(DockerSettings.settings: _*)
