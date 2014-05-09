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


class RichEitherException[B](val underlying: Either[Exception, B]) extends AnyVal {

  def orThrow: B = underlying match {
    case Left(e) => throw e
    case Right(v) => v
  }

}


object RichEither {

  implicit def toRichException[B](either: Either[Exception, B]) =
    new RichEitherException(either)

  implicit def toRich[A, B](either: Either[A, B]) =
    new RichEither(either)

}
