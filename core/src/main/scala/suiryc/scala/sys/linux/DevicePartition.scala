package suiryc.scala.sys.linux

import com.typesafe.scalalogging.StrictLogging
import java.io.File
import java.nio.file.{Files, Path, Paths}
import suiryc.scala.io.SourceEx
import suiryc.scala.misc
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.util.EitherEx


class DevicePartition(val device: Device, val partNumber: Int)
  extends StrictLogging
{

  val block: Path = device.block.resolve(Paths.get(s"${device.block.getFileName}${device.partitionInfix}$partNumber"))

  val dev: Path = device.dev.getParent.resolve(block.getFileName)

  val size: EitherEx[Exception, Long] = Device.size(block)

  protected def blkid(tag: String): Either[Exception, String] =
    try {
      /* Try a direct approach using 'blkid' (requires privileges) */
      val CommandResult(result, stdout, stderr) = Command.execute(Seq("blkid", "-o", "value", "-s", tag.toUpperCase, dev.toString))
      if (result == 0) {
        Right(stdout.trim)
      } else {
        /* Fallback to indirect approach through '/dev/disk/by-tag'.
         * Find the link under this path that points to the partition. The
         * matching filename is the requested tag value.
         * This fallback works for 'uuid' and 'label'.
         */
        val byTAG = Paths.get("/", "dev", "disk", s"by-${tag.toLowerCase}")
        val files =
          if (!Files.isDirectory(byTAG)) Nil
          else misc.Util.wrapNull(byTAG.toFile.listFiles()).toList
        files.find { file =>
          file.getCanonicalPath == dev.toString
        } match {
          case Some(file) =>
            Right(file.toString)

          case None =>
            val msg =
              if (stderr != "") s"Cannot get partition[$dev] ${tag.toLowerCase}: $stderr"
              else s"Cannot get partition[$dev] ${tag.toLowerCase}"
            logger.error(msg)
            Left(new Exception(msg))
        }
      }
    } catch {
      case e: Exception =>
        Left(e)
    }

  /* Note: UUID may be set or changed upon formatting partition */
  def uuid: Either[Exception, String] =
    blkid("UUID").map(uuid => if (uuid == "") "<unknown-uuid>" else uuid)

  def label: Either[Exception, String] =
    blkid("LABEL")

  def fsType: Either[Exception, String] =
    blkid("TYPE")

  def mounted: Boolean = {
    val partitionUUID = uuid.fold(_ => "<unknown-uuid>", uuid => uuid)
    SourceEx.autoCloseFile(Paths.get("/", "proc", "mounts").toFile) { source =>
      source.getLines().map { line =>
        line.trim().split("""\s""").head
      } exists { line =>
        (line == dev.toString) || (line == s"/dev/disk/by-uuid/$partitionUUID")
      }
    }
  }

  def umount: CommandResult = Command.execute(Seq("umount", dev.toString))

  override def equals(other: Any): Boolean = other match {
    case that: DevicePartition => (device == that.device) && (partNumber == that.partNumber)
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq[Any](device, partNumber)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString: String =
    s"Partition(device=$device, partNumber=$partNumber, uuid=$uuid, size=$size)"

}

object DevicePartition {

  /**
   * Creates DevicePartition for given device anc partition number.
   *
   * The actual existence of this partition is not enforced.
   */
  def apply(device: Device, partNumber: Int): DevicePartition =
    new DevicePartition(device, partNumber)

  /**
   * Creates DevicePartition for given device and partition number it it exists.
   */
  def option(device: Device, partNumber: Int): Option[DevicePartition] =
    option(DevicePartition(device, partNumber).block)

  /**
   * Creates DevicePartition for given path it it exists.
   *
   * Handles both '/dev' and '/sys/block' path.
   */
  def option(path: Path): Option[DevicePartition] =
    Device.fromPartition(path).flatMap { device =>
      device.partitions.find { partition =>
        partition.block.getFileName.toString == path.getFileName.toString
      }
    }

  /**
   * Creates DevicePartition for given path it it exists.
   *
   * Handles both '/dev' and '/sys/block' path.
   */
  def option(path: File): Option[DevicePartition] =
    option(path.toPath)

}
