package org.scalajs.jsdependencies.core

import java.nio.file.Path

/** A dependency on a native JavaScript library that has been successfully
 *  resolved
 */
final class ResolvedJSDependency(
    val lib: Path,
    val minifiedLib: Option[Path],
    val info: ResolutionInfo)

object ResolvedJSDependency {
  /** Absolute minimum for a [[ResolvedJSDependency]]:
   *
   *  - The library itself
   *  - Its relative name (lib.name)
   */
  def minimal(lib: Path): ResolvedJSDependency = {
    val name = lib.getFileName().toString()
    val info = new ResolutionInfo(name, Set.empty, Nil, None, None)
    new ResolvedJSDependency(lib, None, info)
  }
}
