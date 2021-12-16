name := "ID Service"

version := "1.0"


scalaVersion := "2.13.7"
crossScalaVersions := Seq("2.13.7", "3.1.0")
val akkaVersion = "2.6.17"
val akkaHttpVersion = "10.2.7"
val sprayJsonVersion = "1.3.6"
scalacOptions ++= Seq("-deprecation", "-feature", "-language:postfixOps")

libraryDependencies ++= Seq(
	"io.spray" %% "spray-json" % sprayJsonVersion,
	"com.typesafe.akka"	%%	"akka-http"	%	akkaHttpVersion,
	"com.typesafe.akka"	%%	"akka-http-spray-json"	%	akkaHttpVersion,
	"com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
	"com.typesafe.akka"	%%	"akka-stream"	%	akkaVersion,
	"com.typesafe.akka"	%%	"akka-actor"	%	akkaVersion,
	"org.scalatest" %% "scalatest" % "3.2.9" % "test"
)
