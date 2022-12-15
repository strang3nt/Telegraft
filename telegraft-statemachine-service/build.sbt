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
val LogBackVersion = "1.4.5"
val ScalaTestVersion = "3.2.14"
val PostgresqlVersion = "42.5.1"
val SlickVersion = "3.4.1"

enablePlugins(AshScriptPlugin, AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin, UniversalPlugin)

dockerUpdateLatest := true
dockerBaseImage := "eclipse-temurin:17-jre-alpine"
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
ThisBuild / dynverSeparator := "-"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "org.postgresql" % "postgresql" % PostgresqlVersion,
  "com.typesafe.slick" %% "slick" % SlickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % LogBackVersion,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  // 2. Using gRPC and/or protobuf
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion)
