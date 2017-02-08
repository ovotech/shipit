organization := "com.ovoenergy"
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  ws,
  filters,
  "io.circe" %% "circe-parser" % "0.7.0",
  "io.circe" %% "circe-generic" % "0.7.0",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.13",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.16",
  "org.typelevel" %% "cats-core" % "0.9.0",
  "com.gu" %% "play-googleauth" % "0.6.0",
  "io.searchbox" % "jest" % "2.0.4",
  "vc.inreach.aws" % "aws-signing-request-interceptor" % "0.0.15",
  "com.amazonaws" % "aws-java-sdk-core" % "1.11.86",
  "io.logz.logback" % "logzio-logback-appender" % "1.0.11",
  "me.moocar" % "logback-gelf" % "0.2",
  "org.scalatest" %% "scalatest" % "2.2.6" %  Test,
  "com.github.alexarchambault"  %% "scalacheck-shapeless_1.13" % "1.1.4" %   Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)

val testReportsDir = sys.env.getOrElse("CI_REPORTS", "target/reports")
testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF", "-u", testReportsDir)
credstashInputDir := file("conf")
enablePlugins(PlayScala, DockerPlugin)
