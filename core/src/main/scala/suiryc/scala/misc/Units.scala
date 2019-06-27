package suiryc.scala.misc

import suiryc.scala.math.BigDecimals


// TODO - handle time system (separate for human readable form, or introduce 'cumulative' notion ?)
// TODO - handle floating points in human representation parsing ?
object Units {

  val scaleSI = 1000L
  val scaleBinary = 1024L

  case class Unit(label: String, factor: Long)

  abstract class AbstractSystem(unityLabel: String) {

    val unity = Unit(unityLabel, 1)

    private val ValueRegexp = """^([0-9]*)\s*([a-zA-Z]*)$""".r

    def units: List[List[Unit]] = Nil

    def findUnit(s: String): Option[Unit] = {
      Option(s).map(_.trim).filterNot(_.isEmpty).map { s =>
        (unity :: units.flatten).find(_.label.equalsIgnoreCase(s))
      }.getOrElse(Some(unity))
    }

    def toUnit(value: Long, unit: Unit, digits: Int = BigDecimals.SCALE_DIGITS_DEFAULT): BigDecimal = {
      BigDecimals.scale(BigDecimal(value) / unit.factor, digits)
    }

    def fromHumanReadable(value: String): Long = value match {
      case ValueRegexp(valueAmount, valueUnit) =>
        val lcunit = valueUnit.toLowerCase
        def get(units: List[Unit]): Option[Long] =
          units.find { unit =>
            unit.label.toLowerCase == lcunit
          }.map { unit =>
            valueAmount.toLong * unit.factor
          }

        if ((lcunit == "") || (lcunit == unityLabel.toLowerCase)) valueAmount.toLong
        else units.foldLeft(None:Option[Long]) { (result, units) =>
          result.orElse(get(units))
        }.getOrElse {
          throw new IllegalArgumentException(s"Invalid value[$valueAmount]")
        }

      case _ =>
        throw new IllegalArgumentException(s"Invalid value[$value]")
    }

    def toHumanReadable(value: Long, runits: List[Unit] = units.head, digits: Int = BigDecimals.SCALE_DIGITS_DEFAULT): String = {
      @scala.annotation.tailrec
      def loop(units: List[Unit]): Unit = units match {
        case head :: tail =>
          if (value < 2 * head.factor) loop(tail)
          else head

        case Nil =>
          unity
      }

      val unit = loop(runits.reverse)
      s"${toUnit(value, unit, digits)} ${unit.label}"
    }

    def format(value: Long): String = {
      def format(value: Long, units: List[Unit]): String = {
        @scala.annotation.tailrec
        def loop(units: List[Unit], unit: Unit): (Long, Unit) = units match {
          case head :: tail =>
            if ((value % head.factor) == 0) loop(tail, head)
            else (value, unit)

          case Nil =>
            (value, unity)
        }

        val (hr, unit) = loop(units, unity)
        s"${hr / unit.factor}${unit.label}"
      }

      units.map(units => format(value, units)).minBy(_.length)
    }

  }

  trait SI extends AbstractSystem {
    // Note: 'K' is reserved for 'Kelvin'
    val kilo = Unit(s"k${unity.label}", scaleSI)
    val mega = Unit(s"M${unity.label}", scaleSI * scaleSI)
    val giga = Unit(s"G${unity.label}", scaleSI * scaleSI * scaleSI)
    val tera = Unit(s"T${unity.label}", scaleSI * scaleSI * scaleSI * scaleSI)
    val peta = Unit(s"P${unity.label}", scaleSI * scaleSI * scaleSI * scaleSI * scaleSI)

    def units_SI: List[Unit] = List(kilo, mega, giga, tera, peta)

    override def units: List[List[Unit]] = super.units :+ units_SI

  }

  trait Binary extends AbstractSystem {
    val kibi = Unit(s"Ki${unity.label}", scaleBinary)
    val mebi = Unit(s"Mi${unity.label}", scaleBinary * scaleBinary)
    val gibi = Unit(s"Gi${unity.label}", scaleBinary * scaleBinary * scaleBinary)
    val tebi = Unit(s"Ti${unity.label}", scaleBinary * scaleBinary * scaleBinary * scaleBinary)
    val pebi = Unit(s"Pi${unity.label}", scaleBinary * scaleBinary * scaleBinary * scaleBinary * scaleBinary)

    def units_Binary: List[Unit] = List(kibi, mebi, gibi, tebi, pebi)

    override def units: List[List[Unit]] = super.units :+ units_Binary

  }

  // scalastyle:off object.name
  object storage
    extends AbstractSystem("B")
    with Binary
    with SI
  // scalastyle:off object.name

}
