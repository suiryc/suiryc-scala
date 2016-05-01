package suiryc.scala.math

import java.time.LocalDate
import java.time.chrono.ChronoLocalDate

/** Ordering helpers. */
object Ordering {

  /**
    * Creates an Ordering from classes extending Comparable in a superclass.
    *
    * A class 'A' may subclass 'B' while 'B' is a 'Comparable[B]', in which case
    * default Ordering implicits don't kick in for 'A'.
    */
  def comparableOrdering[A <: Comparable[B], B >: A]: Ordering[A] = new Ordering[A] {
    def compare(a1: A, a2: A): Int = a1.compareTo(a2)
  }

  // Note: to get an 'Ordering[A]' when 'A <: Comparable[A]',
  // simply 'import scala.math.Ordering._'.

  /** Ordering for LocalDate. */
  implicit val localDateOrdering = comparableOrdering[LocalDate, ChronoLocalDate]

}
