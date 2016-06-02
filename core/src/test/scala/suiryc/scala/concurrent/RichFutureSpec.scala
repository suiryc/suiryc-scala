package suiryc.scala.concurrent

import akka.actor.ActorSystem
import java.util.UUID
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.{Await, Promise, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// scalastyle:off magic.number
@RunWith(classOf[JUnitRunner])
class RichFutureSpec extends WordSpec with Matchers {

  import RichFuture._

  val system = ActorSystem("RichFutureSpec")
  import system.dispatcher

  "withTimeout" should {

    "not kick-in if future succeeds before timeout" in {
      val wrapper = new Wrapper(delay = 10.millis)
      val f = wrapper.action().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      wrapper.executed shouldBe 1
      f.value shouldBe Some(Success(wrapper.id))
    }

    "not kick-in if future fails before timeout" in {
      val wrapper = new Wrapper(success = false, delay = 10.millis)
      val f = wrapper.action().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      wrapper.executed shouldBe 1
      val value = f.value
      value should not be empty
      value.get.isFailure shouldBe true
      value.get.asInstanceOf[Failure[Unit]].exception.getClass should not be classOf[TimeoutException]
    }

    "kick-in if future succeeds after timeout" in {
      val wrapper = new Wrapper(delay = 100.millis)
      val f = wrapper.action().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      // Note future will be executed even if timeout was reached.
      val value = f.value
      value should not be empty
      value.get.isFailure shouldBe true
      value.get.asInstanceOf[Failure[Unit]].exception.getClass shouldBe classOf[TimeoutException]
    }

    "kick-in if future fails after timeout" in {
      val wrapper = new Wrapper(success = false, delay = 100.millis)
      val f = wrapper.action().withTimeout(50.millis)
      Await.ready(f, 2.seconds)
      // Note future will be executed even if timeout was reached.
      val value = f.value
      value should not be empty
      value.get.isFailure shouldBe true
      value.get.asInstanceOf[Failure[Unit]].exception.getClass shouldBe classOf[TimeoutException]
    }

  }

  "ActionTry" should {

    "have an implicit conversion to Try" in {
      // We merely check that some 'Try' functions are accessible thanks to the
      // implicit function defined in the ActionTry companion object.
      val exception = new Exception
      val actionFailure = ActionFailure(1, exception)
      val actionSuccess = ActionSuccess(1, 2)

      actionFailure.isFailure shouldBe true
      actionFailure.isSuccess shouldBe false
      actionFailure.failed shouldBe Success(exception)

      actionSuccess.isFailure shouldBe false
      actionSuccess.isSuccess shouldBe true
      actionSuccess.failed.isFailure shouldBe true
      actionSuccess.failed.failed.isSuccess shouldBe true
      actionSuccess.failed.failed.get shouldBe a[UnsupportedOperationException]
    }

  }

  "executeSequentially" should {

    "execute successful tests sequentially" in {
      testWrappers(List(
        new Wrapper(),
        new Wrapper(delay = 50.millis),
        new Wrapper()
      ))
    }

    "execute tests sequentially even with failures" in {
      testWrappers(List(
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

  "executeAllSequentially" should {

    "execute successful tests sequentially" in {
      testWrappersAll(stopOnError = true, List(
        new Wrapper(),
        new Wrapper(delay = 50.millis),
        new Wrapper()
      ))
    }

    "execute tests sequentially until failure if stopping on error" in {
      testWrappersAll(stopOnError = true, List(
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
      testWrappersAll(stopOnError = false, List(
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

  def checkProcessed(wrapper: Wrapper, previous: Option[Wrapper] = None): Unit = {
    // If a wrapper is processed, it should have been executed only once and
    // have coherent start/end times.
    wrapper.executed shouldBe 1
    assert(wrapper.start > 0)
    wrapper.end shouldBe >=(wrapper.start)
    // If previous one was executed, the newt one should have been after it.
    previous.foreach { previous =>
      if (previous.executed != 0) {
        wrapper.end shouldBe >=(previous.end)
      }
    }
  }

  def testWrappers(wrappers: List[Wrapper]): Unit = {
    // Execute sequentially and wait for result.
    val f = executeSequentially(wrappers.map(_.action))
    val r = Await.ready(f, 2.seconds).value.get
    val expected = wrappers.map { wrapper =>
      if (wrapper.success) ActionSuccess(wrapper.id, wrapper.id)
      else ActionFailure(wrapper.id, wrapper.failure)
    }

    r shouldBe Success(expected)
    wrappers.foldLeft(Option.empty[Wrapper]) { (previousOpt, wrapper) =>
      previousOpt.map { previous =>
        checkProcessed(wrapper, Some(previous))
        wrapper
      }.orElse {
        // First wrapper should have been processed
        checkProcessed(wrapper)
        Some(wrapper)
      }
    }
    ()
  }

  def testWrappersAll(stopOnError: Boolean, wrappers: List[Wrapper]): Unit = {
    // Execute sequentially and wait for result.
    val f = executeAllSequentially(stopOnError, wrappers.map(w => w.action.asInstanceOf[Action[Unit, UUID]]))
    val r = Await.ready(f, 2.seconds).value.get

    if (wrappers.forall(_.success)) {
      r shouldBe Success(wrappers.map(_.id))
    } else {
      r shouldBe Failure(wrappers.find(!_.success).get.failure)
    }
    wrappers.foldLeft(Option.empty[Wrapper]) { (previousOpt, wrapper) =>
      previousOpt.map { previous =>
        if (previous.success || !stopOnError) {
          checkProcessed(wrapper, Some(previous))
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

  class Wrapper(val success: Boolean = true, delay: FiniteDuration = Duration.Zero) {

    // Identifies this wrapper
    val id = UUID.randomUUID()

    // The underlying promise used to build the Future response
    private val promise = Promise[UUID]()
    // The actual exception in case of failure
    lazy val failure = new Exception(id.toString)

    // How many times the wrapper was executed
    var executed = 0
    // Start execution time
    var start: Long = 0L
    // End execution time
    var end: Long = 0L

    val action = Action(id, {
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
    })

    private def complete(): Unit = {
      end = System.currentTimeMillis
      if (success) {
        promise.success(id)
      } else {
        promise.failure(failure)
      }
      ()
    }

  }

}
// scalastyle:on magic.number
