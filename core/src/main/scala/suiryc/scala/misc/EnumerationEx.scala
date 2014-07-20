package suiryc.scala.misc

import scala.language.postfixOps


class EnumerationEx extends scala.Enumeration {

  def apply(s: String): Value = {
    values.find { _.toString().toLowerCase() == s.toLowerCase() } get
  }

}
