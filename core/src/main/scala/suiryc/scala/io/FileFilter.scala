package suiryc.scala.io

import java.io.{File, FileFilter}
import java.nio.file.Files
import java.util.regex.Pattern
import scala.language.implicitConversions

/** File filter. */
final class RichFileFilter(val asFilter: FileFilter)
  extends FileFilterOps
{

  def accept(file: File): Boolean = asFilter.accept(file)

}

trait FileFilterOps {

  def accept(file: File): Boolean

  /**
   * Logical OR.
   * Creates a new filter which accepts files that match this filter or the
   * other.
   */
  def |(filter: FileFilter): FileFilter =
    new SimpleFileFilter( file =>
      accept(file) || filter.accept(file)
    )

  /**
   * Logical AND.
   * Creates a new filter which accepts files that match this filter and the
   * other.
   */
  def &(filter: FileFilter): FileFilter =
    new SimpleFileFilter( file =>
      accept(file) && filter.accept(file)
    )

  /**
   * Subtraction.
   * Creates a new filter which accepts files that match this filter and not
   * the other.
   */
  def -(filter: FileFilter): FileFilter =
    new SimpleFileFilter( file =>
      accept(file) && !filter.accept(file)
    )

  /**
   * Negation.
   * Creates a new filter which accepts files that do not match this filter.
   */
  def unary_- : FileFilter =
    new SimpleFileFilter( file =>
      !accept(file)
    )

}

/** Companion object. */
object RichFileFilter
{

  implicit def richFileFilter(filter: FileFilter): RichFileFilter =
    new RichFileFilter(filter)

  implicit def fileFilter(filter: RichFileFilter): FileFilter = filter.asFilter

}

/** Simple file filter whose accept function is a parameter. */
class SimpleFileFilter(val acceptFunction: File => Boolean)
  extends FileFilter
{

  def accept(file: File) = acceptFunction(file)

}

/** File filter which accepts existing files/directories. */
object ExistsFileFilter
  extends FileFilter
{

  def accept(file: File) = file.exists

}

/**
 * File filter which accepts regular files only.
 * Links are followed.
 */
object RegularFileFilter
  extends FileFilter
{

  def accept(file: File) = Files.isRegularFile(file.toPath)

}

/**
 * File filter which accepts directories only.
 * Links are followed.
 */
object DirectoryFileFilter
  extends FileFilter
{

  def accept(file: File) = Files.isDirectory(file.toPath)

}

/** File filter which accepts links only. */
object LinkFileFilter
  extends FileFilter
{

  def accept(file: File) = Files.isSymbolicLink(file.toPath)

}

/** File filter which accepts all. */
object AllPassFileFilter
  extends FileFilter
{

  def accept(file: File) = true

}
