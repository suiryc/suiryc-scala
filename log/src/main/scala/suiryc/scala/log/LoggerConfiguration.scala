package suiryc.scala.log

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.joran.event.SaxEvent
import ch.qos.logback.core.joran.spi.ConfigurationWatchList
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.status.StatusUtil
import ch.qos.logback.core.util.StatusPrinter
import com.typesafe.scalalogging.StrictLogging
import org.slf4j.LoggerFactory

import java.net.URL
import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

object LoggerConfiguration extends ContextAwareBase with StrictLogging {

  private var reconfigureListeners = List.empty[ReconfigureListener]

  private val contextOpt = Option(LoggerFactory.getILoggerFactory) match {
    case Some(context: LoggerContext) =>
      setContext(context)
      Some(context)

    case Some(v) =>
      // Belt and suspenders: we should be using logback
      logger.warn(s"Unhandled logger factory=<${v.getClass}>")
      None

    case None =>
      // Belt and suspenders: there *should* be a context
      logger.warn("No logger factory")
      None
  }

  def stop(): Unit = {
    withContext("stop logback")(_.stop())
  }

  def watch(period: FiniteDuration): Unit = {
    withContext("watch for logback configuration changes")(watch(period, _))
  }

  def reload(onChange: Boolean): Unit = {
    fireEnteredRunMethod()
    withContext("reload logback configuration") { context =>
      if (!onChange || changeDetected()) reload(context)
    }
  }

  private def getWatchList: Option[ConfigurationWatchList] =
    Option(ConfigurationWatchListUtil.getConfigurationWatchList(context))

  private def getMainURL: Option[URL] = getWatchList.flatMap(l => Option(l.getMainURL))

  private def changeDetected(): Boolean = getWatchList.exists(_.changeDetected())

  private def withContext(label: String)(f: LoggerContext => Unit): Unit = {
    contextOpt match {
      case Some(context) =>
        try {
          f(context)
        } catch {
          case ex: Exception =>
            logger.error(s"Failed to $label: ${ex.getMessage}", ex)
        }

      case None =>
        logger.warn(s"Cannot $label: no handled logger factory")
    }
  }

  private def watch(period: FiniteDuration, context: LoggerContext): Unit = {
    val internal = Option(context.getObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK)).nonEmpty
    if (internal) {
      logger.debug("Logback already reconfigures on change")
    } else {
      getMainURL match {
        case Some(url) if !url.toString.endsWith("xml") =>
          logger.debug("Cannot watch for logback configuration changes: only XML configuration is handled")

        case Some(url) =>
          // Since we access the filesystem, use the 'blocking' system even
          // though reloading should not take that long.
          import suiryc.scala.akka.CoreSystem.Blocking._
          addInfo(s"Will scan for changes in [$url] period [$period]")
          // Use the system scheduler (not monix) as it takes less time to start
          // and is good enough for what we need here.
          system.scheduler.scheduleWithFixedDelay(period, period) { () =>
            fireEnteredRunMethod()
            if (changeDetected()) {
              val threshold = System.currentTimeMillis
              addInfo("Detected change in configuration files.")
              addInfo(s"${CoreConstants.RESET_MSG_PREFIX}named [${context.getName}]")
              reload(context, threshold)
            }
          }
          ()

        case None =>
          logger.debug("Cannot watch for logback configuration changes: no main URL")
      }
    }
  }

  private def reload(context: LoggerContext, threshold: Long = System.currentTimeMillis): Unit = {
    logger.info("Reloading logback configuration")
    // This mimics automatic configuration reloading.
    // See: ch.qos.logback.classic.joran.ReconfigureOnChangeTask
    // Manual JoranConfiguration invoking also do some of this.
    // See: http://logback.qos.ch/manual/configuration.html#joranDirectly
    fireChangeDetected()
    val success = getMainURL match {
      case Some(url) if !url.toString.endsWith("xml") =>
        logger.warn("Cannot reload logback configuration: only XML configuration is handled")
        false

      case Some(url) =>
        val jc = new JoranConfigurator()
        jc.setContext(context)
        val statusUtil = new StatusUtil(context)
        val eventList  = jc.recallSafeConfiguration()
        context.reset()

        val fallback = try {
          jc.doConfigure(url)
          statusUtil.hasXMLParsingErrors(threshold)
        } catch {
          case _: Exception =>
            // Error details have been added to status and will be printed by
            // StatusPrinter.
            true
        }
        if (fallback) restoreSafeLogger(context, eventList)
        // We need to wait for safe logger to be restored (if needed) to be
        // sure the logger will log.
        // ErrorStatus.WARN (not accessible) = 1
        if (fallback || (statusUtil.getHighestLevel(threshold) >= 1)) {
          logger.warn("There were issues reloading logback configuration: see stdout/stderr for details")
        }
        // Explicitly print status after handling fallback, so that its issues
        // will also be printed if any.
        StatusPrinter.printInCaseOfErrorsOrWarnings(context, threshold)
        !fallback

      case None =>
        logger.warn("Cannot reload logback configuration: no main URL")
        false
    }
    fireDoneReconfiguring(success)
  }

  private def restoreSafeLogger(context: LoggerContext, eventList: java.util.List[SaxEvent]): Unit = {
    val failsafeEvents = eventList.asScala.toList.filterNot { event =>
      "include".equalsIgnoreCase(event.getLocalName)
    }.asJava
    val jc = new JoranConfigurator()
    jc.setContext(context)
    val oldCWL = ConfigurationWatchListUtil.getConfigurationWatchList(context)
    val newCWL = oldCWL.buildClone()

    if (Option(failsafeEvents).forall(_.isEmpty)) {
      addWarn("No previous configuration to fall back on.")
    } else {
      addWarn("Given previous errors, falling back to previously registered safe configuration.")
      try {
        context.reset()
        ConfigurationWatchListUtil.registerConfigurationWatchList(context, newCWL)
        jc.doConfigure(failsafeEvents)
        addInfo("Re-registering previous fallback configuration once more as a fallback configuration point")
        jc.registerSafeConfiguration(eventList)

        addInfo("after registerSafeConfiguration: " + eventList)
      } catch {
        case ex: Exception =>
          addError("Unexpected exception thrown by a configuration considered safe.", ex)
      }
    }
  }

  def addReconfigureListener(listener: ReconfigureListener): Unit = this.synchronized {
    reconfigureListeners :+= listener
  }

  private def fireEnteredRunMethod(): Unit = this.synchronized {
    reconfigureListeners.foreach(_.enteredRunMethod())
  }

  private def fireChangeDetected(): Unit = this.synchronized {
    reconfigureListeners.foreach(_.changeDetected())
  }

  private def fireDoneReconfiguring(success: Boolean): Unit = this.synchronized {
    reconfigureListeners.foreach(_.doneReconfiguring(success))
  }

  trait ReconfigureListener {
    def enteredRunMethod(): Unit = {}
    def changeDetected(): Unit = {}
    def doneReconfiguring(@unused success: Boolean): Unit = {}
  }

}
