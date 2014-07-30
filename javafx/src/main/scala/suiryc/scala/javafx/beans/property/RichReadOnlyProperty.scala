package suiryc.scala.javafx.beans.property

import javafx.beans.property.ReadOnlyProperty
import javafx.beans.value.ObservableValue
import suiryc.scala.javafx.beans.value.ChangeListener
import suiryc.scala.javafx.event.Subscription


/* Note: 'Property' is derived from 'ReadOnlyProperty', so we only need to
 * handle the latter.
 */

class RichReadOnlyProperty[T](val underlying: ReadOnlyProperty[T]) extends AnyVal {

  def listen2(fn: Function4[Subscription, ObservableValue[_ <: T], T, T, Unit]): Subscription = {
    val subscription = new RichReadOnlyProperty.ProxySubscription()
    val listener = ChangeListener[T](fn(subscription, _, _, _))

    /* Note: is is important to set the actual subscription before listening
     * to value changes.
     */
    subscription.setSubscription(new Subscription {
      override def unsubscribe() {
        underlying.removeListener(listener)
      }
    })
    underlying.addListener(listener)

    subscription
  }

  def listen(fn: Function3[ObservableValue[_ <: T], T, T, Unit]): Subscription = {
    val listener = ChangeListener[T](fn)
    underlying.addListener(listener)

    new Subscription {
      override def unsubscribe() {
        underlying.removeListener(listener)
      }
    }
  }

  def listen2(fn: Function2[Subscription, T, Unit]): Subscription =
    listen2((s, _, _, v) => fn(s, v))

  def listen(fn: Function1[T, Unit]): Subscription =
    listen((_, _, v) => fn(v))

  def listen2(fn: Function1[Subscription, Unit]): Subscription =
    listen2((s, _, _, _) => fn(s))

  def listen(fn: => Unit): Subscription =
    listen((_, _, _) => fn)

}

object RichReadOnlyProperty {

  import scala.language.implicitConversions

  implicit def toRichReadOnlyProperty[T](v: ReadOnlyProperty[T]) =
    new RichReadOnlyProperty(v)

  private class ProxySubscription extends Subscription {

    private var subscription: Subscription = _

    def setSubscription(subscription: Subscription) {
      this.subscription = subscription
    }

    override def unsubscribe() {
      Option(subscription).foreach(_.unsubscribe())
    }

  }

}
