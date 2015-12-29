package suiryc.scala.util


// scalastyle:off method.name
class RichEither[A, B](val underlying: Either[A, B]) extends AnyVal {

  def &&[A2 >: A, B2 >: B](other: => Either[A2, B2]): Either[A2, B2] = underlying match {
    case Left(_) => underlying
    case Right(_) => other
  }

  def ||[A2 >: A, B2 >: B](other: => Either[A2, B2]): Either[A2, B2] = underlying match {
    case Left(_) => other
    case Right(_) => underlying
  }

  def &&[A2 >: A, B2 >: B](other: => Unit): Unit = underlying match {
    case Left(_) =>
    case Right(_) => other
  }

  def ||[A2 >: A, B2 >: B](other: => Unit): Unit = underlying match {
    case Left(_) => other
    case Right(_) =>
  }

}
// scalastyle:on method.name


class RichEitherThrowable[B](val underlying: Either[Throwable, B]) extends AnyVal {

  def orThrow: B = underlying match {
    case Left(e) => throw e
    case Right(v) => v
  }

}


object RichEither {

  import scala.language.implicitConversions

  implicit def toRichThrowable[B](either: Either[Throwable, B]): RichEitherThrowable[B] =
    new RichEitherThrowable(either)

  implicit def toRich[A, B](either: Either[A, B]): RichEither[A, B] =
    new RichEither(either)

}
