package suiryc.scala.log

import ch.qos.logback.classic.Level
import suiryc.scala.misc.Enumeration


object LogLevel extends Enumeration {

  import Enumeration._

  override protected val caseSensitive = false

  case class Value(name: String, shortName: String, level: Level)
    extends BaseValue
    with Aliased
  {
    override val aliases = List(shortName)
  }

  val TRACE = Value("TRACE", "TRC", Level.TRACE)
  val DEBUG = Value("DEBUG", "DBG", Level.DEBUG)
  val INFO = Value("INFO", "INF", Level.INFO)
  val WARNING = Value("WARNING", "WRN", Level.WARN)
  val ERROR = Value("ERROR", "ERR", Level.ERROR)

}
