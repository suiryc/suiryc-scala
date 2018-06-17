package suiryc.scala.util

import org.scalatest.FunSuite

// scalastyle:off magic.number
class HashSuite extends FunSuite {

  private val hex = "0123456789abcdef"
  private val bytes = Array[Byte](0x01, 0x23, 0x45, 0x67, 0x89.asInstanceOf[Byte],
    0xab.asInstanceOf[Byte], 0xcd.asInstanceOf[Byte], 0xef.asInstanceOf[Byte])

  test("Hexadecimal representation shall match initial one") {
    val a = Hash(hex)
    assert(hex === a.hex)
  }

  test("Hexadecimal representation shall match initial bytes array") {
    val a = Hash(bytes)
    assert(hex === a.hex)
  }

  test("Bytes array shall match initial one") {
    val a = Hash(bytes)
    assert(bytes === a.bytes)
  }

  test("Bytes array shall match initial hexadecimal representation") {
    val a = Hash(hex)
    assert(bytes === a.bytes)
  }

  test("Object equality") {
    val a = Hash(hex)
    val b = Hash(bytes)
    assert(a === b)
  }

  test("Implicit conversion from hexadecimal representation") {
    val a = Hash(hex)
    val b: Hash = hex
    assert(a === b)
  }

  test("Implicit conversion from bytes array") {
    val a = Hash(hex)
    val b: Hash = bytes
    assert(a === b)
  }

  test("null bytes array is an illegal argument") {
    intercept[IllegalArgumentException] {
      // scalastyle:off null
      Hash(null.asInstanceOf[Array[Byte]])
      // scalastyle:on null
    }
    ()
  }

  test("null hexadecimal representation is an illegal argument") {
    intercept[IllegalArgumentException] {
      // scalastyle:off null
      Hash(null.asInstanceOf[String])
      // scalastyle:on null
    }
    ()
  }

  test("Odd hexadecimal representation") {
    val a = Hash("123")
    val b = Hash(Array[Byte](0x01, 0x23))
    assert(a === b)
  }

}
// scalastyle:on magic.number
