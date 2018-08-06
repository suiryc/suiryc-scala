package suiryc.scala.settings

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigUtil}
import java.nio.file.Path
import org.scalatest.{Matchers, WordSpec}
import scala.collection.JavaConverters._

class ConfigEntrySpec extends WordSpec with Matchers {

  private val PATH_REF_PREFIX = "ref"

  private val PATH_BOOLEAN = "key.boolean"
  private val VALUE_BOOLEAN = true

  private val PATH_INT = "key.int"
  private val VALUE_INT = 1234

  private val PATH_LONG = "key.long"
  private val VALUE_LONG = 42L

  private val PATH_DOUBLE = "key.double"
  private val VALUE_DOUBLE = 0.5

  private val PATH_STRING = "key.string"
  private val VALUE_STRING = "Some string"

  private val PATH_UNSET = "key.unset"
  private val PATH_LIST = "key.list"

  private val reference = ConfigFactory.parseString(
    s"""
       |$PATH_REF_PREFIX$PATH_BOOLEAN = ${!VALUE_BOOLEAN}
       |$PATH_REF_PREFIX$PATH_INT = ${VALUE_INT + 1}
       |$PATH_REF_PREFIX$PATH_LONG = ${VALUE_LONG + 1}
       |$PATH_REF_PREFIX$PATH_DOUBLE = ${VALUE_DOUBLE + 1}
       |$PATH_REF_PREFIX$PATH_STRING = "$VALUE_STRING - new"
       |$PATH_REF_PREFIX$PATH_LIST = []
    """.stripMargin
  )

  private val config = ConfigFactory.parseString(
    s"""
       |$PATH_BOOLEAN = $VALUE_BOOLEAN
       |$PATH_INT = $VALUE_INT
       |$PATH_LONG = $VALUE_LONG
       |$PATH_DOUBLE = $VALUE_DOUBLE
       |$PATH_STRING = "$VALUE_STRING"
       |$PATH_LIST = []
    """.stripMargin
  )

  def newSettings(config: Config = this.config, reference: Config = this.reference): PortableSettings = {
    // scalastyle:off null
    new PortableSettings(null, config, reference) {
      // Disable backup/save
      override def backup(): Unit = { }
      override def save(path: Path, backup: Boolean): Unit = { }
    }
    // scalastyle:on null
  }

  def newSettings(config: String, reference: String): PortableSettings = {
    newSettings(ConfigFactory.parseString(config), ConfigFactory.parseString(reference))
  }

