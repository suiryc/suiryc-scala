package suiryc.scala.concurrent.locks

import java.util.concurrent.locks.Lock

/**
 * Rich lock.
 *
 * Adds helper to automatically lock/unlock around some code.
 */
class RichLock(val underlying: Lock) extends AnyVal {

  def withLock[A](f: => A): A = {
    underlying.lock()
    try {
      f
    } finally {
      underlying.unlock()
    }
  }

}

object RichLock {

  import scala.language.implicitConversions

  implicit def toRichLock(lock: Lock): RichLock =
    new RichLock(lock)

}
