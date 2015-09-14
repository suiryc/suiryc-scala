package suiryc.scala.io

import java.io.{File, FileFilter}
import java.nio.file.Path
import scala.language.implicitConversions

class PathFinder protected(
  val filters: List[PathFilter],
  val include: Boolean,
  val parent: Either[String, PathFinder],
  val previous: Option[PathFinder] = None
)
{

  // TODO - not efficient: parent is associated to each child (== parent.get called multiple times)
  private def updateParent(other: PathFinder): PathFinder = {
    val newPrevious = previous map { other / _ }
    parent match {
      case Left(base) =>
        new PathFinder(new ChildPathFilter(base) :: filters.tail, include, Right(other), newPrevious)

      case Right(finder) =>
        new PathFinder(filters, include, Right(other / finder), newPrevious)
    }
  }

  // TODO - could be more efficient: recursive call on previous
  private def queue(other: PathFinder, include: Boolean): PathFinder =
    new PathFinder(other.filters, include, other.parent,
      Some(other.previous map { finder => queue(finder, finder.include) } getOrElse this))

  def ++(other: PathFinder): PathFinder = queue(other, include = true)

  def --(other: PathFinder): PathFinder = queue(other, include = false)

  def /(other: PathFinder): PathFinder = other.updateParent(this)

  // TODO - not efficient: parent is associated to each child
  private def addFilters(filter: PathFilter*): PathFinder =
    new PathFinder(filters ::: filter.toList, include, parent,
      previous map { _ addFilters(filter: _*) })

  def *(filter: FileFilter): PathFinder =
    addFilters(new SimplePathFilter(DirectoryFileFilter),
      new ChildrenPathFilter(filter, AllPassFileFilter, false, Some(1)))

  def /(filter: FileFilter): PathFinder = this * filter

  def ?(filter: FileFilter): PathFinder = addFilters(new SimplePathFilter(filter))

  def ? : PathFinder = ?(ExistsFileFilter)

  /* Note: using a PathFinder here is more efficient than an ExactNameFilter;
   * the latter does indeed search in all the children folders to match our
   * path.
   */
  def /(path: String): PathFinder = this / PathFinder(path)

  def **(filter: FileFilter, recursiveFilter: FileFilter = DirectoryFileFilter,
      followLinks: Boolean = false, maxDepth: Option[Int] = None): PathFinder =
    addFilters(new SimplePathFilter(DirectoryFileFilter),
      new ChildrenPathFilter(filter, recursiveFilter, followLinks, maxDepth))

  def ***(followLinks: Boolean, maxDepth: Option[Int]): PathFinder = {
    import suiryc.scala.io.RichFileFilter._

    addFilters(new SimplePathFilter(ExistsFileFilter | LinkFileFilter),
      new ChildrenPathFilter(AllPassFileFilter, AllPassFileFilter, followLinks, maxDepth))
  }

  def *** : PathFinder = ***(followLinks = false, None)

  def get(): Set[File] = {
    val parentFiles = parent match {
      case Left(base) => Set(new File(base))
      case Right(finder) => finder.get()
    }

    @scala.annotation.tailrec
    def loop(parentFiles: List[File], result: Set[File]): Set[File] =
      parentFiles match {
        case Nil => result
        case head :: tail => loop(tail, result ++ get(head))
      }

    val files1 = previous.map { _.get() }.getOrElse(Set.empty)
    val files2 = loop(parentFiles.toList, Set())
    if (include) files1 ++ files2
    else files1 -- files2
  }

  private def get(parent: File, filters: List[PathFilter]): Set[File] = {
    filters match {
      case Nil =>
        Set(parent)

      case head :: tail =>
        (for { child <- head.search(parent) }
          yield get(child, tail)).foldLeft(Set[File]())(_ ++ _)
    }
  }

  private def get(parent: File): Set[File] =
    get(parent, filters)

}

object PathFinder {

  def apply(base: String): PathFinder =
    new PathFinder(List(new SimplePathFilter(AllPassFileFilter)), true, Left(base))

  def apply(base: File): PathFinder =
    PathFinder(base.getPath)

  def apply(base: Path): PathFinder =
    PathFinder(base.toFile)

  implicit def pathFinder(base: String): PathFinder =
    PathFinder(base)

  implicit def pathFinder(file: File): PathFinder =
    PathFinder(file.getPath)

  implicit def pathFinder(path: Path): PathFinder =
    PathFinder(path.toFile)

}
