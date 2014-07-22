package suiryc.scala.io

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import suiryc.scala.akka.CoreSystem

/** Writer expecting full lines. */
trait LineWriter {

  /**
   * Write one line.
   *
   * Note: line must not contain line ending (CR and/or LF).
   */
  def write(line: String)

}


class ProxyLineWriter(_writers: Seq[LineWriter] = Seq.empty, async: Boolean = false)
 extends LineWriter
{

  protected var writers = _writers.toSet

  protected val system = CoreSystem.system
  protected val actor: ActorRef =
    if (async) system.actorOf(Props(new ProxyActor).withDispatcher("dispatcher"))
    else null


  override def write(line: String) {
    if (async) actor ! line
    else write(writers, line)
  }

  def addWriter(writer: LineWriter) {
    if (async) actor ! AddWriter(writer)
    else writers += writer
  }

  def removeWriter(writer: LineWriter) {
    if (async) actor ! RemoveWriter(writer)
    else writers -= writer
  }

  @inline private def write(writers: Set[LineWriter], line: String) {
    writers foreach(_.write(line))
  }

  private case class AddWriter(writer: LineWriter)
  private case class RemoveWriter(writer: LineWriter)

  private class ProxyActor extends Actor {

    override def receive = proxy(writers.toSet)

    def proxy(writers: Set[LineWriter]): Receive = {
      case line: String =>
        write(writers, line)

      case AddWriter(writer) =>
        context.become(proxy(writers + writer))

      case RemoveWriter(writer) =>
        context.become(proxy(writers - writer))
    }

  }

}
