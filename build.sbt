name := "proxy"

organization := "io.flow"

ThisBuild / scalaVersion := "2.13.5"

lazy val allScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-Xfatal-warnings",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:adapted-args",
  "-Ypatmat-exhaust-depth", "100", // Fixes: Exhaustivity analysis reached max recursion depth, not all missing cases are reported.
  "-Wconf:src=generated/.*:silent",
  "-Wconf:src=target/.*:silent", // silence the unused imports errors generated by the Play Routes
)

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtTwirl)
  .enablePlugins(NewRelic, JavaAgent)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      filters,
      guice,
      ws,
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % "1.11.925",
      "com.pauldijou" %% "jwt-play-json" % "4.3.0",
      "commons-codec" % "commons-codec" % "1.15",
      "io.apibuilder" %% "apibuilder-validation" % "0.4.21",
      "io.flow" %% "lib-play-graphite-play28" % "0.1.88",
      "io.flow" %% "lib-usage-play28" % "0.1.15",
      "org.typelevel" %% "cats-core" % "2.3.1",
      "org.yaml" % "snakeyaml" % "1.27",
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
    ),
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.7",
    testOptions += Tests.Argument("-oF"),
    scalacOptions ++= allScalacOptions,
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("proxy-" + _),
  Test / javaOptions += "-Dconfig.file=conf/application.test.conf",
  newrelicConfig := (resourceDirectory in Compile).value / "newrelic.yml",
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / publishArtifact := false,
  credentials += credsToUse,
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
