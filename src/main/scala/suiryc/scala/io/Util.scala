package suiryc.scala.io

import java.io.{File, FileFilter}
import java.nio.file.{Files, LinkOption, Path}
import java.nio.file.attribute.BasicFileAttributes
import scala.reflect.ClassTag

/** Misc utilities. */
object Util {

  /**
   * Filters files.
   *
   * @param files  files to filter
   * @param filter filter to apply
   * @return list of files accepted by the filter
   */
  def filterFiles(files: List[File], filter: FileFilter): List[File] =
    files.filter(filter.accept)

  /**
   * Gets whether a file is a directory.
   * Also works for symbolic links.
   *
   * @param file path to test
   * @return whether path is a directory (even if symbolic link)
   */
  @annotation.tailrec
  def isDirectory(file: File): Option[File] = if (file.isDirectory) Some(file)
    else if (isSymbolicLink(file)) isDirectory(Files.readSymbolicLink(file.toPath).toFile)
    else None

  /**
   * Gets whether a file is a symbolic link.
   *
   * @param file path to test
   * @return whether path is a symbolic link
   */
  def isSymbolicLink(file: File): Boolean =
    readAttributes(file.toPath, classOf[BasicFileAttributes], 
      LinkOption.NOFOLLOW_LINKS) map { _.isSymbolicLink } getOrElse(false)

  /**
   * Reads path attributes.
   *
   * @param path path to get attributes from
   * @return attributes
   */
  def readAttributes[A <: BasicFileAttributes](path: Path, typ: Class[A], options: LinkOption*): Option[A] =
    if (Files.exists(path, options: _*)) Some(Files.readAttributes(path, typ, options: _*))
    else None

}
