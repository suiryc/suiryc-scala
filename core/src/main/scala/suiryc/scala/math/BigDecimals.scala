package suiryc.scala.math

import scala.math.BigDecimal.RoundingMode

/** BigDecimal helpers. */
object BigDecimals {

  /** Rounds to long (half up). */
  def round(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.HALF_UP).longValue

  /** Rounds to long (half even). */
  def roundHalfEven(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.HALF_EVEN).longValue

  /** Rounds to long (floor). */
  def floor(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.FLOOR).longValue

  /** Rounds to long (down). */
  def roundDown(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.DOWN).longValue

  /** Rounds to long (ceil). */
  def ceil(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.CEILING).longValue

  /** Rounds to long (up). */
  def roundUp(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.UP).longValue

  private val SCALE_DIGITS_DEFAULT = 4

  /** Scales value to keep given number of significant digits. */
  @inline def scale(v: BigDecimal, digits: Int = SCALE_DIGITS_DEFAULT): BigDecimal = {
    // Adapt scale (significant digits) according to value.
    // 'precision' = number of displayed digits
    // 'scale' = number of decimal digits
    // 'precision - scale' = (if >=0) number of integral digits
    // If 'scale' is 0 (no decimal digits), keep it that way.
    // If 'precision - scale >= digits' we already have enough integral digits
    // and can set scale to 0 (truncate decimal digits).
    // Otherwise we want to set scale to 'digits - integralDigits'.
    val integralDigits = v.precision - v.scale
    val scale =
      if ((v.scale == 0) || (integralDigits >= digits)) 0
      else digits - integralDigits
    v.setScale(scale, BigDecimal.RoundingMode.HALF_UP)
  }

}
