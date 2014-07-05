package suiryc.scala.javafx.beans.property

import javafx.beans.property.ReadOnlyProperty
import javafx.beans.value.ObservableValue
import suiryc.scala.javafx.beans.value.ChangeListener
import suiryc.scala.javafx.event.Subscription


/* Note: 'Property' is derived from 'ReadOnlyProperty', so we only need to
 * handle the latter.
 */

class RichReadOnlyProperty[T](val underlying: ReadOnlyProperty[T]) extends AnyVal {

  def listen(fn: Function3[ObservableValue[_ <: T], T, T, Unit]): Subscription = {
    val listener = ChangeListener[T](fn)
    underlying.addListener(listener)

    new Subscription {
      override def unsubscribe() {
        underlying.removeListener(listener)
      }
    }
  }

  def listen(fn: Function1[T, Unit]): Subscription =
    listen((_, _, v) => fn(v))

  def listen(fn: => Unit): Subscription =
    listen((_, _, _) => fn)

}

object RichReadOnlyProperty {

  import scala.language.implicitConversions

  implicit def toRichReadOnlyProperty[T](v: ReadOnlyProperty[T]) =
    new RichReadOnlyProperty(v)

}
