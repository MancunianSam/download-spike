name := """download-spike"""
organization := "uk.gov.nationalarchives"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.3"
val pac4jVersion = "4.2.0"
val playPac4jVersion = "10.0.2"
val awsVersion = "2.15.82"

libraryDependencies += guice
libraryDependencies += "org.pac4j" % "pac4j-http" % pac4jVersion exclude("com.fasterxml.jackson.core", "jackson-databind")
libraryDependencies += "org.pac4j" % "pac4j-oidc" % pac4jVersion exclude("commons-io", "commons-io") exclude("com.fasterxml.jackson.core", "jackson-databind")
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies += "org.pac4j" %% "play-pac4j" % playPac4jVersion
libraryDependencies += play.sbt.PlayImport.cacheApi
libraryDependencies += "com.github.karelcemus" %% "play-redis" % "2.6.0"
libraryDependencies += "software.amazon.awssdk" % "cognitoidentity" % awsVersion
libraryDependencies += "software.amazon.awssdk" % "s3" % awsVersion


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "uk.gov.nationalarchives.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "uk.gov.nationalarchives.binders._"
