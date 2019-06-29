package suiryc.scala.util

import scala.concurrent.duration._
import suiryc.scala.math.BigDecimals.scale

/** Bench helpers. */
object Bench {

  // How many loops by default for warmup
  private val WARMUP_LOOPS_DEFAULT = 1000
  // How many loops by default for time
  private val TIME_LOOPS_DEFAULT = 1000000

  /**
   * Warmups.
   *
   * Ends when either:
   *  - code has been called the requested number of times
   *  - the elapsed duration reached the requested limit
   *
   * @param n how many loops (default 1000)
   * @param duration how long to warmup (default 1s)
   * @param f code to warmup
   * @tparam T result type
   * @return code result
   */
  def warmup[T](n: Int = WARMUP_LOOPS_DEFAULT, duration: Option[FiniteDuration] = Some(1.seconds))(f: => T): T = {
    time(None, n, duration)(f)
  }

  /**
   * Times code.
   *
   * Ends when either:
   *  - code has been called the requested number of times
   *  - the elapsed duration reached the requested limit
   *
   * @param label label to indicate when printing time stats
   * @param n how many loops (default 1000000)
   * @param duration how long to warmup (default 2s)
   * @param f code to warmup
   * @tparam T result type
   * @return code result
   */
  def time[T](label: String, n: Int = TIME_LOOPS_DEFAULT, duration: Option[FiniteDuration] = Some(2.seconds))(f: => T): T = {
    time(Some(label), n, duration)(f)
  }

  @inline private def time[T](label: Option[String], n: Int, duration: Option[FiniteDuration])(f: => T): T = {
    @inline def getTime: Long = System.nanoTime
    val timeScaleSecond = 1000000000L
    val timeScaleMillisecond = 1000000L
    val start = getTime
    val checkTimeMax = duration.map(_.toNanos + start).getOrElse(0L)
    var checkTime = if (duration.isDefined) 1 else 0
    var checkTimeLast = if (duration.isDefined) start else 0

    @scala.annotation.tailrec
    def loop(looped: Int, remaining: Int): (Int, T) = {
      val r = f
      if (remaining > 0) {
        val end = if ((checkTime > 0) && (remaining % checkTime == 0)) {
          val now = getTime
          if (now >= checkTimeMax) true
          else {
            if ((now - checkTimeLast) / timeScaleMillisecond < 50) {
              checkTime *= 2
            }
            checkTimeLast = now
            false
          }
        } else false
        if (end) (looped + 1, r)
        else loop(looped + 1, remaining - 1)
      } else (looped, r)
    }

    val (looped, r) = loop(0, n)
    val elapsedTime = getTime - start
    label.foreach { label =>
      val elapsed = scale(BigDecimal(elapsedTime) / timeScaleSecond)
      val perLoop = scale(BigDecimal(elapsedTime) / timeScaleSecond / looped)
      val perLoopNs = elapsedTime / looped
      val rate = if (elapsedTime > 0) {
        scale(BigDecimal(looped) * timeScaleSecond / elapsedTime)
      } else {
        BigDecimal(0)
      }
      // scalastyle:off token
      println(s"$label: looped=<$looped> elapsed=<$elapsed> perLoop=<$perLoop> perLoopNs=<$perLoopNs> perSecond=<$rate>")
      // scalastyle:on token
    }
    r
  }

}
