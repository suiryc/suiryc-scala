package suiryc.scala.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

// scalastyle:off magic.number
@RunWith(classOf[JUnitRunner])
class HexUndumperSuite extends FunSuite {

  import HexUndumper._

  // 00: 1CCC 54C6 8885 3788 8E91 A819 0D8B 568A  |.ÌTÆ..7...¨...V.|
  // 10: D638 A817 A37E 62A4 4428 A276            |Ö8¨.£~b¤D(¢v    |
  private val binary: Array[Byte] =
    Hash("1CCC54C6888537888E91A8190D8B568AD638A817A37E62A44428A276").bytes

  val dumperSettingsDefault = HexDumper.Settings(endWithEOL = false)

  test("undump") {
    for (input <- List(binary.take(3), binary.take(16), binary)) {
      val builder = new StringBuilder()
      val settings = dumperSettingsDefault.copy(output = HexDumper.Output(builder))
      HexDumper.dump(input, settings)
      val a = builder.toString()
      val b = undump(a)
      assert(b === input)
    }
  }

  test("undump with matching pattern in ASCII view") {
    val dump ="""00: 0000 0000 0000 0000 0000 0000 0000 0000  |00: 1122|"""
    val a = List.fill(16)(0x00.toByte)
    val b = undump(dump).toList
    assert(b === a)
  }

}
// scalastyle:on magic.number
