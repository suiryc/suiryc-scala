package suiryc.scala.math

import java.time.LocalDate
import java.time.chrono.ChronoLocalDate

/** Ordered helpers. */
object Ordered {

  import scala.language.implicitConversions

  class ComparableOrdered[A <: Comparable[A]](val a: A) extends AnyVal with Ordered[A] {
    def compare(b: A): Int = a.compareTo(b)
  }

  /**
   * Creates an Ordered from classes extending Comparable in a superclass.
   *
   * A class 'A' may subclass 'B' while 'B' is a 'Comparable[B]', in which case
   * default Ordered implicits don't kick in for 'A'.
   */
  class SubComparableOrdered[A <: Comparable[B], B >: A](val a: A) extends AnyVal with Ordered[A] {
    def compare(b: A): Int = a.compareTo(b)
  }

  // Note: to get an 'Ordered[A]' when 'A <: Comparable[A]',
  // simply 'import scala.math.Ordered._'.

  /** Ordered for LocalDate. */
  implicit def localDateToOrdered(a: LocalDate): Ordered[LocalDate] =
    new SubComparableOrdered[LocalDate, ChronoLocalDate](a)

}
