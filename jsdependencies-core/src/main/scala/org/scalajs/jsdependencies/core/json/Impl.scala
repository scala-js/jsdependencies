package org.scalajs.jsdependencies.core.json

import org.json.simple.JSONValue

import java.io.{Writer, Reader}
import java.util.function.{BiConsumer, Consumer}

private[json] object Impl {

  type Repr = Object

  def fromString(x: String): Repr = x
  def fromNumber(x: Number): Repr = x
  def fromBoolean(x: Boolean): Repr = java.lang.Boolean.valueOf(x)

  def fromList(x: List[Repr]): Repr = {
    val result = new java.util.LinkedList[Repr]
    x.foreach(result.add(_))
    result
  }

  def fromMap(x: Map[String, Repr]): Repr = {
    val result = new java.util.HashMap[String, Repr]
    for ((key, value) <- x)
      result.put(key, value)
    result
  }

  def toString(x: Repr): String = x.asInstanceOf[String]
  def toNumber(x: Repr): Number = x.asInstanceOf[Number]
  def toBoolean(x: Repr): Boolean =
    x.asInstanceOf[java.lang.Boolean].booleanValue()

  def toList(x: Repr): List[Repr] = {
    val builder = List.newBuilder[Repr]
    x.asInstanceOf[java.util.List[Repr]].forEach(new Consumer[Repr] {
      def accept(elem: Repr): Unit =
        builder += elem
    })
    builder.result()
  }

  def toMap(x: Repr): Map[String, Repr] = {
    val builder = Map.newBuilder[String, Repr]
    x.asInstanceOf[java.util.Map[String, Repr]].forEach(new BiConsumer[String, Repr] {
      def accept(key: String, value: Repr): Unit =
        builder += key -> value
    })
    builder.result()
  }

  def serialize(x: Repr): String =
    JSONValue.toJSONString(x)

  def serialize(x: Repr, writer: Writer): Unit =
    JSONValue.writeJSONString(x, writer)

  def deserialize(str: String): Repr = JSONValue.parseWithException(str)

  def deserialize(reader: Reader): Repr = JSONValue.parseWithException(reader)

}
