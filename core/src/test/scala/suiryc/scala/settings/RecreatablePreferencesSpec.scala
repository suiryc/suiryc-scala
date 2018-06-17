package suiryc.scala.settings

import java.util.prefs.Preferences
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}

class RecreatablePreferencesSpec extends WordSpec with Matchers with BeforeAndAfterEach {

  private var prefs: Preferences = _

  private val KEY_BOOLEAN = "key.boolean"
  private val VALUE_BOOLEAN = true

  private val KEY_LONG = "key.long"
  private val VALUE_LONG = 42L

  private val KEY_UNUSED = "key.unused"

  override def beforeEach(): Unit = {
    // Use unique node name to prevent interfering with other parallel tests.
    prefs = Preferences.userRoot.node(s"suiryc.test.${getClass.getName}")
    prefs.putBoolean(KEY_BOOLEAN, VALUE_BOOLEAN)
    prefs.putLong(KEY_LONG, VALUE_LONG)
  }

  override def afterEach(): Unit = {
    Option(prefs).filter(_.nodeExists("")).foreach(_.removeNode())
  }

  "RecreatablePreferences" should {

    "handle its wrapped Preferences node" in {
      val recreatable = new RecreatablePreferences(prefs)
      def rprefs: Preferences = recreatable.prefs

      rprefs.nodeExists("") shouldBe true

      val keys = rprefs.keys.toSet
      keys should contain(KEY_BOOLEAN)
      rprefs.getBoolean(KEY_BOOLEAN, !VALUE_BOOLEAN) shouldBe VALUE_BOOLEAN

      keys should contain(KEY_LONG)
      rprefs.getLong(KEY_LONG, VALUE_LONG + 1) shouldBe VALUE_LONG

      keys should not contain KEY_UNUSED

      prefs.removeNode()
      prefs.nodeExists("") shouldBe false
    }

    "recreate its wrapped Preferences node when necessary" in {
      val recreatable = new RecreatablePreferences(prefs)
      def rprefs: Preferences = recreatable.prefs

      prefs.removeNode()
      prefs.nodeExists("") shouldBe false

      rprefs.nodeExists("") shouldBe true
      // Note: 'old' Preferences objects don't point to the recreated node.
      prefs.nodeExists("") shouldBe false

      val keys = rprefs.keys.toSet
      keys should not contain KEY_BOOLEAN
      keys should not contain KEY_LONG
    }

  }

}
