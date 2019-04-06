package suiryc.scala.log

import ch.qos.logback.classic.Level
import suiryc.scala.misc.{CaseInsensitiveEnumeration, EnumerationWithAliases}

/**
 * Logback log level enumeration.
 *
 * Has short name aliases and case insensitive matching.
 */
object LogLevel extends EnumerationWithAliases with CaseInsensitiveEnumeration {

  // Notes:
  // We cannot override the original 'Value' class, but still shadow it for
  // easier usage (we only have such values in this enumeration).
  // We also cannot properly override 'values', so define 'levels' instead.

  class Value(name: String, shortName: String, val level: Level)
    extends ValWithAliases(name, Seq(shortName))

  private def newLevel(name: String, shortName: String, level: Level): Value =
    new Value(name, shortName,level)

  def levels: Set[Value] = values.asInstanceOf[Set[Value]]

  val TRACE = newLevel("TRACE", "TRC", Level.TRACE)
  val DEBUG = newLevel("DEBUG", "DBG", Level.DEBUG)
  val INFO = newLevel("INFO", "INF", Level.INFO)
  val WARNING = newLevel("WARNING", "WRN", Level.WARN)
  val ERROR = newLevel("ERROR", "ERR", Level.ERROR)

}
