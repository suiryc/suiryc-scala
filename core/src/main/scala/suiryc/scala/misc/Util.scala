package suiryc.scala.misc

import java.io.{File, FileFilter}
import java.nio.file.{Files, LinkOption, Path}
import java.nio.file.attribute.BasicFileAttributes
import scala.reflect.ClassTag

/** Misc utilities. */
object Util {

  /**
   * Wraps null array.
   * Replaces null by empty array if needed.
   *
   * @param a array to wrap
   * @return non-null array
   */
  def wrapNull[T: ClassTag](a: Array[T]) =
    if (a == null) new Array[T](0)
    else a

}
