package suiryc.scala.javafx.geometry

import javafx.geometry.{BoundingBox, Bounds}
import javafx.scene.Node
import javafx.scene.control.ScrollPane

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
   * @return viewed content bounds
   */
  def getViewedBounds(scrollPane: ScrollPane): Bounds = {
    val contentBounds = scrollPane.getContent.getLayoutBounds
    val viewportBounds = scrollPane.getViewportBounds

    val hmin = scrollPane.getHmin
    val hmax = scrollPane.getHmax
    val hvalue = scrollPane.getHvalue
    val contentWidth = contentBounds.getWidth
    val viewportWidth = viewportBounds.getWidth
    val hoffset =
      if ((contentWidth <= viewportWidth) || ((hmax - hmin) <= 0)) 0
      else (contentWidth - viewportWidth) * (hvalue - hmin) / (hmax - hmin)

    val vmin = scrollPane.getVmin
    val vmax = scrollPane.getVmax
    val vvalue = scrollPane.getVvalue
    val contentHeight = contentBounds.getHeight
    val viewportHeight = viewportBounds.getHeight
    val voffset =
      if ((contentHeight <= viewportHeight) || ((vmax - vmin) <= 0)) 0
      else (contentHeight - viewportHeight) * (vvalue - vmin) / (vmax - vmin)

    new BoundingBox(hoffset, voffset, 0, viewportWidth, viewportHeight, 0)
  }

  /**
   * Determines the hvalue for which the given hoffset appears at the indicated
   * scroll pane view position.
   *
   * @param scrollPane scroll pane to get hvalue from
   * @param hoffset the hoffset for which to compute the hvalue
   * @return the corresponding hvalue
   */
  def computeHValue(scrollPane: ScrollPane, hoffset: Double, pos: ScrollOffsetPosition.Value = ScrollOffsetPosition.Begin): Double = {
    val contentBounds = scrollPane.getContent.getLayoutBounds
    val viewportBounds = scrollPane.getViewportBounds

    val hmin = scrollPane.getHmin
    val hmax = scrollPane.getHmax
    val contentWidth = contentBounds.getWidth
    val viewportWidth = viewportBounds.getWidth
    val hoffsetDelta = pos match {
      case ScrollOffsetPosition.Begin  => 0.0
      case ScrollOffsetPosition.Middle => viewportWidth / 2
      case ScrollOffsetPosition.End    => viewportWidth
    }
    val hvalue =
      if ((contentWidth <= viewportWidth) || (hmax - hmin <= 0)) 0
      else hmin + (hoffset - hoffsetDelta) * (hmax - hmin) / (contentWidth - viewportWidth)
    if (hvalue > hmax) hmax
    else if (hvalue < hmin) hmin
    else hvalue
  }

}

object ScrollOffsetPosition extends Enumeration {
  val Begin = Value
  val Middle = Value
  val End = Value
}
