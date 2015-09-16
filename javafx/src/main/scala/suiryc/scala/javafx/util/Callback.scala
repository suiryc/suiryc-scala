package suiryc.scala.javafx.util

import javafx.util.{Callback => jfxCallback}

/** javafx.util.Callback helpers. */
object Callback {

  import scala.language.implicitConversions

  /** Creates a Callback from a function. */
  def apply[P, R](fn: P => R): jfxCallback[P, R] = new jfxCallback[P, R] {
    override def call(p: P): R = fn(p)
  }

  /** Creates a Callback from a function. */
  def apply[P, R](fn: => R): jfxCallback[P, R] = new jfxCallback[P, R] {
    override def call(p: P): R = fn
  }

  /** Implicit conversion from function to Callback. */
  implicit def fn1ToCallback[P, R](fn: P => R): jfxCallback[P, R] =
    Callback(fn)

  /** Implicit conversion from function to Callback. */
  implicit def fn0ToCallback[P, R](fn: () => R): jfxCallback[P, R] =
    Callback(fn())

}
