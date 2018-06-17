package suiryc.scala.akka

import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

class ReaperSuite
  extends TestKit(ActorSystem("ReaperSuite"))
  with ImplicitSender
  with FunSuiteLike
  with BeforeAndAfterAll
{
  import Reaper._

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  test("Reaper should work") {
    // Some dummy actors
    val a = TestProbe()
    val b = TestProbe()
    val c = TestProbe()

    // Setup our reaper
    val reaper = system.actorOf(Props(new TestReaper(testActor)))
    reaper ! WatchMe(a.ref)
    reaper ! WatchMe(c.ref)

    // Make sure our actors do work
    a.ref ! "Working a"
    a.expectMsg("Working a")
    b.ref ! "Working b"
    b.expectMsg("Working b")

    // Stop the watched actors
    system.stop(a.ref)
    system.stop(c.ref)

    // The reaper should have something for us
    expectMsg("Dead")
    ()
  }

}

/**
 * Our test reaper. Sends the snooper a message when all the souls have been
 * reaped.
 */
class TestReaper(snooper: ActorRef) extends Reaper {

  def allSoulsReaped(): Unit = snooper ! "Dead"

}
