package suiryc.scala.javafx.scene.control

import java.io.{PrintWriter, StringWriter}
import javafx.scene.{Node, Scene}
import javafx.scene.control._
import javafx.scene.effect.BoxBlur
import javafx.scene.layout.{GridPane, Priority, StackPane}
import javafx.scene.paint.Color
import javafx.stage.{Modality, Stage, StageStyle, Window}
import scala.collection.JavaConverters._
import suiryc.scala.RichOption._
import suiryc.scala.javafx.I18NBase
import suiryc.scala.javafx.beans.value.RichObservableValue
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.concurrent.JFXSystem

/** Dialog/Alert helpers. */
object Dialogs {

  // Notes:
  // See: http://code.makery.ch/blog/javafx-dialogs-official/
  // Alert is based on Dialog.
  // Its 'header' and 'content' are visually separated. To only show a simple
  // message (with corresponding alert type icon), the 'header' must not be set
  // (None value). Also works with 'expandable content' (e.g. Exception).

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
      buttons: List[ButtonType],
      defaultButton: Option[ButtonType]): Option[ButtonType] =
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
      defaultButton.foreach { buttonType =>
        setDefaultButton(alert, buttonType)
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
      headerText: Option[String] = None,
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil,
      defaultButton: Option[ButtonType] = None): Option[ButtonType] =
    buildAlert(Alert.AlertType.CONFIRMATION, owner, title, headerText, contentText, ex, buttons, defaultButton)

  /** Builds and shows Alert information dialog. */
  def information(
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String] = None,
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil,
      defaultButton: Option[ButtonType] = None): Option[ButtonType] =
    buildAlert(Alert.AlertType.INFORMATION, owner, title, headerText, contentText, ex, buttons, defaultButton)

  /** Builds and shows Alert warning dialog. */
  def warning(
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String] = None,
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil,
     defaultButton: Option[ButtonType] = None): Option[ButtonType] =
    buildAlert(Alert.AlertType.WARNING, owner, title, headerText, contentText, ex, buttons, defaultButton)

  /** Builds and shows Alert error dialog. */
  def error(
      owner: Option[Window],
      title: Option[String],
      headerText: Option[String] = None,
      contentText: Option[String] = None,
      ex: Option[Throwable] = None,
      buttons: List[ButtonType] = Nil,
      defaultButton: Option[ButtonType] = None): Option[ButtonType] =
    buildAlert(Alert.AlertType.ERROR, owner, title, headerText, contentText, ex, buttons, defaultButton)

  /**
   * Helps to display a Node the modal way.
   *
   * Actually creates a transparent modal stage which contains the Node.
   * The returned stage can be shown when necessary, and re-used if applicable.
   *
   * @param parent the parent (relating to which we are modal)
   * @param builder callback that gives the content while handling how and when
   *                the stage is closed
   * @return modal stage
   */
  // scalastyle:off method.length null
  def modalNode(parent: Window, builder: Stage => Node): Stage = {
    // Notes:
    // We wish to display a Node, centered on parent window, in a modal way
    // (parent cannot be interacted with until we are done).
    // To do so, we can build a transparent modal stage, which scenes contains
    // the Node, and let caller close the stage when we are done.
    // As visual hint, we blur parent window and surround Node with visible
    // border, color and CSS effects.
    // Based on http://stackoverflow.com/a/17579619, with only what matters
    // remaining, and tweaked.
    val parentEffect = new BoxBlur()

    class ModalNode extends Stage {
      initStyle(StageStyle.TRANSPARENT)
      initOwner(parent)
      initModality(Modality.WINDOW_MODAL)

      val contentPane = new StackPane()
      contentPane.getStyleClass.add("modal-node-content")
      contentPane.getChildren.add(builder(this))
      val layout = new StackPane()
      layout.getChildren.setAll(contentPane)
      val scene = new Scene(layout, Color.TRANSPARENT)
      scene.getStylesheets.add(getClass.getResource("modal-node.css").toExternalForm)
      scene.getRoot.getStyleClass.add("modal-dialog-root")
      setScene(scene)

      // Centers us (and thus the node)
      def centerNode(): Unit = {
        setX(parent.getX + parent.getWidth / 2 - getWidth / 2)
        setY(parent.getY + parent.getHeight / 2 - getHeight / 2)
      }

      // Notes:
      // We could listen to parent being moved or resized in order to re-center,
      // but this should not be necessary (we are modal). Moreover it prevents
      // the Stage from being GCed until the parent is GCed, unless we cancel
      // the listener when necessary, which is not worth the hassle.
      // We can at least listen for the node being resized (dynamic content).
      RichObservableValue.listen(
        List(widthProperty, heightProperty),
        centerNode()
      )

      showingProperty.listen {
        // Center and apply blur effect on parent when we are showing
        if (isShowing) centerNode()
        parent.getScene.getRoot.setEffect(
          if (isShowing) {
            parentEffect
          } else {
            null
          }
        )
      }
      // Note: due to a bug in JavaFX, if there is only one listener and the
      // stage contains a PopupWindow (e.g. through a DatePicker) which does
      // trigger the stage closing, then the listener is not notified when the
      // stage is closed.
      // As a workaround, add a second no-op listener to prevent it.
      // See: https://bugs.openjdk.java.net/browse/JDK-8159905
      showingProperty.listen {}
    }

    new ModalNode()
  }
  // scalastyle:on method.length null

  /** Sets dialog default button. */
  def setDefaultButton(dialog: Dialog[_], buttonType: ButtonType): Unit = {
    val pane = dialog.getDialogPane
    if (Option(pane.lookupButton(buttonType).asInstanceOf[Button]).exists(!_.isDefaultButton)) {
      pane.getButtonTypes.asScala.foreach { t =>
        pane.lookupButton(t).asInstanceOf[Button].setDefaultButton(t == buttonType)
      }
    }
  }

}
