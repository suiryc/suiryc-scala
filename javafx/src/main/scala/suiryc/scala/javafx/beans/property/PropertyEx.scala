package suiryc.scala.javafx.beans.property

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}


class PropertyEx[T] {

  private var _value: T = _
  def apply() = _value
  def update(v: T) {
    _value = v
    _property.update(v)
  }

  private val _property = ObjectProperty(_value)
  val property: ReadOnlyObjectProperty[T] = _property

  def this(v: T) {
    this
    _value = v
  }

  override def toString = _value.toString

}

object PropertyEx {

  def apply[T](v: T) =
    new PropertyEx[T](v)

  /* Note: defining an implicit conversion from Any may be dangerous:
   * val v1 = PropertyEx(true)
   * val v2 = PropertyEx(false)
   * v1() = v2
   *  compiles, but creates a temporary PropertyEx[PropertyEx[Boolean]]
   *  from 'v1' to assign v2
   *
  implicit def anyToProp[T](v: T) =
    new PropertyEx[T](v)
   */

}
