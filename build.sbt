val scalaJSVersion = "1.0.0-M7"

def addSbtPluginWorkaround(moduleID: ModuleID): Setting[_] = {
  /* Work around https://github.com/sbt/sbt/issues/3393.
   * This is the fixed definition of addSbtPlugin to be
   * released with sbt 0.13.17.
   */
  libraryDependencies += {
    val sbtV = (sbtBinaryVersion in pluginCrossBuild).value
    val scalaV = (scalaBinaryVersion in update).value
    Defaults.sbtPluginExtra(moduleID, sbtV, scalaV)
  }
}

inThisBuild(Seq(
  version := "1.0.0-SNAPSHOT",
  organization := "org.scala-js",

  crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.6"),
  crossSbtVersions := Seq("1.0.4", "0.13.17"),
  scalaVersion := "2.10.7",
  scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings"),

  homepage := Some(url("https://www.scala-js.org/")),
  licenses += ("BSD New",
      url("https://github.com/scala-js/jsdependencies/blob/master/LICENSE")),
  scmInfo := Some(ScmInfo(
      url("https://github.com/scala-js/jsdependencies"),
      "scm:git:git@github.com:scala-js/jsdependencies.git",
      Some("scm:git:git@github.com:scala-js/jsdependencies.git")))
))

val commonSettings = Def.settings(
  // Scaladoc linking
  apiURL := {
    val name = moduleName.value
    val v = version.value
    Some(url(s"https://www.scala-js.org/api/$name/$v/"))
  },
  autoAPIMappings := true,

  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <developers>
      <developer>
        <id>sjrd</id>
        <name>Sébastien Doeraene</name>
        <url>https://github.com/sjrd/</url>
      </developer>
      <developer>
        <id>gzm0</id>
        <name>Tobias Schlatter</name>
        <url>https://github.com/gzm0/</url>
      </developer>
      <developer>
        <id>nicolasstucki</id>
        <name>Nicolas Stucki</name>
        <url>https://github.com/nicolasstucki/</url>
      </developer>
    </developers>
  ),
  pomIncludeRepository := { _ => false }
)

lazy val root: Project = project.in(file(".")).
  settings(
    publishArtifact in Compile := false,
    publish := {},
    publishLocal := {},

    clean := clean.dependsOn(
      clean in `jsdependencies-core`,
      clean in `sbt-jsdependencies`
    ).value
  )

lazy val `jsdependencies-core`: Project = project.in(file("jsdependencies-core")).
  settings(
    commonSettings,

    libraryDependencies ++= Seq(
      "org.scala-js" %% "scalajs-ir" % scalaJSVersion,
      "org.scala-js" %% "scalajs-io" % scalaJSVersion,
      "com.googlecode.json-simple" % "json-simple" % "1.1.1" exclude("junit", "junit"),

      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
  )

lazy val `sbt-jsdependencies`: Project = project.in(file("jsdependencies-sbt-plugin")).
  settings(
    commonSettings,

    sbtPlugin := true,
    scalaBinaryVersion :=
      CrossVersion.binaryScalaVersion(scalaVersion.value),

    addSbtPluginWorkaround("org.scala-js" % "sbt-scalajs" % scalaJSVersion),

    // Add API mappings for sbt (seems they don't export their API URL)
    apiMappings ++= {
      val deps = (externalDependencyClasspath in Compile).value
      val sbtJars = deps filter { attributed =>
        val p = attributed.data.getPath
        p.contains("/org.scala-sbt/") && p.endsWith(".jar")
      }
      val docUrl =
        url(s"http://www.scala-sbt.org/${sbtVersion.value}/api/")
      sbtJars.map(_.data -> docUrl).toMap
    }
  ).
  dependsOn(`jsdependencies-core`)
