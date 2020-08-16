package org.scalajs.jsdependencies.sbtplugin

import scala.annotation.tailrec

import scala.collection.mutable
import scala.util.Try

import java.io.{FileFilter => _, _}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}

import sbt._
import sbt.Keys._

import org.scalajs.jsenv.JSUtils.escapeJS

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import org.scalajs.jsdependencies.core._
import org.scalajs.jsdependencies.core.DependencyResolver.DependencyFilter
import org.scalajs.jsdependencies.core.ManifestFilters.ManifestFilter

import com.google.common.jimfs.Jimfs

object JSDependenciesPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaJSPlugin

  object autoImport {
    import KeyRanks._

    val scalaJSNativeLibraries = TaskKey[Attributed[Seq[(String, Path)]]](
        "scalaJSNativeLibraries", "All the *.js files on the classpath", CTask)

    val packageJSDependencies = TaskKey[File]("packageJSDependencies",
        "Packages all dependencies of the preLink classpath in a single file.", AMinusTask)

    val packageMinifiedJSDependencies = TaskKey[File]("packageMinifiedJSDependencies",
        "Packages minified version (if available) of dependencies of the preLink " +
        "classpath in a single file.", AMinusTask)

    val jsDependencyManifest = TaskKey[File]("jsDependencyManifest",
        "Writes the JS_DEPENDENCIES file.", DTask)

    val jsDependencyManifests = TaskKey[Attributed[Iterable[JSDependencyManifest]]](
        "jsDependencyManifests", "All the JS_DEPENDENCIES on the classpath", DTask)

    val jsDependencies = SettingKey[Seq[AbstractJSDep]]("jsDependencies",
        "JavaScript libraries this project depends upon. Also used to depend on the DOM.", APlusSetting)

    val jsDependencyFilter = SettingKey[DependencyFilter]("jsDependencyFilter",
        "The filter applied to the raw JavaScript dependencies before execution", CSetting)

    val jsManifestFilter = SettingKey[ManifestFilter]("jsManifestFilter",
        "The filter applied to JS dependency manifests before resolution", CSetting)

    val resolvedJSDependencies = TaskKey[Attributed[Seq[ResolvedJSDependency]]]("resolvedJSDependencies",
        "JS dependencies after resolution.", DTask)

    /** Builder to allow declarations like:
     *
     *  {{{
     *  ProvidedJS / "foo.js"
     *  ProvidedJS / "foo.js" % "test"
     *  }}}
     */
    object ProvidedJS {
      def /(name: String): ProvidedJSModuleID = ProvidedJSModuleID(name, None)
    }

    /** Builder to allow declarations like:
     *
     *  {{{
     *  "org.webjars" % "jquery" % "1.10.2" / "jquery.js"
     *  "org.webjars" % "jquery" % "1.10.2" / "jquery.js" % "test"
     *  }}}
     */
    implicit class JSModuleIDBuilder(module: ModuleID) {
      def /(name: String): JarJSModuleID = JarJSModuleID(module, name)
    }

  }

  import autoImport._

  /** Collect certain file types from a classpath.
   *
   *  @param cp Classpath to collect from
   *  @param filter Filter for (real) files of interest (not in jars)
   *  @param collectJar Collect elements from a jar (called for all jars)
   *  @param collectFile Collect a single file. Params are the file and the
   *      relative path of the file (to its classpath entry root).
   *  @return Collected elements attributed with physical files they originated
   *      from (key: scalaJSSourceFiles).
   */
  private def collectFromClasspath[T](cp: Def.Classpath, filter: FileFilter,
      collectJar: File => Seq[T],
      collectFile: (File, String) => T): Attributed[Seq[T]] = {

    val realFiles = Seq.newBuilder[File]
    val results = Seq.newBuilder[T]

    for (cpEntry <- Attributed.data(cp) if cpEntry.exists) {
      if (cpEntry.isFile && cpEntry.getName.endsWith(".jar")) {
        realFiles += cpEntry
        results ++= collectJar(cpEntry)
      } else if (cpEntry.isDirectory) {
        for {
          (file, relPath0) <- sbt.Path.selectSubpaths(cpEntry, filter)
        } {
          val relPath = relPath0.replace(java.io.File.separatorChar, '/')
          realFiles += file
          results += collectFile(file, relPath)
        }
      } else {
        throw new IllegalArgumentException(
            "Illegal classpath entry: " + cpEntry.getPath)
      }
    }

    Attributed.blank(results.result()).put(
        scalaJSSourceFiles, realFiles.result())
  }

  // Almost entirely copied from FileVirtualIRFiles.scala upstream
  private def jarListEntries[T](jar: File,
      p: String => Boolean): List[(String, Path)] = {

    import java.util.zip._

    val jarPath = jar.getPath
    val memFS = Jimfs.newFileSystem()

    val stream =
      new ZipInputStream(new BufferedInputStream(new FileInputStream(jar)))
    try {
      val buf = new Array[Byte](4096)

      @tailrec
      def readAll(out: OutputStream): Unit = {
        val read = stream.read(buf)
        if (read != -1) {
          out.write(buf, 0, read)
          readAll(out)
        }
      }

      def makeVF(e: ZipEntry): (String, Path) = {
        val size = e.getSize
        val out =
          if (0 <= size && size <= Int.MaxValue) new ByteArrayOutputStream(size.toInt)
          else new ByteArrayOutputStream()

        try {
          readAll(out)
          val relName = e.getName
          val path = memFS.getPath(s"jarcontent/$relName")
          Files.createDirectories(path.getParent())
          val vf = Files.write(path, out.toByteArray())
          relName -> vf
        } finally {
          out.close()
        }
      }

      Iterator.continually(stream.getNextEntry())
        .takeWhile(_ != null)
        .filter(e => p(e.getName))
        .map(makeVF)
        .toList
    } finally {
      stream.close()
    }
  }

  private def jsFilesInJar(jar: File): List[(String, Path)] =
    jarListEntries(jar, _.endsWith(".js"))

  private def jsDependencyManifestsInJar(jar: File): List[JSDependencyManifest] = {
    for (vf <- jarListEntries(jar, _ == JSDependencyManifest.ManifestFileName))
      yield JSDependencyManifest.read(vf._2)
  }

  /** Concatenates a bunch of Path's.
   *  Adds a '\n' after each file.
   */
  private def concatFiles(output: Path, files: Seq[Path]): Unit = {
    val out = new BufferedOutputStream(Files.newOutputStream(output))
    try {
      for (file <- files) {
        val in = new BufferedInputStream(Files.newInputStream(file))
        try {
          val buffer = new Array[Byte](4096)
          @tailrec
          def loop(): Unit = {
            val size = in.read(buffer)
            if (size > 0) {
              out.write(buffer, 0, size)
              loop()
            }
          }
          loop()
        } finally {
          in.close()
        }

        // ASCII new line after each file
        out.write('\n')
      }
    } finally {
      out.close()
    }
  }

  // tmpSuffixRE and tmpFile copied from HTMLRunnerBuilder.scala in Scala.js

  private val tmpSuffixRE = """[a-zA-Z0-9-_.]*$""".r

  private def tmpFile(path: String, in: InputStream): File = {
    try {
      /* - createTempFile requires a prefix of at least 3 chars
       * - we use a safe part of the path as suffix so the extension stays (some
       *   browsers need that) and there is a clue which file it came from.
       */
      val suffix = tmpSuffixRE.findFirstIn(path).orNull

      val f = File.createTempFile("tmp-", suffix)
      f.deleteOnExit()
      Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING)
      f
    } finally {
      in.close()
    }
  }

  private def materialize(file: Path): File = {
    try {
      file.toFile()
    } catch {
      case _: UnsupportedOperationException =>
        tmpFile(file.toString(), new BufferedInputStream(Files.newInputStream(file)))
    }
  }

  private def packageJSDependenciesSetting(taskKey: TaskKey[File],
      cacheName: String,
      getLib: ResolvedJSDependency => Path): Setting[Task[File]] = {
    taskKey := Def.taskDyn {
      if ((skip in taskKey).value)
        Def.task((artifactPath in taskKey).value)
      else Def.task {
        val s = (streams in taskKey).value
        val deps = resolvedJSDependencies.value
        val output = (artifactPath in taskKey).value

        val realFiles = deps.get(scalaJSSourceFiles).get
        val resolvedDeps = deps.data

        FileFunction.cached(s.cacheDirectory / cacheName,
            FilesInfo.lastModified,
            FilesInfo.exists) { _ => // We don't need the files

          IO.createDirectory(output.getParentFile)
          concatFiles(output.toPath(), resolvedDeps.map(getLib))
          Set(output)
        } (realFiles.toSet)

        output
      }
    }.value
  }

  lazy val configSettings: Seq[Setting[_]] = (
      Seq(packageJSDependencies, packageMinifiedJSDependencies).map { key =>
        moduleName in key := {
          val configSuffix = configuration.value match {
            case Compile => ""
            case config  => "-" + config.name
          }
          moduleName.value + configSuffix
        }
      }
  ) ++ Seq(
      fastOptJS := fastOptJS.dependsOn(packageJSDependencies).value,
      fullOptJS := fullOptJS.dependsOn(packageJSDependencies).value,
      fullOptJS := fullOptJS.dependsOn(packageMinifiedJSDependencies).value,

      artifactPath in packageJSDependencies :=
        ((crossTarget in packageJSDependencies).value /
            ((moduleName in packageJSDependencies).value + "-jsdeps.js")),

      packageJSDependenciesSetting(packageJSDependencies, "package-js-deps", _.lib),

      artifactPath in packageMinifiedJSDependencies :=
        ((crossTarget in packageMinifiedJSDependencies).value /
            ((moduleName in packageMinifiedJSDependencies).value + "-jsdeps.min.js")),

      packageJSDependenciesSetting(packageMinifiedJSDependencies,
          "package-min-js-deps", dep => dep.minifiedLib.getOrElse(dep.lib)),

      jsDependencyManifest := {
        val myModule = thisProject.value.id
        val config = configuration.value.name

        // Collect all libraries
        val jsDeps = jsDependencies.value.collect {
          case dep: JSModuleID if dep.configurations.forall(_ == config) =>
            dep.jsDep
        }

        val manifest = new JSDependencyManifest(new Origin(myModule, config),
            jsDeps.toList)

        // Write dependency file to class directory
        val targetDir = classDirectory.value
        IO.createDirectory(targetDir)

        val file = targetDir / JSDependencyManifest.ManifestFileName
        val path = file.toPath()

        // Prevent writing if unnecessary to not invalidate dependencies
        val needWrite = !file.exists || {
          Try {
            val readManifest = JSDependencyManifest.read(path)
            readManifest != manifest
          } getOrElse true
        }

        if (needWrite)
          JSDependencyManifest.write(manifest, path)

        file
      },

      products := products.dependsOn(jsDependencyManifest).value,

      jsDependencyManifests := {
        val filter = jsManifestFilter.value
        val rawManifests = collectFromClasspath(fullClasspath.value,
            new ExactFilter(JSDependencyManifest.ManifestFileName),
            collectJar = jsDependencyManifestsInJar(_),
            collectFile = { (file, _) =>
              JSDependencyManifest.read(file.toPath())
            })

        rawManifests.map(manifests => filter(manifests))
      },

      scalaJSNativeLibraries := {
        collectFromClasspath(fullClasspath.value,
            "*.js", collectJar = jsFilesInJar,
            collectFile = (f, relPath) => relPath -> f.toPath())
      },

      resolvedJSDependencies := {
        val dependencyFilter = jsDependencyFilter.value
        val attLibs = scalaJSNativeLibraries.value
        val attManifests = jsDependencyManifests.value

        // Collect originating files
        val realFiles = {
          attLibs.get(scalaJSSourceFiles).get ++
          attManifests.get(scalaJSSourceFiles).get
        }

        // Collect available JS libraries
        val availableLibs = {
          val libs = mutable.Map.empty[String, Path]
          for (lib <- attLibs.data)
            libs.getOrElseUpdate(lib._1, lib._2)
          libs.toMap
        }

        // Actually resolve the dependencies
        val resolved = DependencyResolver.resolveDependencies(
            attManifests.data, availableLibs, dependencyFilter)

        Attributed.blank[Seq[ResolvedJSDependency]](resolved)
            .put(scalaJSSourceFiles, realFiles)
      },

      // Add the resolved JS dependencies to the list of JS files given to envs
      jsEnvInput := {
        import org.scalajs.jsenv.Input

        val env = jsEnv.value
        val prevInput = jsEnvInput.value
        val deps = resolvedJSDependencies.value.data

        if (deps.isEmpty) {
          /* If no dependencies need to be added, do not throw an error on
           * unknown Inputs
           */
          prevInput
        } else {
          /* Implement the behavior of commonJSName without having to burn it
           * inside NodeJSEnv, and hence in the JSEnv API.
           * Since this matches against NodeJSEnv specifically, it obviously
           * breaks the OO approach, but oh well ...
           */
          val libs = env match {
            case _: org.scalajs.jsenv.nodejs.NodeJSEnv =>
              val memFS = Jimfs.newFileSystem()
              val usedNames = mutable.Set.empty[String]

              def getFileNameFor(commonJSName: String): String = {
                var name = commonJSName
                var suffix = 0
                while (!usedNames.add(name)) {
                  suffix += 1
                  name = s"${commonJSName}_$suffix"
                }
                s"require-$name.js"
              }

              for (dep <- deps) yield {
                dep.info.commonJSName.fold {
                  dep.lib
                } { commonJSName =>
                  val fname = materialize(dep.lib).getAbsolutePath
                  Files.write(memFS.getPath(getFileNameFor(commonJSName)),
                      s"""$commonJSName = require("${escapeJS(fname)}");""".getBytes(StandardCharsets.UTF_8))
                }
              }

            case _ =>
              deps.map(_.lib)
          }

          libs.map(Input.Script(_)) ++ prevInput
        }
      }
  )

  lazy val compileSettings: Seq[Setting[_]] = configSettings

  lazy val testSettings: Seq[Setting[_]] = configSettings

  override def projectSettings: Seq[Setting[_]] = Def.settings(
      inConfig(Compile)(compileSettings),
      inConfig(Test)(testSettings),

      // add all the webjars your jsDependencies depend upon
      libraryDependencies ++= jsDependencies.value.collect {
        case JarJSModuleID(module, _) => module
      },

      jsDependencies := Seq(),
      jsDependencyFilter := identity,
      jsManifestFilter := identity
  )

}
