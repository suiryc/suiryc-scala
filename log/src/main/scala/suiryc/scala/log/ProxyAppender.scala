package suiryc.scala.log

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, ThrowableProxy}
import ch.qos.logback.core.AppenderBase
import com.typesafe.config.ConfigFactory
import suiryc.scala.misc.{MessageLevel, MessageWriter}


class ProxyAppender(_writers: Seq[MessageWriter] = Seq.empty, async: Boolean = false)
  extends AppenderBase[ILoggingEvent]
{

  protected var writers = _writers.toSet

  protected val actor: ActorRef =
    if (async) ProxyAppender.system.actorOf(Props(new ProxyActor))
    else null


  override def start() {
    super.start()
  }

  override def append(event: ILoggingEvent) {
    if (async) actor ! event
    else write(writers, event)
  }

  def addWriter(writer: MessageWriter) {
    if (async) actor ! AddWriter(writer)
    else writers += writer
  }

  def removeWriter(writer: MessageWriter) {
    if (async) actor ! RemoveWriter(writer)
    else writers -= writer
  }

  @inline private def write(writers: Set[MessageWriter], event: ILoggingEvent) {
    if (!writers.isEmpty) {
      val msg = event.getMessage
      val level = event.getLevel.levelInt match {
        case Level.TRACE_INT => MessageLevel.TRACE
        case Level.DEBUG_INT => MessageLevel.DEBUG
        case Level.INFO_INT => MessageLevel.INFO
        case Level.WARN_INT => MessageLevel.WARNING
        case Level.ERROR_INT => MessageLevel.ERROR
      }
      val throwable = event.getThrowableProxy() match {
        case p: ThrowableProxy =>
          Some(p.getThrowable())

        case _ =>
          None
      }

      writers foreach { writer =>
        writer.write(level, msg, throwable)
      }
    }
  }

  private case class AddWriter(writer: MessageWriter)
  private case class RemoveWriter(writer: MessageWriter)

  private class ProxyActor extends Actor {

    override def receive = proxy(writers.toSet)

    def proxy(writers: Set[MessageWriter]): Receive = {
      case event: ILoggingEvent =>
        write(writers, event)

      case AddWriter(writer) =>
        context.become(proxy(writers + writer))

      case RemoveWriter(writer) =>
        context.become(proxy(writers - writer))
    }

  }

}

object ProxyAppender {

  protected val specificConfig = ConfigFactory.load().getConfig("suiryc.log")

  val config = specificConfig.withFallback(ConfigFactory.load())

  protected val system = ActorSystem("suiryc-log-proxyappender", config)

}
