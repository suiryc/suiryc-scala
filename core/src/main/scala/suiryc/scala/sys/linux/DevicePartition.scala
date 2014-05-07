package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.io.Source
import suiryc.scala.io.SourceEx
import suiryc.scala.misc
import suiryc.scala.sys.{Command, CommandResult}


class DevicePartition(val device: Device, val partNumber: Int)
  extends Logging
{

  val block = device.block.resolve(Paths.get(device.block.getFileName().toString + partNumber))

  val dev = device.dev.getParent().resolve(block.getFileName())

  val size = Device.size(block)

  protected def blkid(tag: String): Either[Throwable, String] =
    try {
      /* Try a direct approach using 'blkid' (requires privileges) */
      val CommandResult(result, stdout, stderr) = Command.execute(Seq("blkid", "-o", "value", "-s", tag.toUpperCase(), dev.toString))
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
          file.getCanonicalPath() == dev.toString
        } match {
          case Some(file) =>
            Right(file.toString)

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

  def fsType: Either[Throwable, String] =
    blkid("TYPE")

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

  def umount = Command.execute(Seq("umount", dev.toString))

  override def toString =
    s"Partition(device=$device, partNumber=$partNumber, uuid=$uuid, size=$size)"

}

object DevicePartition {

  private val PathRegexp = """^([^0-9]+)([0-9]+)$""".r

  def apply(device: Device, partNumber: Int): DevicePartition =
    new DevicePartition(device, partNumber)

  def apply(path: Path): DevicePartition = {
    path.getFileName().toString match {
      case PathRegexp(name, partNumber) =>
        val pathParent = path.getParent()
        val deviceBlock = pathParent.compareTo(Paths.get("/dev")) match {
          case 0 =>
            Paths.get("/sys", "block", name)

          case _ =>
            pathParent
        }

        val device = Device(deviceBlock)
        DevicePartition(device, partNumber.toInt)

      case _ =>
        throw new Exception(s"Invalid partition path: $path")
    }
  }

  def apply(path: File): DevicePartition =
    apply(path.toPath())

  def option(path: Path): Option[DevicePartition] = {
    val part = DevicePartition(path)

    part.device.partitions find(_.partNumber == part.partNumber)
  }

  def option(path: File): Option[DevicePartition] =
    option(path.toPath())

}
