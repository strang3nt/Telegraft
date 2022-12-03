lazy val AkkaVersion = "2.7.0"
lazy val AkkaHttpVersion = "10.4.0"
lazy val AkkaManagementVersion = "1.2.0"
lazy val AkkaPersistenceJdbcVersion = "5.2.0"
lazy val AkkaProjectionVersion = "1.3.1"
lazy val LogBackVersion = "1.4.5"
lazy val ScalaTestVersion = "3.2.14"
lazy val PostgresqlVersion = "42.5.1"
lazy val ScalaMockVersion = "5.2.0"

name := "telegraft-raft-service"
enablePlugins(AshScriptPlugin, AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin, UniversalPlugin)

organization := "com.telegraft"
scalaVersion := "2.13.10"

Compile / scalacOptions ++= Seq(
  "-release:17",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint",
  "-language:implicitConversions")
Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false
Test / javaOptions += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application-test.conf"
Test / fork := true

run / fork := false
Global / cancelable := false // ctrl-c
Universal / javaOptions += "-Dconfig.resource=local.conf"

dockerUpdateLatest := true
dockerBaseImage := "eclipse-temurin:17-jre-alpine"
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
ThisBuild / dynverSeparator := "-"

libraryDependencies ++= Seq(
  // 1. Basic dependencies for a clustered application
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  // Akka Management powers Health Checks and Akka Cluster Bootstrapping
  "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % LogBackVersion,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
  "org.scalamock" %% "scalamock" % ScalaMockVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  // 2. Using gRPC and/or protobuf
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
  // 3. Using Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
  "org.postgresql" % "postgresql" % PostgresqlVersion,
  // 4. Querying or projecting data from Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-jdbc" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-slick" % AkkaProjectionVersion)
