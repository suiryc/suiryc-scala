package suiryc.scala.io

import com.typesafe.scalalogging.StrictLogging
import java.io.{File, FileInputStream}
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{CopyOption, Files, LinkOption, Path, StandardCopyOption}
import java.nio.file.attribute.BasicFileAttributeView


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
    def copy(sourceRoot: Path, source: Option[Path], targetRoot: Path) {
      import RichFile._

      source match {
        case Some(source) =>
          val sourcePath = sourceRoot.resolve(source)
          val sourceRealPath =
            if (followLinks) sourcePath.getParent.toRealPath().resolve(sourcePath.toFile.getName)
            else sourcePath
          if (followLinks && !sourceRealPath.startsWith(sourceRoot))
            logger.warn(s"Real path[$sourceRealPath] is outside root path[$sourceRoot], skipping")
          else {
            val sourceReal = sourceRoot.relativize(sourceRealPath)
            val pathTarget = targetRoot.resolve(sourceReal)
            if (!pathTarget.exists) {
              // first make sure parent exists (both real and possible link)
              copy(sourceRoot, Option(source.getParent), targetRoot)
              if (followLinks) copy(sourceRoot, Option(sourceReal.getParent), targetRoot)
              // then copy source to target
              if (Files.isRegularFile(sourceRealPath) || Files.isSymbolicLink(sourceRealPath) || Files.isDirectory(sourceRealPath)) {
                logger.trace(s"Copying source[$sourceRealPath] to[$pathTarget]")
                Files.copy(sourceRealPath, pathTarget,
                  options:_*)
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
  def setTimes(path: Path, times: FileTimes): Unit = {
    if ((times.lastModified != null) || (times.creation != null) || (times.lastAccess != null)) {
      // If there is only the 'last modified' time, set it directly on 'File'.
      // Otherwise change the file attributes.
      if ((times.creation == null) && (times.lastAccess == null)) {
        path.toFile.setLastModified(times.lastModified.toMillis)
        ()
      }
      else {
        val attrView = Files.getFileAttributeView(path, classOf[BasicFileAttributeView])
        attrView.setTimes(times.lastModified, times.lastAccess, times.creation)
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

}
