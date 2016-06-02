package suiryc.scala.concurrent

import java.util.concurrent.{ThreadFactory, Executors, TimeUnit}
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
 * Rich Future.
 *
 * Adds features like timeout or sequential execution.
 *
 * See: http://semberal.github.io/scala-future-timeout-patterns.html
 */
class RichFuture[A](val underlying: Future[A]) extends AnyVal {

  import RichFuture._

  def withTimeout(delay: Long, unit: TimeUnit): Future[A] = {
    // No need to do anything if given Future is already completed
    if (underlying.isCompleted) underlying
    else {
      val promise = Promise[A]()
      val cbTimeout = Runnable {
        // Nothing to do if given Future is already completed
        if (!promise.isCompleted) {
          promise.tryFailure(new TimeoutException(s"Future timeout ($delay $unit)"))
          ()
        }
      }
      promise.completeWith(underlying)
      scheduler.schedule(cbTimeout, delay, unit)
      promise.future
    }
  }

  def withTimeout(delay: FiniteDuration): Future[A] =
    withTimeout(delay.length, delay.unit)

}

object RichFuture {

  import scala.language.implicitConversions

  /**
   * An 'action'.
   *
   * Holds an input, and returns a future when triggered.
   */
  case class Action[A, +B](input: A, f: A => Future[B]) extends (() => Future[B]) {
    override def apply(): Future[B] = f(input)
  }

  object Action {

    /** Builds an action from a by-name parameter. */
    def apply[A](action: => Future[A]): Action[Unit, A] =
      Action((), (_: Unit) => action)

    /**
     * Builds an action from a by-name parameter and input value.
     *
     * This is a convenience function where the caller works with a code block
     * to which it wants to attach a context.
     * The context is used as input for the action, so that it can be retrieved
     * in the failed action result.
     */
    def apply[A, B](input: A, action: => Future[B]): Action[A, B] =
      Action(input, _ => action)

  }

  /** An action result. */
  sealed trait ActionTry[+A, +B] {
    protected val asTry: Try[B]
  }

  object ActionTry {
    /** Bring into context an implicit conversion to a Try instance. */
    implicit def toTry[A, B](a: ActionTry[A, B]): Try[B] = a.asTry
  }

  /** A succeeded action result. */
  case class ActionSuccess[+A, +B](input: A, value: B) extends ActionTry[A, B] {
    override lazy protected val asTry: Try[B] = Success(value)
  }

  /** A failed action result. */
  case class ActionFailure[+A, +B](input: A, exception: Throwable) extends ActionTry[A, B] {
    override lazy protected val asTry: Try[B] = Failure(exception)
  }

  // Note: we need a scheduler, and Java provides some.
  // But we don't want the created threads to prevent leaving application,
  // so create a simple factory - based on Java default one - and set created
  // thread as daemon.

  protected val defaultThreadFactory = Executors.defaultThreadFactory()

  protected class DaemonThreadFactory extends ThreadFactory {

    override def newThread(r: Runnable): Thread = {
      val thread = defaultThreadFactory.newThread(r)
      thread.setDaemon(true)
      thread
    }

  }

  protected val daemonThreadFactory = new DaemonThreadFactory()

  protected val scheduler = Executors.newScheduledThreadPool(1, daemonThreadFactory)

  /** Converts a Future to RichFuture. */
  implicit def futureToRichFuture[A](future: Future[A]): RichFuture[A] =
    new RichFuture[A](future)

  def withTimeout[A](future: Future[A], delay: Long, unit: TimeUnit): Future[A] =
    future.withTimeout(delay, unit)

  def withTimeout[A](future: Future[A], delay: FiniteDuration): Future[A] =
    future.withTimeout(delay.length, delay.unit)

  /**
   * Executes futures sequentially.
   *
   * Provided a sequence of actions (functions returning a Future), execute them
   * sequentially and return a sequence containing the results of each action.
   *
   * @param actions sequence of actions
   * @param ec execution context
   * @tparam A kind of action input
   * @tparam B kind of action result
   * @return result of actions
   */
  def executeSequentially[A, B](actions: Seq[Action[A, B]])(implicit ec: ExecutionContext): Future[Seq[ActionTry[A, B]]] =
    actions.foldLeft(Future.successful(List.empty[ActionTry[A, B]])) { (acc, action) =>
      acc.flatMap { r1 =>
        // Combine new result with previous one.
        action().map { r2 =>
          r1 :+ ActionSuccess(action.input, r2)
        }.recover {
          case ex: Exception => r1 :+ ActionFailure(action.input, ex)
        }
      }
    }

  /**
   * Executes futures sequentially.
   *
   * Vararg variant.
   */
  def executeSequentially[A, B](actions: Action[A, B]*)(implicit ec: ExecutionContext, d: DummyImplicit): Future[Seq[ActionTry[A, B]]] =
    executeSequentially(actions)

  /**
   * Executes futures sequentially.
   *
   * Provided a sequence of actions (functions returning a Future), execute them
   * sequentially and return a sequence containing the results of all actions.
   * If stopping on error is required, any failed action prevent execution of
   * the following ones, and resulting Future contains the failure.
   * If not stopping on error, all futures are executed, and result is available
   * once all executions are done.
   *
   * @param stopOnError whether to stop on error
   * @param actions sequence of actions
   * @param ec execution context
   * @tparam A kind of action result
   * @return result of actions
   */
  def executeAllSequentially[A](stopOnError: Boolean, actions: Seq[Action[Unit, A]])(implicit ec: ExecutionContext): Future[Seq[A]] = {
    // Notes:
    // Since we may wish to keep on triggering futures even if a previous one
    // failed, we need to use a 'Try' as computation result.
    // If stopping on error, we simply combine the result of a previous
    // computation with the next one. If not stopping on error, we need to
    // recover each computation with its own Failure and propagate it.
    actions.foldLeft(Future.successful(Try(List.empty[A]))) { (acc, action) =>
      acc.flatMap {
        case Success(r1) =>
          // Combine new result with previous one.
          val f2 = action().map { r2 =>
            Success(r1 :+ r2)
          }
          if (stopOnError) {
            f2
          } else {
            // Recover Failure with itself.
            f2.recover {
              case ex: Exception => Failure(ex)
            }
          }

        case failure: Failure[List[A]] =>
          // We can only be here if a previous computation failed but we are
          // not stopping on error. So trigger next computation but propagate
          // current Failure.
          action().map(_ => failure).recover { case _ => failure }
      }
    }.flatMap {
      case Success(r)  => Future.successful(r)
      case Failure(ex) => Future.failed(ex)
    }
  }

  /**
   * Executes futures sequentially.
   *
   * Vararg variant.
   */
  def executeAllSequentially[A](stopOnError: Boolean, actions: Action[Unit, A]*)(implicit ec: ExecutionContext, d: DummyImplicit): Future[Seq[A]] =
    executeAllSequentially(stopOnError, actions)

}
