package suiryc.scala.misc

import scala.language.postfixOps


class XEnumeration extends Enumeration {

  def apply(s: String): Value = {
    values.find { _.toString().toLowerCase() == s.toLowerCase() } get
  }

}
