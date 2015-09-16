package suiryc.scala.concurrent

import java.util.concurrent.{Callable => jCallable}

/** java.util.concurrent.Callable helpers. */
object Callable {

  import scala.language.implicitConversions

  /** Creates a Callable from a by-name parameter. */
  def apply[V](fn: => V): jCallable[V] = new jCallable[V] {
    override def call(): V = fn
  }

  /** Implicit conversion from function to Callable. */
  implicit def fn0ToCallable[V](fn: () => V): jCallable[V] =
    Callable(fn())

}
