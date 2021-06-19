package suiryc.scala.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// scalastyle:off magic.number
class HashSpec extends AnyWordSpec with Matchers {

  private val hex = "0123456789abcdef"
  private val bytes = Array[Byte](0x01, 0x23, 0x45, 0x67, 0x89.asInstanceOf[Byte],
    0xab.asInstanceOf[Byte], 0xcd.asInstanceOf[Byte], 0xef.asInstanceOf[Byte])

  "Hash" should {

    "have hexadecimal text representation for bytes array" in {
      val a = Hash(bytes)
      a.hex shouldBe hex
    }

    "keep bytes array" in {
      val a = Hash(bytes)
      a.bytes shouldBe bytes
    }

    "parse hexadecimal text" in {
      val a = Hash(hex)
      a.hex shouldBe hex
      a.bytes shouldBe bytes
    }

    "handle object equality" in {
      val a = Hash(hex)
      val b = Hash(bytes)
      a shouldBe b
    }

    "have implicit conversion from hexadecimal representation" in {
      val a = Hash(hex)
      val b: Hash = hex
      b shouldBe a
    }

    "have implicit conversion from bytes array" in {
      val a = Hash(hex)
      val b: Hash = bytes
      b shouldBe a
    }

    "reject null bytes array as an illegal argument" in {
      assertThrows[IllegalArgumentException] {
        // scalastyle:off null
        Hash(null.asInstanceOf[Array[Byte]])
        // scalastyle:on null
      }
    }

    "reject null hexadecimal representation as an illegal argument" in {
      assertThrows[IllegalArgumentException] {
        // scalastyle:off null
        Hash(null.asInstanceOf[String])
        // scalastyle:on null
      }
    }

    "handle parding odd hexadecimal representation" in {
      val a = Hash("123")
      val b = Hash(Array[Byte](0x01, 0x23))
      a shouldBe b
    }

  }

}
// scalastyle:on magic.number
