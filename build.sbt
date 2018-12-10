name := "proxy"

organization := "io.flow"

scalaVersion in ThisBuild := "2.12.8"

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(NewRelic)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      filters,
      guice,
      ws,
      "commons-codec" % "commons-codec" % "1.11",
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % "1.11.453",
      "com.jason-goodwin" %% "authentikat-jwt" % "0.4.5",
      "io.flow" %% "apibuilder-validation" % "0.3.6",
      "io.flow" %% "lib-play-play26" % "0.5.10",
      "io.flow" %% "lib-log" % "0.0.39",
      "org.yaml" % "snakeyaml" % "1.23",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
    )
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("proxy-" + _),
  scalacOptions += "-feature",
  javaOptions in Test += "-Dconfig.file=conf/application.test.conf",
  newrelicConfig := (resourceDirectory in Compile).value / "newrelic.yml",
  sources in (Compile,doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false,
  credentials += credsToUse,
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)

version := "0.5.65"
