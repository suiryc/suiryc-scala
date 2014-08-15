package suiryc.scala.log

import ch.qos.logback.classic.{Level, LoggerContext}
import org.slf4j.LoggerFactory


object Loggers {

  val loggerContext  = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  /** Sets root logger level. */
  def setLevel(level: Level) {
    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[ch.qos.logback.classic.Logger]
    rootLogger.setLevel(level)
  }

  /** Sets root logger level. */
  def setLevel(level: String) {
    setLevel(Level.valueOf(level.toUpperCase))
  }

}
