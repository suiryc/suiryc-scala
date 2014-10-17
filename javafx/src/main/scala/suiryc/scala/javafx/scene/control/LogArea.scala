package suiryc.scala.javafx.scene.control

import akka.actor.{Actor, Props}
import javafx.scene.control.TextArea
import suiryc.scala.akka.CoreSystem
import suiryc.scala.io.LineWriter
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.log.ThresholdLogLinePatternWriter

/**
 * Read-only text area that can receive lines (from log or other output) to
 * append or prepend.
 */
class LogArea(
  val textArea: TextArea,
  val append: Boolean = true
) extends LineWriter
{ logArea =>

  import LogArea._

  /* Note: final newline is important in case an exception is to be included
   * (directly follows the formatted message line, without leading newline).
   */
  protected var pattern = "%d{HH:mm:ss.SSS} %-5level %logger{24} - %msg%n"

  def setPattern(pattern: String) {
    this.pattern = pattern
    msgWriter.setPattern(pattern)
  }

  def getPattern = pattern

  textArea.setEditable(false)

  /* Note: JavaFX thread CPU usage may reach limit when updating on each change.
   * So limit the refresh rate to 10 times per second. 
   */

  protected val system = CoreSystem.system
  /* Note: we need to give the creator function because the actor is tied to
   * this class instance (hence no default actor available for Props).
   */
  protected val actor = system.actorOf(Props(new LogAreaActor))

  lazy val msgWriter: ThresholdLogLinePatternWriter =
    new LogAreaWriter

  override def write(line: String) {
    actor ! Write(line)
  }

  def setText(text: String) {
    actor ! Set(text)
  }

  def clear() {
    setText("")
  }

  private class LogAreaWriter
    extends ThresholdLogLinePatternWriter
  {

    setPattern(pattern)

    override def write(line: String) =
      logArea.write(line)

  }

  private class LogAreaActor extends Actor {

    case object Flush

    override def receive = nominal

    protected def setText(s: String) {
      JFXSystem.schedule {
        textArea.setText(s)
      }
    }

    def nominal: Receive = {
      case Write(s) =>
        import scala.concurrent.duration._
        import system.dispatcher
        context.become(bufferize(s))
        system.scheduler.scheduleOnce(100.millis, self, Flush)

      case Set(s) =>
        setText(s)
    }

    def bufferize(text: String): Receive = {
      case Write(s) =>
        if (append) context.become(bufferize(s"$text\n$s"))
        else context.become(bufferize(s"$s\n$text"))

      case Flush =>
        JFXSystem.schedule {
          val current = textArea.getText
          if (current == "") textArea.setText(text)
          else if (append) textArea.appendText(s"\n$text")
          else textArea.setText(s"$text\n$current")
        }
        context.become(nominal)

      case Set(s) =>
        setText(s)
        context.become(nominal)
    }

  }

}

object LogArea {

  protected case class Write(line: String)

  protected case class Set(text: String)

  def apply(textArea: TextArea) =
    new LogArea(textArea)

}
