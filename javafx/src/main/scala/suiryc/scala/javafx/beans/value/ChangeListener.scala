package suiryc.scala.javafx.beans.value

import javafx.beans.{value => jfxbv}


case class ChangeListener[T](fn: (jfxbv.ObservableValue[_ <: T], T, T) => Unit)
  extends jfxbv.ChangeListener[T]
{

  def changed(observable: jfxbv.ObservableValue[_ <: T], oldValue: T, newValue: T): Unit =
    fn(observable, oldValue, newValue)

}
