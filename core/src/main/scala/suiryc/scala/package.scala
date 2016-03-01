package suiryc

package object scala {

  // Note: class defined in package object so that we can make it 'implicit'

  /** Enrich Enumeration with case-insensitive name resolver. */
  implicit class RichEnumeration[A <: Enumeration](val enum: A) extends AnyVal {

    def byName(s: String): A#Value =
      enum.values.find(_.toString.toLowerCase == s.toLowerCase).getOrElse {
        throw new NoSuchElementException(s"No value found for '$s'")
      }

  }

}
