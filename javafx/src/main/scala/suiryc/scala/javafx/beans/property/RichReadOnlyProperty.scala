package suiryc.scala.javafx.beans.property

import javafx.beans.property.ReadOnlyProperty
import javafx.beans.value.ObservableValue
import suiryc.scala.concurrent.Cancellable
import suiryc.scala.javafx.beans.value.ChangeListener

/**
 * ReadOnlyProperty enhancements.
 *
 * Allows to attach listening code (adds a new listener) and returns a
 * subscription that can be cancelled (removes the listener).
 * Handles multiple variants:
 *   - listening code can receive its own subscription and cancel itself
 *   - listening code can receive the whole observed value, only the new value
 *     or nothing
 *   - other subscriptions can be attached to chain cancellations
 *
 * Notes:
 *   - subscription does not have its own class name and is simply a
 *     'Cancellable'
 *   - 'Property' is derived from 'ReadOnlyProperty', so we only need to handle
 *     the latter
 */
class RichReadOnlyProperty[A](val underlying: ReadOnlyProperty[A]) extends AnyVal {

  /**
   * Listens value change with auto subscription and chained subscriptions.
   *
   * Listening code is given its subscription and can auto-cancel itself.
   * Subscription attached to created listener will first process chained
   * subscriptions before itself upon cancellation.
   *
   * @param chain subscriptions to chain
   * @param fn listening function
   * @return new subscription chained with given ones
   */
  def listen2(chain: Seq[Cancellable], fn: (Cancellable, ObservableValue[_ <: A], A, A) => Unit): Cancellable = {
    // We need to create a subscription to give to the listening code. To do so
    // we need to create a listener, which is based on the listening code and
    // thus needs the subscription.
    // So we have to first create a dummy subscription to give to the listening
    // code, which allows us to create the actual listener. Then we can provide
    // the listener to the dummy subscription.
    val subscription = new RichReadOnlyProperty.CancellableListener[A] {
      override def cancel() {
        chain.foreach(_.cancel())
        underlying.removeListener(listener)
        super.cancel()
      }
    }
    val listener = ChangeListener[A](fn(subscription, _, _, _))

    // Note: it is important to set the listener before listening to value
    // changes.
    subscription.listener = listener
    underlying.addListener(listener)

    subscription
  }

  /** Listens value change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: (Cancellable, ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen2(Seq(chain), fn)

  /** Listens value change with auto subscription. */
  def listen2(fn: (Cancellable, ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen2(Seq.empty, fn)

  /** Listens value change with auto subscription and chained subscriptions. */
  def listen2(chain: Seq[Cancellable], fn: (Cancellable, A) => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, v: A) => fn(s, v))

  /** Listens value change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: (Cancellable, A) => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, v: A) => fn(s, v))

  /** Listens value change with auto subscription. */
  def listen2(fn: (Cancellable, A) => Unit): Cancellable =
    listen2((s, _, _, v) => fn(s, v))

  /** Listens value change with auto subscription and chained subscriptions. */
  def listen2(chain: Seq[Cancellable], fn: Cancellable => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, _: A) => fn(s))

  /** Listens value change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: Cancellable => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, _: A) => fn(s))

  /** Listens value change with auto subscription. */
  def listen2(fn: Cancellable => Unit): Cancellable =
    listen2((s, _, _, _) => fn(s))

  /**
   * Listens value change with chained subscriptions.
   *
   * Subscription attached to created listener will first process chained
   * subscriptions before itself upon cancellation.
   *
   * @param chain subscriptions to chain
   * @param fn listening function
   * @return new subscription chained with given ones
   */
  def listen(chain: Seq[Cancellable], fn: (ObservableValue[_ <: A], A, A) => Unit): Cancellable = {
    val listener = ChangeListener[A](fn)
    underlying.addListener(listener)

    new Cancellable {
      override def cancel() {
        chain.foreach(_.cancel())
        underlying.removeListener(listener)
        super.cancel()
      }
    }
  }

