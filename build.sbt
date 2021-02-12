name := "proxy"

organization := "io.flow"

scalaVersion in ThisBuild := "2.13.3"

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)
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
      "io.flow" %% "lib-play-graphite-play28" % "0.1.36",
      "io.flow" %% "lib-usage-play28" % "0.1.15",
      "org.typelevel" %% "cats-core" % "2.3.1",
      "org.yaml" % "snakeyaml" % "1.27",
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.7.1" cross CrossVersion.full),
      "com.github.ghik" %% "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
    ),
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.7",
    testOptions += Tests.Argument("-oF"),
    // silence all warnings on autogenerated files
    flowGeneratedFiles ++= Seq(
      "app/generated/.*".r,
      "test/generated/.*".r,
      "target/.*".r,
    ),
    // Make sure you only exclude warnings for the project directories, i.e. make builds reproducible
    scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}"
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("proxy-" + _),
  javaOptions in Test += "-Dconfig.file=conf/application.test.conf",
  newrelicConfig := (resourceDirectory in Compile).value / "newrelic.yml",
  sources in (Compile,doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false,
  credentials += credsToUse,
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
version := "0.6.52"
