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
    localToParent(node, root, node.getBoundsInLocal)
  }

  /**
   * Converts bounds relatively from 'local' node to 'parent' node.
   *
   * @param local local node to which bounds are relative to
   * @param parent parent node to get bounds relative to (must be a parent of 'local')
   * @param bounds bounds to convert
   * @return bounds relatively to 'parent' node
   */
  def localToParent(local: Node, parent: Node, bounds: Bounds): Bounds = {
    getHierarchy(local, parent).foldLeft(bounds) { (bounds, node) =>
      node.localToParent(bounds)
    }
  }

  /**
   * Converts bounds relatively from 'parent' node to 'local' node.
   *
   * @param local local node to get bounds relative to
   * @param parent parent node to which bounds are relative to (must be a parent of 'local')
   * @param bounds bounds to convert
   * @return bounds relatively to 'local' node
   */
  def parentToLocal(local: Node, parent: Node, bounds: Bounds): Bounds = {
    getHierarchy(local, parent).reverse.foldLeft(bounds) { (bounds, node) =>
      node.parentToLocal(bounds)
    }
  }

  private def getHierarchy(node: Node, root: Node): List[Node] = {
    @scala.annotation.tailrec
    def loop(hierarchy: List[Node], node: Node): List[Node] = {
      val parentOpt = if (node ne root) Option(node.getParent) else None
      parentOpt match {
        case Some(parent) => loop(hierarchy :+ node, parent)
        case None => hierarchy
      }
    }

    loop(Nil, node)
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
      x.foreach { x =>
        scrollPane.setHvalue(math.min(hmax, hmin + x * (hmax - hmin) / (contentWidth - viewportWidth)))
      }
    }

    val vmin = scrollPane.getVmin
    val vmax = scrollPane.getVmax
    val contentHeight = contentBounds.getHeight
    val viewportHeight = viewportBounds.getHeight
    if ((contentHeight > viewportHeight) && ((vmax - vmin) > 0)) {
      y.foreach { y =>
        scrollPane.setVvalue(math.min(vmax, vmin + y * (vmax - vmin) / (contentHeight - viewportHeight)))
      }
    }
  }

}
