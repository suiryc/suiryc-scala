package suiryc.scala.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.{Failure, Success}

class UsingSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  "Using" when {

    "handling one resource" should {

      "return result when there is no issue" in {
        val resource = new DummyResource()
        val r = Using(resource) { _ =>
          1
        }
        r shouldBe Success(1)
        Dummy.get shouldBe List(resource)
      }

      "return issue when code fails" in {
        val resource = new DummyResource()
        val ex = new Exception
        val r = Using(resource) { _ =>
          throw ex
        }
        r shouldBe Failure(ex)
        Dummy.get shouldBe List(resource)
      }

      "return closing issue when code succeeds" in {
        val ex = new Exception
        val resource = new DummyResource(fail = Some(ex))
        val r = Using(resource) { _ =>
        }
        r shouldBe Failure(ex)
        Dummy.get shouldBe List(resource)
      }

      "return code issue even if closing fails" in {
        val ex1 = new Exception
        val resource = new DummyResource(fail = Some(ex1))
        val ex2 = new Exception
        val r = Using(resource) { _ =>
          throw ex2
        }
        r shouldBe Failure(ex2)
        Dummy.get shouldBe List(resource)
      }

      "handle resource creation issue" in {
        val ex = new Exception
        def resource: AutoCloseable = throw ex
        val r = Using(resource) { _ =>
        }
        r shouldBe Failure(ex)
        Dummy.get shouldBe empty
      }

    }

    "managing multiple resources" should {

      "return result when there is no issue" in {
        val resource1 = new DummyResource()
        val resource2 = new DummyResource()
        val resource3 = new DummyResource()
        val r = Using.Manager { use =>
          use(resource1)
          use(resource2)
          use(resource3)
          1
        }
        r shouldBe Success(1)
        Dummy.get shouldBe List(resource3, resource2, resource1)
      }

      "return issue when code fails" in {
        val resource1 = new DummyResource()
        val resource2 = new DummyResource()
        val resource3 = new DummyResource()
        val ex = new Exception
        val r = Using.Manager { use =>
          use(resource1)
          use(resource2)
          use(resource3)
          throw ex
        }
        r shouldBe Failure(ex)
        Dummy.get shouldBe List(resource3, resource2, resource1)
      }

      "only close managed resources" in {
        val ex = new Exception
        val resource1 = new DummyResource()
        def resource2: AutoCloseable = throw ex
        val resource3 = new DummyResource()
        val r = Using.Manager { use =>
          use(resource1)
          use(resource2)
          use(resource3)
        }
        r shouldBe Failure(ex)
        Dummy.get shouldBe List(resource1)
      }

      "return closing issue when code succeeds" in {
        val ex1 = new Exception
        val ex2 = new Exception
        val ex3 = new Exception
        val resource1 = new DummyResource(Some(ex1))
        val resource2 = new DummyResource(Some(ex2))
        val resource3 = new DummyResource(Some(ex3))
        val r = Using.Manager { use =>
          use(resource1)
          use(resource2)
          use(resource3)
        }
        r shouldBe Failure(ex3)
        Dummy.get shouldBe List(resource3, resource2, resource1)
      }

      "return code issue even if closing fails" in {
        val ex = new Exception
        val ex1 = new Exception
        val ex2 = new Exception
        val ex3 = new Exception
        val resource1 = new DummyResource(Some(ex1))
        val resource2 = new DummyResource(Some(ex2))
        val resource3 = new DummyResource(Some(ex3))
        val r = Using.Manager { use =>
          use(resource1)
          use(resource2)
          use(resource3)
          throw ex
        }
        r shouldBe Failure(ex)
        Dummy.get shouldBe List(resource3, resource2, resource1)
      }

    }

  }

  override def beforeEach(): Unit = Dummy.clear()

  protected object Dummy {
    private var closed = List.empty[DummyResource]
    def get: List[DummyResource] = closed
    def add(resource: DummyResource): Unit = closed :+= resource
    def clear(): Unit = closed = List.empty
  }

  protected class DummyResource(fail: Option[Exception] = None) extends AutoCloseable {
    override def close(): Unit = {
      Dummy.add(this)
      fail.foreach(throw _)
    }
  }

}
