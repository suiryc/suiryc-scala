package suiryc.scala.javafx.concurrent

import akka.actor.{Actor, ActorRef, Props}
import grizzled.slf4j.Logging
import javafx.application.Platform
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}
import suiryc.scala.akka.CoreSystem


object JFXSystem
  extends Logging
{

  /* Note: Akka intercepts thrown exception inside JavaFX actor, thus no need to
   * try/catch when doing 'action'.
   */
  /* XXX - try/catch when doing 'action' inside JavaFX thread ? */

  protected case class Action(action: () => Unit)

  val config = CoreSystem.config.getConfig("javafx")
  protected val warnReentrant = config.getBoolean("system.warn-reentrant")
  protected val system = CoreSystem.system
  protected val jfxActor = newJFXActor(Props[JFXActor], "JavaFX-dispatcher")

  import system.dispatcher

  /** Create an actor using the JavaFX thread backed dispatcher. */
  def newJFXActor(props: Props): ActorRef =
    system.actorOf(props.withDispatcher("javafx.dispatcher"))

  /** Create an actor using the JavaFX thread backed dispatcher. */
  def newJFXActor(props: Props, name: String): ActorRef =
    system.actorOf(props.withDispatcher("javafx.dispatcher"), name)

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
    else jfxActor ! Action { () => action }
  }

  def schedule(initialDelay: FiniteDuration, interval: FiniteDuration)(action: => Unit) =
    system.scheduler.schedule(initialDelay, interval, jfxActor, Action { () => action })

  def scheduleOnce(delay: FiniteDuration)(action: => Unit) =
    system.scheduler.scheduleOnce(delay, jfxActor, Action { () => action })

  private class JFXActor extends Actor {

    override def receive = {
      case msg: Action =>
        msg.action()
    }

  }

}
