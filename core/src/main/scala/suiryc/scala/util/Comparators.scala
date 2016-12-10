package suiryc.scala.util

import java.util.Comparator

/** Comparator helpers. */
object Comparators {

  /**
   * Builds a Comparator working on Options with Ordering.
   *
   * See: http://stackoverflow.com/a/7602389
   */
  def optionComparator[A: Ordering]: Comparator[Option[A]] = (o1: Option[A], o2: Option[A]) => {
    import scala.math.Ordering.Implicits._
    if (o1 == o2) 0
    else if (o1 < o2) -1
    else 1
  }

}
