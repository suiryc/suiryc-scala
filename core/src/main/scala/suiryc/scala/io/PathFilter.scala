package suiryc.scala.io

import java.io.{File, FileFilter}
import java.nio.file.Files
import suiryc.scala.misc


abstract class PathFilter
{

  def search(base: File): Set[File]

}

class SimplePathFilter(filter: FileFilter)
  extends PathFilter
{

  override def search(base: File): Set[File] =
    if (filter.accept(base)) Set(base)
    else Set()

}

class ChildPathFilter(child: String)
  extends PathFilter
{

  override def search(base: File): Set[File] = Set(new File(base, child))

}

class ChildrenPathFilter(
  filter: FileFilter,
  recursiveFilter: FileFilter,
  followLinks: Boolean,
  maxDepth: Option[Int]
)
  extends PathFilter
{
  import RichFileFilter._

  private val recursiveFilterActual =
    (if (recursiveFilter eq filter) DirectoryFileFilter
    else DirectoryFileFilter & recursiveFilter) &
      (if (followLinks) AllPassFileFilter else -LinkFileFilter)

  override def search(base: File): Set[File] =
    if (Files.isDirectory(base.toPath)) {
      searchDescendants(base, maxDepth.getOrElse(Int.MaxValue) - 1)
    } else if (Files.isSymbolicLink(base.toPath) || Files.isRegularFile(base.toPath)) {
      Set(base)
    } else {
      Set[File]()
    }

  private def searchDescendants(base: File, levels: Int): Set[File] = {
    val files = misc.Util.wrapNull(base listFiles filter).toList
    (files ::: {
      if (levels > 0) {
        val folders = if (recursiveFilter eq filter) {
          files.filter(recursiveFilterActual.accept)
        } else {
          misc.Util.wrapNull(base listFiles recursiveFilterActual).toList
        }
        for {
          childDirectory <- folders
          child <- searchDescendants(new File(base, childDirectory.getName), levels - 1)
        } yield child
      } else Nil
    }).toSet
  }

}
