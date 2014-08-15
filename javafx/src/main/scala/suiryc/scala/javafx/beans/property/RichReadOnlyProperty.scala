package suiryc.scala.javafx.beans.property

import javafx.beans.property.ReadOnlyProperty
import javafx.beans.value.ObservableValue
import suiryc.scala.concurrent.Cancellable
import suiryc.scala.javafx.beans.value.ChangeListener


/* Note: 'Property' is derived from 'ReadOnlyProperty', so we only need to
 * handle the latter.
 */

class RichReadOnlyProperty[T](val underlying: ReadOnlyProperty[T]) extends AnyVal {

  def listen2(fn: (Cancellable, ObservableValue[_ <: T], T, T) => Unit): Cancellable = {
    val subscription = new RichReadOnlyProperty.ProxyCancellable()
    val listener = ChangeListener[T](fn(subscription, _, _, _))

    /* Note: is is important to set the actual subscription before listening
     * to value changes.
     */
    subscription.setCancellable(new Cancellable {
      override def cancel() {
        underlying.removeListener(listener)
        super.cancel()
      }
    })
    underlying.addListener(listener)

    subscription
  }

  def listen(fn: (ObservableValue[_ <: T], T, T) => Unit): Cancellable = {
    val listener = ChangeListener[T](fn)
    underlying.addListener(listener)

    new Cancellable {
      override def cancel() {
        underlying.removeListener(listener)
        super.cancel()
      }
    }
  }

  def listen2(fn: (Cancellable, T) => Unit): Cancellable =
    listen2((s, _, _, v) => fn(s, v))

  def listen(fn: T => Unit): Cancellable =
    listen((_, _, v) => fn(v))

  def listen2(fn: Cancellable => Unit): Cancellable =
    listen2((s, _, _, _) => fn(s))

  def listen(fn: => Unit): Cancellable =
    listen((_, _, _) => fn)

}

object RichReadOnlyProperty {

  import scala.language.implicitConversions

  implicit def toRichReadOnlyProperty[T](v: ReadOnlyProperty[T]) =
    new RichReadOnlyProperty(v)

  private class ProxyCancellable extends Cancellable {

    private var cancellable: Cancellable = _

    def setCancellable(cancellable: Cancellable) {
      this.cancellable = cancellable
    }

    override def cancel() {
      Option(cancellable).foreach(_.cancel())
      super.cancel()
    }

  }

}
