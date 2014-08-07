package suiryc.scala.javafx.event

import javafx.{event => jfxe}

object EventHandler {

  import scala.language.implicitConversions

  implicit def fn1ToEventHandler[T <: jfxe.Event](fn: Function1[T, Unit]): jfxe.EventHandler[T] =
    EventHandler(fn)

  def apply[T <: jfxe.Event](fn: Function1[T, Unit]): jfxe.EventHandler[T] = {
    new jfxe.EventHandler[T] {
      override def handle(event: T) =
        fn(event)
    }
  }

}
