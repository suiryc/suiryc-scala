package suiryc.scala.javafx.beans.value

import javafx.beans.value.ObservableValue
import suiryc.scala.concurrent.Cancellable

/**
 * ObservableValue enhancements.
 *
 * Allows to attach listening code (adds a new listener) and returns a
 * subscription that can be cancelled (removes the listener).
 * Handles multiple variants:
 *   - listening code can receive its own subscription and cancel itself
 *   - listening code can receive the whole observed change, only the new value
 *     or nothing
 *   - other subscriptions can be attached to chain cancellations
 *
 * Note: subscription does not have its own class name and is simply a
 * 'Cancellable'.
 */
class RichObservableValue[A](val underlying: ObservableValue[A]) extends AnyVal {

  /**
   * Listens change with auto subscription and chained subscriptions.
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
    val subscription = new RichObservableValue.CancellableListener[A] {
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

  /** Listens change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: (Cancellable, ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen2(Seq(chain), fn)

  /** Listens change with auto subscription. */
  def listen2(fn: (Cancellable, ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen2(Seq.empty, fn)

  /** Listens change with auto subscription and chained subscriptions. */
  def listen2(chain: Seq[Cancellable], fn: (Cancellable, A) => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, v: A) => fn(s, v))

  /** Listens change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: (Cancellable, A) => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, v: A) => fn(s, v))

  /** Listens change with auto subscription. */
  def listen2(fn: (Cancellable, A) => Unit): Cancellable =
    listen2((s, _, _, v) => fn(s, v))

  /** Listens change with auto subscription and chained subscriptions. */
  def listen2(chain: Seq[Cancellable], fn: Cancellable => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, _: A) => fn(s))

  /** Listens change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: Cancellable => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: ObservableValue[_ <: A], _: A, _: A) => fn(s))

  /** Listens change with auto subscription. */
  def listen2(fn: Cancellable => Unit): Cancellable =
    listen2((s, _, _, _) => fn(s))

  /**
   * Listens change with chained subscriptions.
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

  /** Listens change with chained subscription. */
  def listen(chain: Cancellable, fn: (ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen(Seq(chain), fn)

  /** Listens change. */
  def listen(fn: (ObservableValue[_ <: A], A, A) => Unit): Cancellable =
    listen(Seq.empty, fn)

  /** Listens change with chained subscriptions. */
  def listen(chain: Seq[Cancellable], fn: A => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, v: A) => fn(v))

  /** Listens change with chained subscription. */
  def listen(chain: Cancellable, fn: A => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, v: A) => fn(v))

  /** Listens change. */
  def listen(fn: A => Unit): Cancellable =
    listen((_, _, v) => fn(v))

  /** Listens change with chained subscriptions. */
  def listen(chain: Seq[Cancellable], fn: => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, _: A) => fn)

  /** Listens change with chained subscription. */
  def listen(chain: Cancellable, fn: => Unit): Cancellable =
    listen(chain, (_: ObservableValue[_ <: A], _: A, _: A) => fn)

  /** Listens change. */
  def listen(fn: => Unit): Cancellable =
    listen((_, _, _) => fn)

}

object RichObservableValue {

  import scala.language.implicitConversions

  /** Implicit conversion. */
  implicit def toRichObservableValue[A](v: ObservableValue[A]): RichObservableValue[A] =
    new RichObservableValue(v)

  /**
   * Attaches listening code with auto subscription to multiple observables.
   *
   * The created subscription removes all listeners upon cancellation.
   *
   * @param observables observables to listen on
   * @param fn listening code
   * @return subscription
   */
  def listen2(observables: Seq[ObservableValue[_ <: Object]], fn: Cancellable => Unit): Cancellable = {
    val subscription = new CancellableListeners {
      override def cancel() {
        listeners.foreach { case (observable, listener) =>
          observable.removeListener(listener)
        }
        super.cancel()
      }
    }
    val listeners = observables.map { observable =>
      observable -> ChangeListener[Object]((_, _, _) => fn(subscription))
    }

    subscription.listeners = listeners
    listeners.foreach { case (observable, listener) =>
      observable.addListener(listener)
    }

    subscription
  }

  /**
   * Attaches listening code to multiple observables.
   *
   * The created subscription removes all listeners upon cancellation.
   *
   * @param observables observables to listen on
   * @param fn listening code
   * @return subscription
   */
  def listen(observables: Seq[ObservableValue[_ <: Object]], fn: => Unit): Cancellable = {
    val listeners = observables.map { observable =>
      observable -> ChangeListener[Object]((_, _, _) => fn)
    }
    listeners.foreach { case (observable, listener) =>
      observable.addListener(listener)
    }

    new Cancellable {
      override def cancel() {
        listeners.foreach { case (observable, listener) =>
          observable.removeListener(listener)
        }
        super.cancel()
      }
    }
  }

  /** Dummy subscription used for auto subscription. */
  trait CancellableListener[A] extends Cancellable {
    var listener: ChangeListener[A] = _
  }

  /** Dummy subscription used for auto subscription. */
  trait CancellableListeners extends Cancellable {
    var listeners: Seq[(ObservableValue[_ <: Object], ChangeListener[_ <: Object])] = Nil
  }

}
