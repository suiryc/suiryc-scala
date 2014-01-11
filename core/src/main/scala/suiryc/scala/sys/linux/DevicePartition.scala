package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.Paths
import scala.io.Source
import suiryc.scala.sys.Command


class DevicePartition(val device: Device, val partNumber: Int)
  extends Logging
{

  val block = new File(device.block, device.block.getName() + partNumber)

  val dev = new File(device.dev.getParentFile(), device.dev.getName() + partNumber)

  val size = Device.size(block)

  /* Note: UUID may be set or changed upon formatting partition */
  def uuid =
    try {
      val (result, stdout, stderr) = Command.execute(Seq("blkid", "-o", "value", "-s", "UUID", dev.toString))
      if ((result == 0) && (stdout.trim() != "")) {
        Right(stdout.trim)
      }
      else if (stderr != "") {
        val msg = s"Cannot get partition[$dev] UUID: $stderr"
        error(msg)
        Left(new Exception(msg))
      }
      else {
        val msg = "Cannot get partition[$dev] UUID"
        error(msg)
        Left(new Exception(msg))
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

  def umount = Command.execute(Seq("umount", dev.toString()))

  override def toString =
    s"Partition(device=$device, partNumber=$partNumber, uuid=$uuid, size=$size)"

}
