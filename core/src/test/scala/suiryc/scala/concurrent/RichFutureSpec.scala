package suiryc.scala.concurrent

import akka.actor.ActorSystem
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@RunWith(classOf[JUnitRunner])
class RichFutureSpec extends WordSpec with Matchers {

  import RichFuture._

  val system = ActorSystem("RichFutureSpec")
  import system.dispatcher

  "withTimeout" should {

    "not kick-in if future succeeds before timeout" in {
      val wrapper = new Wrapper(delay = 10.millis)
      val f = wrapper.futureExec().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      wrapper.executed shouldBe 1
      f.value shouldBe Some(Success(()))
    }

    "not kick-in if future fails before timeout" in {
      val wrapper = new Wrapper(success = false, delay = 10.millis)
      val f = wrapper.futureExec().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      wrapper.executed shouldBe 1
      val value = f.value
      value should not be empty
      value.get.isFailure shouldBe true
      value.get.asInstanceOf[Failure[Unit]].exception.getClass should not be classOf[TimeoutException]
    }

    "kick-in if future succeeds after timeout" in {
      val wrapper = new Wrapper(delay = 100.millis)
      val f = wrapper.futureExec().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      // Note future will be executed even if timeout was reached.
      val value = f.value
      value should not be empty
      value.get.isFailure shouldBe true
      value.get.asInstanceOf[Failure[Unit]].exception.getClass shouldBe classOf[TimeoutException]
    }

    "kick-in if future fails after timeout" in {
      val wrapper = new Wrapper(success = false, delay = 100.millis)
      val f = wrapper.futureExec().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      // Note future will be executed even if timeout was reached.
      val value = f.value
      value should not be empty
      value.get.isFailure shouldBe true
      value.get.asInstanceOf[Failure[Unit]].exception.getClass shouldBe classOf[TimeoutException]
    }

  }

  "executeSequentially" should {

    "execute successful tests sequentially" in {
      testWrappers(stopOnError = true, List(
        new Wrapper(),
        new Wrapper(delay = 50.millis),
        new Wrapper()
      ))
    }

    "execute tests sequentially until failure if stopping on error" in {
      testWrappers(stopOnError = true, List(
        new Wrapper(),
        new Wrapper(delay = 50.millis),
        new Wrapper(success = false),
        new Wrapper(),
        new Wrapper(success = false, delay = 50.millis),
        new Wrapper(),
        new Wrapper(success = false),
        new Wrapper(success = false),
        new Wrapper()
      ))
    }

    "execute all tests sequentially even upon failure if not stopping on error" in {
      testWrappers(stopOnError = false, List(
        new Wrapper(),
        new Wrapper(delay = 50.millis),
        new Wrapper(success = false),
        new Wrapper(),
        new Wrapper(success = false, delay = 50.millis),
        new Wrapper(),
        new Wrapper(success = false),
        new Wrapper(success = false),
        new Wrapper()
      ))
    }

  }

  def testWrappers(stopOnError: Boolean, wrappers: List[Wrapper]): Unit = {
    // If a wrapper is processed, it should have been executed only once and
    // have coherent start/end times.
    def checkProcessed(wrapper: Wrapper): Unit = {
      wrapper.executed shouldBe 1
      assert(wrapper.start > 0)
      wrapper.end shouldBe >=(wrapper.start)
    }

    // Execute sequentially and wait for result.
    val f = executeSequentially(stopOnError, wrappers.map(_.futureExec): _*)
    Await.ready(f, 2.seconds)

    wrappers.foldLeft(Option.empty[Wrapper]) { (previousOpt, wrapper) =>
      previousOpt.map { previous =>
        if (previous.success || !stopOnError) {
          // If previous wrapper was successful or we do not stop on error, the
          // current wrapper should have been processed after it.
          checkProcessed(wrapper)
          wrapper.end shouldBe >=(previous.end)
          wrapper
        } else {
          // If a previous wrapper failed (and we do stop on error), the
          // current wrapper should not have been processed.
          wrapper.executed shouldBe 0
          wrapper.start shouldBe 0
          wrapper.end shouldBe 0
          // Keep 'previous' to check none of the following wrappers was
          // processed.
          previous
        }
      }.orElse {
        // First wrapper should have been processed
        checkProcessed(wrapper)
        Some(wrapper)
      }
    }
    ()
  }

  type FutureExec = () => Future[Unit]

  class Wrapper(val success: Boolean = true, delay: FiniteDuration = Duration.Zero) {

    private val promise = Promise[Unit]()

    // How many times the wrapper was executed
    var executed = 0
    // Start execution time
    var start: Long = 0L
    // End execution time
    var end: Long = 0L

    val futureExec: FutureExec = () => {
      executed += 1
      start = System.currentTimeMillis
      if (delay.length > 0) {
        system.scheduler.scheduleOnce(delay) {
          complete()
        }
      } else {
        complete()
      }
      promise.future
    }

    private def complete(): Unit = {
      end = System.currentTimeMillis
      if (success) {
        promise.success(())
      } else {
        promise.failure(new Exception)
      }
      ()
    }

  }

}
