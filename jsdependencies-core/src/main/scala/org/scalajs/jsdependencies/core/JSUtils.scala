package org.scalajs.jsdependencies.core

private[core] object JSUtils {
  final val isKeyword: Set[String] = Set(
      // Value keywords
      "true", "false", "null", "undefined",

      // Current JavaScript keywords
      "break", "case", "catch", "continue", "debugger", "default", "delete",
      "do", "else", "finally", "for", "function", "if", "in", "instanceof",
      "new", "return", "switch", "this", "throw", "try", "typeof", "var",
      "void", "while", "with",

      // Future reserved keywords
      "class", "const", "enum", "export", "extends", "import", "super",

      // Future reserved keywords in Strict mode
      "implements", "interface", "let", "package", "private", "protected",
      "public", "static", "yield"
  )
  
  def isValidIdentifier(name: String): Boolean = {
    name.nonEmpty && {
      val c = name.head
      (c == '$' || c == '_' || c.isUnicodeIdentifierStart) &&
      name.tail.forall(c => (c == '$') || c.isUnicodeIdentifierPart) &&
      !isKeyword(name)
    }
  }
}
