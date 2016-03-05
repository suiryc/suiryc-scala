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

  /** An 'action' (which returns a future when triggered). */
  type Action[A] = () => Future[A]

  object Action {

    /** Builds an action from a by-name parameter. */
    def apply[A](action: => Future[A]): Action[A] =
      () => action

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
   * If stopping on error is required, any failed action prevent execution of
   * the following ones, and resulting Future contains the failure.
   * If not stopping on error
   *
   * @param stopOnError whether to stop on error
   * @param fs sequence of actions
   * @param ec execution context
   * @tparam A kind of action result
   * @return result of actions
   */
  def executeSequentially[A](stopOnError: Boolean, fs: Seq[Action[A]])(implicit ec: ExecutionContext): Future[Seq[A]] = {
    // Notes:
    // Since we may wish to keep on triggering futures even if a previous one
    // failed, we need to use a 'Try' as computation result.
    // If stopping on error, we simply combine the result of a previous
    // computation with the next one. If not stopping on error, we need to
    // recover each computation with its own Failure and propagate it.
    fs.foldLeft(Future.successful(Try(List.empty[A]))) { (acc, f) =>
      acc.flatMap {
        case Success(r1) =>
          // Combine new result with previous one.
          val f2 = f().map { r2 =>
            Success(r1 :+ r2)
          }
          if (stopOnError) {
            f2
          } else {
            // Recover Failure with itself.
            f2.recover {
              case ex: Throwable => Failure(ex)
            }
          }

        case failure: Failure[List[A]] =>
          // We can only be here if a previous computation failed but we are
          // not stopping on error. So trigger next computation but propagate
          // current Failure.
          f().map(_ => failure).recover { case _ => failure }
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
  def executeSequentially[A](stopOnError: Boolean, fs: Action[A]*)(implicit ec: ExecutionContext, d: DummyImplicit): Future[Seq[A]] =
    executeSequentially(stopOnError, fs)

}
