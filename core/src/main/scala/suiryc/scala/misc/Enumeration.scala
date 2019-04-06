package suiryc.scala.misc


/**
 * Mark an enumeration as case-insensitive.
 *
 * This of course only matters for code here, there is not impact on original
 * Enumeration behaviour.
 */
trait CaseInsensitiveEnumeration extends scala.Enumeration

/** Enumeration that can attach aliases to values. */
trait EnumerationWithAliases extends scala.Enumeration {

  // Notes:
  // We cannot override the 'Value' class, so we will extend 'Val' to embed our
  // aliases; this is enough since we don't plan to make the aliases visible.
  // We also cannot override final method 'withName', so define our own.

  // Whether name and aliases are case insensitive.
  private val caseInsensitive = this.isInstanceOf[CaseInsensitiveEnumeration]

  // Normalizes value (lowercase when case-insensitive).
  @inline
  private def normalize(s: String): String = if (caseInsensitive) s.toLowerCase else s

  protected class ValWithAliases(i: Int, name: String, val aliases: Set[String]) extends Val(i, name) {
    def this(name: String, aliases: Seq[String]) = this(nextId, name, aliases.toSet)
    val normalized: Set[String] = (aliases + name).map(normalize)
  }

  /**
   * Finds value by name (or alias).
   *
   * Handles case-insensitive values when applicable.
   */
  def byName(s: String): Value = {
    values.find {
      case withAliases: ValWithAliases ⇒ withAliases.normalized.contains(normalize(s))
      case v: Val ⇒ normalize(s) == normalize(v.toString)
    }.getOrElse {
      throw new NoSuchElementException(s"No value found for '$s'")
    }
  }

}
