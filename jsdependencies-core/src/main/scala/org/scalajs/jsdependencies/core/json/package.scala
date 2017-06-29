package org.scalajs.jsdependencies.core

import java.io.{Reader, Writer}

/** Some type-class lightweight wrappers around simple-json.
 *
 *  They allow to write `xyz.toJSON` to obtain classes that can be
 *  serialized by simple-json and `fromJSON[T](xyz)` to get an
 *  object back.
 */
package object json {
  private[core] type JSON = Impl.Repr

  private[core] implicit class JSONPimp[T: JSONSerializer](x: T) {
    def toJSON: JSON = implicitly[JSONSerializer[T]].serialize(x)
  }

  private[core] def fromJSON[T](x: JSON)(implicit d: JSONDeserializer[T]): T =
    d.deserialize(x)

  private[core] def writeJSON(x: JSON, writer: Writer): Unit =
    Impl.serialize(x, writer)

  private[core] def jsonToString(x: JSON): String =
    Impl.serialize(x)

  private[core] def readJSON(str: String): JSON =
    Impl.deserialize(str)

  private[core] def readJSON(reader: Reader): JSON =
    Impl.deserialize(reader)

}
