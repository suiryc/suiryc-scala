package suiryc.scala.javafx.concurrent

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javafx.application.Platform
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}
import suiryc.scala.akka.CoreSystem

/**
 * JavaFX concurrent helpers.
 *
 * Mainly delegating action to a dedicated actor running inside the JavaFX
 * thread.
 */
object JFXSystem
  extends StrictLogging
{

  // Note: Akka intercepts thrown exception inside JavaFX actor, thus no need to
  // try/catch when doing 'action'.

  // TODO - try/catch when doing 'action' inside JavaFX thread ?

  /** Message to delegate action. */
  protected case class Action(action: () => Unit)

  /** JavaJX configuration ('javafx' path relative to core config). */
  val config: Config = CoreSystem.config.getConfig("javafx")
  /** Whether to warn if requesting to schedule action while already in JavaFX thread. */
  protected val warnReentrant = config.getBoolean("system.warn-reentrant")
  /** Akka system. */
  protected val system = CoreSystem.system
  /** JavaFX actor to which actions are delegated. */
  protected val jfxActor = newJFXActor(Props[JFXActor], "JavaFX-dispatcher")

  import system.dispatcher

  /** Creates an actor using the JavaFX thread backed dispatcher. */
  def newJFXActor(props: Props): ActorRef =
    system.actorOf(props.withDispatcher("javafx.dispatcher"))

  /** Creates an actor using the JavaFX thread backed dispatcher. */
  def newJFXActor(props: Props, name: String): ActorRef =
    system.actorOf(props.withDispatcher("javafx.dispatcher"), name)

  @inline protected def reentrant() {
    if (warnReentrant) {
      val throwable = new Exception("Already using JavaFX thread")
      logger.warn("Caller delegating action to JavaFX thread while already using it",
        throwable)
    }
  }

  /**
   * Delegates action to JavaFX using Platform.runLater.
   *
   * Purposely does not perform the action synchronously if we already are in
   * the JavaFX thread.
   * Execution may be performed before other actions delegated through dedicated
   * actor.
   */
  def runLater(action: => Unit): Unit =
    Platform.runLater(() => action)

  /** Delegates action to JavaFX using a Future, and waits for result. */
  def await[T](action: => T, logReentrant: Boolean = true): T = {
    if (Platform.isFxApplicationThread) {
      // We are already in the JavaFX thread. So *DO NOT* create a Future to
      // await on it otherwise we will block the application: the actor uses
      // the same thread.
      if (logReentrant) reentrant()
      action
    }
    else {
      val f = Future {
        action
      } (JFXExecutor.executor)

      Await.ready(f, Duration.Inf).value match {
        case None =>
          // should not happen
          throw new Exception("Awaited Future not ready")

        case Some(Failure(ex)) =>
          throw ex

        case Some(Success(v)) =>
          v
      }
    }
  }

  /**
   * Delegates action to JavaFX using dedicated actor.
   *
   * Unless requested to execute 'later' (i.e. not synchronously), executes the
   * action right away if we are in the JavaFX thread.
   */
  def schedule(action: => Unit, later: Boolean = false, logReentrant: Boolean = true): Unit = {
    if (!later && Platform.isFxApplicationThread) {
      if (logReentrant) reentrant()
      action
    }
    else jfxActor ! Action { () => action }
  }

  /** Delegates periodic action to JavaFX using dedicated actor. */
  def schedule(initialDelay: FiniteDuration, interval: FiniteDuration)(action: => Unit): Cancellable =
    system.scheduler.schedule(initialDelay, interval, jfxActor, Action { () => action })

  /** Delegates delayed action to JavaFX using dedicated actor. */
  def scheduleOnce(delay: FiniteDuration)(action: => Unit): Cancellable =
    system.scheduler.scheduleOnce(delay, jfxActor, Action { () => action })

  private class JFXActor extends Actor {

    override def receive: Receive = {
      case msg: Action =>
        msg.action()
    }

  }

}
