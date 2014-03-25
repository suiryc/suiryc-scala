package suiryc.scala.javafx.concurrent

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import grizzled.slf4j.Logging
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}
import scalafx.application.Platform


object JFXSystem
  extends Logging
{

  /* XXX - try/catch when doing 'action' inside JavaFX thread ? */

  type Action = () => Unit

  protected val specificConfig = ConfigFactory.load().getConfig("suiryc.javafx")
  protected val warnReentrant = specificConfig.getBoolean("system.warn-reentrant")

  protected val config = specificConfig.withFallback(ConfigFactory.load())
  protected val system = ActorSystem("javafx", config)
  protected val jfxActor = system.actorOf(Props[JFXActor].withDispatcher("suiryc.javafx.dispatcher"), "JavaFX-dispatcher")

  import system.dispatcher

  @inline protected def reentrant() {
    if (warnReentrant) {
      val throwable = new Exception("Already using JavaFX thread")
      warn("Caller delegating action to JavaFX thread while already using it",
        throwable)
    }
  }

  def await[T](action: => T, logReentrant: Boolean = true): T = {
    if (Platform.isFxApplicationThread) {
      /* We are already in the JavaFX thread. So *DO NOT* create a Future to
       * await on it otherwise we will block the application: the actor uses
       * the same thread.
       */
      if (logReentrant) reentrant()
      action
    }
    else {
      val f = Future {
        action
      } (JFXExecutor.executor)

      Await.ready(f, Duration.Inf).value match {
        case None =>
          /* should not happen */
          throw new Exception("Awaited Future not ready")

        case Some(Failure(ex)) =>
          throw ex

        case Some(Success(v)) =>
          v
      }
    }
  }

  def schedule(action: => Unit, logReentrant: Boolean = true) = {
    if (Platform.isFxApplicationThread) {
      if (logReentrant) reentrant()
      action
    }
    else jfxActor ! { () => action }
  }

  def schedule(initialDelay: FiniteDuration, interval: FiniteDuration)(action: => Unit) =
    system.scheduler.schedule(initialDelay, interval, jfxActor, { () => action })

  def scheduleOnce(delay: FiniteDuration)(action: => Unit) =
    system.scheduler.scheduleOnce(delay, jfxActor, { () => action })

  private class JFXActor extends Actor {

    override def receive = {
      case action: Action =>
        action()
    }

  }

}
