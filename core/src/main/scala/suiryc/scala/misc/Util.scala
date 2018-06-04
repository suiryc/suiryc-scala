package suiryc.scala.misc

import java.nio.file.{Files, Path, Paths}
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
  def wrapNull[T: ClassTag](a: Array[T]): Array[T] =
    Option(a).getOrElse(new Array[T](0))

  /**
   * Gets a class location.
   * Gets path for given class: parent folder of its jar, or the running folder.
   */
  def classLocation[T: ClassTag]: Path = {
    val ct = implicitly[ClassTag[T]]
    // See: http://stackoverflow.com/a/12733172
    val appPath = Paths.get(ct.runtimeClass.getProtectionDomain.getCodeSource.getLocation.toURI)
    // We either got the class jar (file), or the running folder
    if (Files.isDirectory(appPath)) appPath
    else appPath.getParent
  }

}
