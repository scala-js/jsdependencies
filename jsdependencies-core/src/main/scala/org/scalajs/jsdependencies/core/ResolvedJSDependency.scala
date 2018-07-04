package org.scalajs.jsdependencies.core

import org.scalajs.io._

/** A dependency on a native JavaScript library that has been successfully
 *  resolved
 */
final class ResolvedJSDependency(
    val lib: VirtualBinaryFile,
    val minifiedLib: Option[VirtualBinaryFile],
    val info: ResolutionInfo)

object ResolvedJSDependency {
  /** Absolute minimum for a [[ResolvedJSDependency]]:
   *
   *  - The library itself
   *  - Its relative name (lib.name)
   */
  def minimal(lib: VirtualBinaryFile): ResolvedJSDependency = {
    val path = lib.path
    val separatorIndex = Math.max(path.lastIndexOf('/'),
        path.lastIndexOf(java.io.File.separatorChar))
    val name = path.substring(separatorIndex + 1)
    val info = new ResolutionInfo(name, Set.empty, Nil, None, None)
    new ResolvedJSDependency(lib, None, info)
  }
}
