package suiryc.scala.settings

import java.util.prefs.Preferences
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PreferenceSpec extends WordSpec with Matchers with BeforeAndAfterEach {

  import Preference._

  private val KEY_BOOLEAN = "key.boolean"
  private val VALUE_BOOLEAN = true

  private val KEY_INT = "key.int"
  private val VALUE_INT = 1234

  private val KEY_LONG = "key.long"
  private val VALUE_LONG = 42L

  private val KEY_STRING = "key.string"
  private val VALUE_STRING = "Some string"

  implicit private var prefs: Preferences = _

  override def beforeEach(): Unit = {
    prefs = Preferences.userRoot.node("suiryc.test")
  }

  override def afterEach(): Unit = {
    Option(prefs).filter(_.nodeExists("")).foreach(_.removeNode())
  }

  "Preference" should {

    "handle its wrapped Preferences value" in {
      val boolean = Preference.from(KEY_BOOLEAN, !VALUE_BOOLEAN)
      val int = Preference.from(KEY_INT, VALUE_INT + 1)
      val long = Preference.from(KEY_LONG, VALUE_LONG + 1)
      val string = Preference.from(KEY_STRING, VALUE_STRING + " - new")

      boolean.option shouldBe empty
      boolean() shouldBe !VALUE_BOOLEAN
      int.option shouldBe empty
      int() shouldBe (VALUE_INT + 1)
      long.option shouldBe empty
      long() shouldBe (VALUE_LONG + 1)
      string.option shouldBe empty
      string() shouldBe (VALUE_STRING + " - new")

      boolean() = VALUE_BOOLEAN
      int() = VALUE_INT
      long() = VALUE_LONG
      string() = VALUE_STRING

      boolean.option should not be empty
      boolean() shouldBe VALUE_BOOLEAN
      int.option should not be empty
      int() shouldBe VALUE_INT
      long.option should not be empty
      long() shouldBe VALUE_LONG
      string.option should not be empty
      string() shouldBe VALUE_STRING
    }

  }

}
