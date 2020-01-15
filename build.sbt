name := "proxy"

organization := "io.flow"

scalaVersion in ThisBuild := "2.12.10"

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
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % "1.11.642",
      "com.jason-goodwin" %% "authentikat-jwt" % "0.4.5",
      "commons-codec" % "commons-codec" % "1.13",
      "io.apibuilder" %% "apibuilder-validation" % "0.4.16",
      "io.flow" %% "lib-play-graphite-play26" % "0.1.29",
      "io.flow" %% "lib-usage" % "0.1.12",
      "org.typelevel" %% "cats-core" % "2.0.0",
      "org.yaml" % "snakeyaml" % "1.25",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.4.2"),
      "com.github.ghik" %% "silencer-lib" % "1.4.2" % Provided
    ),
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.4",
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
version := "0.6.31"
