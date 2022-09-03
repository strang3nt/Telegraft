lazy val akkaHttpVersion = "10.2.9"
lazy val akkaVersion = "2.6.19"
lazy val scalaVersion = "2.13.8"
lazy val circeVersion = "0.14.2"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other

fork := true

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.telegraft",
    )),
    name := "telegraft",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "3.0.4",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",

      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.13" % Test
    )
  )
