package test

import scala.scalajs.js

object Test {
  def main(args: Array[String]): Unit = {
    println("Hello world")
    assert(js.typeOf(js.Dynamic.global.Mustache) == "object")
    assert((js.Dynamic.global.Mustache.version: Any) == "0.8.1")
  }
}
