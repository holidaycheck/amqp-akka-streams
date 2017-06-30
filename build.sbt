import sbt._

lazy val resolvers =  Seq(
  "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
  Resolver.jcenterRepo
)

lazy val itSettings = Defaults.itSettings ++ Project.inConfig(IntegrationTest)(Seq(Keys.fork := true, Keys.parallelExecution := false))

lazy val libraryDependencies = {
  val akkaVersion = "2.4.19"
  val rabbitMqVersion = "3.6.5"
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
    Keys.version := "1.3.1",
    Keys.scalaVersion := "2.12.3",
    Keys.crossScalaVersions := Seq("2.11.8", "2.12.3"),
    Keys.scalacOptions ++= Seq("-deprecation", "-target:jvm-1.8"),
    Keys.javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-unchecked", "-deprecation", "-feature"),
    Keys.resolvers := resolvers,
    Keys.publishMavenStyle := true,
    Keys.concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    Keys.libraryDependencies := libraryDependencies,
    Keys.credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
  )
  .settings(itSettings: _*)
