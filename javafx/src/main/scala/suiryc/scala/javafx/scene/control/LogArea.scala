package suiryc.scala.javafx.scene.control

import akka.actor.{Actor, Props}
import javafx.scene.control.TextArea
import scala.beans.BeanProperty
import suiryc.scala.akka.CoreSystem
import suiryc.scala.io.LineWriter
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.log.ThresholdLogLinePatternWriter

/**
 * Read-only text area that can receive lines (from log or other output) to
 * append or prepend.
 */
class LogArea
  extends TextArea
  with LineWriter
{ logArea =>

  @BeanProperty
  protected var append = true

  protected var pattern = "%d{HH:mm:ss.SSS} %-5level %logger{24} - %msg"

  def setPattern(pattern: String) {
    this.pattern = pattern
    msgWriter.setPattern(pattern)
  }

  def getPattern() = pattern

  setEditable(false)

  /* Note: JavaFX thread CPU usage may reach limit when updating on each change.
   * So limit the refresh rate to 10 times per second. 
   */

  protected val system = CoreSystem.system
  /* Note: we need to give the creator function because the actor is tied to
   * this class instance (hence no default ctor available for Props).
   */
  protected val actor = system.actorOf(Props(new LogAreaActor))

  lazy val msgWriter: ThresholdLogLinePatternWriter =
    new LogAreaWriter

  override def write(line: String) {
    actor ! line
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

    def nominal: Receive = {
      case s: String =>
        import scala.concurrent.duration._
        import system.dispatcher
        context.become(bufferize(s))
        system.scheduler.scheduleOnce(100.millis, self, Flush)
    }

    def bufferize(text: String): Receive = {
      case s: String =>
        if (append) context.become(bufferize(s"$text\n$s"))
        else context.become(bufferize(s"$s\n$text"))

      case Flush =>
        JFXSystem.schedule {
          val current = getText()
          if (current == "") setText(text)
          else if (append) appendText(s"\n$text")
          else setText(s"$text\n$current")
        }
        context.become(nominal)
    }

  }

}
