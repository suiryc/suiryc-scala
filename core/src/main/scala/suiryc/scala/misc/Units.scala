package suiryc.scala.misc


/* XXX - handle time system (separate for human readable form, or introduce 'cumulative' notion ?) */
/* XXX - handle floating points in human representation ? */
object Units {

  case class Unit(label: String, factor: Long)

  abstract class AbstractSystem(unityLabel: String) {

    val unity = Unit(unityLabel, 1)

    private val ValueRegexp = """^([0-9]*)\s*([a-zA-Z]*)$""".r

    def units: List[List[Unit]] = Nil

    def fromHumanReadable(value: String): Long = value match {
      case ValueRegexp(value, valueUnit) =>
        val lcunit = valueUnit.toLowerCase()
        def get(units: List[Unit]): Option[Long] =
          units find { unit =>
            unit.label.toLowerCase == lcunit
          } map { unit =>
            value.toLong * unit.factor
          }

        if ((lcunit == "") || (lcunit == unityLabel.toLowerCase)) value.toLong
        else units.foldLeft(None:Option[Long]) { (result, units) =>
          result orElse get(units)
        } getOrElse(
          throw new IllegalArgumentException(s"Invalid value[$value]")
        )

      case _ =>
        throw new IllegalArgumentException(s"Invalid value[$value]")
    }

    def toHumanReadable(value: Long, runits: List[Unit] = units.head) = {
      @scala.annotation.tailrec
      def loop(units: List[Unit]): (Long, Unit) = units match {
        case head :: tail =>
          if (value < 2 * head.factor) loop(tail)
          else (value, head)

        case Nil =>
          (value, unity)
      }

      val (hr, unit) = loop(runits.reverse)
      s"${hr / unit.factor} ${unit.label}"
    }

  }

  trait SI extends AbstractSystem {
    /* Note: 'K' is reserved for 'Kelvin' */
    val kilo = Unit(s"k${unity.label}", 1000L)
    val mega = Unit(s"M${unity.label}", 1000L * 1000L)
    val giga = Unit(s"G${unity.label}", 1000L * 1000L * 1000L)
    val tera = Unit(s"T${unity.label}", 1000L * 1000L * 1000L * 1000L)
    val peta = Unit(s"P${unity.label}", 1000L * 1000L * 1000L * 1000L * 1000L)

    def units_SI = List(kilo, mega, giga, tera, peta)

    override def units = super.units :+ units_SI

  }

  trait Binary extends AbstractSystem {
    val kibi = Unit(s"Ki${unity.label}", 1024L)
    val mebi = Unit(s"Mi${unity.label}", 1024L * 1024L)
    val gibi = Unit(s"Gi${unity.label}", 1024L * 1024L * 1024L)
    val tebi = Unit(s"Ti${unity.label}", 1024L * 1024L * 1024L * 1024L)
    val pebi = Unit(s"Pi${unity.label}", 1024L * 1024L * 1024L * 1024L * 1024L)

    def units_Binary = List(kibi, mebi, gibi, tebi, pebi)

    override def units = super.units :+ units_Binary

  }

  object storage
    extends AbstractSystem("B")
    with Binary
    with SI

}
