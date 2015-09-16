package suiryc.scala.concurrent

import java.lang.{Runnable => jRunnable}

/** java.lang.Runnable helpers. */
object Runnable {

  import scala.language.implicitConversions

  /** Creates a Runnable from a by-name parameter. */
  def apply(action: => Unit): jRunnable = new jRunnable {
    override def run() = action
  }

  /** Implicit conversion from function to Runnable. */
  implicit def fn0ToRunnable(fn: () => Unit): jRunnable =
    Runnable(fn())

}
