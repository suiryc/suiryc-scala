package suiryc.scala.util

import monix.execution.Scheduler
import scala.concurrent.duration._

/**
 * Call throttler.
 *
 * Wraps code to throttle by limiting its execution to at most once per period
 * of time.
 * The time at which call is executed depends on the implementation.
 */
trait CallThrottler {

  /**
   * Throttles underlying code execution.
   *
   * Actual execution may happen synchronously or not depending on the
   * implementation and the last time code was executed.
   */
  def throttle(): Unit

}

/**
 * Single code call throttler.
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
class SingleCallThrottler(scheduler: Scheduler, duration: FiniteDuration, f: Boolean ⇒ Any) extends CallThrottler {

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
   * Throttles underlying code execution.
   *
   * Call is done right away if the delay since the last call exceeds the
   * throttling duration.
   * Otherwise it is throttled: the first attempt triggers scheduling of next
   * actual code call (at the end of the period past the latest actual call),
   * next attempts are ignored (only one delayed call is scheduled).
   */
  override def throttle(): Unit = this.synchronized {
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
            // Note: remember the time at which the call shall have been made
            // instead of the time it is actually done, so that we try to
            // schedule calls at a fixed rate.
            call(inEC = true, now + delay)
          }
        }
        throttling = true
      }
    }
  }

}

/**
 * Handles multiple calls to throttle for a given duration.
 *
 * Unlike the single call code throttler, this throttler always attempt to group
 * code calls. As such, no call is ever made synchronously: at best a minimal
 * delay is used so that if other calls are attempted in the meantime, they will
 * be grouped together. Consequently, it is guaranteed that all calls are done
 * in the scheduler execution context.
 */
private class CallsDurationThrottler(scheduler: Scheduler, duration: FiniteDuration) {

  import CallsThrottler._

  // Currently throttled codes (call done once throttling delay has elapsed)
  private var calls = Set[() ⇒ Any]()
  // Last time calls were done
  private var lastCall: Long = 0
  // Whether calls are being throttled right now
  private var throttling: Boolean = false

  /**
   * Throttles code execution.
   *
   * If the delay since the last calls exceeds the throttling duration, postpone
   * this call so that other calls have a chance to be grouped with it. This
   * initial delay is half the throttling duration, with a minimum and maximum of
   * 10ms and 100ms.
   * Otherwise throttling duration is enforced and this call is grouped with the
   * other calls attempted before the duration is elapsed.
   *
   * Each code, passed as a function callback parameter, is identified by its
   * hashcode: if passed twice in the same period, only one call will be done.
   */
  def throttle(f: () ⇒ Any): Unit = this.synchronized {
    if (!throttling) {
      val now = System.currentTimeMillis
      val delay = math.max(
        math.max(
          THROTTLE_INITIAL_MIN_DELAY,
          math.min(THROTTLE_INITIAL_MAX_DELAY, duration.toMillis / 2)
        ),
        lastCall + duration.toMillis - now
      )
      scheduler.scheduleOnce(delay.millis) {
        this.synchronized {
          throttling = false
          // Note: remember the time at which the call shall have been made
          // instead of the time it is actually done, so that we try to
          // schedule calls at a fixed rate.
          lastCall = now + delay
          calls.foreach(_())
          calls = Set.empty
        }
      }
      throttling = true
    }
    calls += f
  }

}

/** Code throttler as part of a group (sharing same duration). */
class GroupedCallThrottler(group: CallsDurationThrottler, f: () ⇒ Any) extends CallThrottler {
  /**
   * Throttles code execution.
   *
   * Throttlers sharing the same duration have their calls grouped. All calls
   * are done asynchronously in the scheduler execution context.
   */
  override def throttle(): Unit = group.throttle(f)
}

/**
 * Multiple codes calls throttler.
 *
 * Throttles some codes calling to at most once per period of time.
 * More than one code call can be registered, in which case all related codes
 * will be called at the same time (through the scheduler execution context).
 *
 * @param scheduler scheduler to delay code call
 */
class CallsThrottler(scheduler: Scheduler) {

  // We remember throttlers grouped by duration so that all calls for the same
  // duration are grouped together.
  private var throttlers: Map[Long, CallsDurationThrottler] = Map.empty

  private def getThrottler(duration: FiniteDuration): CallsDurationThrottler = this.synchronized {
    val delay = duration.toMillis
    throttlers.get(delay) match {
      case Some(throttler) ⇒
        // Use existing group
        throttler

      case None ⇒
        // Create a new group for this duration
        val throttler = new CallsDurationThrottler(scheduler, duration)
        throttlers += (delay → throttler)
        throttler
    }
  }

  /**
   * Creates a throttler for given code and duration.
   *
   * Creating multiple throttlers for the same duration will group them to
   * execute their code at the same time.
   *
   * @param duration period of time to wait between code calls
   * @param f code to call
   */
  def callThrottler(duration: FiniteDuration)(f: () ⇒ Any): CallThrottler = {
    new GroupedCallThrottler(getThrottler(duration), f)
  }

}

object CallThrottler {

  /** Builds a (single code) call throttler. */
  def apply(scheduler: Scheduler, duration: FiniteDuration)(f: Boolean ⇒ Any): CallThrottler =
    new SingleCallThrottler(scheduler, duration, f)

}

object CallsThrottler {

  // Minimal delay before first group throttled call.
  val THROTTLE_INITIAL_MIN_DELAY = 10L
  // Maximal delay before first group throttled call.
  val THROTTLE_INITIAL_MAX_DELAY = 100L

  /** Builds a throttler to handle grouped code calls. */
  def apply(scheduler: Scheduler): CallsThrottler =
    new CallsThrottler(scheduler)

}
