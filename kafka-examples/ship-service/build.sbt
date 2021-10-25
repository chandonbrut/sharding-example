import Dependencies._

ThisBuild / scalaVersion     := "2.13.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "ship-service",
    libraryDependencies += scalaTest % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
val AkkaHttpVersion = "10.2.6"
val AkkaVersion = "2.6.17"
val KafkaVersion = "3.0.0"
val SprayJson = "1.3.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka"  %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor"  % AkkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "org.apache.kafka" % "kafka-clients" % KafkaVersion,
  "io.spray" %%  "spray-json" % SprayJson
)
