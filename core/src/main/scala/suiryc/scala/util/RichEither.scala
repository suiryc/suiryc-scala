package suiryc.scala.misc

import scala.language.implicitConversions


class RichEither[A, B](val underlying: Either[A, B]) extends AnyVal {

  def &&[A2 >: A, B2 >: B](other: => Either[A2, B2]) = underlying match {
    case Left(_) => underlying
    case Right(_) => other
  }

  def ||[A2 >: A, B2 >: B](other: => Either[A2, B2]) = underlying match {
    case Left(_) => other
    case Right(_) => underlying
  }

  def &&[A2 >: A, B2 >: B](other: => Unit) = underlying match {
    case Left(_) =>
    case Right(_) => other
  }

  def ||[A2 >: A, B2 >: B](other: => Unit) = underlying match {
    case Left(_) => other
    case Right(_) =>
  }

}


object RichEither {

  implicit def toRich[A, B](either: Either[A, B]) =
    new RichEither(either)

}
