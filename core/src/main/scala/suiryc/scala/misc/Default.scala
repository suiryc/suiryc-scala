package suiryc.scala.misc

trait Default[A] {
  def value: A
}

trait LowPriorityImplicitsForDefault { this: Default.type =>
  implicit def forAnyRef[A](implicit ev: Null <:< A): Default[A] = Default.withValue(ev(null))
}

/**
 * Helper to get default value for a given type.
 *
 * See: http://missingfaktor.blogspot.fr/2011/08/emulating-cs-default-keyword-in-scala.html
 */
object Default extends LowPriorityImplicitsForDefault {
  def withValue[A](a: A): Default[A] = new Default[A] {
    def value = a
  }

  implicit val forBoolean = Default.withValue(false)
  implicit val forChar = Default.withValue(' ')
  implicit def forNumeric[A](implicit n: Numeric[A]): Default[A] = Default.withValue(n.zero)
  implicit val forString = Default.withValue("")
  implicit def forOption[A]: Default[Option[A]] = Default.withValue(None:Option[A])
  // TODO - default value for a collection would be 'empty'

  /** Gets the default value for a given type. */
  def value[A : Default]: A = implicitly[Default[A]].value
}
