package suiryc.scala.settings

import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences
import scala.concurrent.duration.{Duration, FiniteDuration}
import suiryc.scala.RichEnumeration
import Preference._

/**
 * Preference value.
 *
 * Functions to interact with a given Preferences node value.
 */
trait Preference[T] {

  /** Preferences node getter. */
  protected val prefsGetter: PreferencesGetter

  /** Preferences node. */
  protected def prefs: Preferences = prefsGetter()

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
  protected def removeValue(): Unit =
    prefs.remove(path)

  /**
   * Gets the optional preference value.
   *
   * @return value or None if not present
   */
  // scalastyle:off null
  def option: Option[T] =
    Option(prefs.get(path, null)) match {
      case Some(_) =>
        Some(prefsValue(default))

      case None =>
        None
    }
  // scalastyle:on null

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
class BooleanPreference(protected val prefsGetter: PreferencesGetter, protected val path: String, val default: Boolean)
  extends Preference[Boolean]
{
  override protected def prefsValue(default: Boolean): Boolean = prefs.getBoolean(path, default)
  override protected def updateValue(v: Boolean): Unit = prefs.putBoolean(path, v)
}

/** Int preference. */
class IntPreference(protected val prefsGetter: PreferencesGetter, protected val path: String, val default: Int)
  extends Preference[Int]
{
  override protected def prefsValue(default: Int): Int = prefs.getInt(path, default)
  override protected def updateValue(v: Int): Unit = prefs.putInt(path, v)
}

/** Long preference. */
class LongPreference(protected val prefsGetter: PreferencesGetter, protected val path: String, val default: Long)
  extends Preference[Long]
{
  override protected def prefsValue(default: Long): Long = prefs.getLong(path, default)
  override protected def updateValue(v: Long): Unit = prefs.putLong(path, v)
}

/** String preference. */
class StringPreference(protected val prefsGetter: PreferencesGetter, protected val path: String, val default: String)
  extends Preference[String]
{
  override protected def prefsValue(default: String): String = prefs.get(path, default)
  override protected def updateValue(v: String): Unit = prefs.put(path, v)
}

/** Enumeration preference. */
class EnumerationPreference[T <: Enumeration](protected val prefsGetter: PreferencesGetter, protected val path: String, enum: T, val default: T#Value)
  extends Preference[T#Value]
{
  override protected def prefsValue(default: T#Value): T#Value = enum.byName(prefs.get(path, default.toString))
  override protected def updateValue(v: T#Value): Unit = prefs.put(path, v.toString)
}

/** Preference builder. */
trait PreferenceBuilder[T] {
  def build(prefsGetter: PreferencesGetter, path: String, default: T): Preference[T]
}

object Preference {

  import scala.language.implicitConversions

  protected[settings] type PreferencesGetter = () => Preferences

  /** Implicit function to get actual value from a preference. */
  implicit def toValue[T](p: Preference[T]): T = p()

  /** Builds a preference for a type with implicit builder. */
  def from[T](prefsGetter: PreferencesGetter, path: String, default: T)(implicit builder: PreferenceBuilder[T]): Preference[T] =
    builder.build(prefsGetter, path, default)

  /** Builds an Enumeration preference. */
  def from[T <: Enumeration](prefsGetter: PreferencesGetter, path: String, enum: T, default: T#Value): Preference[T#Value] =
    new EnumerationPreference(prefsGetter, path, enum, default)

  /** RecreatablePreferences getter. */
  implicit def recreatableToGetter(recreatable: RecreatablePreferences): PreferencesGetter = () => recreatable.prefs

  /** Simple Preferences getter. */
  implicit def prefsToGetter(prefs: Preferences): PreferencesGetter = () => prefs

  /** BaseSettings Preferences getter. */
  implicit def settingsToGetter(settings: BaseSettings): PreferencesGetter = () => settings.prefs

  /** Boolean preference builder. */
  implicit val booleanBuilder: PreferenceBuilder[Boolean] =
    (prefsGetter: PreferencesGetter, path: String, default: Boolean) => new BooleanPreference(prefsGetter, path, default)

  /** Int preference builder. */
  implicit val intBuilder: PreferenceBuilder[Int] =
    (prefsGetter: PreferencesGetter, path: String, default: Int) => new IntPreference(prefsGetter, path, default)

  /** Long preference builder. */
  implicit val longBuilder: PreferenceBuilder[Long] =
    (prefsGetter: PreferencesGetter, path: String, default: Long) => new LongPreference(prefsGetter, path, default)

  /** String preference builder. */
  implicit val stringBuilder: PreferenceBuilder[String] =
    (prefsGetter: PreferencesGetter, path: String, default: String) => new StringPreference(prefsGetter, path, default)

  /**
   * Gets a preference builder mapping between Outer and Inner types.
   *
   * Uses given conversion functions.
   * Note that given functions must handle possibly 'null' values.
   *
   * @param toInner function to convert value from Inner to Outer type
   * @param toOuter function to convert value from Outer to Inner type
   */
  def typeBuilder[Outer, Inner](toInner: Outer => Inner, toOuter: Inner => Outer)
    (implicit innerBuilder: PreferenceBuilder[Inner]): PreferenceBuilder[Outer] =
    (bprefsGetter: PreferencesGetter, bpath: String, bdefault: Outer) => new Preference[Outer] {
      private val prefInner = innerBuilder.build(bprefsGetter, bpath, toInner(bdefault))
      override protected val path: String = bpath
      override val default: Outer = bdefault
      override val prefsGetter: PreferencesGetter = bprefsGetter
      override protected def prefsValue(default: Outer): Outer = prefInner.option.map(toOuter).getOrElse(default)
      override protected def updateValue(v: Outer): Unit = prefInner.updateValue(toInner(v))
    }

  /** Path preference builder. */
  implicit val pathBuilder: PreferenceBuilder[Path] = typeBuilder[Path, String](
    { p => Option(p).map(_.toString).orNull },
    { s => Option(s).map(Paths.get(_)).orNull }
  )

  /** Duration preference builder. */
  implicit val durationBuilder: PreferenceBuilder[Duration] = typeBuilder[Duration, String](
    { p => Option(p).map(_.toString).orNull },
    { s => Option(s).map(Duration.apply).orNull }
  )

  /** FiniteDuration preference builder. */
  implicit val finiteDurationBuilder: PreferenceBuilder[FiniteDuration] = typeBuilder[FiniteDuration, String](
    { p => Option(p).map(_.toString).orNull },
    { s => Option(s).map(Duration.apply(_).asInstanceOf[FiniteDuration]).orNull }
  )

}
