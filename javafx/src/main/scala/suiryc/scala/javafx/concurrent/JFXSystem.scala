package suiryc.scala.javafx.concurrent

import akka.actor.{Actor, ActorRef, Cancellable, Props, Terminated}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import java.util.concurrent.TimeUnit
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

  // Notes:
  // We don't want the (core) system to prevent the JVM from exiting. Thus its
  // threads are (configuration) made daemonic so that the application don't
  // have to explicitly terminate it.
  // But there is an additional issue when dealing with JavaFX: when its thread
  // is stopped (exit) actors assigned to the special dispatcher won't work
  // anymore; in particular they cannot be properly stopped anymore.
  // By default, akka systems are automatically terminated upon JVM exit through
  // a shutdown hook (with a 10s timeout).
  // See: https://doc.akka.io/docs/akka/current/actors.html#coordinated-shutdown
  // So in this case the actual JVM exit is delayed until timeout.
  // There are two ways to prevent this:
  // 1. Explicitly stop those actors (or more generally terminate the system)
  //   before exiting JavaFX; see 'gracefulStop' and 'terminate'
  // 2. Disable the automatic system termination for the (core) system
  // As for daemon threads, 2. is done through configuration by default. The
  // application can still override this behaviour and/or explicitly terminate
  // the actors and system before exiting JavaFX when needed.
  //
  // Akka intercepts thrown exception inside JavaFX actor, thus no need to
  // try/catch when doing 'action'.

  /** Message to delegate action. */
  protected case class Action(action: () => Unit)

  /** JavaJX configuration ('javafx' path relative to core config). */
  val config: Config = CoreSystem.config.getConfig("javafx")
  /** Whether to warn if requesting to schedule action while already in JavaFX thread. */
  private val warnReentrant = config.getBoolean("system.warn-reentrant")
  /** Akka system. */
  private val system = CoreSystem.system
  /** JavaFX actor to which actions are delegated. */
  private val jfxActor = newJFXActor(Props[JFXActor], "JavaFX-dispatcher")

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

  /** The default (configured) graceful stop timeout. */
  lazy val gracefulStopTimeout: FiniteDuration =
    FiniteDuration(config.getDuration("system.graceful-stop.timeout").toMillis, TimeUnit.MILLISECONDS)

  /** Gracefully stops the internal "JavaFX actor". */
  def gracefulStop(timeout: FiniteDuration): Future[Boolean] = {
    akka.pattern.gracefulStop(jfxActor, timeout)
  }

  /** Terminates the (core) system with JavaFX actors, then JavaFX. */
  def terminate(): Future[Terminated] = {
    system.terminate().map { v =>
      Platform.exit()
      v
    }
  }

  private class JFXActor extends Actor {

    override def receive: Receive = {
      case msg: Action =>
        msg.action()
    }

  }

}
