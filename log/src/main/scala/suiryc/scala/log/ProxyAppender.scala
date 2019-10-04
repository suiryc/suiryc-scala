package suiryc.scala.log

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import suiryc.scala.akka.CoreSystem


class ProxyAppender(_writers: Seq[LogWriter] = Seq.empty, async: Boolean = false)
  extends AppenderBase[ILoggingEvent]
{

  protected var writers: Set[LogWriter] = _writers.toSet

  protected val system: ActorSystem = CoreSystem.system
  protected val actor: Option[ActorRef] =
    if (!async) None
    else Some(system.actorOf(Props(new ProxyActor).withDispatcher("log.dispatcher")))


  override def start(): Unit = {
    super.start()
  }

  override def append(event: ILoggingEvent): Unit = {
    actor.fold{
      write(writers, event)
    } {
      _ ! event
    }
  }

  def addWriter(writer: LogWriter): Unit = {
    actor.fold{
      writers += writer
    } {
      _ ! AddWriter(writer)
    }
  }

  def removeWriter(writer: LogWriter): Unit = {
    actor.fold{
      writers -= writer
    } {
      _ ! RemoveWriter(writer)
    }
  }

  @inline private def write(writers: Set[LogWriter], event: ILoggingEvent): Unit = {
    writers.foreach { writer =>
      writer.write(event)
    }
  }

  private case class AddWriter(writer: LogWriter)
  private case class RemoveWriter(writer: LogWriter)

  private class ProxyActor extends Actor {

    override def receive: Receive = proxy(writers)

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
