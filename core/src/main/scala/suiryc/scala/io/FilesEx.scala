package suiryc.scala.io

import grizzled.slf4j.Logging
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}


object FilesEx
  extends Logging
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
  def copy(
    sourceRoot: Path,
    source: Path,
    targetRoot: Path,
    followLinks: Boolean = true,
    owner: Option[Owner] = None
  ) {
    def copy(sourceRoot: Path, source: Option[Path], targetRoot: Path) {
      import RichFile._

      source match {
        case Some(source) =>
          val sourcePath = sourceRoot.resolve(source)
          val sourceRealPath =
            if (followLinks) sourcePath.getParent.toRealPath().resolve(sourcePath.toFile.getName)
            else sourcePath
          if (followLinks && !sourceRealPath.startsWith(sourceRoot))
            warn(s"Real path[$sourceRealPath] is outside root path[$sourceRoot], skipping")
          else {
            val sourceReal = sourceRoot.relativize(sourceRealPath)
            val pathTarget = targetRoot.resolve(sourceReal)
            if (!pathTarget.exists) {
              /* first make sure parent exists (both real and possible link) */
              copy(sourceRoot, Option(source.getParent), targetRoot)
              if (followLinks) copy(sourceRoot, Option(sourceReal.getParent), targetRoot)
              /* then copy source to target */
              trace(s"Copying source[$sourceRealPath] to[$pathTarget]")
              Files.copy(sourceRealPath, pathTarget,
                StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
            }
          }

        case None =>
          if (!targetRoot.exists) {
            /* first make sure parent exists */
            copy(sourceRoot, None, targetRoot.getParent)
            /* then create target (directory) with user/group if necessary */
            trace(s"Creating target[$targetRoot]")
            targetRoot.mkdir
            owner foreach { owner =>
              targetRoot.changeOwner(owner.user, owner.group)
            }
          }
      }
    }

    debug(s"Copying source[$source] root[$sourceRoot] to[$targetRoot]")

    copy(sourceRoot, Some(source), targetRoot)
  }

}
