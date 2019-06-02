package org.scalajs.jsdependencies.core

import scala.collection.immutable.{Seq, Traversable}

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Files}

import org.scalajs.jsdependencies.core.json._

/** The information written to a "JS_DEPENDENCIES" manifest file. */
final class JSDependencyManifest(
    val origin: Origin,
    val libDeps: List[JSDependency]) {

  import JSDependencyManifest._

  override def equals(that: Any): Boolean = that match {
    case that: JSDependencyManifest =>
      this.origin == that.origin &&
      this.libDeps == that.libDeps
    case _ =>
      false
  }

  override def hashCode(): Int = {
    import scala.util.hashing.MurmurHash3._
    var acc = HashSeed
    acc = mix(acc, origin.##)
    acc = mixLast(acc, libDeps.##)
    finalizeHash(acc, 2)
  }

  override def toString(): String = {
    val b = new StringBuilder
    b ++= s"JSDependencyManifest(origin=$origin"
    if (libDeps.nonEmpty)
      b ++= s", libDeps=$libDeps"
    b ++= ")"
    b.result()
  }
}

object JSDependencyManifest {

  // "org.scalajs.jsdependencies.core.JSDependencyManifest".##
  private final val HashSeed = -902988673

  final val ManifestFileName = "JS_DEPENDENCIES"

  implicit object JSDepManJSONSerializer extends JSONSerializer[JSDependencyManifest] {
    @inline def optList[T](x: List[T]): Option[List[T]] =
      if (x.nonEmpty) Some(x) else None

    def serialize(x: JSDependencyManifest): JSON = {
      new JSONObjBuilder()
        .fld("origin", x.origin)
        .opt("libDeps", optList(x.libDeps))
        .toJSON
    }
  }

  implicit object JSDepManJSONDeserializer extends JSONDeserializer[JSDependencyManifest] {
    def deserialize(x: JSON): JSDependencyManifest = {
      val obj = new JSONObjExtractor(x)
      new JSDependencyManifest(
          obj.fld[Origin]("origin"),
          obj.opt[List[JSDependency]]("libDeps").getOrElse(Nil))
    }
  }

  def write(dep: JSDependencyManifest, output: Path): Unit = {
    val writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)
    try {
      write(dep, writer)
    } finally {
      writer.close()
    }
  }

  def write(dep: JSDependencyManifest, writer: Writer): Unit =
    writeJSON(dep.toJSON, writer)

  def read(file: Path): JSDependencyManifest = {
    val reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)
    try {
      read(reader)
    } finally {
      reader.close()
    }
  }

  def read(reader: Reader): JSDependencyManifest =
    fromJSON[JSDependencyManifest](readJSON(reader))

}
