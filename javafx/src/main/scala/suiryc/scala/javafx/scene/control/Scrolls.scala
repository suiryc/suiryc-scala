package suiryc.scala.javafx.scene.control

import javafx.scene.control.{ScrollBar, ScrollPane}

/** Scroll helpers. */
object Scrolls {

  /**
   * Determines the value for which the given offset appears at the indicated
   * scroll bar position.
   *
   * @param min the minimum scroll value
   * @param max the maximum scroll value
   * @param contentSize size of total content
   * @param viewportSize size of viewable content
   * @param offset the offset for which to compute the value
   * @return the corresponding value
   */
  @inline private def computeValue(min: Double, max: Double, contentSize: Double, viewportSize: Double,
    offset: Double, pos: ScrollOffsetPosition.Value): Double =
  {
    val offsetDelta = pos match {
      case ScrollOffsetPosition.Begin  => 0.0
      case ScrollOffsetPosition.Middle => viewportSize / 2
      case ScrollOffsetPosition.End    => viewportSize
    }
    val value =
      if ((contentSize <= viewportSize) || (max - min <= 0)) 0
      else min + (offset - offsetDelta) * (max - min) / (contentSize - viewportSize)
    if (value > max) max
    else if (value < min) min
    else value
  }

  /**
   * Determines the value for which the given offset appears at the indicated
   * scroll bar position.
   *
   * @param scrollBar scroll bar to get hvalue from
   * @param contentSize size of total content
   * @param viewportSize size of viewable content
   * @param offset the offset for which to compute the value
   * @return the corresponding value
   */
  def computeValue(scrollBar: ScrollBar, contentSize: Double, viewportSize: Double,
    offset: Double, pos: ScrollOffsetPosition.Value = ScrollOffsetPosition.Begin): Double =
  {
    val min = scrollBar.getMin
    val max = scrollBar.getMax

    computeValue(min, max, contentSize, viewportSize, offset, pos)
  }

  /**
   * Determines the hvalue for which the given offset appears at the indicated
   * scroll pane view position.
   *
   * @param scrollPane scroll pane to get hvalue from
   * @param offset the offset for which to compute the hvalue
   * @return the corresponding hvalue
   */
  def computeHValue(scrollPane: ScrollPane, offset: Double, pos: ScrollOffsetPosition.Value = ScrollOffsetPosition.Begin): Double = {
    val contentBounds = scrollPane.getContent.getLayoutBounds
    val viewportBounds = scrollPane.getViewportBounds

    val min = scrollPane.getHmin
    val max = scrollPane.getHmax
    val contentSize = contentBounds.getWidth
    val viewportSize = viewportBounds.getWidth

    computeValue(min, max, contentSize, viewportSize, offset, pos)
  }

}

object ScrollOffsetPosition extends Enumeration {
  val Begin, Middle, End = Value
}
