package suiryc

import suiryc.scala.misc.EnumerationWithAliases

package object scala {

  // Note: class defined in package object so that we can make it 'implicit'

  /** Enrich Enumeration with case-insensitive name resolver. */
  implicit class RichEnumeration[A <: Enumeration](val enum: A) extends AnyVal {

    private type WithAliases = A with EnumerationWithAliases
    private def withAliases: Option[WithAliases] = enum match {
      case v: EnumerationWithAliases => Some(v.asInstanceOf[WithAliases])
      case _ => None
    }

    def byName(s: String): A#Value = withAliases.map(v => v.byName(s): A#Value).getOrElse {
      enum.values.find(_.toString.toLowerCase == s.toLowerCase).getOrElse {
        throw new NoSuchElementException(s"No value found for '$s'")
      }
    }

  }

}
