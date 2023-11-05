package suiryc.scala.log.status

import ch.qos.logback.core.BasicStatusManager
import ch.qos.logback.core.spi.{ContextAwareBase, LifeCycle}
import ch.qos.logback.core.status.{OnConsoleStatusListener, Status, StatusListener, StatusUtil}
import ch.qos.logback.core.util.StatusPrinter
import suiryc.scala.log.LoggerConfiguration
import suiryc.scala.log.LoggerConfiguration.ReconfigureListener

import java.lang.{StringBuilder => jStringBuilder}
import java.util.{LinkedList => jLinkedList}
import scala.concurrent.duration.FiniteDuration

// Notes:
// Based on ch.qos.logback.core.status.OnPrintStreamStatusListenerBase.
// We only react to events which level is above INFO.
object NoticeStatusListener {

  // Default history time to log (300ms as of logback 1.2.3).
  val DEFAULT_RETROSPECTIVE: Long = new OnConsoleStatusListener().getRetrospective

  // Default history size to keep (150 events as of logback 1.2.3).
  private val STATUS_LIST_SIZE = BasicStatusManager.TAIL_SIZE

  // Whether a listener was created, either programmatically ('start') or
  // through the logback configuration.
  var listening: Boolean = false

  // Latest reconfigure time mark: upon issue, we will only log status events
  // related to the reconfiguring.
  private var reconfigureTimeMark: Long = 0

  // Programmatically created listener.
  private var listener: Option[NoticeStatusListener] = None

  // Notes:
  // LoggerContext listeners can be added, but the onReset callback is
  // triggered before current status listeners are cleared, so we can't
  // re-register ourself through it.
  // Instead we use our own ReconfigureListener that is called after
  // reconfiguring: we can then re-register our status listener, and
  // print status events when applicable.
  // This only matters for programmatically created listeners: configured ones
  // will be automatically re-created and registered.

  private def setupListener(): Unit = this.synchronized {
    listener.foreach { listener =>
      listener.getContext.getStatusManager.add(listener)
      listener.init()
    }
  }

  LoggerConfiguration.addReconfigureListener(new ReconfigureListener {
    override def changeDetected(): Unit                    = reconfigureTimeMark = System.currentTimeMillis()
    override def doneReconfiguring(success: Boolean): Unit = setupListener()
  })

  def start(retrospective: FiniteDuration, prefix: Option[String]): Unit = this.synchronized {
    // Ensure we only start one listener here. If one was created through
    // configuration, it also matters.
    if (!listening && listener.isEmpty) {
      LoggerConfiguration.withContext("start status listener") { ctx =>
        // Note: when attaching the listener this way, it won't be explicitly
        // started/stopped, which is not a problem because we don't rely on it.
        val listener = new NoticeStatusListener
        listener.setLateInit()
        listener.setContext(ctx)
        listener.setRetrospective(retrospective.toMillis)
        prefix.foreach(listener.setPrefix)
        this.listener = Some(listener)
        listening = true
        setupListener()
      }
    }
  }

}

class NoticeStatusListener extends ContextAwareBase with StatusListener with LifeCycle {

  import NoticeStatusListener._

  private var retrospectiveThreshold: Long = NoticeStatusListener.DEFAULT_RETROSPECTIVE

  private var prefix: Option[String] = None

  // Whether we just attached this listener programmatically.
  private var lateInit: Boolean = false

  private var started: Boolean = false

  private val statusList = new jLinkedList[Status]()

  listening = true

  override def start(): Unit = {
    started = true
    init()
  }

  override def stop(): Unit = {
    started = false
  }

  def init(): Unit = {
    if (context != null) statusList.synchronized {
      var notice = false
      val events = context.getStatusManager.getCopyOfStatusList
      statusList.clear()
      StatusUtil.filterStatusListByTimeThreshold(events, reconfigureTimeMark).forEach { status =>
        statusList.add(status)
        if (status.getLevel > Status.INFO) notice = true
      }
      // See: https://logback.qos.ch/manual/configuration.html#automaticStatusPrinting
      // If we just attached this listener, logback did already print status
      // if there were issues. We only need to do it either because we were
      // set through configuration (in which case logback do not print status)
      // or were being reloaded.
      if (notice && !lateInit) printStatusList()
      lateInit = false
    }
  }

  override def isStarted: Boolean = started

  override def addStatusEvent(status: Status): Unit = {
    add(status)
    if (status.getLevel > Status.INFO) printStatusList()
  }

  private def add(status: Status): Unit = {
    val limit =
      if (retrospectiveThreshold <= 0) Long.MaxValue
      else status.getTimestamp - retrospectiveThreshold

    @scala.annotation.tailrec
    def clean(): Unit = {
      val first = statusList.peekFirst()
      if ((first != null) && ((first.getTimestamp < limit) || (statusList.size() == STATUS_LIST_SIZE))) {
        statusList.remove()
        clean()
      }
    }
    statusList.synchronized {
      clean()
      statusList.add(status)
      ()
    }
  }

  private def printStatus(status: Status): Unit = {
    val sb = new jStringBuilder
    prefix.foreach(sb.append)
    StatusPrinter.buildStr(sb, "", status)
    print(sb)
  }

  private def printStatusList(): Unit = {
    statusList.synchronized {
      statusList.forEach(printStatus)
      statusList.clear()
    }
  }

  def setRetrospective(retrospective: Long): Unit = {
    retrospectiveThreshold = retrospective
  }

  def getRetrospective: Long = retrospectiveThreshold

  def getPrefix: String = prefix.orNull

  def setPrefix(prefix: String): Unit = {
    this.prefix = Option(prefix)
  }

  def setLateInit(): Unit = lateInit = true

}
