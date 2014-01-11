package suiryc.scala.misc


abstract class EitherEx[+A, +B](val either: Either[A, B]) {

  def apply(): B

}


object EitherEx {

  def apply[A, B](right: Right[A, B]) =
    new EitherEx(right) {
      override def apply() = right.b
    }

  def apply[A, B](left: Left[A, B], default : => B) =
    new EitherEx(left) {
      override def apply() = default
    }

  def apply[A, B](either: Either[A, B], default : => B) =
    new EitherEx(either) {
      override def apply() = either match {
        case Left(_) =>
          default

        case Right(v) =>
          v
      }
    }

}