  "ConfigEntry" should {

    "handle exists" in {
      val settings = newSettings()
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).exists shouldBe true
      ConfigEntry.from[Int](settings, getPath(PATH_INT)).exists shouldBe true
      ConfigEntry.from[Long](settings, getPath(PATH_LONG)).exists shouldBe true
      ConfigEntry.from[Double](settings, getPath(PATH_DOUBLE)).exists shouldBe true
      ConfigEntry.from[String](settings, getPath(PATH_STRING)).exists shouldBe true
      ConfigEntry.from[String](settings, getPath(PATH_UNSET)).exists shouldBe false

      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).exists shouldBe true
      ConfigEntry.from[Int](settings, getPath(PATH_REF_PREFIX + PATH_INT)).exists shouldBe true
      ConfigEntry.from[Long](settings, getPath(PATH_REF_PREFIX + PATH_LONG)).exists shouldBe true
      ConfigEntry.from[Double](settings, getPath(PATH_REF_PREFIX + PATH_DOUBLE)).exists shouldBe true
      ConfigEntry.from[String](settings, getPath(PATH_REF_PREFIX + PATH_STRING)).exists shouldBe true
    }

    "handle get" in {
      val settings = newSettings()
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).get shouldBe VALUE_BOOLEAN
      ConfigEntry.from[Int](settings, getPath(PATH_INT)).get shouldBe VALUE_INT
      ConfigEntry.from[Long](settings, getPath(PATH_LONG)).get shouldBe VALUE_LONG
      ConfigEntry.from[Double](settings, getPath(PATH_DOUBLE)).get shouldBe VALUE_DOUBLE
      ConfigEntry.from[String](settings, getPath(PATH_STRING)).get shouldBe VALUE_STRING
      intercept[ConfigException.Missing] {
        ConfigEntry.from[String](settings, getPath(PATH_UNSET)).get
      }

      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).get shouldBe !VALUE_BOOLEAN
      ConfigEntry.from[Int](settings, getPath(PATH_REF_PREFIX + PATH_INT)).get shouldBe VALUE_INT + 1
      ConfigEntry.from[Long](settings, getPath(PATH_REF_PREFIX + PATH_LONG)).get shouldBe VALUE_LONG + 1
      ConfigEntry.from[Double](settings, getPath(PATH_REF_PREFIX + PATH_DOUBLE)).get shouldBe VALUE_DOUBLE + 1
      ConfigEntry.from[String](settings, getPath(PATH_REF_PREFIX + PATH_STRING)).get shouldBe VALUE_STRING + " - new"
    }

    "handle opt" in {
      val settings = newSettings()
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).opt shouldBe Some(VALUE_BOOLEAN)
      ConfigEntry.from[Int](settings, getPath(PATH_INT)).opt shouldBe Some(VALUE_INT)
      ConfigEntry.from[Long](settings, getPath(PATH_LONG)).opt shouldBe Some(VALUE_LONG)
      ConfigEntry.from[Double](settings, getPath(PATH_DOUBLE)).opt shouldBe Some(VALUE_DOUBLE)
      ConfigEntry.from[String](settings, getPath(PATH_STRING)).opt shouldBe Some(VALUE_STRING)
      ConfigEntry.from[String](settings, getPath(PATH_UNSET)).opt shouldBe None

      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).opt shouldBe Some(!VALUE_BOOLEAN)
      ConfigEntry.from[Int](settings, getPath(PATH_REF_PREFIX + PATH_INT)).opt shouldBe Some(VALUE_INT + 1)
      ConfigEntry.from[Long](settings, getPath(PATH_REF_PREFIX + PATH_LONG)).opt shouldBe Some(VALUE_LONG + 1)
      ConfigEntry.from[Double](settings, getPath(PATH_REF_PREFIX + PATH_DOUBLE)).opt shouldBe Some(VALUE_DOUBLE + 1)
      ConfigEntry.from[String](settings, getPath(PATH_REF_PREFIX + PATH_STRING)).opt shouldBe Some(VALUE_STRING + " - new")
    }

    "handle getList" in {
      val settings1 = newSettings(
        s"""
           |$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
           |$PATH_INT = [ $VALUE_INT, ${VALUE_INT + 1} ]
           |$PATH_LONG = [ $VALUE_LONG, ${VALUE_LONG + 1} ]
           |$PATH_DOUBLE = [ $VALUE_DOUBLE, ${VALUE_DOUBLE + 1} ]
           |$PATH_STRING = [ "$VALUE_STRING", "$VALUE_STRING - new" ]
        """.stripMargin,
        ""
      )
      ConfigEntry.from[Boolean](settings1, getPath(PATH_BOOLEAN)).getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      ConfigEntry.from[Int](settings1, getPath(PATH_INT)).getList shouldBe List(VALUE_INT, VALUE_INT + 1)
      ConfigEntry.from[Long](settings1, getPath(PATH_LONG)).getList shouldBe List(VALUE_LONG, VALUE_LONG + 1)
      ConfigEntry.from[Double](settings1, getPath(PATH_DOUBLE)).getList shouldBe List(VALUE_DOUBLE, VALUE_DOUBLE + 1)
      ConfigEntry.from[String](settings1, getPath(PATH_STRING)).getList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
      intercept[ConfigException.Missing] {
        ConfigEntry.from[String](settings1, getPath(PATH_UNSET)).getList
      }

      val settings2 = newSettings(
        "",
        s"""
           |$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
           |$PATH_INT = [ $VALUE_INT, ${VALUE_INT + 1} ]
           |$PATH_LONG = [ $VALUE_LONG, ${VALUE_LONG + 1} ]
           |$PATH_DOUBLE = [ $VALUE_DOUBLE, ${VALUE_DOUBLE + 1} ]
           |$PATH_STRING = [ "$VALUE_STRING", "$VALUE_STRING - new" ]
        """.stripMargin
      )
      ConfigEntry.from[Boolean](settings2, getPath(PATH_BOOLEAN)).getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      ConfigEntry.from[Int](settings2, getPath(PATH_INT)).getList shouldBe List(VALUE_INT, VALUE_INT + 1)
      ConfigEntry.from[Long](settings2, getPath(PATH_LONG)).getList shouldBe List(VALUE_LONG, VALUE_LONG + 1)
      ConfigEntry.from[Double](settings2, getPath(PATH_DOUBLE)).getList shouldBe List(VALUE_DOUBLE, VALUE_DOUBLE + 1)
      ConfigEntry.from[String](settings2, getPath(PATH_STRING)).getList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
    }

    "handle optList" in {
      val settings1 = newSettings(
        s"""
           |$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
           |$PATH_INT = [ $VALUE_INT, ${VALUE_INT + 1} ]
           |$PATH_LONG = [ $VALUE_LONG, ${VALUE_LONG + 1} ]
           |$PATH_DOUBLE = [ $VALUE_DOUBLE, ${VALUE_DOUBLE + 1} ]
           |$PATH_STRING = [ "$VALUE_STRING", "$VALUE_STRING - new" ]
        """.stripMargin,
        ""
      )
      ConfigEntry.from[Boolean](settings1, getPath(PATH_BOOLEAN)).optList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      ConfigEntry.from[Int](settings1, getPath(PATH_INT)).optList shouldBe List(VALUE_INT, VALUE_INT + 1)
      ConfigEntry.from[Long](settings1, getPath(PATH_LONG)).optList shouldBe List(VALUE_LONG, VALUE_LONG + 1)
      ConfigEntry.from[Double](settings1, getPath(PATH_DOUBLE)).optList shouldBe List(VALUE_DOUBLE, VALUE_DOUBLE + 1)
      ConfigEntry.from[String](settings1, getPath(PATH_STRING)).optList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
      ConfigEntry.from[String](settings1, getPath(PATH_UNSET)).optList shouldBe Nil

      val settings2 = newSettings(
        "",
        s"""
           |$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
           |$PATH_INT = [ $VALUE_INT, ${VALUE_INT + 1} ]
           |$PATH_LONG = [ $VALUE_LONG, ${VALUE_LONG + 1} ]
           |$PATH_DOUBLE = [ $VALUE_DOUBLE, ${VALUE_DOUBLE + 1} ]
           |$PATH_STRING = [ "$VALUE_STRING", "$VALUE_STRING - new" ]
        """.stripMargin
      )
      ConfigEntry.from[Boolean](settings2, getPath(PATH_BOOLEAN)).optList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      ConfigEntry.from[Int](settings2, getPath(PATH_INT)).optList shouldBe List(VALUE_INT, VALUE_INT + 1)
      ConfigEntry.from[Long](settings2, getPath(PATH_LONG)).optList shouldBe List(VALUE_LONG, VALUE_LONG + 1)
      ConfigEntry.from[Double](settings2, getPath(PATH_DOUBLE)).optList shouldBe List(VALUE_DOUBLE, VALUE_DOUBLE + 1)
      ConfigEntry.from[String](settings2, getPath(PATH_STRING)).optList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
    }

    "handle set" in {
      val settings = newSettings()
      val entryBoolean = ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN))
      entryBoolean.set(!VALUE_BOOLEAN)
      entryBoolean.get shouldBe !VALUE_BOOLEAN
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).get shouldBe !VALUE_BOOLEAN
      val entryInt = ConfigEntry.from[Int](settings, getPath(PATH_INT))
      entryInt.set(VALUE_INT + 1)
      entryInt.get shouldBe VALUE_INT + 1
      ConfigEntry.from[Int](settings, getPath(PATH_INT)).get shouldBe VALUE_INT + 1
      val entryLong = ConfigEntry.from[Long](settings, getPath(PATH_LONG))
      entryLong.set(VALUE_LONG + 1)
      entryLong.get shouldBe VALUE_LONG + 1
      ConfigEntry.from[Long](settings, getPath(PATH_LONG)).get shouldBe VALUE_LONG + 1
      val entryDouble = ConfigEntry.from[Double](settings, getPath(PATH_DOUBLE))
      entryDouble.set(VALUE_DOUBLE + 1)
      entryDouble.get shouldBe VALUE_DOUBLE + 1
      ConfigEntry.from[Double](settings, getPath(PATH_DOUBLE)).get shouldBe VALUE_DOUBLE + 1
      val entryString = ConfigEntry.from[String](settings, getPath(PATH_STRING))
      entryString.set(VALUE_STRING + " - new")
      entryString.get shouldBe VALUE_STRING + " - new"
      ConfigEntry.from[String](settings, getPath(PATH_STRING)).get shouldBe VALUE_STRING + " - new"
      val entryUnset = ConfigEntry.from[String](settings, getPath(PATH_UNSET))
      entryUnset.set(VALUE_STRING)
      entryUnset.get shouldBe VALUE_STRING
      ConfigEntry.from[String](settings, getPath(PATH_UNSET)).get shouldBe VALUE_STRING
    }

    "handle setList" in {
      val settings = newSettings()
      val entryBoolean = ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN))
      entryBoolean.setList(List(VALUE_BOOLEAN, !VALUE_BOOLEAN))
      entryBoolean.getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      val entryInt = ConfigEntry.from[Int](settings, getPath(PATH_INT))
      entryInt.setList(List(VALUE_INT, VALUE_INT + 1))
      entryInt.getList shouldBe List(VALUE_INT, VALUE_INT + 1)
      ConfigEntry.from[Int](settings, getPath(PATH_INT)).getList shouldBe List(VALUE_INT, VALUE_INT + 1)
      val entryLong = ConfigEntry.from[Long](settings, getPath(PATH_LONG))
      entryLong.setList(List(VALUE_LONG, VALUE_LONG + 1))
      entryLong.getList shouldBe List(VALUE_LONG, VALUE_LONG + 1)
      ConfigEntry.from[Long](settings, getPath(PATH_LONG)).getList shouldBe List(VALUE_LONG, VALUE_LONG + 1)
      val entryDouble = ConfigEntry.from[Double](settings, getPath(PATH_DOUBLE))
      entryDouble.setList(List(VALUE_DOUBLE, VALUE_DOUBLE + 1))
      entryDouble.getList shouldBe List(VALUE_DOUBLE, VALUE_DOUBLE + 1)
      ConfigEntry.from[Double](settings, getPath(PATH_DOUBLE)).getList shouldBe List(VALUE_DOUBLE, VALUE_DOUBLE + 1)
      val entryString = ConfigEntry.from[String](settings, getPath(PATH_STRING))
      entryString.setList(List(VALUE_STRING, VALUE_STRING + " - new"))
      entryString.getList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
      ConfigEntry.from[String](settings, getPath(PATH_STRING)).getList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
      val entryUnset = ConfigEntry.from[String](settings, getPath(PATH_UNSET))
      entryUnset.setList(List(VALUE_STRING, VALUE_STRING + " - new"))
      entryUnset.getList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
      ConfigEntry.from[String](settings, getPath(PATH_UNSET)).getList shouldBe List(VALUE_STRING, VALUE_STRING + " - new")
    }

    "handle reset" in {
      val settings = newSettings(
        s"""
           |$PATH_BOOLEAN = $VALUE_BOOLEAN
           |$PATH_INT = $VALUE_INT
        """.stripMargin,
        s"""
           |$PATH_INT = ${VALUE_INT + 1}
           |$PATH_LONG = $VALUE_LONG
        """.stripMargin
      )
      val entry1 = ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN))
      entry1.reset()
      entry1.exists shouldBe false
      intercept[ConfigException.Missing] {
        entry1.get
      }
      intercept[ConfigException.Missing] {
        ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).get
      }

      val entry2 = ConfigEntry.from[Int](settings, getPath(PATH_INT))
      entry2.reset()
      entry2.exists shouldBe true
      entry2.get shouldBe VALUE_INT + 1
      ConfigEntry.from[Int](settings, getPath(PATH_INT)).get shouldBe VALUE_INT + 1

      val entry3 = ConfigEntry.from[Long](settings, getPath(PATH_LONG))
      entry3.reset()
      entry3.exists shouldBe true
      entry3.get shouldBe VALUE_LONG
      ConfigEntry.from[Long](settings, getPath(PATH_LONG)).get shouldBe VALUE_LONG
    }

    "not cache 'exists' result" in {
      val settings = newSettings()
      val entry1 = ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN))
      entry1.exists shouldBe true
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).reset()
      entry1.exists shouldBe false

      val entry2 = ConfigEntry.from[Boolean](settings, getPath(PATH_UNSET))
      entry2.exists shouldBe false
      ConfigEntry.from[Boolean](settings, getPath(PATH_UNSET)).set(VALUE_BOOLEAN)
      entry2.exists shouldBe true
    }

    "cache 'get'/'opt'/'getList'/'optList' results when present" in {
      val settings = newSettings()
      val entry1 = ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN))
      entry1.get shouldBe VALUE_BOOLEAN
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).reset()
      entry1.get shouldBe VALUE_BOOLEAN
      entry1.opt shouldBe Some(VALUE_BOOLEAN)

      entry1.reset()
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).setList(List(VALUE_BOOLEAN, !VALUE_BOOLEAN))
      entry1.getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).reset()
      entry1.getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      entry1.optList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
    }

    "handle refExists" in {
      val settings = newSettings()
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).refExists shouldBe false
      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).refExists shouldBe true
    }

    "handle refGet" in {
      val settings = newSettings()
      intercept[ConfigException.Missing] {
        ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).refGet
      }
      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).refGet shouldBe !VALUE_BOOLEAN
    }

    "handle refOpt" in {
      val settings = newSettings()
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).refOpt shouldBe None
      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).refOpt shouldBe Some(!VALUE_BOOLEAN)
    }

    "handle refGetList" in {
      val settings = newSettings(
        s"""
           |$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
        """.stripMargin,
        s"""
           |$PATH_REF_PREFIX$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
        """.stripMargin
      )
      intercept[ConfigException.Missing] {
        ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).refGetList
      }
      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).refGetList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
    }

    "handle refOptList" in {
      val settings = newSettings(
        s"""
           |$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
        """.stripMargin,
        s"""
           |$PATH_REF_PREFIX$PATH_BOOLEAN = [ $VALUE_BOOLEAN, ${!VALUE_BOOLEAN} ]
        """.stripMargin
      )
      ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).refOptList shouldBe Nil
      ConfigEntry.from[Boolean](settings, getPath(PATH_REF_PREFIX + PATH_BOOLEAN)).refOptList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
    }

    "handle withDefault for unset path" in {
      val settings = newSettings()
      val entry1 = ConfigEntry.from[Boolean](settings, getPath(PATH_UNSET)).withDefault(VALUE_BOOLEAN)
      entry1.get shouldBe VALUE_BOOLEAN
      entry1.opt shouldBe Some(VALUE_BOOLEAN)
      entry1.reset()
      entry1.get shouldBe VALUE_BOOLEAN
      entry1.opt shouldBe Some(VALUE_BOOLEAN)

      val entry2 = ConfigEntry.from[Boolean](settings, getPath(PATH_UNSET)).withDefault(List(VALUE_BOOLEAN, !VALUE_BOOLEAN))
      entry2.getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      entry2.optList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      entry2.reset()
      entry2.getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      entry2.optList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
    }

    "handle withDefault for existing path" in {
      val settings = newSettings()
      val entry1 = ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN)).withDefault(!VALUE_BOOLEAN)
      entry1.get shouldBe VALUE_BOOLEAN
      entry1.opt shouldBe Some(VALUE_BOOLEAN)
      entry1.reset()
      entry1.get shouldBe !VALUE_BOOLEAN
      entry1.opt shouldBe Some(!VALUE_BOOLEAN)

      val entry2 = ConfigEntry.from[Boolean](settings, getPath(PATH_LIST)).withDefault(List(VALUE_BOOLEAN, !VALUE_BOOLEAN))
      entry2.getList shouldBe Nil
      entry2.optList shouldBe Nil
      entry2.reset()
      entry2.getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      entry2.optList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
    }

    "propagate changes to wrapped value" in {
      val settings = newSettings()
      val entry1 = ConfigEntry.from[Boolean](settings, getPath(PATH_BOOLEAN))
      entry1.get shouldBe VALUE_BOOLEAN
      entry1.opt shouldBe Some(VALUE_BOOLEAN)
      entry1.withDefault(VALUE_BOOLEAN).set(!VALUE_BOOLEAN)
      entry1.get shouldBe !VALUE_BOOLEAN
      entry1.opt shouldBe Some(!VALUE_BOOLEAN)

      val entry2 = ConfigEntry.from[Boolean](settings, getPath(PATH_LIST))
      entry2.setList(List(!VALUE_BOOLEAN, VALUE_BOOLEAN))
      entry2.getList shouldBe List(!VALUE_BOOLEAN, VALUE_BOOLEAN)
      entry2.optList shouldBe List(!VALUE_BOOLEAN, VALUE_BOOLEAN)
      entry2.withDefault(Nil).setList(List(VALUE_BOOLEAN, !VALUE_BOOLEAN))
      entry2.getList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
      entry2.optList shouldBe List(VALUE_BOOLEAN, !VALUE_BOOLEAN)
    }

  }

  private def getPath(s: String): Seq[String] = ConfigUtil.splitPath(s).asScala

}
