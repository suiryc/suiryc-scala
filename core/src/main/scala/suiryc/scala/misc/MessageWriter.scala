package suiryc.scala.misc

import java.io.{PrintWriter, StringWriter}
import suiryc.scala.io.LineWriter

/** Writer expecting messages. */
trait MessageWriter {

  def write(level: MessageLevel.LevelValue, msg: String, throwable: Option[Throwable]): Unit

}

/** Default implementation formatting message to a line. */
trait MessageLineWriter
  extends MessageWriter
  with LineWriter
{

  override def write(level: MessageLevel.LevelValue, msg: String, throwable: Option[Throwable]) {
    val line = s"[${level.shortName}] $msg" +
      throwable.fold { "" } { throwable =>
        val writer = new StringWriter()
        throwable.printStackTrace(new PrintWriter(writer))
        System.lineSeparator + writer.toString().trim()
      }

    write(line)
  }

}
