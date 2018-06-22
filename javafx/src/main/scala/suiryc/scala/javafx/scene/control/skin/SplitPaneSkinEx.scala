package suiryc.scala.javafx.scene.control.skin

import javafx.scene.Scene
import javafx.scene.control.SplitPane
import javafx.scene.control.skin.SplitPaneSkin

/** SplitPane skin enhancements. */
class SplitPaneSkinEx(pane: SplitPane) extends SplitPaneSkin(pane) {

  // The main issue with SplitPane is that dividers position changes when the
  // parent dimension changes, which is annoying and also prevents to restore
  // those positions at the same time the stage is being shown.
  // Luckily, it seems that restoring the values after each 'layoutChildren'
  // call fixes this behaviour.
  // See: https://stackoverflow.com/a/44284465

  override protected def layoutChildren(x: Double, y: Double, w: Double, h: Double): Unit = {
    val saved = getSkinnable.getDividerPositions
    super.layoutChildren(x, y, w, h)
    getSkinnable.setDividerPositions(saved:_*)
  }

}

object SplitPaneSkinEx {

  lazy val stylesheet: String = classOf[SplitPaneSkinEx].getResource("split-pane-ex.css").toExternalForm

  /** Helper to add stylesheet. */
  def addStylesheet(scene: Scene): Unit = {
    scene.getStylesheets.add(stylesheet)
    ()
  }

}
