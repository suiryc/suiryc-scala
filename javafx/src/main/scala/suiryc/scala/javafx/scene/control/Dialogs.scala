package suiryc.scala.javafx.scene.control

import java.io.{PrintWriter, StringWriter}
import javafx.scene.control.{Alert, ButtonType, Label, TextArea}
import javafx.scene.layout.{GridPane, Priority}
import javafx.stage.{Stage, Window}
import suiryc.scala.RichOption._
import suiryc.scala.javafx.I18NBase
import suiryc.scala.javafx.concurrent.JFXSystem

/** Dialog/Alert helpers. */
object Dialogs {

  /**
   * Builds ands shows Alert dialog.
   *
   * @param kind dialog kind
   * @param owner owner
   * @param title dialog title
   * @param headerText dialog title text
   * @param contentText dialog content text
   * @param ex exception to show in dialog expendable content
   * @param buttons dialog buttons to use
   * @return user action
   */
  // scalastyle:off method.length
  private def buildAlert(
      kind: Alert.AlertType,
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String],
      contentText: Option[String],
      ex: Option[Throwable],
      buttons: List[ButtonType]): Option[ButtonType] =
  {
    // Note: it is mandatory to create the 'Alert' inside JavaFX thread.
    JFXSystem.await({
      val alert = new Alert(kind)
      // Note: if owner is a Stage, its Scene properties are used, so make sure
      // there is one.
      owner.find {
        case stage: Stage => Option(stage.getScene).isDefined
        case _ => true
      }.foreach(alert.initOwner)
      title.foreach(alert.setTitle)
      alert.setHeaderText(headerText.filterNot(_.trim.isEmpty).orNull)
      alert.setContentText(contentText.filterNot(_.trim.isEmpty).orNull)

      if (buttons.nonEmpty) {
        alert.getButtonTypes.setAll(buttons: _*)
      }

      ex.foreach { ex =>
        // See: http://code.makery.ch/blog/javafx-dialogs-official/
        val sw = new StringWriter()
        val pw = new PrintWriter(sw)
        ex.printStackTrace(pw)
        val exceptionText = sw.toString

        val label = new Label(I18NBase.getResources.getString("error.exception-stacktrace"))

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
      }

      alert.showAndWait()
    }, logReentrant = false)
  }
  // scalastyle:on method.length

  /** Builds and shows Alert confirmation dialog. */
  def confirmation(
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String],
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil): Option[ButtonType] =
    buildAlert(Alert.AlertType.CONFIRMATION, owner, title, headerText, contentText, ex, buttons)

  /** Builds and shows Alert information dialog. */
  def information(
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String],
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil): Option[ButtonType] =
    buildAlert(Alert.AlertType.INFORMATION, owner, title, headerText, contentText, ex, buttons)

  /** Builds and shows Alert warning dialog. */
  def warning(
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String],
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil): Option[ButtonType] =
    buildAlert(Alert.AlertType.WARNING, owner, title, headerText, contentText, ex, buttons)

  /** Builds and shows Alert error dialog. */
  def error(
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String],
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil): Option[ButtonType] =
    buildAlert(Alert.AlertType.ERROR, owner, title, headerText, contentText, ex, buttons)

}
