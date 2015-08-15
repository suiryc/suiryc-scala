package suiryc.scala.javafx.collections

import javafx.{collections => jfxc}
import javafx.collections.ObservableList
import suiryc.scala.concurrent.Cancellable

/**
 * ObservableList enhancements.
 *
 * Allows to attach listening code (adds a new listener) and returns a
 * subscription that can be cancelled (removes the listener).
 * Handles multiple variants:
 *   - listening code can receive its own subscription and cancel itself
 *   - listening code can receive the whole observed change or nothing
 *   - other subscriptions can be attached to chain cancellations
 *
 * Note: subscription does not have its own class name and is simply a
 * 'Cancellable'.
 */
class RichObservableList[A](val underlying: ObservableList[A]) extends AnyVal {

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
  def listen2(chain: Seq[Cancellable], fn: (Cancellable, jfxc.ListChangeListener.Change[_ <: A]) => Unit): Cancellable = {
    // We need to create a subscription to give to the listening code. To do so
    // we need to create a listener, which is based on the listening code and
    // thus needs the subscription.
    // So we have to first create a dummy subscription to give to the listening
    // code, which allows us to create the actual listener. Then we can provide
    // the listener to the dummy subscription.
    val subscription = new RichObservableList.CancellableListener[A] {
      override def cancel() {
        chain.foreach(_.cancel())
        underlying.removeListener(listener)
        super.cancel()
      }
    }
    val listener = ListChangeListener[A](fn(subscription, _))

    // Note: it is important to set the listener before listening to value
    // changes.
    subscription.listener = listener
    underlying.addListener(listener)

    subscription
  }

  /** Listens change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: (Cancellable, jfxc.ListChangeListener.Change[_ <: A]) => Unit): Cancellable =
    listen2(Seq(chain), fn)

  /** Listens change with auto subscription. */
  def listen2(fn: (Cancellable, jfxc.ListChangeListener.Change[_ <: A]) => Unit): Cancellable =
    listen2(Seq.empty, fn)

  /** Listens change with auto subscription and chained subscriptions. */
  def listen2(chain: Seq[Cancellable], fn: Cancellable => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: jfxc.ListChangeListener.Change[_ <: A]) => fn(s))

  /** Listens change with auto subscription and chained subscription. */
  def listen2(chain: Cancellable, fn: Cancellable => Unit): Cancellable =
    listen2(chain, (s: Cancellable, _: jfxc.ListChangeListener.Change[_ <: A]) => fn(s))

  /** Listens change with auto subscription. */
  def listen2(fn: Cancellable => Unit): Cancellable =
    listen2((s, _) => fn(s))

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
  def listen(chain: Seq[Cancellable], fn: jfxc.ListChangeListener.Change[_ <: A] => Unit): Cancellable = {
    val listener = ListChangeListener[A](fn)
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
  def listen(chain: Cancellable, fn: (jfxc.ListChangeListener.Change[_ <: A]) => Unit): Cancellable =
    listen(Seq(chain), fn)

  /** Listens change. */
  def listen(fn: jfxc.ListChangeListener.Change[_ <: A] => Unit): Cancellable =
    listen(Seq.empty, fn)

  /** Listens change with chained subscriptions. */
  def listen(chain: Seq[Cancellable], fn: => Unit): Cancellable =
    listen(chain, (_: jfxc.ListChangeListener.Change[_ <: A]) => fn)

  /** Listens change with chained subscription. */
  def listen(chain: Cancellable, fn: => Unit): Cancellable =
    listen(chain, (_: jfxc.ListChangeListener.Change[_ <: A]) => fn)

  /** Listens change. */
  def listen(fn: => Unit): Cancellable =
    listen(_ => fn)

}

object RichObservableList {

  import scala.language.implicitConversions

  /** Implicit conversion. */
  implicit def toRich[A](v: ObservableList[A]): RichObservableList[A] =
    new RichObservableList(v)

  /**
   * Attaches listening code with auto subscription to multiple observables.
   *
   * The created subscription removes all listeners upon cancellation.
   *
   * @param observables observables to listen on
   * @param fn listening code
   * @return subscription
   */
  def listen2[A](observables: Seq[ObservableList[_ <: A]], fn: (Cancellable, jfxc.ListChangeListener.Change[_ <: A]) => Unit): Cancellable = {
    val dummyCancellable = new Cancellable {
      override def cancel() {
        super.cancel()
      }
    }

    observables.foldLeft(dummyCancellable) { (cancellable, observable) =>
      observable.listen2(cancellable, fn)
    }
  }

  /** Attaches listening code with auto subscription to multiple observables. */
  def listen2(observables: Seq[ObservableList[_ <: Object]], fn: Cancellable => Unit): Cancellable =
    listen2[Any](observables, (s: Cancellable, _: jfxc.ListChangeListener.Change[_ <: Any]) => fn(s))

  /**
   * Attaches listening code to multiple observables.
   *
   * The created subscription removes all listeners upon cancellation.
   *
   * @param observables observables to listen on
   * @param fn listening code
   * @return subscription
   */
  def listen[A](observables: Seq[ObservableList[_ <: A]], fn: jfxc.ListChangeListener.Change[_ <: A] => Unit): Cancellable = {
    val dummyCancellable = new Cancellable {
      override def cancel() {
        super.cancel()
      }
    }

    observables.foldLeft(dummyCancellable) { (cancellable, observable) =>
      observable.listen(cancellable, fn)
    }
  }

  /** Attaches listening code to multiple observables. */
  def listen(observables: Seq[ObservableList[_ <: Object]], fn: => Unit): Cancellable =
    listen[Any](observables, (_: jfxc.ListChangeListener.Change[_ <: Any]) => fn)

  /** Dummy subscription used for auto subscription. */
  trait CancellableListener[A] extends Cancellable {
    var listener: ListChangeListener[A] = _
  }

}
