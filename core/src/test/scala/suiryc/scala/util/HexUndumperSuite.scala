package suiryc.scala.util

import java.io.ByteArrayOutputStream
import org.scalatest.FunSuite

// scalastyle:off magic.number non.ascii.character.disallowed
class HexUndumperSuite extends FunSuite {

  import HexUndumper._

  // 00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|
  // 10: D638 A817 A37E 62A4 4428 A276            |Ö8¨.£~b¤D(¢v    |
  private val binary: Array[Byte] =
    Hash("1CCC54C6888537888E91A8190D8B568AD638A817A37E62A44428A276").bytes

  val dumperSettingsDefault = HexDumper.Settings(endWithEOL = false)

  test("undump various number of bytes") {
    for (input <- List(binary.take(3), binary.take(16), binary)) {
      val builder = new StringBuilder()
      val settings = dumperSettingsDefault.copy(output = HexDumper.Output(builder))
      HexDumper.dump(input, settings)
      val a = undump(builder.toString)
      assert(a === input)
    }
  }

  test("undump with specific length") {
    val dump = """00 01 02 03 04 05 06 07 08 09"""
    val baos = new ByteArrayOutputStream()
    val settings = Settings(output = Output(baos), length = 3)
    undump(dump, settings)
    val a = baos.toByteArray
    val b = (0 to 2).map(_.toByte)
    assert(a === b)
  }

  test("undump with specific offset and length") {
    val dump = """00 01 02 03 04 05 06 07 08 09"""
    val baos = new ByteArrayOutputStream()
    val settings = Settings(output = Output(baos), offset = 1, length = 3)
    undump(dump, settings)
    val a = baos.toByteArray
    val b = (1 to 3).map(_.toByte)
    assert(a === b)
  }

  test("undump with offset beyond data") {
    val dump = """00 01 02 03 04 05 06 07 08 09"""
    val baos = new ByteArrayOutputStream()
    val settings = Settings(output = Output(baos), offset = 10)
    undump(dump, settings)
    val a = baos.toByteArray
    assert(a.isEmpty)
  }

  test("undump with length beyond data") {
    val dump = """00 01 02 03 04 05 06 07 08 09"""
    val baos = new ByteArrayOutputStream()
    val settings = Settings(output = Output(baos), length = 20)
    undump(dump, settings)
    val a = baos.toByteArray
    val b = (0 to 9).map(_.toByte)
    assert(a === b)
  }

  test("undump without offset prefix") {
    val dump = """0000 0000 0000 0000 0000 0000 0000 0000"""
    val a = undump(dump).toList
    val b = List.fill(16)(0x00.toByte)
    assert(a === b)
  }

  test("undump with authorized chars in hexadecimal view") {
    val dump = """00 00-0000 0000 0000 0000 0000 0000 0000"""
    val a = undump(dump).toList
    val b = List.fill(16)(0x00.toByte)
    assert(a === b)
  }

  test("undump with matching pattern in ASCII view") {
    val dump = """00: 0000 0000 0000 0000 0000 0000 0000 0000  |00: 1122|"""
    val a = undump(dump).toList
    val b = List.fill(16)(0x00.toByte)
    assert(a === b)
  }

}
// scalastyle:on magic.number non.ascii.character.disallowed
