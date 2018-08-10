organization := "com.ovoenergy"
scalaVersion := "2.11.11" // TODO Scala 2.12
scalacOptions += "-Ypartial-unification"

// The version number is fixed, for the sake of simpler deployment scripts.
// The uploaded docker image will be tagged with the git sha1.
version := "1.0"

resolvers += Resolver.bintrayRepo("ovotech", "maven")
// TODO bump various libs
val circeVersion = "0.9.0"
val cirisVersion = "0.10.2"
libraryDependencies ++= Seq(
  ws,
  filters,
  "io.circe"                   %% "circe-parser"                   % circeVersion,
  "io.circe"                   %% "circe-generic"                  % circeVersion,
  "com.typesafe.akka"          %% "akka-slf4j"                     % "2.4.16",
  "org.typelevel"              %% "cats-core"                      % "1.2.0",
  "com.gu"                     %% "play-googleauth"                % "0.7.2",
  "io.searchbox"               % "jest"                            % "5.3.3",
  "vc.inreach.aws"             % "aws-signing-request-interceptor" % "0.0.15",
  "com.amazonaws"              % "aws-java-sdk-core"               % "1.11.261",
  "me.moocar"                  % "logback-gelf"                    % "0.2",
  "is.cir"                     %% "ciris-core"                     % cirisVersion,
  "is.cir"                     %% "ciris-cats"                     % cirisVersion,
  "com.ovoenergy"              %% "ciris-aws-ssm"                  % "0.6",
  "org.scalatest"              %% "scalatest"                      % "3.0.4" % Test,
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.13"      % "1.1.8" % Test,
  "org.scalacheck"             %% "scalacheck"                     % "1.13.5" % Test
)

enablePlugins(PlayScala, DockerPlugin)
scalafmtOnCompile in ThisBuild := true

// Docker packaging stuff
javaOptions in Universal ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx256m",
    "-J-Xms256m"
)
dockerUpdateLatest := false
