package suiryc.scala.settings

import java.util.prefs.Preferences
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatest.junit.JUnitRunner
import suiryc.scala.misc.{Enumeration => sEnumeration}

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

  private val KEY_ENUM = "key.enum"
  object TestEnumeration extends Enumeration {
    val First = Value
    val Default = Value
    val Test = Value
  }

  private val KEY_SENUM = "key.senum"
  object TestSEnumeration extends sEnumeration {
    case class Value(name: String) extends BaseValue
    val First = Value("first")
    val Default = Value("default")
    val Test = Value("test")
  }

  private var prefs: Preferences = _

  override def beforeEach(): Unit = {
    // Use unique node name to prevent interfering with other parallel tests.
    prefs = Preferences.userRoot.node(s"suiryc.test.${getClass.getName}")
  }

  override def afterEach(): Unit = {
    Option(prefs).filter(_.nodeExists("")).foreach(_.removeNode())
  }

  "Preference" should {

    "handle its wrapped Preferences value" in {
      val boolean = Preference.from(prefs, KEY_BOOLEAN, !VALUE_BOOLEAN)
      val int = Preference.from(prefs, KEY_INT, VALUE_INT + 1)
      val long = Preference.from(prefs, KEY_LONG, VALUE_LONG + 1)
      val string = Preference.from(prefs, KEY_STRING, VALUE_STRING + " - new")
      val enum = Preference.from(prefs, KEY_ENUM, TestEnumeration, TestEnumeration.Default)
      val senum = Preference.from(prefs, KEY_SENUM, TestSEnumeration, TestSEnumeration.Default)

      boolean.option shouldBe empty
      boolean() shouldBe !VALUE_BOOLEAN
      int.option shouldBe empty
      int() shouldBe (VALUE_INT + 1)
      long.option shouldBe empty
      long() shouldBe (VALUE_LONG + 1)
      string.option shouldBe empty
      string() shouldBe (VALUE_STRING + " - new")
      enum.option shouldBe empty
      enum() shouldBe TestEnumeration.Default
      senum.option shouldBe empty
      senum() shouldBe TestSEnumeration.Default

      boolean() = VALUE_BOOLEAN
      int() = VALUE_INT
      long() = VALUE_LONG
      string() = VALUE_STRING
      enum() = TestEnumeration.Test
      senum() = TestSEnumeration.Test

      boolean.option should not be empty
      boolean() shouldBe VALUE_BOOLEAN
      int.option should not be empty
      int() shouldBe VALUE_INT
      long.option should not be empty
      long() shouldBe VALUE_LONG
      string.option should not be empty
      string() shouldBe VALUE_STRING
      enum.option should not be empty
      enum() shouldBe TestEnumeration.Test
      senum.option should not be empty
      senum() shouldBe TestSEnumeration.Test
    }

    "recreate its Preferences node when applicable" in {
      val recreatable = new RecreatablePreferences(prefs)

      val boolean = Preference.from(recreatable, KEY_BOOLEAN, !VALUE_BOOLEAN)
      val int = Preference.from(recreatable, KEY_INT, VALUE_INT + 1)
      val long = Preference.from(recreatable, KEY_LONG, VALUE_LONG + 1)
      val string = Preference.from(recreatable, KEY_STRING, VALUE_STRING + " - new")
      val enum = Preference.from(recreatable, KEY_ENUM, TestEnumeration, TestEnumeration.Default)
      val senum = Preference.from(recreatable, KEY_SENUM, TestSEnumeration, TestSEnumeration.Default)

      boolean() = VALUE_BOOLEAN
      int() = VALUE_INT
      long() = VALUE_LONG
      string() = VALUE_STRING
      enum() = TestEnumeration.Test
      senum() = TestSEnumeration.Test

      prefs.removeNode()

      // Note: if node is not recreated, accessing values will throw an Exception.
      boolean.option shouldBe empty
      boolean() shouldBe !VALUE_BOOLEAN
      int.option shouldBe empty
      int() shouldBe (VALUE_INT + 1)
      long.option shouldBe empty
      long() shouldBe (VALUE_LONG + 1)
      string.option shouldBe empty
      string() shouldBe (VALUE_STRING + " - new")
      enum.option shouldBe empty
      enum() shouldBe TestEnumeration.Default
      senum.option shouldBe empty
      senum() shouldBe TestSEnumeration.Default
    }

  }

}
