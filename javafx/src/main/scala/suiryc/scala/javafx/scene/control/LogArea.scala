package suiryc.scala.javafx.scene.control

import javafx.scene.control.TextArea
import scala.beans.BeanProperty
import suiryc.scala.io.LineWriter

/**
 * Read-only text area that can receive lines (from log or other output) to
 * append or prepend.
 */
class LogArea
  extends TextArea
  with LineWriter
{

  @BeanProperty
  var append = true

  setEditable(false)

  def appendLine(s: String) {
    val current = this.getText()

    if (current == "") this.setText(s)
    else this.appendText(s"\n$s")
  }

  def prependLine(s: String) {
    val current = this.getText()

    if (current == "") this.setText(s)
    else this.setText(s"$s\n$current")
  }

  override def write(line: String) {
    if (append) appendLine(line)
    else prependLine(line)
  }

}
