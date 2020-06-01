package suiryc.scala.sys.linux

import com.typesafe.scalalogging.StrictLogging
import java.io.File
import java.nio.file.{Path, Paths}
import suiryc.scala.io.{AllPassFileFilter, NameFilter, PathFinder, SourceEx}
import suiryc.scala.io.NameFilter._
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.util.EitherEx


class Device(val block: Path) extends StrictLogging {
  import Device._
  import PathFinder._
  import NameFilter._

  val dev: Path = Paths.get("/dev").resolve(block.getFileName)

  protected def defaultName = "<unknown>"

  val nameOption: Option[String] = propertyContent(block, "device", "name")
  val name: String = nameOption.getOrElse(defaultName)

  val vendorOption: Option[String] = propertyContent(block, "device", "vendor")
  val vendor: String = vendorOption.getOrElse(defaultName)

  val modelOption: Option[String] = propertyContent(block, "device", "model")
  val model: String = modelOption.getOrElse(defaultName)

  val ueventProps: Map[String, String] = {
    val uevent = Paths.get(block.toString, "device", "uevent").toFile
    val props = Map.empty[String, String]

    if (uevent.exists()) {
      SourceEx.autoCloseFile(uevent) { source =>
        source.getLines().toList.foldLeft(props) { (props, line) =>
          line match {
            case KeyValueRegexp(key, value) =>
              props + (key.trim() -> value.trim())

            case _ =>
              props
          }
        }
      }
    } else props
  }

  val size: EitherEx[Exception, Long] = Device.size(block)

  val readOnly: Boolean =
    propertyContent(block, "ro").exists { v =>
      v.toInt != 0
    }

  val removable: Boolean =
    propertyContent(block, "removable").exists { v =>
      v.toInt != 0
    }

  protected[linux] def partitionInfix: String = {
    val sysName = block.getFileName.toString
    if (sysName(sysName.length - 1).isDigit) "p"
    else ""
  }

  val partitions: Set[DevicePartition] = {
    val devName = dev.getFileName.toString
    (block * s"""$devName$partitionInfix[0-9]+""".r).get().map { path =>
      DevicePartition(this, path.getName.substring(devName.length() + partitionInfix.length()).toInt)
    }
  }

  /* Notes:
   * We can get some device information such as its id through various means:
   * blkid, fdisk, etc.
   * All of them require privileges.
   */
  protected def blkid(tag: String): Either[Exception, String] =
    try {
      val CommandResult(result, stdout, stderr) = Command.execute(Seq("blkid", "-o", "value", "-s", tag.toUpperCase, dev.toString))
      if (result == 0) {
        Right(stdout.trim)
      } else {
        val msg =
          if (stderr != "") s"Cannot get device[$dev] ${tag.toLowerCase}: $stderr"
          else s"Cannot get device[$dev] ${tag.toLowerCase}"
        logger.error(msg)
        Left(new Exception(msg))
      }
    } catch {
      case e: Exception =>
        Left(e)
    }

  /* Note: PTUUID may be set or changed upon (re)creating partition table */
  def uuid: Either[Exception, String] =
    blkid("PTUUID").map(uuid => if (uuid == "") "<unknown-uuid>" else uuid)

  def ptType: Either[Exception, String] =
    blkid("PTTYPE")

  def partprobe(): CommandResult =
    Command.execute(Seq("partprobe", dev.toString))

  override def equals(other: Any): Boolean = other match {
    case that: Device => block == that.block
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq[Any](block)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString: String =
    s"Device(block=$block, vendor=$vendor, model=$model, ueventProps=$ueventProps)"

}


class NetworkBlockDevice(override val block: Path)
  extends Device(block)
{

  override protected def defaultName = "Network Block Device"

}

class LoopbackDevice(override val block: Path)
  extends Device(block)
{

  override protected def defaultName = "Loopback Device"

}


object Device
  extends StrictLogging
{

  private val KeyValueRegexp = """^([^=]*)=(.*)$""".r

  def propertyContent(block: Path, path: String*): Option[String] = {
    val file = Paths.get(block.toString, path: _*).toFile

    if (file.exists()) Option {
      SourceEx.autoCloseFile(file) { source =>
        source.getLines().map { line =>
          line.trim()
        }.filterNot { line =>
          line == ""
        }.mkString(" / ")
      }
    } else None
  }

  def size(block: Path): EitherEx[Exception, Long] = {
    propertyContent(block, "size").map { size =>
      EitherEx(Right(size.toLong * 512))
    }.getOrElse {
      try {
        val dev = Paths.get("/dev").resolve(block.getFileName)
        val CommandResult(result, stdout, stderr) = Command.execute(Seq("blockdev", "--getsz", dev.toString))
        if (result == 0) {
          EitherEx(Right(stdout.trim.toLong * 512))
        }
        else {
          val msg = s"Cannot get device size: $stderr"
          logger.error(msg)
          EitherEx(Left(new Exception(msg)), -1L)
        }
      } catch {
        case e: Exception =>
          EitherEx(Left(e), -1L)
      }
    }
  }

  def fromPartition(path: Path): Option[Device] = {
    val sysBlock = Paths.get("/sys", "block")

    if (path.startsWith(sysBlock)) {
      Some(Device(path.getParent))
    } else {
      val finder = PathFinder(sysBlock) * AllPassFileFilter * path.getFileName.toString
      finder.get().headOption.map(file => Device(file.toPath.getParent))
    }
  }

  /**
   * Creates a Device for the given path.
   *
   * Handles both '/dev' and '/sys/block' path.
   * The actual existence of this device is not enforced.
   */
  def apply(path: Path): Device = {
    lazy val sysblockPath = Paths.get("/sys", "block", path.getFileName.toString)
    val block = if (Option(path.getParent).contains(Paths.get("/dev")) && sysblockPath.toFile.exists()) {
      sysblockPath
    } else path

    if (block.getFileName.toString.startsWith("nbd")) new NetworkBlockDevice(block)
    else if (block.getFileName.toString.startsWith("loop")) new LoopbackDevice(block)
    else new Device(block)
  }

  /**
   * Creates a Device for the given block path.
   *
   * Handles both '/dev' and '/sys/block' path.
   * The actual existence of this device is not enforced.
   */
  def apply(path: File): Device =
    Device(path.toPath)

}
