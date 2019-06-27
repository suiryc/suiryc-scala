package suiryc.scala.concurrent.duration

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import suiryc.scala.misc.Util

/** Duration helpers. */
object Durations {

  /**
   * Gets duration from string.
   *
   * @return parsed duration; None upon format error
   */
  def parse(s: String): Option[Duration] = {
    Option(s).flatMap { v =>
      try {
        Some(Util.parseDuration(v))
      } catch {
        case _: Exception => None
      }
    }
  }

  /**
   * Gets finite duration from string.
   *
   * @return parsed duration; None upon format error or non-finite duration
   */
  def parseFinite(s: String): Option[FiniteDuration] = {
    parse(s).filter(_.isFinite).asInstanceOf[Option[FiniteDuration]]
  }

  /** Gets time unit short representation (ns, us, ms, s, min, h, d). */
  def shortUnit(unit: TimeUnit): String = {
    unit match {
      case TimeUnit.NANOSECONDS  => "ns"
      case TimeUnit.MICROSECONDS => "us"
      case TimeUnit.MILLISECONDS => "ms"
      case TimeUnit.SECONDS      => "s"
      case TimeUnit.MINUTES      => "min"
      case TimeUnit.HOURS        => "h"
      case TimeUnit.DAYS         => "d"
    }
  }

}
