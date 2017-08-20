package suiryc.scala.settings

import com.typesafe.config.ConfigFactory
import java.util.prefs.Preferences
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
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

  private val config = ConfigFactory.parseString(
    s"""
      |$KEY_BOOLEAN = $VALUE_BOOLEAN
      |$KEY_INT = $VALUE_INT
      |$KEY_LONG = $VALUE_LONG
      |$KEY_STRING = "$VALUE_STRING"
    """.stripMargin
  )

  private var prefs: Preferences = _

  implicit private var settings: BaseSettings = _

  override def beforeEach(): Unit = {
    prefs = Preferences.userRoot.node("suiryc.test")
    settings = new BaseSettings(config, prefs)
  }

  override def afterEach(): Unit = {
    Option(prefs).filter(_.nodeExists("")).foreach(_.removeNode())
  }

  "PersistentSetting" should {

    "handle its wrapped Config and Preferences node key" in {
      val boolean = PersistentSetting.from(KEY_BOOLEAN, !VALUE_BOOLEAN)
      val int = PersistentSetting.from(KEY_INT, VALUE_INT + 1)
      val long = PersistentSetting.from(KEY_LONG, VALUE_LONG + 1)
      val string = PersistentSetting.from(KEY_STRING, VALUE_STRING + " - copy")

      boolean() shouldBe VALUE_BOOLEAN
      int() shouldBe VALUE_INT
      long() shouldBe VALUE_LONG
      string() shouldBe VALUE_STRING

      boolean() = !VALUE_BOOLEAN
      int() += 1
      long() += 1
      string() += " - new"

      boolean() shouldBe !VALUE_BOOLEAN
      int() shouldBe (VALUE_INT + 1)
      long() shouldBe (VALUE_LONG + 1)
      string() shouldBe (VALUE_STRING + " - new")
    }

  }

}
