package suiryc.scala

import java.util.Optional


object RichOption {

  import scala.language.implicitConversions

  implicit def fromOptional[T](optional: Optional[T]): Option[T] =
    if (optional.isPresent) Some(optional.get)
    else None

}
