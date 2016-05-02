package suiryc.scala.math

import scala.math.BigDecimal.RoundingMode

/** BigDecimal helpers. */
object BigDecimals {

  /** Round to long (half up). */
  def round(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.HALF_UP).longValue

  /** Round to long (half even). */
  def roundHalfEven(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.HALF_EVEN).longValue

  /** Round to long (floor). */
  def floor(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.FLOOR).longValue

  /** Round to long (down). */
  def roundDown(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.DOWN).longValue

  /** Round to long (ceil). */
  def ceil(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.CEILING).longValue

  /** Round to long (up). */
  def roundUp(v: BigDecimal): Long =
    v.setScale(0, RoundingMode.UP).longValue

}
