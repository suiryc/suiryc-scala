package suiryc.scala.log

import ch.qos.logback.classic.{Level, PatternLayout}
import ch.qos.logback.classic.spi.ILoggingEvent
import suiryc.scala.io.LineWriter


/** Writer expecting log events. */
trait LogWriter {

  def write(event: ILoggingEvent): Unit

}

trait ThresholdLogWriter
  extends LogWriter
{

  protected var threshold: Level = Level.TRACE

  def setThreshold(level: Level): Unit = {
    threshold = level
  }

  abstract override def write(event: ILoggingEvent): Unit = {
    if (event.getLevel.isGreaterOrEqual(threshold)) super.write(event)
  }

}

/** Log writer using pattern layout. */
trait LogLinePatternWriter
  extends LogWriter
  with LineWriter
{

  protected val patternLayout = new PatternLayout()
  patternLayout.setContext(Loggers.loggerContext)
  patternLayout.start()

  def setPattern(pattern: String): Unit = {
    patternLayout.stop()
    patternLayout.setPattern(pattern)
    patternLayout.start()
  }

  override def write(event: ILoggingEvent): Unit = {
    write(patternLayout.doLayout(event).stripLineEnd)
  }

}

trait ThresholdLogLinePatternWriter
  extends LogLinePatternWriter
  with ThresholdLogWriter
