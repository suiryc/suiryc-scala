package suiryc.scala.util


abstract class EitherEx[+A, +B](val either: Either[A, B]) {

  def apply(): B

}


object EitherEx {

  def apply[A, B](right: Right[A, B]): EitherEx[A, B] =
    new EitherEx(right) {
      override def apply(): B = right.value
    }

  def apply[A, B](left: Left[A, B], default : => B): EitherEx[A, B] =
    new EitherEx(left) {
      override def apply(): B = default
    }

  def apply[A, B](either: Either[A, B], default : => B): EitherEx[A, B] =
    new EitherEx(either) {
      override def apply(): B = either match {
        case Left(_) =>
          default

        case Right(v) =>
          v
      }
    }

}
