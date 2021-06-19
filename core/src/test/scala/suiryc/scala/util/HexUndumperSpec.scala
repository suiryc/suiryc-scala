package suiryc.scala.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayOutputStream

// scalastyle:off magic.number non.ascii.character.disallowed
class HexUndumperSpec extends AnyWordSpec with Matchers {

  import HexUndumper._

  // 00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|
  // 10: D638 A817 A37E 62A4 4428 A276            |Ö8¨.£~b¤D(¢v    |
  private val binary: Array[Byte] =
    Hash("1CCC54C6888537888E91A8190D8B568AD638A817A37E62A44428A276").bytes

  private val dumperSettingsDefault = HexDumper.Settings(endWithEOL = false)

  "HexUndumper" should {

    "handle various number of bytes" in {
      for (input <- List(binary.take(3), binary.take(16), binary)) {
        val builder = new StringBuilder()
        val settings = dumperSettingsDefault.copy(output = HexDumper.Output(builder))
        HexDumper.dump(input, settings)
        val a = undump(builder.toString)
        a shouldBe input
      }
    }

    "handle specific length" in {
      val dump = """00 01 02 03 04 05 06 07 08 09"""
      val baos = new ByteArrayOutputStream()
      val settings = Settings(output = Output(baos), length = 3)
      undump(dump, settings)
      val a = baos.toByteArray
      val b = (0 to 2).map(_.toByte)
      a shouldBe b
    }

    "handle specific offset and length" in {
      val dump = """00 01 02 03 04 05 06 07 08 09"""
      val baos = new ByteArrayOutputStream()
      val settings = Settings(output = Output(baos), offset = 1, length = 3)
      undump(dump, settings)
      val a = baos.toByteArray
      val b = (1 to 3).map(_.toByte)
      a shouldBe b
    }

    "handle offset beyond data" in {
      val dump = """00 01 02 03 04 05 06 07 08 09"""
      val baos = new ByteArrayOutputStream()
      val settings = Settings(output = Output(baos), offset = 10)
      undump(dump, settings)
      val a = baos.toByteArray
      a shouldBe empty
    }

    "handle length beyond data" in {
      val dump = """00 01 02 03 04 05 06 07 08 09"""
      val baos = new ByteArrayOutputStream()
      val settings = Settings(output = Output(baos), length = 20)
      undump(dump, settings)
      val a = baos.toByteArray
      val b = (0 to 9).map(_.toByte)
      a shouldBe b
    }

    "handle multi-byte data" in {
      val dump = """0000 0000 0000 0000 0000 0000 0000 0000"""
      val a = undump(dump).toList
      val b = List.fill(16)(0x00.toByte)
      a shouldBe b
    }

    "handle authorized chars in hexadecimal view" in {
      val dump = """00 00-0000 0000 0000 0000 0000 0000 0000"""
      val a = undump(dump).toList
      val b = List.fill(16)(0x00.toByte)
      a shouldBe b
    }

    "handle matching pattern in ASCII view" in {
      val dump = """00: 0000 0000 0000 0000 0000 0000 0000 0000  |00: 1122|"""
      val a = undump(dump).toList
      val b = List.fill(16)(0x00.toByte)
      a shouldBe b
    }

  }

}
// scalastyle:on magic.number non.ascii.character.disallowed
