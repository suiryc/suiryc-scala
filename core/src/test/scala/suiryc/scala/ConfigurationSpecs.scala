package suiryc.scala

import com.typesafe.config.{ConfigFactory, ConfigValueType}
import org.scalatest.wordspec.AnyWordSpec
import suiryc.scala.util.TestToolsBase

import java.nio.file.{Path, Paths}


class ConfigurationSpecs extends AnyWordSpec with TestToolsBase {

  "ConfigTools" should {

    import ConfigTools._

    "handle getting optional values" in {
      val cfg = ConfigFactory.parseString(
        """boolean = true
          |int = 1234
          |double = 1.234
          |string = "value"
          |config = {}
          |resource = "r"
          |""".stripMargin
      )
      cfg.getOptionalValue("boolean").map(_.valueType()) shouldBe Some(ConfigValueType.BOOLEAN)
      cfg.getOptionalValue("nothing") shouldBe empty
      cfg.getOptionalBoolean("boolean") shouldBe Some(true)
      cfg.getOptionalBoolean("nothing") shouldBe empty
      cfg.getOptionalInt("int") shouldBe Some(1234)
      cfg.getOptionalInt("nothing") shouldBe empty
      cfg.getOptionalDouble("double") shouldBe Some(1.234)
      cfg.getOptionalDouble("nothing") shouldBe empty
      cfg.getOptionalString("string") shouldBe Some("value")
      cfg.getOptionalString("nothing") shouldBe empty
      cfg.getOptionalConfig("config").map(_.isEmpty) shouldBe Some(true)
      cfg.getOptionalConfig("nothing") shouldBe empty
    }

    "allow enforcing string value is non-empty" in {
      val cfg = ConfigFactory.parseString(
        """non-empty = " "
          |empty = ""
          |""".stripMargin
      )
      cfg.getNonEmptyString("non-empty") shouldBe " "
      assertError[Exception]("'empty' must not be empty") {
        cfg.getNonEmptyString("empty")
      }
    }

    "handle getting FiniteDuration" in {
      import scala.concurrent.duration._
      val cfg = ConfigFactory.parseString(
        """one-ms = 1ms
          |nine-s = 9s
          |sixty-s = 60s
          |one-m = 1m
          |""".stripMargin
      )
      cfg.getScalaDuration("one-ms") shouldBe 1.millis
      cfg.getScalaDuration("nine-s") shouldBe 9.seconds
      // During conversion, biggest time unit possible is used
      cfg.getScalaDuration("sixty-s") shouldBe 1.minute
      cfg.getScalaDuration("one-m") shouldBe 1.minute
    }

  }

  "Configuration" should {

    val KEY_PREFIX = "suiryc-scala.unit-tests.configuration"

    def buildPath(name: String): Path = {
      List("core", "src", "test", "resources", s"$name.conf").foldLeft(Paths.get("."))(_.resolve(_))
    }

    // Notes:
    // Don't include file extension in name; the code will handle it.
    // And have a dedicated test to ensure it also works with file extension.
    val nameByPath    = "suiryc-scala-by-path"
    val pathByPath    = buildPath(nameByPath)
    val nameByName    = "suiryc-scala-by-name"

    // We need to clear config library properties during these tests, since the
    // nominal code won't change them if already set (which may be the case due
    // to other unit tests, if using the configuration).
    // Restore original values after the test.
    def withoutConfigProperties[A](code: => A): A = {
      def get(key: String): Option[String] = Option(System.getProperty(key))
      def set(key: String, opt: Option[String]): Unit = {
        opt match {
          case Some(v) => System.setProperty(key, v)
          case None    => System.clearProperty(key)
        }
        ()
      }

      val configFile     = get("config.file")
      val configResource = get("config.resource")
      val configUrl      = get("config.url")
      try {
        set("config.file", None)
        set("config.resource", None)
        set("config.url", None)
        code
      } finally {
        set("config.file", configFile)
        set("config.resource", configResource)
        set("config.url", configUrl)
      }
    }

    "handle loading by path" in {
      withoutConfigProperties {
        val conf = Configuration.load(confPath = Some(pathByPath), resourceName = None)
        conf.getBoolean(s"$KEY_PREFIX.by-name") shouldBe false
        conf.getBoolean(s"$KEY_PREFIX.by-path") shouldBe true
      }
    }

    "handle loading by name" in {
      withoutConfigProperties {
        val conf = Configuration.load(confPath = None, resourceName = Some(nameByName))
        conf.getBoolean(s"$KEY_PREFIX.by-name") shouldBe true
        conf.getBoolean(s"$KEY_PREFIX.by-path") shouldBe false
      }
    }

    "handle resource name with extension" in {
      withoutConfigProperties {
        val conf = Configuration.load(confPath = None, resourceName = Some(s"$nameByName.conf"))
        conf.getBoolean(s"$KEY_PREFIX.by-name") shouldBe true
        conf.getBoolean(s"$KEY_PREFIX.by-path") shouldBe false
      }
    }

  }

}
