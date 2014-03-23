package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Files, Paths}
import scala.io.Source
import suiryc.scala.misc
import suiryc.scala.sys.{Command, CommandResult}


class DevicePartition(val device: Device, val partNumber: Int)
  extends Logging
{

  val block = new File(device.block, device.block.getName() + partNumber)

  val dev = new File(device.dev.getParentFile(), device.dev.getName() + partNumber)

  val size = Device.size(block)

  /* Note: UUID may be set or changed upon formatting partition */
  def uuid =
    try {
      /* Try a direct approach using 'blkid' (requires privileges) */
      val CommandResult(result, stdout, stderr) = Command.execute(Seq("blkid", "-o", "value", "-s", "UUID", dev.getPath()))
      if ((result == 0) && (stdout.trim() != "")) {
        Right(stdout.trim)
      }
      else {
        /* Fallback to indirect approach through '/dev/disk/by-uuid' */
        val byUUID = Paths.get("/", "dev", "disk", "by-uuid")
        val files =
          if (!Files.isDirectory(byUUID)) Nil
          else misc.Util.wrapNull(byUUID.toFile().listFiles()).toList
        files find { file =>
          file.getCanonicalPath() == dev.getPath()
        } match {
          case Some(file) =>
            Right(file.getName())

          case None =>
            if (stderr != "") {
              val msg = s"Cannot get partition[$dev] UUID: $stderr"
              error(msg)
              Left(new Exception(msg))
            }
            else {
              val msg = s"Cannot get partition[$dev] UUID"
              error(msg)
              Left(new Exception(msg))
            }
        }
      }
    }
    catch {
      case e: Throwable =>
        Left(e)
    }

  def mounted = {
    val partitionUUID = uuid.fold(_ => "<unknown>", uuid => uuid)
    Source.fromFile(Paths.get("/", "proc", "mounts").toFile()).getLines() map { line =>
      line.trim().split("""\s""").head
    } exists { line =>
      (line == dev.toString()) || (line == s"/dev/disk/by-uuid/$partitionUUID")
    }
  }

  def umount = Command.execute(Seq("umount", dev.getPath()))

  override def toString =
    s"Partition(device=$device, partNumber=$partNumber, uuid=$uuid, size=$size)"

}
