organization := "com.ovoenergy"
scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  ws,
  filters,
  "io.circe"                   %% "circe-parser"                   % "0.9.0",
  "io.circe"                   %% "circe-generic"                  % "0.9.0",
  "com.typesafe.akka"          %% "akka-slf4j"                     % "2.4.16",
  "org.typelevel"              %% "cats-core"                      % "1.0.1",
  "com.gu"                     %% "play-googleauth"                % "0.7.2",
  "io.searchbox"               % "jest"                            % "5.3.3",
  "vc.inreach.aws"             % "aws-signing-request-interceptor" % "0.0.15",
  "com.amazonaws"              % "aws-java-sdk-core"               % "1.11.261",
  "io.logz.logback"            % "logzio-logback-appender"         % "1.0.11",
  "me.moocar"                  % "logback-gelf"                    % "0.2",
  "org.scalatest"              %% "scalatest"                      % "3.0.4" % Test,
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.13"      % "1.1.8" % Test,
  "org.scalacheck"             %% "scalacheck"                     % "1.13.5" % Test
)

val testReportsDir = sys.env.getOrElse("CI_REPORTS", "target/reports")
testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF", "-u", testReportsDir)
enablePlugins(PlayScala, DockerPlugin)

scalafmtOnCompile in ThisBuild := true

credstashInputDir := file("conf")
commsPackagingAwsAccountId := "852955754882"
commsPackagingDownloadAivenStuffAtStartup := false
