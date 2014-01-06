package suiryc.scala.io

import java.io.{File, FileFilter}
import java.util.regex.Pattern
import scala.language.implicitConversions
import scala.util.matching.Regex

/** File filter working with filenames. */
abstract class NameFilter
  extends FileFilter
  with FileFilterOps
{

  def accept(name: String): Boolean

  def accept(file: File): Boolean = accept(file.getName)

}

/** Simple name filter which accepts a file by its exact name. */
class ExactNameFilter(val matchName: String)
  extends NameFilter
{

  def accept(name: String) = matchName == name

}

/** Simple name filter which accepts files whose name match a pattern. */
class PatternNameFilter(val regex: Regex)
  extends NameFilter
{

  def accept(name: String) = regex.pattern.matcher(name).matches

}

/** Companion object. */
object NameFilter
{

  /** Implicit conversion from string to exact filter. */
  implicit def exactFilter(s: String): NameFilter = new ExactNameFilter(s)

  /** Implicit conversion from regex to pattern filter. */
  implicit def globFilter(r: Regex): NameFilter = new PatternNameFilter(r)

}
