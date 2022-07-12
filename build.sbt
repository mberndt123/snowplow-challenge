val http4sVersion = "0.23.12"
val circeVersion = "0.14.2"

val snowplowTask = project.settings(
  scalaVersion := "3.1.2",
  fork := true,
  libraryDependencies ++= Seq(
    "org.tpolecat" %% "doobie-h2-circe" % "1.0.0-RC2",
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "io.circe" %% "circe-literal" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.11",
    "com.monovore" %% "decline-effect" % "2.3.0",
    "org.flywaydb" % "flyway-core" % "8.5.13",
    "com.github.java-json-tools" % "json-schema-validator" % "2.2.14",
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
    "org.http4s" %% "http4s-client" % http4sVersion % Test
  ),
  buildInfoPackage := "mberndt.snowplowTask"
).enablePlugins(BuildInfoPlugin)


