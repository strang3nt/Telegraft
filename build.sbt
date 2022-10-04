scalaVersion := "2.13.8"

// make version compatible with docker for publishing
ThisBuild / dynverSeparator := "-"

scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")
classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars
fork := true
Compile / run / fork := true

Compile / mainClass / run := Some("com.telegraft.TelegraftApp")

enablePlugins(JavaServerAppPackaging, DockerPlugin, UniversalPlugin)

// Docker packaging

import com.typesafe.sbt.packager.docker._

dockerExposedPorts := Seq(8080, 8558, 25520)
dockerUpdateLatest := true
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
dockerBaseImage := "eclipse-temurin:17-alpine"
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apk", "add", "bash")
)

// End docker packaging

lazy val akkaHttpVersion = "10.2.10"
lazy val akkaVersion     = "2.6.20"
lazy val circeVersion    = "0.14.2"
lazy val akkaManagementVersion = "1.1.4"
lazy val postgresqlVersion     = "42.5.0"
lazy val akkaProjectionVersion = "1.2.5"
lazy val akkaPersistenceJdbcVersion = "5.1.0"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.telegraft",
    )),
    name := "telegraft",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.0",
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "4.0.0",
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.lightbend.akka" %% "akka-projection-eventsourced" % akkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-core" % akkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-slick" % akkaProjectionVersion,
      "com.lightbend.akka" %% "akka-persistence-jdbc" % akkaPersistenceJdbcVersion,
      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.40.0-RC3",
      "org.postgresql" % "postgresql" % postgresqlVersion,


      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.13" % Test
    )
  )
