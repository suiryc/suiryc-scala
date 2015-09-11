package suiryc.scala.javafx.stage

import javafx.geometry.BoundingBox
import javafx.scene.control.Dialog
import javafx.stage.Stage
import suiryc.scala.javafx.beans.value.RichObservableValue._

/** JavaFX Stage helpers. */
object Stages {

  private lazy val isLinux: Boolean =
    Option(System.getProperty("os.name")).exists { os =>
      os.toLowerCase.startsWith("linux")
    }

  /**
   * Tracks minimum stage dimensions.
   *
   * Sets stage minimum width and height according to its content.
   * If given, also sets stage size once done.
   *
   * @param stage stage to set minimum dimensions on
   * @param size initial stage size to set, or None
   */
  def trackMinimumDimensions(stage: Stage, size: Option[(Double, Double)] = None): Unit = {
    // Notes:
    // The main problem is to determine the window decoration size: difference
    // between the stage and scene sizes.
    //
    // On Windows, letting JavaFX handle changes (by running the code with
    // Platform.runLater) before getting minimal scene root size and setting
    // stage minimal size (taking into account decoration size) works.
    //
    // But it does often not work on Linux (Ubuntu at least). Depending on the
    // situation (primary stage, modal window, stage scene root replacing, ...)
    // stage/scene dimensions may be unknown (NaN) and being changed more than
    // once while the stage is being built/displayed (0.0, 1.0, or the
    // decoration size).
    // Simply waiting on some properties like bounds does not always work:
    // sometimes it stays NaN until user interacts with the window.
    // Waiting on the scene/stage dimensions is not good either: sometimes it
    // changes in multiple steps (different values, only width or height, etc),
    // and sometimes the stage size does not include the window decoration
    // until user interacts with it ...
    // There does not seem to be a viable way to do it in Linux, so don't.

    if (!isLinux) {
      // The actual tracking code
      def track(): Unit = {
        import suiryc.scala.javafx.concurrent.JFXSystem

        // To get the stage minimum dimensions we need to:
        // 1. Let JavaFX handle the changes: do our stuff with Platform.runLater
        JFXSystem.runLater {
          val scene = stage.getScene
          // 2. Get the decoration size: difference between the stage and the scene
          val decorationWidth = stage.getWidth - scene.getWidth
          val decorationHeight = stage.getHeight - scene.getHeight
          // 3. Ask JavaFX for the scene content minimum width and height
          // Note: '-1' is a special value to retrieve the current value
          val minWidth = scene.getRoot.minWidth(-1) + decorationWidth
          val minHeight = scene.getRoot.minHeight(-1) + decorationHeight

          // Now we can set the stage minimum dimensions.
          stage.setMinWidth(minWidth)
          stage.setMinHeight(minHeight)
          size match {
            case Some((width, height)) =>
              if (width > minWidth) stage.setWidth(width)
              if (height > minHeight) stage.setHeight(height)

            case None =>
          }
        }
      }

      // Track now if stage is showing, or wait for it
      if (stage.isShowing) track()
      else stage.showingProperty.listen2 { (cancellable, showing) =>
        if (showing) {
          cancellable.cancel()
          track()
        }
      }
    }
  }

  /** Keeps stage bounds (x, y, width, height) upon hiding/showing. */
  def keepBounds(stage: Stage): Unit = {
    var box: Option[BoundingBox] = None

    // Notes:
    //  - when showing again stage after hiding, position and size are resetted
    //  - to prevent artifacts (black areas on top or side of scene), it is
    //    better to set position and size after showing stage; which is the case
    //    when listening to 'showing' changes
    stage.showingProperty.listen { showing =>
      if (showing) box.foreach { box =>
        stage.setX(box.getMinX)
        stage.setY(box.getMinY)
        stage.setWidth(box.getWidth)
        stage.setHeight(box.getHeight)
      }
      else box = Some(new BoundingBox(stage.getX, stage.getY, stage.getWidth, stage.getHeight))
    }
  }

  /**
   * Gets dialog stage.
   *
   * See: http://code.makery.ch/blog/javafx-dialogs-official/
   */
  def getStage(dialog: Dialog[_]): Stage =
    dialog.getDialogPane.getScene.getWindow.asInstanceOf[Stage]

}
