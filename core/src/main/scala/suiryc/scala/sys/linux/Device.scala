package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.Paths
import scala.io.Source
import suiryc.scala.io.{NameFilter, PathFinder}
import suiryc.scala.misc.EitherEx
import suiryc.scala.sys.{Command, CommandResult}


class Device(val block: File) {
  import Device._
  import PathFinder._
  import NameFilter._

  val dev = new File("/dev", block.getName())

  val vendor =
    propertyContent(block, "device", "vendor") getOrElse "<unknown>"

  val model =
    propertyContent(block, "device", "model") getOrElse "<unknown>"

  val ueventProps = {
    val uevent = Paths.get(block.toString(), "device", "uevent").toFile()
    val props = Map.empty[String, String]

    if (uevent.exists()) {
      Source.fromFile(uevent).getLines().toList.foldLeft(props) { (props, line) =>
        line match {
          case KeyValueRegexp(key, value) =>
            props + (key.trim() -> value.trim())

          case _ =>
            props
        }
      }
    }
    else props
  }

  val size = Device.size(block)

  val removable =
    propertyContent(block, "removable") map { removable =>
      removable.toInt != 0
    } getOrElse false

  val partitions =
    (block * s"""${dev.getName()}[0-9]+""".r).get map { path =>
      new DevicePartition(this, path.getName().substring(dev.getName().length()).toInt)
    }

  override def toString =
    s"Device(block=$block, vendor=$vendor, model=$model, ueventProps=$ueventProps)"

}


object Device
  extends Logging
{

  private val KeyValueRegexp = """^([^=]*)=(.*)$""".r

  def propertyContent(block: File, path: String*) = {
    val file = Paths.get(block.toString(), path: _*).toFile()

    Option(
      if (file.exists()) 
        Source.fromFile(file).getLines() map { line =>
          line.trim()
        } filterNot { line =>
          line == ""
        } mkString(" / ")
      else null
    )
  }

  def size(block: File): EitherEx[Throwable, Long] = {
    propertyContent(block, "size") map { size =>
      EitherEx(Right(size.toLong * 512))
    } getOrElse {
      try {
        val dev = new File("/dev", block.getName())
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

  def apply(block: File): Device = new Device(block)

}
