package suiryc.scala.log

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.LoggerFactory
import suiryc.scala.io.SystemStreams


object Loggers {

  lazy private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]

  val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  /** Whether (root) logger uses console as output. */
  lazy private val useConsole: Boolean = {
    val it = rootLogger.iteratorForAppenders()
    @scala.annotation.tailrec
    def loop(): Boolean = {
      if (!it.hasNext) false
      else if (it.next.isInstanceOf[ConsoleAppender[_]]) true
      else loop()
    }
    loop()
  }

  /** Capture IO unless logger uses console. */
  def captureIo(): Option[SystemStreams] = {
    if (!useConsole) {
      Some {
        SystemStreams.replace(
          SystemStreams.loggerOutput("stdout"),
          SystemStreams.loggerOutput("stderr", error = true)
        )
      }
    } else {
      // scalastyle:off token
      System.out.println("Not capturing IO: logger uses console")
      // scalastyle:on token
      None
    }
  }

  /** Sets root logger level. */
  def setLevel(level: Level): Unit = {
    rootLogger.setLevel(level)
  }

  /** Sets root logger level. */
  def setLevel(level: String): Unit = {
    setLevel(Level.valueOf(level.toUpperCase))
  }

}
