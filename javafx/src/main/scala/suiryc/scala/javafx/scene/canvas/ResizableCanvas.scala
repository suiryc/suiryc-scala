package suiryc.scala.javafx.scene.canvas

import javafx.beans.property.{BooleanProperty, SimpleBooleanProperty}
import javafx.scene.canvas.Canvas
import javafx.scene.layout.Region
import suiryc.scala.javafx.beans.value.RichObservableValue._

/**
 * Resizable canvas.
 *
 * Canvas is a non-resizable Node. To have it resizable we need to override
 * it and link 'prefWith'/'prefHeight' to actual width/height.
 * For JavaFX to better handle resizing (up/down) it is also useful to override
 * 'minWidth'/'minHeight' and 'maxWidth'/'maxHeight' since by  default it uses
 * 'prefWidth'/'prefHeight' which may prevent proper resizing of parent pane.
 *
 * @see http://werner.yellowcouch.org/log/resizable-javafx-canvas/
 * @see http://dlsc.com/2014/04/10/javafx-tip-1-resizable-canvas/
 */
class ResizableCanvas extends Canvas {

  override def isResizable: Boolean = true

  /** Sets min/pref/max value. */
  def setFixedWidth(value: Double): Unit = {
    setWidth(value)
    setMinWidth(value)
    setMaxWidth(value)
  }

  /** Sets min/pref/max value. */
  def setFixedHeight(value: Double): Unit = {
    setHeight(value)
    setMinHeight(value)
    setMaxHeight(value)
  }

  override def minWidth(height: Double): Double = _minWidth
  override def minHeight(width: Double): Double = _minHeight
  override def prefWidth(height: Double): Double = getWidth
  override def prefHeight(width: Double): Double = getHeight
  override def maxWidth(height: Double): Double = _maxWidth
  override def maxHeight(width: Double): Double = _maxHeight

  private var _minWidth: Double = Region.USE_COMPUTED_SIZE
  def getMinWidth: Double =  _minWidth
  def setMinWidth(value: Double) {
    _minWidth = value
    requestParentLayout()
  }

  private var _minHeight: Double = Region.USE_COMPUTED_SIZE
  def getMinHeight: Double =  _minHeight
  def setMinHeight(value: Double) {
    _minHeight = value
    requestParentLayout()
  }

  private var _maxWidth: Double = Region.USE_COMPUTED_SIZE
  def getMaxWidth: Double =  _maxWidth
  def setMaxWidth(value: Double) {
    _maxWidth = value
    requestParentLayout()
  }

  private var _maxHeight: Double = Region.USE_COMPUTED_SIZE
  def getMaxHeight: Double =  _maxHeight
  def setMaxHeight(value: Double) {
    _maxHeight = value
    requestParentLayout()
  }

  // A property to clear on resize.
  // Notes:
  // Parent 'resize' does nothing but is called more often than necessary
  // compared to actual width/height changes. So listen to those properties
  // changes to trigger clearing of canvas.
  private var sizeListen = false
  private val clearOnResize = new SimpleBooleanProperty(ResizableCanvas.this, "clearOnResize")
  def clearOnResizeProperty: BooleanProperty = clearOnResize
  def isClearOnResize: Boolean = clearOnResizeProperty.get
  def setClearOnResize(value: Boolean): Unit = {
    clearOnResizeProperty.set(value)
    if (isClearOnResize && !sizeListen) {
      sizeListen = true
      widthProperty.listen(doClearOnResize())
      heightProperty.listen(doClearOnResize())
      ()
    }
  }

  @inline protected def requestParentLayout() {
    val parent = getParent
    // scalastyle:off null
    if (parent != null) parent.requestLayout()
    // scalastyle:on null
  }

  @inline protected def doClearOnResize(): Unit = {
    if (isClearOnResize) getGraphicsContext2D.clearRect(0, 0, getWidth, getHeight)
  }

}
