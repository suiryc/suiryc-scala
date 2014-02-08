package suiryc.scala.javafx.concurrent

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory


object JFXSystem {

  type Action = () => Unit

  val config = ConfigFactory.load().getConfig("suiryc.javafx").withFallback(ConfigFactory.load())
  val system = ActorSystem("javafx", config)
  val jfxActor = system.actorOf(Props[JFXActor].withDispatcher("suiryc.javafx.dispatcher"), "JavaFX-dispatcher")

  def schedule(action: => Unit) =
    jfxActor ! { () => action }

  class JFXActor extends Actor {

    override def receive = {
      case action: Action =>
        action()
    }

  }

}
