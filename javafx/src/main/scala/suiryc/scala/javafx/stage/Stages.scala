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
    // On Windows, letting JavaFX handle changes (by running the code with
    // Platform.runLater) before getting minimal scene root size and setting
    // stage minimal size (taking into account decoration size) works.
    //
    // But it does often not work on Linux (Ubuntu at least). Depending on the
    // situation (primary stage, modal window, stage scene root replacing, ...)
    // stage/scene dimensions may be unknown (NaN) and being changed more than
    // once while the stage is being built/displayed (0.0, 1.0, or the
    // decoration size).
    // Simply waiting on some properties (e.g. bounds) does not always work:
    // sometimes it stays NaN until user interacts with the window.
    // Waiting on the scene/stage dimensions is not good: sometimes it changes
    // in multiple steps (different values, only width or height, etc).
    // What seems to work here (but not 100% on Windows) is to wait for the
    // 'needsLayout' property (changes far less often than bounds) to become
    // false, while trying to force layout to change by changing the stage size:
    // if not doing so, sometimes the window decoration is not accounted in the
    // stage size until the next user interaction.

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
    else {
      val root = stage.getScene.getRoot
      var step = 0

      // The actual code checking for minimum dimensions
      def check(): Boolean = {
        val scene = stage.getScene
        // Get stage size
        val stageWidth = stage.getWidth
        val stageHeight = stage.getHeight
        // Get scene size
        val sceneWidth = scene.getWidth
        val sceneHeight = scene.getHeight
        // Ask JavaFX for the scene content minimum width and height
        // Note: '-1' is a special value to retrieve the current value
        val rootMinWidth = scene.getRoot.minWidth(-1)
        val rootMinHeight = scene.getRoot.minHeight(-1)

        def valueOk(v: Double, min: Double) = !v.isNaN && (v > min)

        // Check all values are ok
        // Note: when showing the stage, some or all values will be NaN the
        // first time, 0.0 or 1.0 later and finally valid.
        def allOk(min: Double) = valueOk(stageWidth, min) && valueOk(stageHeight, min) &&
          valueOk(sceneWidth, min) && valueOk(sceneHeight, min) &&
          valueOk(rootMinWidth, min) && valueOk(rootMinHeight, min)

        // Note: sometimes dimensions on first step are 1.0; and we expect
        // more valid values in other steps.
        if (allOk(if (step == 0) 0.0 else 1.0)) {
          if (step == 0) {
            step += 1
            // Change stage dimensions to try to force layout
            stage.setWidth(stage.getWidth + 1)
            stage.setHeight(stage.getHeight + 1)
            false
          }
          else if (step == 1) {
            step += 1
            // Restore stage previous dimension
            stage.setWidth(stage.getWidth - 1)
            stage.setHeight(stage.getHeight - 1)
            false
          }
          else if (stageHeight > sceneHeight) {
            // Note: we made sure that the stage dimensions take into account
            // decorations before performing this last step.
            // At worst we would wait for the next user interaction.

            // Get the decoration size: difference between the stage and the scene
            val decorationWidth = stageWidth - sceneWidth
            val decorationHeight = stageHeight - sceneHeight
            // Compute the minimum stage size
            val minWidth = rootMinWidth + decorationWidth
            val minHeight = rootMinHeight + decorationHeight

            // Now we can set the stage minimum dimensions
            stage.setMinWidth(minWidth)
            stage.setMinHeight(minHeight)
            true
          }
          else false
        }
        else false
      }

      // Note: set stage size first, because actual final step may be completed
      // later (user interaction).
      size match {
        case Some((width, height)) =>
          stage.setWidth(width)
          stage.setHeight(height)

        case None =>
      }

      // Try to trigger layout from JavaFX
      root.requestLayout()
      root.needsLayoutProperty.listen2 { (cancellable, needsLayout) =>
        if (!needsLayout && check()) {
          cancellable.cancel()
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
