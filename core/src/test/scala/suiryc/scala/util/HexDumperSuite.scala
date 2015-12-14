package suiryc.scala.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

// scalastyle:off magic.number
@RunWith(classOf[JUnitRunner])
class HexDumperSuite extends FunSuite {

  import HexDumper._

  // 00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|
  // 10: D638 A817 A37E 62A4 4428 A276            |Ö8¨.£~b¤D(¢v    |
  private val binary: Array[Byte] =
    Hash("1CCC54C6888537888E91A8190D8B568AD638A817A37E62A44428A276").bytes

  val settingsDefault = Settings(endWithEOL = false)

  test("default settings with less than 16 bytes array") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder))
    dump(binary.take(3), settings)
    val a = builder.toString()
    val b = """00: 1CCC 54                                  |.ÌT             |"""
    assert(a === b)
  }

  test("default settings with 16 bytes array") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder))
    dump(binary.take(16), settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|"""
    assert(a === b)
  }

  test("default settings with more than 16 bytes array") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder))
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|
              |10: D638 A817 A37E 62A4 4428 A276            |Ö8¨.£~b¤D(¢v    |""".stripMargin
    assert(a === b)
  }

  test("default settings with specific length") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), length = 3)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1CCC 54                                  |.ÌT             |"""
    assert(a === b)
  }

  test("default settings with specific offset and length") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), offset = 1, length = 3)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: CC54 C6                                  |ÌTÆ             |"""
    assert(a === b)
  }

  test("default settings with non-empty output") {
    val builder = new StringBuilder("#")
    val settings = settingsDefault.copy(output = Output(builder))
    dump(binary, settings)
    val a = builder.toString()
    val b = """#00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|
              |10: D638 A817 A37E 62A4 4428 A276            |Ö8¨.£~b¤D(¢v    |""".stripMargin
    assert(a === b)
  }

  test("default settings with End-Of-Line at the end") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), endWithEOL = true)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|
              |10: D638 A817 A37E 62A4 4428 A276            |Ö8¨.£~b¤D(¢v    |
              |""".stripMargin
    assert(a === b)
  }

  test("default settings with 32 bytes per line") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), viewBytes = 32)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  D638 A817 A37E 62A4 4428 A276            |.ÌTÆ..7...¨...V.|Ö8¨.£~b¤D(¢v    |"""
    assert(a === b)
  }

  test("default settings with 32 bytes per line and only 16 bytes array") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), viewBytes = 32)
    dump(binary.take(16), settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A                                           |.ÌTÆ..7...¨...V.|"""
    assert(a === b)
  }

  test("large hexadecimal view") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), hexViewMode = HexadecimalViewMode.Large)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1C CC 54 C6 88 85 37 88  8E 91 A8 19 0D 8B 56 8A  |.ÌTÆ..7...¨...V.|
              |10: D6 38 A8 17 A3 7E 62 A4  44 28 A2 76              |Ö8¨.£~b¤D(¢v    |""".stripMargin
    assert(a === b)
  }

  test("undivided ASCII view") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), viewBytes = 32, asciiViewMode = AsciiViewMode.Undivided)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  D638 A817 A37E 62A4 4428 A276            |.ÌTÆ..7...¨...V.Ö8¨.£~b¤D(¢v    |"""
    assert(a === b)
  }

  test("default settings with 29 bytes per line") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), viewBytes = 29)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  D638 A817 A37E 62A4 4428 A276     |.ÌTÆ..7...¨...V.|Ö8¨.£~b¤D(¢v |"""
    assert(a === b)
  }


  test("undivided ASCII view with 29 bytes per line") {
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder), viewBytes = 29, asciiViewMode = AsciiViewMode.Undivided)
    dump(binary, settings)
    val a = builder.toString()
    val b = """00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  D638 A817 A37E 62A4 4428 A276     |.ÌTÆ..7...¨...V.Ö8¨.£~b¤D(¢v |"""
    assert(a === b)
  }

  test("default settings with whole byte value range") {
    val range = (0x00 to 0xFF).map(_.toByte).toArray
    val builder = new StringBuilder()
    val settings = settingsDefault.copy(output = Output(builder))
    dump(range, settings)
    val a = builder.toString()
    val b = """00: 0001 0203 0405 0607 0809 0A0B 0C0D 0E0F  |................|
              |10: 1011 1213 1415 1617 1819 1A1B 1C1D 1E1F  |................|
              |20: 2021 2223 2425 2627 2829 2A2B 2C2D 2E2F  | !"#$%&'()*+,-./|
              |30: 3031 3233 3435 3637 3839 3A3B 3C3D 3E3F  |0123456789:;<=>?|
              |40: 4041 4243 4445 4647 4849 4A4B 4C4D 4E4F  |@ABCDEFGHIJKLMNO|
              |50: 5051 5253 5455 5657 5859 5A5B 5C5D 5E5F  |PQRSTUVWXYZ[\]^_|
              |60: 6061 6263 6465 6667 6869 6A6B 6C6D 6E6F  |`abcdefghijklmno|
              |70: 7071 7273 7475 7677 7879 7A7B 7C7D 7E7F  |pqrstuvwxyz{|}~.|
              |80: 8081 8283 8485 8687 8889 8A8B 8C8D 8E8F  |................|
              |90: 9091 9293 9495 9697 9899 9A9B 9C9D 9E9F  |................|
              |A0: A0A1 A2A3 A4A5 A6A7 A8A9 AAAB ACAD AEAF  | ¡¢£¤¥¦§¨©ª«¬­®¯|
              |B0: B0B1 B2B3 B4B5 B6B7 B8B9 BABB BCBD BEBF  |°±²³´µ¶·¸¹º»¼½¾¿|
              |C0: C0C1 C2C3 C4C5 C6C7 C8C9 CACB CCCD CECF  |ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏ|
              |D0: D0D1 D2D3 D4D5 D6D7 D8D9 DADB DCDD DEDF  |ÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞß|
              |E0: E0E1 E2E3 E4E5 E6E7 E8E9 EAEB ECED EEEF  |àáâãäåæçèéêëìíîï|
              |F0: F0F1 F2F3 F4F5 F6F7 F8F9 FAFB FCFD FEFF  |ðñòóôõö÷øùúûüýþÿ|""".stripMargin
    assert(a === b)
  }

}
// scalastyle:on magic.number
