package suiryc.scala.javafx.stage

import javafx.stage.Stage
import suiryc.scala.javafx.concurrent.JFXSystem

/** JavaFX Stage helpers. */
object Stages {

  /**
   * Tracks minimum stage dimensions.
   *
   * Sets stage minimum width and height according to its content.
   * If given, also sets stage size once done.
   *
   * @param stage stage to set minimum dimensions on
   * @param size initial stage size to set, or None
   */
  def trackMinimumDimensions(stage: Stage, size: Option[(Double, Double)] = None) {
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
          if ((stage.getWidth < width) && (width > minWidth)) stage.setWidth(width)
          if ((stage.getHeight < height) && (height > minHeight)) stage.setHeight(height)

        case None =>
      }
    }
  }

}
