package suiryc.scala.log

import akka.actor.{Actor, ActorRef, Props}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import suiryc.scala.akka.CoreSystem


class ProxyAppender(_writers: Seq[LogWriter] = Seq.empty, async: Boolean = false)
  extends AppenderBase[ILoggingEvent]
{

  protected var writers = _writers.toSet

  protected val system = CoreSystem.system
  protected val actor: ActorRef =
    if (async) system.actorOf(Props(new ProxyActor).withDispatcher("log.dispatcher"))
    else null


  override def start() {
    super.start()
  }

  override def append(event: ILoggingEvent) {
    if (async) actor ! event
    else write(writers, event)
  }

  def addWriter(writer: LogWriter) {
    if (async) actor ! AddWriter(writer)
    else writers += writer
  }

  def removeWriter(writer: LogWriter) {
    if (async) actor ! RemoveWriter(writer)
    else writers -= writer
  }

  @inline private def write(writers: Set[LogWriter], event: ILoggingEvent) {
    writers foreach { writer =>
      writer.write(event)
    }
  }

  private case class AddWriter(writer: LogWriter)
  private case class RemoveWriter(writer: LogWriter)

  private class ProxyActor extends Actor {

    override def receive = proxy(writers.toSet)

    def proxy(writers: Set[LogWriter]): Receive = {
      case event: ILoggingEvent =>
        write(writers, event)

      case AddWriter(writer) =>
        context.become(proxy(writers + writer))

      case RemoveWriter(writer) =>
        context.become(proxy(writers - writer))
    }

  }

}
