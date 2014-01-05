package suiryc.scala.misc

import scala.language.postfixOps


class EnumerationEx extends Enumeration {

  def apply(s: String): Value = {
    values.find { _.toString().toLowerCase() == s.toLowerCase() } get
  }

}
