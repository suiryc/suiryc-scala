package suiryc.scala.concurrent

import java.util.concurrent.{ThreadFactory, Executors, TimeUnit}
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.concurrent.duration.FiniteDuration

/**
 * Rich Future.
 *
 * Adds timeout feature.
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

}
