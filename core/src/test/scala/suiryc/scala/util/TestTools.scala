package suiryc.scala.util

import org.scalactic.source
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import suiryc.scala.Configuration

import scala.reflect.ClassTag


object TestConfigurationInit {

  // When tests are run from local IDE, we wish to ensure only the test
  // configuration is used, not the nominal one (which may be present in the
  // classpath too; it is at least with IntelliJ).
  // The easiest way to do so is to give the test configuration a unique name
  // and explicitly use this resource name: classpath is expected to see it,
  // and we don't have to deal with file paths this way.
  //
  // For unit tests setup, doing this must be done ASAP (before any other code
  // accesses the configuration), and we have to take into account that:
  //  - 'object' instances are created lazily when a field/function is actually
  //    accessed: this holds true for class fields/ctor code using the object
  //  - when instantiating a class, fields are initialized as part of ctor code
  //    (that is any code present directly inside the object/class definition)
  //    at the same place they appear in the ctor code
  //  - when extending a trait/class, its fields/ctor are processed first (in
  //    the order of extending)
  //  - assigning a class 'Unit' field into another class field does not
  //    actually do anything (the target class field is not accessed): so e.g.
  //    declaring a field in a class that 'gets' its value from an object field
  //    of type 'Unit' does not trigger access to the object target field
  // So a solution is to:
  //  - do this in a dedicated 'object'
  //  - explicitly access the 'object' (e.g. a dummy 'init' method) from a
  //    dedicated trait
  //  - extend this dedicated trait *first* in all base test traits that need
  //    to use the configuration
  // Then base test traits can be extended in any order: the test configuration
  // will be properly used.
  //
  // When using sbt, we simply declare the property in build.sbt, which makes
  // this code unneeded (actually does nothing in this case).
  Configuration.setDefaultApplication(
    resourceName = Some("suiryc-scala-unit-tests")
  )

  def init(): Unit = {}

}

trait TestConfigurationInit {
  TestConfigurationInit.init()
}

trait TestToolsBase extends TestConfigurationInit with Matchers {

  /** Asserts Exception is thrown with message. */
  protected def assertError[T <: Exception](
    msg: String
  )(f: => Any)(implicit classTag: ClassTag[T], pos: source.Position): Assertion = {
    val ex = intercept[T](f)
    ex.getMessage shouldBe msg
  }

}
