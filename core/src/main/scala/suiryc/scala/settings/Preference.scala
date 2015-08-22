package suiryc.scala.settings

import java.util.prefs.Preferences
import suiryc.scala.misc.{Enumeration => sEnumeration}
import suiryc.scala.misc.EnumerationEx

/**
 * Preference value.
 *
 * Functions to interact with a given Preferences node value.
 */
trait Preference[T] {

  /** Preferences node. */
  protected val prefs: Preferences

  /** Key of the value to interact with. */
  protected val path: String

  /** Default value if key is not present in node. */
  val default: T

  /**
   * Gets the preference value.
   *
   * @return value or null if not present
   */
  protected def prefsValue(default: T): T

  /** Updates the preference value. */
  protected def updateValue(v: T): Unit

  /** Removes the preference value. */
  protected def removeValue() =
    prefs.remove(path)

  /**
   * Gets the optional preference value.
   *
   * @return value or None if not present
   */
  def option: Option[T] =
    Option(prefs.get(path, null)) match {
      case Some(_) =>
        Some(prefsValue(default))

      case None =>
        None
    }

  /**
   * Gets the preference value.
   *
   * @return value or default if not present
   */
  def apply(): T =
    prefsValue(default)

  /**
   * Updates the preference value.
   *
   * Removes value if new value is null.
   */
  def update(v: T): Unit =
    Option(v).fold {
      removeValue()
    } { v =>
      updateValue(v)
    }

}

/** Boolean preference. */
class BooleanPreference(protected val path: String, val default: Boolean)(implicit val prefs: Preferences) extends Preference[Boolean] {
  override protected def prefsValue(default: Boolean): Boolean = prefs.getBoolean(path, default)
  override protected def updateValue(v: Boolean) = prefs.putBoolean(path, v)
}

/** Long preference. */
class LongPreference(protected val path: String, val default: Long)(implicit val prefs: Preferences) extends Preference[Long] {
  override protected def prefsValue(default: Long): Long = prefs.getLong(path, default)
  override protected def updateValue(v: Long) = prefs.putLong(path, v)
}

/** String preference. */
class StringPreference(protected val path: String, val default: String)(implicit val prefs: Preferences) extends Preference[String] {
  override protected def prefsValue(default: String): String = prefs.get(path, default)
  override protected def updateValue(v: String) = prefs.put(path, v)
}

/** EnumerationEx preference. */
class EnumerationExPreference[T <: EnumerationEx](protected val path: String, val default: T#Value)(implicit val prefs: Preferences, enum: T) extends Preference[T#Value] {
  override protected def prefsValue(default: T#Value): T#Value = enum(prefs.get(path, default.toString))
  override protected def updateValue(v: T#Value) = prefs.put(path, v.toString)
}

/** Special Enumeration preference. */
class SEnumerationPreference[T <: sEnumeration](protected val path: String, val default: T#Value)(implicit val prefs: Preferences, enum: T) extends Preference[T#Value] {
  override protected def prefsValue(default: T#Value): T#Value = enum.withName(prefs.get(path, default.toString))
  override protected def updateValue(v: T#Value) = prefs.put(path, v.toString)
}

/** Preference builder. */
trait PreferenceBuilder[T] {
  def build(path: String, default: T)(implicit prefs: Preferences): Preference[T]
}

object Preference {

  import scala.language.implicitConversions

  /** Implicit function to get actual value from a preference. */
  implicit def toValue[T](p: Preference[T]): T = p()

  /** Builds a preference for a type with implicit builder. */
  def from[T](path: String, default: T)(implicit prefs: Preferences, builder: PreferenceBuilder[T]): Preference[T] =
    builder.build(path, default)

  /** Builds an EnumerationEx preference. */
  def from[T <: EnumerationEx](path: String, default: T#Value)(implicit prefs: Preferences, enum: T): Preference[T#Value] =
    new EnumerationExPreference(path, default)

  /** Builds a special Enumeration preference. */
  def from[T <: sEnumeration](path: String, default: T#Value)(implicit prefs: Preferences, enum: T): Preference[T#Value] =
    new SEnumerationPreference(path, default)

  /** Boolean preference builder. */
  implicit val booleanBuilder = new PreferenceBuilder[Boolean] {
    def build(path: String, default: Boolean)(implicit prefs: Preferences): Preference[Boolean] = new BooleanPreference(path, default)
  }

  /** Long preference builder. */
  implicit val longBuilder = new PreferenceBuilder[Long] {
    def build(path: String, default: Long)(implicit prefs: Preferences): Preference[Long] = new LongPreference(path, default)
  }

  /** String preference builder. */
  implicit val stringBuilder = new PreferenceBuilder[String] {
    def build(path: String, default: String)(implicit prefs: Preferences): Preference[String] = new StringPreference(path, default)
  }

}
