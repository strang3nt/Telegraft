name := "telegraft-statemachine-service"

organization := "com.telegraft"

scalaVersion := "2.13.10"

Compile / scalacOptions ++= Seq(
  "-release:17",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false
Test / javaOptions += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application-test.conf"
Test / fork := true

run / fork := false
Global / cancelable := false // ctrl-c

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"
val AkkaManagementVersion = "1.2.0"
val AkkaPersistenceJdbcVersion = "5.2.0"
val AkkaProjectionVersion = "1.3.1"
val LogBackVersion = "1.4.5"
val ScalaTestVersion = "3.2.14"
val PostgresqlVersion = "42.5.1"

enablePlugins(AshScriptPlugin, AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin, UniversalPlugin)

Universal / javaOptions += "-Dconfig.resource=local.conf"
dockerUpdateLatest := true
dockerBaseImage := "eclipse-temurin:17-jre-alpine"
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
ThisBuild / dynverSeparator := "-"

libraryDependencies ++= Seq(
  // 1. Basic dependencies for a clustered application
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  // Akka Management powers Health Checks and Akka Cluster Bootstrapping
  "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % LogBackVersion,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
  // 2. Using gRPC and/or protobuf
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
  // 3. Using Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "org.postgresql" % "postgresql" % PostgresqlVersion,
  // 4. Querying or projecting data from Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-jdbc" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-slick" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test)
