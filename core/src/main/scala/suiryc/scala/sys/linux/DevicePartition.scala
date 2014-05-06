package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Files, Paths}
import scala.io.Source
import suiryc.scala.io.SourceEx
import suiryc.scala.misc
import suiryc.scala.sys.{Command, CommandResult}


class DevicePartition(val device: Device, val partNumber: Int)
  extends Logging
{

  val block = new File(device.block, device.block.getName() + partNumber)

  val dev = new File(device.dev.getParentFile(), device.dev.getName() + partNumber)

  val size = Device.size(block)

  protected def blkid(tag: String): Either[Throwable, String] =
    try {
      /* Try a direct approach using 'blkid' (requires privileges) */
      val CommandResult(result, stdout, stderr) = Command.execute(Seq("blkid", "-o", "value", "-s", tag.toUpperCase(), dev.getPath()))
      if (result == 0) {
        Right(stdout.trim)
      }
      else {
        /* Fallback to indirect approach through '/dev/disk/by-tag' */
        val byTAG = Paths.get("/", "dev", "disk", s"by-${tag.toLowerCase()}")
        val files =
          if (!Files.isDirectory(byTAG)) Nil
          else misc.Util.wrapNull(byTAG.toFile().listFiles()).toList
        files find { file =>
          file.getCanonicalPath() == dev.getPath()
        } match {
          case Some(file) =>
            Right(file.getName())

          case None =>
            val msg =
              if (stderr != "") s"Cannot get partition[$dev] ${tag.toLowerCase()}: $stderr"
              else s"Cannot get partition[$dev] ${tag.toLowerCase()}"
            error(msg)
            Left(new Exception(msg))
        }
      }
    }
    catch {
      case e: Throwable =>
        Left(e)
    }

  /* Note: UUID may be set or changed upon formatting partition */
  def uuid: Either[Throwable, String] =
    blkid("UUID").right.map(uuid => if (uuid == "") "<unknown-uuid>" else uuid)

  def label: Either[Throwable, String] =
    blkid("LABEL")

  def mounted: Boolean = {
    val partitionUUID = uuid.fold(_ => "<unknown-uuid>", uuid => uuid)
    SourceEx.autoCloseFile(Paths.get("/", "proc", "mounts").toFile()) { source =>
      source.getLines() map { line =>
        line.trim().split("""\s""").head
      } exists { line =>
        (line == dev.toString()) || (line == s"/dev/disk/by-uuid/$partitionUUID")
      }
    }
  }

  def umount = Command.execute(Seq("umount", dev.getPath()))

  override def toString =
    s"Partition(device=$device, partNumber=$partNumber, uuid=$uuid, size=$size)"

}
