package suiryc.scala.misc

import scala.collection.SortedSet
import scala.reflect.NameTransformer._
import scala.util.matching.Regex

/**
 * Tweaked from http://stackoverflow.com/a/4236489 to have helper functions
 * similar to scala.Enumeration (some code taken from original).
 */
abstract class Enumeration {

  import Enumeration._

  protected val caseSensitive = true

  type Value <: BaseValue

  protected[misc] trait BaseValue
    extends Ordered[BaseValue]
  { this: Value =>

    val id: Int = nextId

    val name: String

    override def compare(that: BaseValue): Int =
      if (this.id < that.id) -1
      else if (this.id == that.id) 0
      else 1

    override def toString: String = name

    nextId += 1
    add(this)

  }

  protected object ValueOrdering extends Ordering[Value] {
    def compare(x: Value, y: Value): Int = x compare y
  }

  type ValueSet = SortedSet[Value]

  private var nextId = 0

  def maxId: Int = nextId

  protected def ordering: Ordering[Value] = ValueOrdering

  private var _values: ValueSet = SortedSet[Value]()(ordering)

  private def add(value: Value) = {
    _values += value
    value
  }

  def values: ValueSet = _values

  def apply(n: Int): Value =
    values.find(_.id == n).get

  def apply(name: String): Value =
    withName(name)

  def withName(name: String): Value =
    values.find { value =>
      if (caseSensitive) value.name == name
      else value.name.toLowerCase == name.toLowerCase
    }.orElse { values.find {
        case aliased: Aliased =>
          if (caseSensitive) aliased.aliases.contains(name)
          else aliased.aliases.map(_.toLowerCase).contains(name.toLowerCase)

        case _ =>
          false
      }
    }.get

  override def toString: String =
    ((getClass.getName stripSuffix MODULE_SUFFIX_STRING split '.').last split
       Regex.quote(NAME_JOIN_STRING)).last

}

object Enumeration {

  trait Aliased {
    val aliases: List[String]
  }

}
