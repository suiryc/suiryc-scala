package suiryc.scala.misc

trait Default[A] {
  def value: A
}

trait LowPriorityImplicitsForDefault { this: Default.type =>
  // scalastyle:off null
  implicit def forAnyRef[A](implicit ev: Null <:< A): Default[A] = Default.withValue(ev(null))
  // scalastyle:on null
}

/**
 * Helper to get default value for a given type.
 *
 * See: http://missingfaktor.blogspot.fr/2011/08/emulating-cs-default-keyword-in-scala.html
 */
object Default extends LowPriorityImplicitsForDefault {
  def withValue[A](a: A): Default[A] = new Default[A] {
    def value: A = a
  }

  implicit val forBoolean: Default[Boolean] = Default.withValue(false)
  implicit val forChar: Default[Char] = Default.withValue(' ')
  implicit def forNumeric[A](implicit n: Numeric[A]): Default[A] = Default.withValue(n.zero)
  implicit val forString: Default[String] = Default.withValue("")
  implicit def forOption[A]: Default[Option[A]] = Default.withValue(None: Option[A])
  // TODO - default value for a collection would be 'empty'

  /** Gets the default value for a given type. */
  def value[A : Default]: A = implicitly[Default[A]].value
}
