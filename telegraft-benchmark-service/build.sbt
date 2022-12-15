lazy val ScalaParallelCollectionsVersion = "1.0.4"
lazy val GatlingVersion = "3.8.4"
lazy val GatlingGrpcVersion = "0.15.1"

name := "telegraft-benchmark-service"
organization := "com.telegraft"

scalaVersion := "2.13.10"

enablePlugins(GatlingPlugin)

Compile / PB.targets ++= Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)
scalacOptions ++= Seq(
  "-release:17",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint",
  "-language:implicitConversions")
javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parallel-collections" % ScalaParallelCollectionsVersion % "test,it",
  // Gatling dependencies
  "io.gatling.highcharts" % "gatling-charts-highcharts" % GatlingVersion % "test,it",
  "io.gatling" % "gatling-test-framework" % GatlingVersion % "test,it",
  "com.github.phisgr" % "gatling-grpc" % GatlingGrpcVersion % "test,it",
  // ScalaPB - grpc code generation
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion

  )
