package suiryc.scala.javafx.scene.chart

import javafx.scene.chart.NumberAxis

/** Axis helpers. */
object Axises {

  /** Gets index display position relatively to axis. */
  def getDisplayPosition(axis: NumberAxis, value: Number): Double = {
    // Notes:
    // When autoranging is on, axis 'getDisplayPosition' is usually right.
    // Otherwise it may be wrong, probably when lower bound is being changed
    // since it uses a 'currentLowerBound' value during computation, which is
    // updated to match actual lower bound upon layoutChildren.
    // To workaround this, we compute the value with the real lower bound:
    //  offset + (value - lowerBound) * scale
    // 'offset' is 0 for horizontal axis, and the axis height for vertical one.
    if (axis.isAutoRanging) axis.getDisplayPosition(value)
    else {
      val offset = if (axis.getSide.isVertical) axis.getHeight else 0
      offset + (value.doubleValue - axis.getLowerBound) * axis.getScale
    }
  }

  /** Gets index displayed at position (relatively to axis). */
  def getValueForDisplay(axis: NumberAxis, displayPosition: Double): Number = {
    // We workaround the same issue as in getDisplayPosition. The formula is:
    //  ((displayPosition - offset) / axis.getScale) + axis.getLowerBound
    // (converted to real axis value)
    if (axis.isAutoRanging) axis.getValueForDisplay(displayPosition)
    else {
      val offset = if (axis.getSide.isVertical) axis.getHeight else 0
      axis.toRealValue(((displayPosition - offset) / axis.getScale) + axis.getLowerBound)
    }
  }

}
