name := "demo-http"

scalaVersion := "2.11.5"

val akkaVersion = "1.0-SNAPSHOT"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaVersion,
  "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
)
