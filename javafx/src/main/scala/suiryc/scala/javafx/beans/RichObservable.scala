package suiryc.scala.javafx.beans

import javafx.beans.{InvalidationListener, Observable}
import suiryc.scala.concurrent.Cancellable

/**
 * Observable enhancements.
 *
 * Allows to attach listening code (adds a new listener) and returns a
 * subscription that can be cancelled (removes the listener).
 *
 * Notes:
 * Subscription does not have its own class name and is simply a
 * 'Cancellable'.
 * Listeners do not change invalidation, which could result in unwanted
 * behaviour: e.g. if an ObservableValue is listened as an Observable, a value
 * change triggers a call on the listener, but if the value is not read (and
 * thus remains invalidated) any other change is not propagated (until
 * invalidation is cleared).
 */
class RichObservable(val underlying: Observable) extends AnyVal {

  import RichObservable._

  /**
   * Listens invalidation with auto subscription.
   *
   * Listening code is given its subscription and can auto-cancel itself.
   *
   * @param fn listening function
   * @return subscription of listening code
   */
  def listen2(fn: (Cancellable, Observable) => Unit): Cancellable = {
    // We need to create a subscription to give to the listening code. To do so
    // we need to create a listener, which is based on the listening code and
    // thus needs the subscription.
    // So we have to first create a dummy subscription to give to the listening
    // code, which allows us to create the actual listener. Then we can provide
    // the listener to the dummy subscription.
    val subscription = new CancellableListener {
      override def cancel(): Unit = {
        underlying.removeListener(this.listener)
        super.cancel()
      }
    }
    val listener: InvalidationListener = o => fn(subscription, o)

    // Note: it is important to set the listener before listening to value
    // changes.
    subscription.listener = listener
    underlying.addListener(listener)

    subscription
  }

  /**
   * Listens invalidation.
   *
   * @param fn listening function
   * @return subscription of listening code
   */
  def listen(fn: Observable => Unit): Cancellable = {
    val listener: InvalidationListener = o => fn(o)
    underlying.addListener(listener)

    new Cancellable {
      override def cancel(): Unit = {
        underlying.removeListener(listener)
        super.cancel()
      }
    }
  }

}

object RichObservable {

  import scala.language.implicitConversions

  /** Implicit conversion. */
  implicit def toRichObservable(observable: Observable): RichObservable =
    new RichObservable(observable)

  /**
   * Attaches listening code with auto subscription to multiple observables.
   *
   * The created subscription removes all listeners upon cancellation.
   *
   * @param observables observables to listen on
   * @param fn listening code
   * @return subscription
   */
  def listen2(observables: Seq[Observable])(fn: Cancellable => Unit): Cancellable = {
    val subscription = new CancellableListener {
      override def cancel(): Unit = {
        observables.foreach(_.removeListener(this.listener))
        super.cancel()
      }
    }
    val listener: InvalidationListener = _ => fn(subscription)

    subscription.listener = listener
    observables.foreach(_.addListener(listener))

    subscription
  }

  /**
   * Attaches listening code with auto subscription to multiple observables.
   *
   * vararg variant.
   */
  def listen2(observables: Observable*)(fn: Cancellable => Unit)(implicit d: DummyImplicit): Cancellable = {
    listen2(observables.toSeq)(fn)
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
  def listen(observables: Seq[Observable])(fn: => Unit): Cancellable = {
    val listener: InvalidationListener = _ => fn
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
  def listen(observables: Observable*)(fn: => Unit)(implicit d: DummyImplicit): Cancellable = {
    listen(observables.toSeq)(fn)
  }

  /** Dummy subscription used for auto subscription. */
  trait CancellableListener extends Cancellable {
    var listener: InvalidationListener = _
  }

}
