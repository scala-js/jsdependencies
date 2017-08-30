import org.scalajs.jsdependencies.core.ManifestFilters

inThisBuild(Seq(
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.11"
))

name := "sbt-plugin-test"

addCommandAlias("testAll",
    ";jsDependenciesTest/packageJSDependencies" +
    ";jsDependenciesTest/packageMinifiedJSDependencies" +
    ";jsDependenciesTest/regressionTestForIssue2243" +
    ";jsNoDependenciesTest/regressionTestForIssue2243")

val regressionTestForIssue2243 = TaskKey[Unit]("regressionTestForIssue2243",
  "", KeyRanks.BTask)

def withRegretionTestForIssue2243(project: Project): Project = {
  project.settings(inConfig(Compile)(Seq(
    regressionTestForIssue2243 := {
      // Regression test for issue #2243
      val _ = Def.sequential(packageJSDependencies in Compile,
          packageMinifiedJSDependencies in Compile).value
      assert((artifactPath in(Compile, packageJSDependencies)).value.exists)
      assert((artifactPath in(Compile, packageMinifiedJSDependencies)).value.exists)
      streams.value.log.info("Regression test for issue #2243 passed")
    }
  )): _*)
}

lazy val jsDependenciesTestDependee = project.
  enablePlugins(ScalaJSPlugin, JSDependenciesPlugin).
  settings(
    // This project contains some jsDependencies to test in jsDependenciesTest
    jsDependencies ++= Seq(
        // The jsDependenciesTest relies on this jQuery dependency
        // If you change it, make sure we still test properly
        "org.webjars" % "jquery" % "1.10.2" / "jquery.js"
    )
  )

lazy val jsDependenciesTest = withRegretionTestForIssue2243(
  project.
  enablePlugins(ScalaJSPlugin, JSDependenciesPlugin).
  settings(
    jsDependencies ++= Seq(
        "org.webjars" % "historyjs" % "1.8.0" / "uncompressed/history.js",
        ProvidedJS / "some-jquery-plugin.js" dependsOn "1.10.2/jquery.js",
        ProvidedJS / "js/foo.js" dependsOn "uncompressed/history.js",

        // cause a circular dependency error if both "history.js"'s are considered equal
        "org.webjars" % "historyjs" % "1.8.0" / "compressed/history.js" dependsOn "foo.js",

        // cause a duplicate commonJSName if the following are not considered equal
        "org.webjars" % "mustachejs" % "0.8.2" / "mustache.js" commonJSName "Mustache",
        "org.webjars" % "mustachejs" % "0.8.2" / "0.8.2/mustache.js" commonJSName "Mustache",

        // cause an ambiguity with the jQuery dependency from the
        // jsDependenciesTestDependee project (if we don't filter)
        ProvidedJS / "js/customJQuery/jquery.js" dependsOn "1.10.2/jquery.js",

        // Test minified dependencies
        "org.webjars" % "immutable" % "3.4.0" / "immutable.js" minified "immutable.min.js"
    ),
    jsManifestFilter := {
      ManifestFilters.reinterpretResourceNames("jsDependenciesTestDependee")(
          "jquery.js" -> "1.10.2/jquery.js")
    }
  ).
  settings(inConfig(Compile)(Seq(
    packageJSDependencies := packageJSDependencies.dependsOn(Def.task {
      // perform verifications on the ordering and deduplications
      val resolvedDeps = resolvedJSDependencies.value.data
      val relPaths = resolvedDeps.map(_.info.relPath)

      assert(relPaths.toSet == Set(
          "META-INF/resources/webjars/mustachejs/0.8.2/mustache.js",
          "META-INF/resources/webjars/historyjs/1.8.0/scripts/uncompressed/history.js",
          "META-INF/resources/webjars/historyjs/1.8.0/scripts/compressed/history.js",
          "META-INF/resources/webjars/jquery/1.10.2/jquery.js",
          "META-INF/resources/webjars/immutable/3.4.0/immutable.js",
          "js/foo.js",
          "js/some-jquery-plugin.js",
          "js/customJQuery/jquery.js"),
          s"Bad set of relPathes: ${relPaths.toSet}")

      val minifiedRelPaths = resolvedDeps.flatMap(_.info.relPathMinified)

      assert(minifiedRelPaths.toSet == Set(
          "META-INF/resources/webjars/immutable/3.4.0/immutable.min.js"),
          s"Bad set of minifiedRelPathes: ${minifiedRelPaths.toSet}")

      val jQueryIndex = relPaths.indexWhere(_ endsWith "1.10.2/jquery.js")
      val jQueryPluginIndex = relPaths.indexWhere(_ endsWith "/some-jquery-plugin.js")
      assert(jQueryPluginIndex > jQueryIndex,
          "the jQuery plugin appears before jQuery")

      val uncompressedHistoryIndex = relPaths.indexWhere(_ endsWith "/uncompressed/history.js")
      val fooIndex = relPaths.indexWhere(_ endsWith "/foo.js")
      val compressedHistoryIndex = relPaths.indexWhere(_ endsWith "/compressed/history.js")
      assert(fooIndex > uncompressedHistoryIndex,
          "foo.js appears before uncompressed/history.js")
      assert(compressedHistoryIndex > fooIndex,
          "compressed/history.js appears before foo.js")

      streams.value.log.info("jsDependencies resolution test passed")
    }).value
  )): _*).
  dependsOn(jsDependenciesTestDependee) // depends on jQuery
)

lazy val jsNoDependenciesTest = withRegretionTestForIssue2243(
  project.
  enablePlugins(ScalaJSPlugin, JSDependenciesPlugin)
)
