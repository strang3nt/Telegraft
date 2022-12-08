lazy val AkkaVersion = "2.7.0"
lazy val AkkaHttpVersion = "10.4.0"
lazy val LogBackVersion = "1.4.5"
lazy val ScalaTestVersion = "3.2.14"
lazy val PostgresqlVersion = "42.5.1"
lazy val ScalaMockVersion = "5.2.0"
lazy val ScalaParallelCollectionsVersion = "1.0.4"

name := "telegraft-raft-service"

enablePlugins(
  AshScriptPlugin,
  AkkaGrpcPlugin,
  JavaAppPackaging,
  DockerPlugin,
  UniversalPlugin)

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
Test / fork := true

run / fork := false
Global / cancelable := false // ctrl-c

dockerUpdateLatest := true
dockerBaseImage := "eclipse-temurin:17-jre-alpine"
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
ThisBuild / dynverSeparator := "-"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parallel-collections" % ScalaParallelCollectionsVersion,
  // Akka Management powers Health Checks and Akka Cluster Bootstrapping
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % LogBackVersion,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
  "org.scalamock" %% "scalamock" % ScalaMockVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  // Using gRPC and/or protobuf
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
  // Using Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "org.postgresql" % "postgresql" % PostgresqlVersion)
