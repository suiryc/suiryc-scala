package suiryc.scala.misc

import suiryc.scala.io.LineWriter

/** Writer expecting messages. */
trait MessageWriter {

  def write(level: MessageLevel.LevelValue, msg: String): Unit

}

/** Default implementation formatting message to a line. */
trait MessageLineWriter
  extends MessageWriter
  with LineWriter
{

  override def write(level: MessageLevel.LevelValue, msg: String) {
    write(s"[${level.shortName}] $msg")
  }

}
