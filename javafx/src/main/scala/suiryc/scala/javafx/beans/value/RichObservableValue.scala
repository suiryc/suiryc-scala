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
 *
 * Note: subscription does not have its own class name and is simply a
 * 'Cancellable'.
 */
class RichObservableValue[A](val underlying: ObservableValue[A]) extends AnyVal {

  /**
   * Listens change with auto subscription.
   *
   * Listening code is given its subscription and can auto-cancel itself.
   *
   * @param fn listening function
   * @return subscription of listening code
   */
  def listen2(fn: (Cancellable, ObservableValue[_ <: A], A, A) => Unit): Cancellable = {
    // We need to create a subscription to give to the listening code. To do so
    // we need to create a listener, which is based on the listening code and
    // thus needs the subscription.
    // So we have to first create a dummy subscription to give to the listening
    // code, which allows us to create the actual listener. Then we can provide
    // the listener to the dummy subscription.
    val subscription = new RichObservableValue.CancellableListener[A] {
      override def cancel(): Unit = {
        underlying.removeListener(this.listener)
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

  /** Listens change with auto subscription. */
  def listen2(fn: (Cancellable, A) => Unit): Cancellable =
    listen2((s, _, _, v) => fn(s, v))

  /** Listens change with auto subscription. */
  def listen2(fn: Cancellable => Unit): Cancellable =
    listen2((s, _, _, _) => fn(s))

  /**
   * Listens change.
   *
   * @param fn listening function
   * @return subscription of listening code
   */
  def listen(fn: (ObservableValue[_ <: A], A, A) => Unit): Cancellable = {
    val listener = ChangeListener[A](fn)
    underlying.addListener(listener)

    new Cancellable {
      override def cancel(): Unit = {
        underlying.removeListener(listener)
        super.cancel()
      }
    }
  }

  /** Listens change. */
  def listen(fn: A => Unit): Cancellable =
    listen((_, _, v) => fn(v))

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
  def listen2[A](observables: Seq[ObservableValue[_ <: A]])(fn: Cancellable => Unit): Cancellable = {
    val subscription = new CancellableListener[A] {
      override def cancel(): Unit = {
        observables.foreach(_.removeListener(this.listener))
        super.cancel()
      }
    }
    val listener = ChangeListener[A]((_, _, _) => fn(subscription))

    subscription.listener = listener
    observables.foreach(_.addListener(listener))

    subscription
  }

  /**
   * Attaches listening code with auto subscription to multiple observables.
   *
   * vararg variant.
   */
  def listen2[A](observables: ObservableValue[_ <: A]*)(fn: Cancellable => Unit)(implicit d: DummyImplicit): Cancellable = {
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
  def listen[A](observables: Seq[ObservableValue[_ <: A]])(fn: => Unit): Cancellable = {
    val listener = ChangeListener[A]((_, _, _) => fn)
    observables.foreach(_.addListener(listener))

    new Cancellable {
      override def cancel(): Unit = {
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
  def listen[A](observables: ObservableValue[_ <: A]*)(fn: => Unit)(implicit d: DummyImplicit): Cancellable = {
    listen[A](observables.toSeq)(fn)
  }

  /** Dummy subscription used for auto subscription. */
  trait CancellableListener[A] extends Cancellable {
    var listener: ChangeListener[A] = _
  }

}
