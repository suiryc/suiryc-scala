package suiryc.scala.settings

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigUtil, ConfigValueFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import suiryc.scala.io.PathsEx

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

object PortableSettingsSpec {

  private val PATH_PREFIX = "suiryc.test"
  private val PATH_UNSET = s"$PATH_PREFIX.unset"

}

class PortableSettingsSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  import PortableSettingsSpec._

  private val reference = ConfigFactory.parseString(
    s"""
      |$PATH_PREFIX {
      |  key0 = "something"
      |  key1 = 0
      |}
    """.stripMargin
  )

  private val config = ConfigFactory.parseString(
    s"""
      |$PATH_PREFIX {
      |  key1 = 1234
      |  key2 = true
      |}
    """.stripMargin
  )

  private var applicationFile: Path = _

  override def beforeEach(): Unit = {
    // scalastyle:off null
    applicationFile = Files.createTempFile("suiryc-scala-core.test", null)
    // scalastyle:on null
  }

  override def afterEach(): Unit = {
    Option(applicationFile).foreach(_.toFile.delete())
    Option(applicationFile).foreach(PathsEx.backupPath(_).toFile.delete())
  }

  def newSettings: PortableSettings = {
    val settings = new PortableSettings(applicationFile, config, reference)
    // Populate the file content
    settings.save()
    settings
  }

  def fileSettings: PortableSettings =
    PortableSettings(applicationFile, ConfigUtil.splitPath(PATH_PREFIX).asScala.toSeq)

  def backupSettings: PortableSettings =
    PortableSettings(PathsEx.backupPath(applicationFile), ConfigUtil.splitPath(PATH_PREFIX).asScala.toSeq)

  "PortableSettings" should {

    "handle getting values from underlying standalone and reference Config" in {
      val settings = newSettings
      settings.config.getString(s"$PATH_PREFIX.key0") shouldBe "something"
      settings.config.getString(s"$PATH_PREFIX.key1") shouldBe "1234"
      settings.config.getInt(s"$PATH_PREFIX.key1") shouldBe 1234
      settings.config.getBoolean(s"$PATH_PREFIX.key2") shouldBe true
      intercept[ConfigException.Missing] {
        settings.config.getValue(PATH_UNSET)
      }
    }

    "backup original config and save modifications" in {
      val settings = newSettings
      val path = s"$PATH_PREFIX.key1"
      // Backup file should be created on the first change
      PathsEx.backupPath(applicationFile).toFile.exists shouldBe false
      settings.withValue(path, ConfigValueFactory.fromAnyRef("something else"))
      PathsEx.backupPath(applicationFile).toFile.exists shouldBe true

      // Current settings should have the new value
      settings.config.getString(path) shouldBe "something else"
      // Backup settings should not have the new value
      backupSettings.config.getString(path) shouldBe "1234"
      // Make sure the saved settings have the new value
      fileSettings.config.getString(path) shouldBe "something else"
    }

    "delay save when requested" in {
      val settings = newSettings
      val path = s"$PATH_PREFIX.key1"
      settings.setDelaySave(true)
      settings.withValue(path, ConfigValueFactory.fromAnyRef("something else"))
      PathsEx.backupPath(applicationFile).toFile.exists shouldBe true

      settings.config.getString(path) shouldBe "something else"
      // The current file shall not have the new value
      fileSettings.config.getString(path) shouldBe "1234"

      settings.setDelaySave(false)
      // Settings should have been saved
      fileSettings.config.getString(path) shouldBe "something else"
    }

    "handle adding new path" in {
      val settings = newSettings
      intercept[ConfigException.Missing] {
        settings.config.getValue(PATH_UNSET)
      }
      settings.withValue(PATH_UNSET, ConfigValueFactory.fromAnyRef("something else"))
      settings.config.getString(PATH_UNSET) shouldBe "something else"
      fileSettings.config.getString(PATH_UNSET) shouldBe "something else"
    }

    "handle removing path which exists in reference" in {
      val settings = newSettings
      val path = s"$PATH_PREFIX.key1"
      settings.withoutPath(path)
      settings.config.getString(path) shouldBe "0"
      // Path only exists in 'reference' (made up for the test)
      intercept[ConfigException.Missing] {
        fileSettings.config.getValue(path)
      }
    }

    "handle removing path which does not exist in reference" in {
      val settings = newSettings
      val path = s"$PATH_PREFIX.key2"
      settings.config.getBoolean(path) shouldBe true
      settings.withoutPath(path)
      intercept[ConfigException.Missing] {
        settings.config.getValue(path)
      }
      intercept[ConfigException.Missing] {
        fileSettings.config.getValue(path)
      }
    }

    "remove path from standalone configuration if value is the same as in reference" in {
      val settings = newSettings
      val path = s"$PATH_PREFIX.key1"
      // Note: the value not being quoted, we cannot use a String here (will be quoted)
      settings.withValue(path, ConfigValueFactory.fromAnyRef(0: Int))
      settings.config.getString(path) shouldBe "0"
      intercept[ConfigException.Missing] {
        fileSettings.config.getValue(path)
      }
    }

    "remove 'object' path which value is empty" in {
      val settings = newSettings
      // We purposely add an intermediate level
      val pathObj1 = s"$PATH_PREFIX.obj"
      val pathObj2 = s"$PATH_PREFIX.obj.sub"
      val path1 = s"$pathObj1.key"
      val path2 = s"$pathObj2.key"
      settings.withValue(path1, ConfigValueFactory.fromAnyRef(true))
      settings.withValue(path2, ConfigValueFactory.fromAnyRef(false))
      fileSettings.config.getObject(pathObj1)
      fileSettings.config.getObject(pathObj2)
      fileSettings.config.getBoolean(path1) shouldBe true
      fileSettings.config.getBoolean(path2) shouldBe false

      // Remove most-nested path
      settings.withoutPath(path2)
      fileSettings.config.getObject(pathObj1)
      fileSettings.config.getBoolean(path1) shouldBe true
      intercept[ConfigException.Missing] {
        fileSettings.config.getObject(pathObj2)
      }
      // Remove other path
      settings.withoutPath(path1)
      intercept[ConfigException.Missing] {
        fileSettings.config.getObject(pathObj1)
      }
      intercept[ConfigException.Missing] {
        fileSettings.config.getObject(pathObj2)
      }

      // Do the same in reverse order
      settings.withValue(path1, ConfigValueFactory.fromAnyRef(true))
      settings.withValue(path2, ConfigValueFactory.fromAnyRef(false))
      settings.withoutPath(path1)
      fileSettings.config.getObject(pathObj1)
      fileSettings.config.getObject(pathObj2)
      intercept[ConfigException.Missing] {
        fileSettings.config.getObject(path1)
      }
      fileSettings.config.getBoolean(path2) shouldBe false
      settings.withoutPath(path2)
      intercept[ConfigException.Missing] {
        fileSettings.config.getObject(pathObj1)
      }
      intercept[ConfigException.Missing] {
        fileSettings.config.getObject(pathObj2)
      }
    }

    "empty configuration when no values remain" in {
      val settings = newSettings
      settings.withoutPath(s"$PATH_PREFIX.key1")
      fileSettings.config.isEmpty shouldBe false
      settings.withoutPath(s"$PATH_PREFIX.key2")
      fileSettings.config.isEmpty shouldBe true
    }

  }

}
