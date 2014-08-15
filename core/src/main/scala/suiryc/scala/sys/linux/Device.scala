package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Path, Paths}
import suiryc.scala.io.{AllPassFileFilter, NameFilter, PathFinder, SourceEx}
import suiryc.scala.io.NameFilter._
import suiryc.scala.sys.{Command, CommandResult}
import suiryc.scala.util.EitherEx


class Device(val block: Path) {
  import Device._
  import PathFinder._
  import NameFilter._

  val dev = Paths.get("/dev").resolve(block.getFileName)

  protected def defaultVendor = "<unknown>"

  val vendor =
    propertyContent(block, "device", "vendor") getOrElse defaultVendor

  protected def defaultModel = "<unknown>"

  val model =
    propertyContent(block, "device", "model") getOrElse defaultModel

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
    }
    else props
  }

  val size = Device.size(block)

  val readOnly =
    propertyContent(block, "ro").exists { v =>
      v.toInt != 0
    }

  val removable =
    propertyContent(block, "removable").exists { v =>
      v.toInt != 0
    }

  protected[linux] def partitionInfix = ""

  val partitions = {
    val devName = dev.getFileName.toString
    (block * s"""${devName}$partitionInfix[0-9]+""".r).get map { path =>
      DevicePartition(this, path.getName.substring(devName.length() + partitionInfix.length()).toInt)
    }
  }

  def partprobe() =
    Command.execute(Seq("partprobe", dev.toString))

  override def toString =
    s"Device(block=$block, vendor=$vendor, model=$model, ueventProps=$ueventProps)"

}


class NetworkBlockDevice(override val block: Path)
  extends Device(block)
{

  override protected def defaultVendor = "Network Block Device"

  override protected def defaultModel = "Network Block Device"

  override protected[linux] def partitionInfix = "p"

}


object Device
  extends Logging
{

  private val KeyValueRegexp = """^([^=]*)=(.*)$""".r

  def propertyContent(block: Path, path: String*): Option[String] = {
    val file = Paths.get(block.toString, path: _*).toFile

    Option(
      if (file.exists())
        SourceEx.autoCloseFile(file) { source =>
          source.getLines().map { line =>
            line.trim()
          }.filterNot { line =>
            line == ""
          }.mkString(" / ")
        }
      else null
    )
  }

  def size(block: Path): EitherEx[Throwable, Long] = {
    propertyContent(block, "size") map { size =>
      EitherEx(Right(size.toLong * 512))
    } getOrElse {
      try {
        val dev = Paths.get("/dev").resolve(block.getFileName)
        val CommandResult(result, stdout, stderr) = Command.execute(Seq("blockdev", "--getsz", dev.toString))
        if (result == 0) {
          EitherEx(Right(stdout.trim.toLong * 512))
        }
        else {
          val msg = s"Cannot get device size: $stderr"
          error(msg)
          EitherEx(Left(new Exception(msg)), -1L)
        }
      }
      catch {
        case e: Throwable =>
          EitherEx(Left(e), -1L)
      }
    }
  }

  def fromPartition(path: Path): Option[Device] = {
    val sysBlock = Paths.get("/sys", "block")

    if (path.startsWith(sysBlock))
      Some(Device(path.getParent))
    else {
      val finder = PathFinder(sysBlock) * AllPassFileFilter * path.getFileName.toString
      finder.get().headOption map(file => Device(file.toPath.getParent))
    }
  }

  def apply(block: Path): Device =
    if (block.getFileName.toString.startsWith("nbd"))
      new NetworkBlockDevice(block)
    else
      new Device(block)

  def apply(block: File): Device =
    Device(block.toPath)

}
