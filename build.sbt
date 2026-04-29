ThisBuild / scalaVersion := "3.8.2"
ThisBuild / organization := "learning.local"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "livelogsentinel",

    Compile / run / fork := true,

    scalacOptions ++= Seq(
      "-Xkind-projector",
      "-Wnonunit-statement"
    ),

    libraryDependencies ++= Seq(
      "org.scala-lang" %% "toolkit" % "0.7.0",
      "org.typelevel" %% "cats-effect" % "3.7.0",
      "org.typelevel" %% "cats-effect-std" % "3.5.3",
      "org.typelevel" %% "munit-cats-effect" % "2.2.0" % Test
    )
  )
