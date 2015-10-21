package suiryc.scala.misc


class RichOptional[T](val underlying: T) extends AnyVal {

  def optional[U](option: Option[U], f: (T, U) => T): T =
    option.fold(underlying)(f(underlying, _))

  def optional(option: Boolean, f: T => T): T =
    if (option) f(underlying) else underlying

}

object RichOptional {
  import scala.language.implicitConversions

  implicit def anyToRichOptional[T](value: T): RichOptional[T] =
    new RichOptional[T](value)

}
