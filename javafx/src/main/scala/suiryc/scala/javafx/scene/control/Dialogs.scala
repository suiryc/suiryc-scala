package suiryc.scala.javafx.scene.control

import java.io.{PrintWriter, StringWriter}
import javafx.scene.control.{Alert, ButtonType, Label, TextArea}
import javafx.scene.layout.{GridPane, Priority}
import javafx.stage.{Stage, Window}
import suiryc.scala.RichOption._
import suiryc.scala.javafx.concurrent.JFXSystem

/** Dialog/Alert helpers. */
object Dialogs {

  /** Builds and shows Alert dialog with stacktrace as content. */
  // TODO - link to official online details
  def error(owner: Option[Window], title: Option[String], headerText: Option[String], ex: Throwable): Option[ButtonType] = {
    // Note: it is mandatory to create the 'Alert' inside JavaFX thread.
    JFXSystem.await({
      val alert = new Alert(Alert.AlertType.ERROR)
      // Note: if owner is a Stage, its Scene properties are used, so make sure
      // there is one.
      owner.find {
        case stage: Stage => Option(stage.getScene).isDefined
        case _ => true
      }.foreach(alert.initOwner)
      title.foreach(alert.setTitle)
      alert.setHeaderText(headerText.orNull)
      alert.setContentText(null)

      val sw = new StringWriter()
      val pw = new PrintWriter(sw)
      ex.printStackTrace(pw)
      val exceptionText = sw.toString

      // TODO - i18n ?
      val label = new Label("The exception stacktrace was:")

      val textArea = new TextArea(exceptionText)
      textArea.setEditable(false)
      textArea.setWrapText(true)

      textArea.setMaxWidth(Double.MaxValue)
      textArea.setMaxHeight(Double.MaxValue)
      GridPane.setVgrow(textArea, Priority.ALWAYS)
      GridPane.setHgrow(textArea, Priority.ALWAYS)

      val expContent = new GridPane()
      expContent.setMaxWidth(Double.MaxValue)
      expContent.add(label, 0, 0)
      expContent.add(textArea, 0, 1)

      alert.getDialogPane.setExpandableContent(expContent)

      alert.showAndWait()
    }, logReentrant = false)
  }

}
