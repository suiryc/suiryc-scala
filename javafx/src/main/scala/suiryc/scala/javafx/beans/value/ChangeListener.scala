package suiryc.scala.javafx.beans.value

import javafx.beans.{value => jfxbv}

/** Wraps a function as a change listener. */
case class ChangeListener[A](fn: (jfxbv.ObservableValue[_ <: A], A, A) => Unit)
  extends jfxbv.ChangeListener[A]
{

  override def changed(observable: jfxbv.ObservableValue[_ <: A], oldValue: A, newValue: A): Unit =
    fn(observable, oldValue, newValue)

}
