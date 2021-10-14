name := "Sharding Example"

version := "1.0"

scalaVersion := "2.13.6"
val akkaVersion = "2.6.16"
val slickVersion = "3.3.3"
val akkaHttpVersion = "10.2.6"
val sprayJsonVersion = "1.3.6"

Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)
Test / fork := false

scalacOptions ++= Seq("-deprecation", "-feature", "-language:postfixOps")

resolvers ++=  Seq(
	"Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
	"OSGeo Release Repository" at "https://repo.osgeo.org/repository/release/",
	"OSGeo Snapshot Repository" at "https://repo.osgeo.org/repository/snapshot/"
)

libraryDependencies ++= Seq(
	"com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.1.1",
	"com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
	"com.typesafe.akka" %% "akka-discovery" % akkaVersion,
	"com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
	"org.postgresql" % "postgresql" % "42.2.24",
	"com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.4",
	"com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
	"com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
	"com.typesafe.slick" %% "slick" % slickVersion,
	"com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
	"org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
	"org.scalatest" %% "scalatest" % "3.2.9",
	"com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
	"com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
	"io.altoo" %% "akka-kryo-serialization" % "2.2.0",
	"com.typesafe.akka"	%%	"akka-actor"	%	akkaVersion,
	"com.typesafe.akka" %% "akka-persistence" % akkaVersion,
	"com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
	"com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
	"com.typesafe.akka"	%%	"akka-stream"	%	akkaVersion,
	"com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
	"com.typesafe.akka"	%%	"akka-http"	%	akkaHttpVersion,
	"com.typesafe.akka"	%%	"akka-http-spray-json"	%	akkaHttpVersion,
	"io.spray" %% "spray-json" % sprayJsonVersion,
	"com.spotify" % "docker-client" % "8.16.0" % Test,
	"com.h2database" % "h2" % "1.4.200" % Test
)