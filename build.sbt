name := "proxy"

organization := "io.flow"

scalaVersion in ThisBuild := "2.11.8"

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(NewRelic)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      filters,
      ws,
      "commons-codec" % "commons-codec" % "1.10",
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % "1.11.135",
      "com.jason-goodwin" %% "authentikat-jwt" % "0.4.5",
      "com.typesafe.play" %% "play-json" % "2.5.12",
      "io.flow" %% "lib-apidoc-json-validation" % "0.0.45",
      "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0" % "test",
      "org.yaml" % "snakeyaml" % "1.18"
    )
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.artifactoryonline.com",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name <<= name("proxy-" + _),
  scalacOptions += "-feature",
  sources in (Compile,doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false,
  credentials += credsToUse,
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  resolvers += "Artifactory" at "https://flow.artifactoryonline.com/flow/libs-release/"
)

version := "0.2.27"
