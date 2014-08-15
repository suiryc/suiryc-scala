package suiryc.scala.io

import java.nio.file.{Files, Path}
import java.nio.file.attribute.{BasicFileAttributes, FileTime}
import java.util.zip.ZipEntry


class FileTimes(
  val creation: FileTime,
  val lastModified: FileTime,
  val lastAccess: FileTime
)

object FileTimes {

  def apply(creation: FileTime, lastModified: FileTime, lastAccess: FileTime, time: Long): FileTimes = {
    def orNull(time: FileTime) =
      if ((time != null) && (time.toMillis <= 0)) null
      else time

    def orTime(time1: FileTime, time2: FileTime) =
      if (time1 == null) time2
      else time1

    new FileTimes(
      orNull(creation),
      orTime(orNull(lastModified), if (time < 0) null else FileTime.fromMillis(time)),
      orNull(lastAccess)
    )
  }

  def apply(entry: ZipEntry): FileTimes =
    FileTimes(
      entry.getCreationTime,
      entry.getLastModifiedTime,
      entry.getLastAccessTime,
      entry.getTime
    )

  def apply(path: Path): FileTimes = {
    val attr = Files.readAttributes(path, classOf[BasicFileAttributes])
    FileTimes(
      attr.creationTime(),
      attr.lastModifiedTime(),
      attr.lastAccessTime(),
      path.toFile.lastModified()
    )
  }

}
