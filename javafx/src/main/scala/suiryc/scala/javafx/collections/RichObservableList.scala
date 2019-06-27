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
 *
 * Note: subscription does not have its own class name and is simply a
 * 'Cancellable'.
 */
class RichObservableList[A](val underlying: ObservableList[A]) extends AnyVal {

  /**
   * Listens change with auto subscription.
   *
   * Listening code is given its subscription and can auto-cancel itself.
   *
   * @param fn listening function
   * @return subscription of listening code
   */
  def listen2(fn: (Cancellable, jfxc.ListChangeListener.Change[_ <: A]) => Unit): Cancellable = {
    // We need to create a subscription to give to the listening code. To do so
    // we need to create a listener, which is based on the listening code and
    // thus needs the subscription.
    // So we have to first create a dummy subscription to give to the listening
    // code, which allows us to create the actual listener. Then we can provide
    // the listener to the dummy subscription.
    val subscription = new RichObservableList.CancellableListener[A] {
      override def cancel() {
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

  /** Listens change with auto subscription. */
  def listen2(fn: Cancellable => Unit): Cancellable =
    listen2((s, _) => fn(s))

  /**
   * Listens change.
   *
   * @param fn listening function
   * @return subscription of listening code
   */
  def listen(fn: jfxc.ListChangeListener.Change[_ <: A] => Unit): Cancellable = {
    val listener = ListChangeListener[A](fn)
    underlying.addListener(listener)

    new Cancellable {
      override def cancel() {
        underlying.removeListener(listener)
        super.cancel()
      }
    }
  }

  /** Listens change. */
  def listen(fn: => Unit): Cancellable =
    listen((_: jfxc.ListChangeListener.Change[_]) => fn)

}

object RichObservableList {

  import scala.language.implicitConversions

  /** Implicit conversion. */
  implicit def toRichObservableList[A](v: ObservableList[A]): RichObservableList[A] =
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
  def listen2[A](observables: Seq[ObservableList[_ <: A]])(fn: Cancellable => Unit): Cancellable = {
    val subscription = new CancellableListener[A] {
      override def cancel() {
        observables.foreach(_.removeListener(listener))
        super.cancel()
      }
    }
    val listener = ListChangeListener[A](_ => fn(subscription))

    subscription.listener = listener
    observables.foreach(_.addListener(listener))

    subscription
  }

  /**
   * Attaches listening code with auto subscription to multiple observables.
   *
   * vararg variant.
   */
  def listen2[A](observables: ObservableList[_ <: A]*)(fn: Cancellable => Unit)(implicit d: DummyImplicit): Cancellable = {
    listen2[A](observables.toSeq)(fn)
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
  def listen(observables: Seq[ObservableList[_ <: Object]])(fn: => Unit): Cancellable = {
    val listener = ListChangeListener[Object](_ => fn)
    observables.foreach(_.addListener(listener))

    new Cancellable {
      override def cancel() {
        observables.foreach(_.removeListener(listener))
        super.cancel()
      }
    }
  }

  /**
   * Attaches listening code to multiple observables.
   *
   * vararg variant.
   */
  def listen(observables: ObservableList[_ <: Object]*)(fn: => Unit)(implicit d: DummyImplicit): Cancellable = {
    listen(observables.toSeq)(fn)
  }

  /** Dummy subscription used for auto subscription. */
  trait CancellableListener[A] extends Cancellable {
    var listener: ListChangeListener[A] = _
  }

}
