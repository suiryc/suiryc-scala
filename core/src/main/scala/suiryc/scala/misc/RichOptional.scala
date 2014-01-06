package suiryc.scala.misc

import scala.language.implicitConversions


class RichOptional[T](val underlying: T) extends AnyVal {

  def optional[U](option: Option[U], f: Function2[T, U, T]): T =
    option.fold(underlying)(f(underlying, _))

  def optional(option: Boolean, f: T => T): T =
    if (option) f(underlying) else underlying

}

object RichOptional {
  import scala.language.implicitConversions

  implicit def anyToRichOptional[T](value: T): RichOptional[T] =
    new RichOptional[T](value)

}
