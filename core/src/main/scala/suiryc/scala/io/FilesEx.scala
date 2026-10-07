package suiryc.scala.io

import com.sun.jna.{Library, Memory, Native, Pointer}
import com.typesafe.scalalogging.StrictLogging
import java.io.{File, FileInputStream}
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{CopyOption, Files, LinkOption, Path, StandardCopyOption}
import java.nio.file.attribute.{BasicFileAttributeView, BasicFileAttributes, FileTime}
import java.util.concurrent.TimeUnit
import suiryc.scala.sys.OS


object FilesEx
  extends StrictLogging
{

  case class Owner(user: Option[String], group: Option[String])

  /**
   * Copies file/directory from source to target.
   *
   * Recursively processes folders. Also processes real path for symbolic links.
   *
   * @param sourceRoot  root for source file
   * @param source      (relative) source to copy
   * @param targetRoot  root for target file
   * @param followLinks whether to follow (and process real path) links
   * @param owner       specific owner to set when creating target directories
   *   that do not have a source equivalent
   */
  // scalastyle:off method.length
  def copy(
    sourceRoot: Path,
    source: Path,
    targetRoot: Path,
    followLinks: Boolean = true,
    owner: Option[Owner] = None,
    options: List[CopyOption] = List(StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
  ): Unit = {
    def copy(sourceRoot: Path, source: Option[Path], targetRoot: Path): Unit = {
      import RichFile._

      source match {
        case Some(srcPath) =>
          val sourcePath = sourceRoot.resolve(srcPath)
          val sourceRealPath =
            if (followLinks) sourcePath.getParent.toRealPath().resolve(sourcePath.toFile.getName)
            else sourcePath
          if (followLinks && !sourceRealPath.startsWith(sourceRoot)) {
            logger.warn(s"Real path[$sourceRealPath] is outside root path[$sourceRoot], skipping")
          } else {
            val sourceReal = sourceRoot.relativize(sourceRealPath)
            val pathTarget = targetRoot.resolve(sourceReal)
            if (!pathTarget.exists) {
              // first make sure parent exists (both real and possible link)
              copy(sourceRoot, Option(srcPath.getParent), targetRoot)
              if (followLinks) copy(sourceRoot, Option(sourceReal.getParent), targetRoot)
              // then copy source to target
              val isSymbolicLink = Files.isSymbolicLink(sourceRealPath)
              if (Files.isRegularFile(sourceRealPath) || isSymbolicLink || Files.isDirectory(sourceRealPath)) {
                logger.trace(s"Copying source[$sourceRealPath] to[$pathTarget]")
                Files.copy(sourceRealPath, pathTarget, options: _*)
                if (isSymbolicLink && OS.isLinux) {
                  // File times were not copied in this case.
                  try {
                    val linkOptions = options.collect {
                      case lo: LinkOption => lo
                    }
                    val attrs = Files.readAttributes(sourceRealPath, classOf[BasicFileAttributes], linkOptions: _*)
                    val fileTimes = new FileTimes(
                      attrs.creationTime(),
                      attrs.lastModifiedTime(),
                      attrs.lastAccessTime()
                    )
                    FilesEx.setTimes(pathTarget, fileTimes, linkOptions)
                  } catch {
                    case ex: Exception =>
                      logger.warn(s"Failed to copy path[$sourceRealPath] file times: ${ex.getMessage}", ex)
                  }
                }
              } else {
                logger.warn(s"Real path[$sourceRealPath] is not a regular file/directory, skipping")
              }
              ()
            }
          }

        case None =>
          if (!targetRoot.exists) {
            // first make sure parent exists
            copy(sourceRoot, None, targetRoot.getParent)
            // then create target (directory) with user/group if necessary
            logger.trace(s"Creating target[$targetRoot]")
            targetRoot.mkdir
            owner.foreach { owner =>
              targetRoot.changeOwner(owner.user, owner.group)
            }
          }
      }
    }

    logger.debug(s"Copying source[$source] root[$sourceRoot] to[$targetRoot]")

    copy(sourceRoot, Some(source), targetRoot)
  }
  // scalastyle:on method.length

  // scalastyle:off null
  def setTimes(
    path: Path,
    times: FileTimes,
    options: List[LinkOption] = List(LinkOption.NOFOLLOW_LINKS)
  ): Unit = {
    if ((times.lastModified != null) || (times.creation != null) || (times.lastAccess != null)) {
      // If there is only the 'last modified' time, set it directly on 'File'.
      // Otherwise, change the file attributes.
      if ((times.creation == null) && (times.lastAccess == null)) {
        path.toFile.setLastModified(times.lastModified.toMillis)
        ()
      }
      else {
        // Note: under Linux, this fails for symbolic links (even when not
        // following them), and we need to use a low-level system call.
        if (OS.isLinux && Files.isSymbolicLink(path)) {
          setCTimes(path, options.contains(LinkOption.NOFOLLOW_LINKS), times.lastModified, times.lastAccess)
          ()
        } else {
          val attrView = Files.getFileAttributeView(path, classOf[BasicFileAttributeView], options: _*)
          attrView.setTimes(times.lastModified, times.lastAccess, times.creation)
        }
      }
    }
    // else: no time to set
  }
  // scalastyle:on null

  /**
   * Maps file in memory.
   *
   * @param file file to map
   * @param offset offset in file
   * @param length length of data to map
   * @return mapped buffer
   */
  def map(file: File, offset: Long = 0, length: Long = -1): MappedByteBuffer = {
    val input = new FileInputStream(file)
    val channel = input.getChannel
    val size = if (length >= 0) length else channel.size
    val mapped = channel.map(FileChannel.MapMode.READ_ONLY, offset, size)
    // Note: we can safely close the input after mapping in memory
    input.close()
    mapped
  }

  trait CLibrary extends Library {
    def utimensat(dirfd: Int, pathname: String, times: Pointer, flags: Int): Int
  }

  object CLibrary {
    val INSTANCE: CLibrary = Native.load("c", classOf[CLibrary])

    val AT_FDCWD: Int = -100
    val AT_SYMLINK_NOFOLLOW: Int = 0x100
  }

  def setCTimes(path: Path, nofollow: Boolean, lastModified: FileTime, lastAccess: FileTime): Unit = {
    val absolutePath = path.toAbsolutePath.toString

    val longSize: Long = 8
    val memory = new Memory(4 * longSize)
    memory.setLong(0, lastAccess.to(TimeUnit.SECONDS))
    memory.setLong(longSize, lastAccess.toInstant.getNano.toLong)
    memory.setLong(2 * longSize, lastModified.to(TimeUnit.SECONDS))
    memory.setLong(3 * longSize, lastModified.toInstant.getNano.toLong)

    val result = CLibrary.INSTANCE.utimensat(
      CLibrary.AT_FDCWD,
      absolutePath,
      memory,
      if (nofollow) CLibrary.AT_SYMLINK_NOFOLLOW else 0
    )

    if (result != 0) {
      throw new Exception(s"Failed to call utimensat: return code=<$result> errno=<${Native.getLastError}>")
    }
  }

}
