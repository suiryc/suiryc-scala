package suiryc.scala.util

import monix.execution.Scheduler
import scala.concurrent.duration._

/**
 * Code call throttler.
 *
 * Throttles some code calling to at most once per period of time.
 * If the period of time is not elapsed since the latest actual call, all
 * subsequent calls (until the period ends) are delayed and merged in one actual
 * call once the period is elapsed.
 *
 * @param scheduler scheduler to delay code call
 * @param duration period of time to wait between code calls
 * @param f code to call; the parameter indicates whether the call is being done
 *          through the scheduler execution context
 */
class CallThrottler(scheduler: Scheduler, duration: FiniteDuration, f: Boolean ⇒ Any) {

  // Last time the code was called
  private var lastCall: Long = 0
  // Whether code call is being throttled right now
  private var throttling: Boolean = false

  // Actual call (throttled)
  private def call(inEC: Boolean, now: Long): Unit = {
    lastCall = now
    f(inEC)
    ()
  }

  /**
   * Attempts to call code.
   *
   * Call is done right away when possible.
   * Otherwise it is throttled: the first attempt triggers scheduling of next
   * actual code call (at the end of the period past the latest actual call),
   * next attempts are ignored (only one delayed call is scheduled).
   */
  def apply(): Unit = this.synchronized {
    if (!throttling) {
      val now = System.currentTimeMillis
      val delay = lastCall + duration.toMillis - now
      if (delay <= 0) {
        call(inEC = false, now)
      } else {
        // Time to throttle.
        scheduler.scheduleOnce(delay.millis) {
          this.synchronized {
            throttling = false
            call(inEC = true, now + delay)
          }
        }
        throttling = true
      }
    }
  }

}

object CallThrottler {

  /** Builds a call throttler. */
  def apply(scheduler: Scheduler, duration: FiniteDuration)(f: Boolean ⇒ Any): CallThrottler =
    new CallThrottler(scheduler, duration, f)

}
