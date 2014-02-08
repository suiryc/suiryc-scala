package suiryc.scala.javafx.scene.control

import scalafx.scene.control.TextArea
import suiryc.scala.io.LineWriter


class LogArea(append: Boolean = true)
  extends TextArea
  with LineWriter
{

  editable = false

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
