package suiryc.scala.javafx.concurrent

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.FiniteDuration


object JFXSystem {

  type Action = () => Unit

  val config = ConfigFactory.load().getConfig("suiryc.javafx").withFallback(ConfigFactory.load())
  val system = ActorSystem("javafx", config)
  val jfxActor = system.actorOf(Props[JFXActor].withDispatcher("suiryc.javafx.dispatcher"), "JavaFX-dispatcher")

  import system.dispatcher

  def schedule(action: => Unit) =
    jfxActor ! { () => action }

  def schedule(initialDelay: FiniteDuration, interval: FiniteDuration)(action: => Unit) =
    system.scheduler.schedule(initialDelay, interval, jfxActor, { () => action })

  def scheduleOnce(delay: FiniteDuration)(action: => Unit) =
    system.scheduler.scheduleOnce(delay, jfxActor, { () => action })

  class JFXActor extends Actor {

    override def receive = {
      case action: Action =>
        action()
    }

  }

}
