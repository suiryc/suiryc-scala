package suiryc.scala.concurrent

import java.lang.{Runnable => jRunnable}

object Runnable {

  /** Creates a java.lang.Runnable with a by-name parameter. */
  def apply(action: => Unit): jRunnable = new jRunnable {
    override def run() = action
  }

}
