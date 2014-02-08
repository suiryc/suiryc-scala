package suiryc.scala.misc


object MessageLevel extends Enumeration {

  sealed trait LevelValue extends Value {
    val shortName: String
  }

  private def levelValue(short: String) =
    new Val with LevelValue {
      val shortName = short
    }

  val TRACE = levelValue("TRC")
  val DEBUG = levelValue("DBG")
  val INFO = levelValue("INF")
  val WARNING = levelValue("WRN")
  val ERROR = levelValue("ERR")

}
