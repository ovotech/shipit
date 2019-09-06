organization := "com.ovoenergy"
scalaVersion := "2.12.8"
scalacOptions += "-Ypartial-unification"

// The version number is fixed, for the sake of simpler deployment scripts.
// The uploaded docker image will be tagged with the git sha1.
version := "1.0"

resolvers += Resolver.bintrayRepo("ovotech", "maven")
val circeVersion = "0.10.1"
val cirisVersion = "0.10.2"
libraryDependencies ++= Seq(
  ws,
  filters,
  "io.circe"                   %% "circe-parser"                   % circeVersion,
  "io.circe"                   %% "circe-generic"                  % circeVersion,
  "com.typesafe.akka"          %% "akka-slf4j"                     % "2.5.16",
  "org.typelevel"              %% "cats-core"                      % "1.4.0",
  "com.gu"                     %% "play-googleauth"                % "0.7.9",
  "io.searchbox"               % "jest"                            % "6.3.1",
  "vc.inreach.aws"             % "aws-signing-request-interceptor" % "0.0.21",
  "com.amazonaws"              % "aws-java-sdk-core"               % "1.11.602",
  "me.moocar"                  % "logback-gelf"                    % "0.2",
  "is.cir"                     %% "ciris-core"                     % cirisVersion,
  "is.cir"                     %% "ciris-cats"                     % cirisVersion,
  "com.ovoenergy"              %% "ciris-aws-ssm"                  % "0.6",
  "org.scalatest"              %% "scalatest"                      % "3.0.5" % Test,
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.14"      % "1.2.3" % Test,
  "org.scalacheck"             %% "scalacheck"                     % "1.14.0" % Test
)

enablePlugins(PlayScala, DockerPlugin)
scalafmtOnCompile in ThisBuild := true

// Docker packaging stuff
dockerBaseImage := "openjdk:8"
javaOptions in Universal ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx512m",
    "-J-Xms512m"
)
dockerUpdateLatest := false
