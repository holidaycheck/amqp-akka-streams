import sbt._

lazy val resolvers =  Seq(
  Opts.resolver.sonatypeReleases,
  Resolver.jcenterRepo
)

lazy val itSettings = Defaults.itSettings ++ Project.inConfig(IntegrationTest)(Seq(Keys.fork := true, Keys.parallelExecution := false))

lazy val libraryDependencies = {
  val akkaVersion = "2.4.19"
  val rabbitMqVersion = "4.2.0"
  Seq(
    // akka
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,

    // rabbit mq
    "com.rabbitmq" % "amqp-client" % rabbitMqVersion,

    // tests
    "org.scalatest" %% "scalatest" % "3.0.3" % "test,it",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % "test",
    "org.reactivestreams" % "reactive-streams-tck" % "1.0.0" % "test",
    "org.apache.qpid" % "qpid-broker" % "6.1.3" % "it"
  )
}

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Keys.name := "amqp-akka-streams",
    Keys.organization := "com.holidaycheck",
    Keys.version := "1.4.0",
    Keys.scalaVersion := "2.12.3",
    Keys.crossScalaVersions := Seq("2.11.8", "2.12.3"),
    Keys.scalacOptions ++= Seq("-deprecation", "-target:jvm-1.8"),
    Keys.javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-unchecked", "-deprecation", "-feature"),
    Keys.resolvers := resolvers,
    Keys.concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    Keys.libraryDependencies := libraryDependencies,
    Keys.credentials += Credentials(Path.userHome / ".ivy2" / ".publicCredentials"),
    PgpKeys.useGpg := true,
    Keys.pomIncludeRepository := { _ => false },
    Keys.licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    Keys.homepage := Some(url("https://github.com/holidaycheck/amqp-akka-streams")),
    Keys.scmInfo := Some(
      ScmInfo(
        browseUrl = url("https://github.com/holidaycheck/amqp-akka-streams"),
        connection = "scm:git@github.com:holidaycheck/amqp-akka-streams.git"
      )
    ),
    Keys.developers := List(
      Developer(
        id    = "mjakubowski84",
        name  = "Marcin Jakubowski",
        email = "marcin.jakubowski@holidaycheck.com",
        url   = url("https://github.com/mjakubowski84")
      )
    ),
    Keys.publishMavenStyle := true,
    Keys.publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
    Keys.publishArtifact in Test := false,
    Keys.publishArtifact in IntegrationTest := false
  )
  .settings(itSettings: _*)
