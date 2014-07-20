package suiryc.scala.misc


object MessageLevel extends Enumeration {

  import Enumeration._

  override protected val caseSensitive = false

  case class Value(name: String, shortName: String)
    extends BaseValue
    with Aliased
  {
    override val aliases = List(shortName)
  }

  val TRACE = Value("TRACE", "TRC")
  val DEBUG = Value("DEBUG", "DBG")
  val INFO = Value("INFO", "INF")
  val WARNING = Value("WARNING", "WRN")
  val ERROR = Value("ERROR", "ERR")

}
