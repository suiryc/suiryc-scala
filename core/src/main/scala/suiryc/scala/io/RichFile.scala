package suiryc.scala.io

import java.io.{File, PrintWriter}
import java.net.URL
import java.nio.file.{Files, FileSystems, LinkOption, Path}
import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermission}
import scala.io.Codec
import scala.language.implicitConversions
import suiryc.scala.misc


final class RichFile(val asFile: File) extends AnyVal
{

  // scalastyle:off method.name
  def /(component: String): File =
    if (component == ".") asFile
    else new File(asFile, component)
  // scalastyle:on method.name

  def exists: Boolean = asFile.exists

  def isDirectory: Boolean = asFile.isDirectory

  def lastModified: Long = asFile.lastModified

  def asURL: URL = asFile.toURI.toURL

  def absolutePath: String = asFile.getAbsolutePath

  def canonicalPath: String = asFile.getCanonicalPath

  def name: String = asFile.getName

  def ext: String = baseAndExt._2

  def base: String = baseAndExt._1

  def baseAndExt: (String, String) = {
    val nme = name
    val dot = nme.lastIndexOf('.')
    if (dot < 0) (nme, "")
    else (nme.substring(0, dot), nme.substring(dot + 1))
  }

  def mkdir: Boolean = asFile.mkdir

  def mkdirs: Boolean = asFile.mkdirs

  def changeOwner(user: Option[String], group: Option[String]): Unit = {
    if (user.isDefined || group.isDefined) {
      val lookupService = FileSystems.getDefault.getUserPrincipalLookupService
      val posix: PosixFileAttributeView = Files.getFileAttributeView(asFile.toPath,
        classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)
      user foreach { user =>
        posix.setOwner(lookupService.lookupPrincipalByName(user))
      }
      group foreach { group =>
        posix.setGroup(lookupService.lookupPrincipalByGroupName(group))
      }
    }
  }

  def changeMode(permissions: java.util.Set[PosixFilePermission]): Unit = {
    val posix: PosixFileAttributeView = Files.getFileAttributeView(asFile.toPath,
      classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)
    posix.setPermissions(permissions)
  }

  /**
   * Deletes file/directory.
   *
   * @param recursive whether to process recursively
   * @param onlyChildren whether to only delete children (if any) or root too
   */
  def delete(recursive: Boolean, onlyChildren: Boolean = false): Boolean =
  {
    @annotation.tailrec
    def loop(files: List[File], rest: List[File], onlyChildren: Boolean, success: Boolean): Boolean =
      files match {
        case Nil =>
          rest match {
            case Nil =>
              success

            case head :: tail =>
              loop(Nil, tail, onlyChildren = false, success = head.delete() & success)
          }

        case head :: tail =>
          val children =
            if (recursive && head.isDirectory && !Files.isSymbolicLink(head.toPath))
              misc.Util.wrapNull(head.listFiles).toList
            else
              Nil
          val (newRest, newSuccess) =
            if (onlyChildren) (rest, success)
            else if (children.isEmpty) {
              (rest, head.delete() & success)
            }
            else (head :: rest, success)
          loop(tail ::: children, newRest, onlyChildren = false, success = newSuccess)
      }

    if (exists) loop(List(asFile), Nil, onlyChildren, success = true)
    else true
  }

  def read(): String =
    SourceEx.autoCloseFile(asFile)(_.mkString)

  def write(s: String)(implicit codec: Codec): Unit = {
    write(s, codec.name)
  }

  def write(s: String, enc: String): Unit = {
    val writer = new PrintWriter(asFile, enc)
    try {
      writer.write(s)
    }
    finally {
      writer.close()
    }
  }

}

object RichFile {

  implicit def path(file: File): Path = file.toPath

  implicit def richFile(file: File): RichFile = new RichFile(file)

  implicit def richFile(path: Path): RichFile = new RichFile(path.toFile)

  implicit def stdFile(file: RichFile): File = file.asFile

  def apply(f: File): RichFile = new RichFile(f)

  def apply(f: String): RichFile = RichFile(new File(f))

  def userHome: File =
    Option(System.getenv("HOME")) orElse Option(System.getProperty("user.home")) map {
      new File(_)
    } getOrElse {
      throw new Error("User home directory is unknown")
    }

  val sep: Char = File.separatorChar

  /**
   * Creates temporary directory.
   *
   * @param prefix name prefix, can be <tt>null</tt>
   * @return temporary directory
   */
  def createTempDirectory(prefix: String): File =
    Files.createTempDirectory(prefix).toFile

}
