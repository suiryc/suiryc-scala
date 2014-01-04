package suiryc.scala.io

import java.io.{File, FileFilter}
import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute.BasicFileAttributes
import suiryc.scala.misc


abstract class PathFilter
{

  def search(base: File): Set[File]

}

class SimplePathFilter(filter: FileFilter)
  extends PathFilter
{

  def search(base: File) =
    if (filter.accept(base)) Set(base)
    else Set()

}

class ChildPathFilter(child: String)
  extends PathFilter
{

  def search(base: File) = Set(new File(base, child))

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

  def search(base: File) = {
    val attr = Util.readAttributes(base.toPath, classOf[BasicFileAttributes],
      LinkOption.NOFOLLOW_LINKS)

    attr map { attr =>
      Util.isDirectory(base) map { base =>
        searchDescendants(base, maxDepth.getOrElse(Int.MaxValue) - 1)
      } getOrElse {
        if (attr.isRegularFile || attr.isSymbolicLink)
          Set(base)
        else Set[File]()
      }
    } getOrElse(Set[File]())
  }

  private def searchDescendants(base: File, levels: Int): Set[File] = {
    val files = misc.Util.wrapNull(base listFiles filter).toList
    (files ::: {
      if (levels > 0) {
        val folders = if (recursiveFilter eq filter)
            Util.filterFiles(files, recursiveFilterActual)
          else
            misc.Util.wrapNull(base listFiles recursiveFilterActual).toList
        for {
          childDirectory <- folders
          child <- searchDescendants(new File(base, childDirectory.getName), levels - 1)
        } yield child
      } else Nil
    }).toSet
  }

}
