name := """ParallelConcat"""

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.github.scopt" %% "scopt" % "3.3.0",
  "org.apache.tika" % "tika-core" % "1.7",
  "org.apache.commons" % "commons-io" % "1.3.2"
)

resolvers += Resolver.sonatypeRepo("public")

