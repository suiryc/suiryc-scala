package suiryc.scala.javafx.collections

import javafx.{collections => jfxc}

/** Wraps a function as a change listener. */
case class ListChangeListener[A](fn: jfxc.ListChangeListener.Change[_ <: A] => Unit)
  extends jfxc.ListChangeListener[A]
{

  override def onChanged(change: jfxc.ListChangeListener.Change[_ <: A]): Unit =
    fn(change)

}
