package suiryc.scala.util

/**
 * Hash class companion object.
 */
object Hash {

  import scala.language.implicitConversions

  /** Factory method from bytes array. */
  def apply(bytes: Array[Byte]): Hash = {
    // scalastyle:off null
    require(bytes != null)
    // scalastyle:on null

    new Hash(bytes)
  }

  /** Factory method from hexadecimal string. */
  def apply(hex: String): Hash = {
    // scalastyle:off null
    require(hex != null)
    // scalastyle:on null

    val actual: String = if (hex.length % 2 == 1)
      "0" + hex
    else
      hex

    // scalastyle:off magic.number
    Hash(actual.grouped(2).map(Integer.parseInt(_, 16).asInstanceOf[Byte]).toArray)
    // scalastyle:on magic.number
  }

  /** Implicit conversion from hexadecimal string. */
  implicit def hexToHash(hex: String): Hash = Hash(hex)

  /** Implicit conversion from bytes array. */
  implicit def bytesToHash(bytes: Array[Byte]): Hash = Hash(bytes)

  /** Implicit conversion to hexadecimal string. */
  def hashToString(hash: Hash): String = hash.hex

  /** Implicit conversion to bytes array. */
  def hashToBytes(hash: Hash): Array[Byte] = hash.bytes

}

/**
 * Hash value.
 */
class Hash private (val bytes: Array[Byte]) {

  /** Hexadecimal representation. */
  lazy val hex = bytes.map("%02x".format(_)).mkString("")

  override def toString: String = hex

  override def hashCode: Int = hex.hashCode

  override def equals(other: Any): Boolean = other match {
    case that: Hash => this.hex == that.hex
    case _ => false
  }

}