  /** Listens value change with chained subscription. */
  def listen(chain: Cancellable, fn: (ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen(Seq(chain), fn)

  /** Listens value change. */
  def listen(fn: (ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen(Seq.empty, fn)

  /** Listens value change with chained subscriptions. */
  def listen(chain: Seq[Cancellable], fn: A => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, v: A) => fn(v))

  /** Listens value change with chained subscription. */
  def listen(chain: Cancellable, fn: A => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, v: A) => fn(v))

  /** Listens value change. */
  def listen(fn: A => Unit): Cancellable =
    listen((_, _, v) => fn(v))

  /** Listens value change with chained subscriptions. */
  def listen(chain: Seq[Cancellable], fn: => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, _: A) => fn)

  /** Listens value change with chained subscription. */
  def listen(chain: Cancellable, fn: => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, _: A) => fn)

  /** Listens value change. */
  def listen(fn: => Unit): Cancellable =
    listen((_, _, _) => fn)

}

object RichReadOnlyProperty {

  import scala.language.implicitConversions

  /** Implicit conversion. */
  implicit def toRichReadOnlyProperty[A](v: ReadOnlyProperty[A]): RichReadOnlyProperty[A] =
    new RichReadOnlyProperty(v)

  /**
   * Attaches listening code with auto subscription to multiple properties.
   *
   * The created subscription removes all listeners upon cancellation.
   *
   * @param props properties to listen on
   * @param fn listening code
   * @return subscription
   */
  def listen2[A](props: Seq[ReadOnlyProperty[_ <: A]], fn: (Cancellable, ObservableValue[_ <: A], A, A) => Unit): Cancellable = {
    val dummyCancellable = new Cancellable {
      override def cancel() {
        super.cancel()
      }
    }

    props.foldLeft(dummyCancellable) { (cancellable, prop) =>
      prop.listen2(cancellable, fn)
    }
  }

  /** Attaches listening code with auto subscription to multiple properties. */
  def listen2[A](props: Seq[ReadOnlyProperty[_ <: A]], fn: (Cancellable, A) => Unit): Cancellable =
    listen2[A](props, (s: Cancellable, _: ObservableValue[_ <: A], _: A, v: A) => fn(s, v))

  /** Attaches listening code with auto subscription to multiple properties. */
  def listen2(props: Seq[ReadOnlyProperty[_ <: Object]], fn: Cancellable => Unit): Cancellable =
    listen2[Any](props, (s: Cancellable, _: ObservableValue[_ <: Any], _: Any, _: Any) => fn(s))

  /**
   * Attaches listening code to multiple properties.
   *
   * The created subscription removes all listeners upon cancellation.
   *
   * @param props properties to listen on
   * @param fn listening code
   * @return subscription
   */
  def listen[A](props: Seq[ReadOnlyProperty[_ <: A]], fn: (ObservableValue[_ <: A], A, A) => Unit): Cancellable = {
    val dummyCancellable = new Cancellable {
      override def cancel() {
        super.cancel()
      }
    }

    props.foldLeft(dummyCancellable) { (cancellable, prop) =>
      prop.listen(cancellable, fn)
    }
  }

  /** Attaches listening code to multiple properties. */
  def listen[A](props: Seq[ReadOnlyProperty[_ <: A]], fn: A => Unit): Cancellable =
    listen[A](props, (_: ObservableValue[_ <: A], _: A, v: A) => fn(v))

  /** Attaches listening code to multiple properties. */
  def listen(props: Seq[ReadOnlyProperty[_ <: Object]], fn: => Unit): Cancellable =
    listen[Any](props, (_: ObservableValue[_ <: Any], _: Any, _: Any) => fn)

  /** Dummy subscription used for auto subscription. */
  trait CancellableListener[A] extends Cancellable {
    var listener: ChangeListener[A] = _
  }

}
