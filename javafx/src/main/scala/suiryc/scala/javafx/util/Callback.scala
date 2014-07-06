package suiryc.scala.javafx.util

import javafx.{util => jfxu}

object Callback {

  import scala.language.implicitConversions

  implicit def fn1ToCallback[P, R](fn: Function1[P, R]): jfxu.Callback[P, R] = {
    new jfxu.Callback[P, R] {
      override def call(p: P): R =
        fn(p)
    }
  }

}
