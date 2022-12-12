addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.2.1")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.11")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
ThisBuild / libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always)
