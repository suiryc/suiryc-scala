package suiryc.scala.settings

import com.typesafe.config.ConfigFactory
import java.util.prefs.Preferences
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import suiryc.scala.misc.{Enumeration => sEnumeration}

class PersistentSettingSpec extends WordSpec with Matchers with BeforeAndAfterEach {

  import PersistentSetting._

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

  private val config = ConfigFactory.parseString(
    s"""
      |$KEY_BOOLEAN = $VALUE_BOOLEAN
      |$KEY_INT = $VALUE_INT
      |$KEY_LONG = $VALUE_LONG
      |$KEY_STRING = "$VALUE_STRING"
      |$KEY_ENUM = ${TestEnumeration.Default}
      |$KEY_SENUM = ${TestSEnumeration.Default}
    """.stripMargin
  )

  private var prefs: Preferences = _

  private var settings: BaseSettings = _

  override def beforeEach(): Unit = {
    // Use unique node name to prevent interfering with other parallel tests.
    prefs = Preferences.userRoot.node(s"suiryc.test.${getClass.getName}")
    settings = new BaseSettings(config, prefs)
  }

  override def afterEach(): Unit = {
    Option(prefs).filter(_.nodeExists("")).foreach(_.removeNode())
  }

  "PersistentSetting" should {

    "handle its wrapped Config and Preferences node key" in {
      val boolean = PersistentSetting.from(settings, KEY_BOOLEAN, !VALUE_BOOLEAN)
      val int = PersistentSetting.from(settings, KEY_INT, VALUE_INT + 1)
      val long = PersistentSetting.from(settings, KEY_LONG, VALUE_LONG + 1)
      val string = PersistentSetting.from(settings, KEY_STRING, VALUE_STRING + " - copy")
      val enum = PersistentSetting.from(settings, KEY_ENUM, TestEnumeration, TestEnumeration.Test)
      val senum = PersistentSetting.from(settings, KEY_SENUM, TestSEnumeration, TestSEnumeration.Test)

      boolean() shouldBe VALUE_BOOLEAN
      int() shouldBe VALUE_INT
      long() shouldBe VALUE_LONG
      string() shouldBe VALUE_STRING
      enum() shouldBe TestEnumeration.Default
      senum() shouldBe TestSEnumeration.Default

      boolean() = !VALUE_BOOLEAN
      int() += 1
      long() += 1
      string() += " - new"
      enum() = TestEnumeration.Test
      senum() = TestSEnumeration.Test

      boolean() shouldBe !VALUE_BOOLEAN
      int() shouldBe (VALUE_INT + 1)
      long() shouldBe (VALUE_LONG + 1)
      string() shouldBe (VALUE_STRING + " - new")
      enum() shouldBe TestEnumeration.Test
      senum() shouldBe TestSEnumeration.Test
    }

    "recreate its Preferences node when necessary" in {
      val boolean = PersistentSetting.from(settings, KEY_BOOLEAN, !VALUE_BOOLEAN)
      val int = PersistentSetting.from(settings, KEY_INT, VALUE_INT + 1)
      val long = PersistentSetting.from(settings, KEY_LONG, VALUE_LONG + 1)
      val string = PersistentSetting.from(settings, KEY_STRING, VALUE_STRING + " - copy")
      val enum = PersistentSetting.from(settings, KEY_ENUM, TestEnumeration, TestEnumeration.Test)
      val senum = PersistentSetting.from(settings, KEY_SENUM, TestSEnumeration, TestSEnumeration.Test)

      boolean() = !VALUE_BOOLEAN
      int() += 1
      long() += 1
      string() += " - new"
      enum() = TestEnumeration.Test
      senum() = TestSEnumeration.Test

      settings.prefs.removeNode()

      // Note: if node is not recreated, accessing values will throw an Exception.
      boolean() shouldBe VALUE_BOOLEAN
      int() shouldBe VALUE_INT
      long() shouldBe VALUE_LONG
      string() shouldBe VALUE_STRING
      enum() shouldBe TestEnumeration.Default
      senum() shouldBe TestSEnumeration.Default
    }

  }

}
