package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Files, Path, Paths}
import suiryc.scala.io.SourceEx
import suiryc.scala.misc
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.util.EitherEx


class DevicePartition(val device: Device, val partNumber: Int)
  extends Logging
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
      }
      else {
        /* Fallback to indirect approach through '/dev/disk/by-tag' */
        val byTAG = Paths.get("/", "dev", "disk", s"by-${tag.toLowerCase}")
        val files =
          if (!Files.isDirectory(byTAG)) Nil
          else misc.Util.wrapNull(byTAG.toFile.listFiles()).toList
        files find { file =>
          file.getCanonicalPath == dev.toString
        } match {
          case Some(file) =>
            Right(file.toString)

          case None =>
            val msg =
              if (stderr != "") s"Cannot get partition[$dev] ${tag.toLowerCase}: $stderr"
              else s"Cannot get partition[$dev] ${tag.toLowerCase}"
            error(msg)
            Left(new Exception(msg))
        }
      }
    }
    catch {
      case e: Exception =>
        Left(e)
    }

  /* Note: UUID may be set or changed upon formatting partition */
  def uuid: Either[Exception, String] =
    blkid("UUID").right.map(uuid => if (uuid == "") "<unknown-uuid>" else uuid)

  def label: Either[Exception, String] =
    blkid("LABEL")

  def fsType: Either[Exception, String] =
    blkid("TYPE")

  def mounted: Boolean = {
    val partitionUUID = uuid.fold(_ => "<unknown-uuid>", uuid => uuid)
    SourceEx.autoCloseFile(Paths.get("/", "proc", "mounts").toFile) { source =>
      source.getLines() map { line =>
        line.trim().split("""\s""").head
      } exists { line =>
        (line == dev.toString) || (line == s"/dev/disk/by-uuid/$partitionUUID")
      }
    }
  }

  def umount: CommandResult = Command.execute(Seq("umount", dev.toString))

  override def toString: String =
    s"Partition(device=$device, partNumber=$partNumber, uuid=$uuid, size=$size)"

}

object DevicePartition {

  def apply(device: Device, partNumber: Int): DevicePartition =
    new DevicePartition(device, partNumber)

  def option(path: Path): Option[DevicePartition] =
    Device.fromPartition(path) flatMap {device =>
      device.partitions find { partition =>
        partition.block.getFileName.toString == path.getFileName.toString
      }
    }

  def option(path: File): Option[DevicePartition] =
    option(path.toPath)

}
