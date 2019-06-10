package suiryc.scala.javafx.geometry

import javafx.geometry.{BoundingBox, Bounds}
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import suiryc.scala.math.BigDecimals

/** Bounds helpers. */
object BoundsEx {

  /**
   * Gets 'node' bounds relatively to 'root' node.
   *
   * @param node node to get bounds from
   * @param root relative node to get bounds from (must be a parent of 'node')
   * @return bounds of 'node' relatively to 'root'
   */
  def getBounds(node: Node, root: Node): Bounds = {
    @scala.annotation.tailrec
    def loop(bounds: Option[Bounds], node: Node): Bounds = {
      val boundsActual = bounds.getOrElse(node.getBoundsInLocal)
      val parentOpt = if (node ne root) Option(node.getParent) else None
      parentOpt match {
        case Some(parent) => loop(Some(node.localToParent(boundsActual)), parent)
        case None => boundsActual
      }
    }

    loop(None, node)
  }

  /**
   * Gets bounds of currently viewed content in scroll pane.
   *
   * See http://stackoverflow.com/a/26241057.
   *
   * @param scrollPane scroll pane to get viewed content bounds from
   * @param round whether to round x/y values to actual pixel index
   * @return viewed content bounds
   */
  def getViewedBounds(scrollPane: ScrollPane, round: Boolean = true): Bounds = {
    val contentBounds = scrollPane.getContent.getLayoutBounds
    val viewportBounds = scrollPane.getViewportBounds

    val hmin = scrollPane.getHmin
    val hmax = scrollPane.getHmax
    val hvalue = scrollPane.getHvalue
    val contentWidth = contentBounds.getWidth
    val viewportWidth = viewportBounds.getWidth
    val hoffset =
      if ((contentWidth <= viewportWidth) || ((hmax - hmin) <= 0)) 0
      else {
        // View can only display whole pixels. Apparently the pixel index is
        // obtained through a rounding equivalent to the 'half down' method.
        val v = (contentWidth - viewportWidth) * (hvalue - hmin) / (hmax - hmin)
        if (round) BigDecimals.roundHalfDown(v).toDouble else v
      }

    val vmin = scrollPane.getVmin
    val vmax = scrollPane.getVmax
    val vvalue = scrollPane.getVvalue
    val contentHeight = contentBounds.getHeight
    val viewportHeight = viewportBounds.getHeight
    val voffset =
      if ((contentHeight <= viewportHeight) || ((vmax - vmin) <= 0)) 0
      else {
        val v = (contentHeight - viewportHeight) * (vvalue - vmin) / (vmax - vmin)
        if (round) BigDecimals.roundHalfDown(v).toDouble else v
      }

    new BoundingBox(hoffset, voffset, 0, viewportWidth, viewportHeight, 0)
  }

  /**
   * Changes scroll so that content view top/left matches requested x/y.
   *
   * @param scrollPane scroll pane to change view
   * @param x top/left x pixel position
   * @param y top/left y pixel position
   */
  def setView(scrollPane: ScrollPane, x: Option[Double], y: Option[Double]): Unit = {
    val contentBounds = scrollPane.getContent.getLayoutBounds
    val viewportBounds = scrollPane.getViewportBounds

    val hmin = scrollPane.getHmin
    val hmax = scrollPane.getHmax
    val contentWidth = contentBounds.getWidth
    val viewportWidth = viewportBounds.getWidth
    if ((contentWidth > viewportWidth) && ((hmax - hmin) > 0)) {
      x.foreach { x ⇒
        scrollPane.setHvalue(math.min(hmax, hmin + x * (hmax - hmin) / (contentWidth - viewportWidth)))
      }
    }

    val vmin = scrollPane.getVmin
    val vmax = scrollPane.getVmax
    val contentHeight = contentBounds.getHeight
    val viewportHeight = viewportBounds.getHeight
    if ((contentHeight > viewportHeight) && ((vmax - vmin) > 0)) {
      y.foreach { y ⇒
        scrollPane.setVvalue(math.min(vmax, vmin + y * (vmax - vmin) / (contentHeight - viewportHeight)))
      }
    }
  }

}
