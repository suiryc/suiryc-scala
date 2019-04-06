package suiryc.scala.settings

import java.nio.file.Path
import java.nio.file.Paths
import suiryc.scala.RichEnumeration
import suiryc.scala.settings.Preference._

/**
 * Persistent setting value.
 *
 * Relies on both config and preference value.
 * If a preference is known, it is used, otherwise the config is used.
 *
 * Persistence is done through Preferences node.
 */
trait PersistentSetting[T] extends Preference[T] {

  // Note: re-defining protected functions from Preference is necessary to make
  // them accessible inside the typeBuilder generated builder.
  override protected def prefsValue(default: T): T
  override protected def updateValue(v: T): Unit

  /** Base settings. */
  protected val settings: BaseSettings

  override val prefsGetter: PreferencesGetter = settings

  /**
   * Gets the config value.
   *
   * Note: scaladoc is not capable of correctly handling declared thrown exceptions.
   * Throws ConfigException.Missing if the value is not configured
   * Throws ConfigException.WrongType if the value is not convertible to the requested type
   */
  protected def configValue: T

  /**
   * Gets the optional config value.
   *
   * @return value or None if not present
   */
  protected def configOption: Option[T] =
    if (!settings.config.hasPath(path)) None
    else Some(configValue)

  /**
   * Gets the optional value.
   *
   * First search in preference, then config.
   *
   * @return value or None if not present
   */
  override def option: Option[T] =
    super.option.orElse(configOption)

  /**
   * Gets the value.
   *
   * First search in preference, then config.
   *
   * @return value or default if not present
   */
  override def apply(): T =
    prefsValue(configOption.getOrElse(default))
    // TODO - more efficient way to check whether path exists and only use 'config' if not ?
    //option getOrElse(default)

}

/** Boolean persistent setting. */
class PersistentBooleanSetting(protected val settings: BaseSettings, override protected val path: String, override val default: Boolean)
  extends BooleanPreference(settings, path, default) with PersistentSetting[Boolean] {
  override protected def configValue: Boolean = settings.config.getBoolean(path)
}

/** Int persistent setting. */
class PersistentIntSetting(protected val settings: BaseSettings, override protected val path: String, override val default: Int)
  extends IntPreference(settings, path, default) with PersistentSetting[Int] {
  override protected def configValue: Int = settings.config.getInt(path)
}

/** Long persistent setting. */
class PersistentLongSetting(protected val settings: BaseSettings, override protected val path: String, override val default: Long)
  extends LongPreference(settings, path, default) with PersistentSetting[Long] {
  override protected def configValue: Long = settings.config.getLong(path)
}

/** String persistent setting. */
class PersistentStringSetting(protected val settings: BaseSettings, override protected val path: String, override val default: String)
  extends StringPreference(settings, path, default) with PersistentSetting[String] {
  override protected def configValue: String = settings.config.getString(path)
}

/** Enumeration persistent setting. */
class PersistentEnumerationSetting[T <: Enumeration](protected val settings: BaseSettings, override protected val path: String,
  enum: T, override val default: T#Value)
  extends EnumerationPreference[T](settings, path, enum, default) with PersistentSetting[T#Value]
{
  override protected def configValue: T#Value = enum.byName(settings.config.getString(path))
}

/** Persistent setting builder. */
trait PersistentSettingBuilder[T] {
  def build(settings: BaseSettings, path: String, default: T): PersistentSetting[T]
}

object PersistentSetting {

  import scala.language.implicitConversions

  /** Implicit function to get actual value from a persistent setting. */
  implicit def toValue[T](p: PersistentSetting[T]): T = p()

  /** Builds a persistent setting for a type with implicit builder. */
  def from[T](settings: BaseSettings, path: String, default: T)(implicit builder: PersistentSettingBuilder[T]): PersistentSetting[T] =
    builder.build(settings, path, default)

  /** Builds an Enumeration persistent setting. */
  def from[T <: Enumeration](settings: BaseSettings, path: String, enum: T, default: T#Value): PersistentSetting[T#Value] =
    new PersistentEnumerationSetting[T](settings, path, enum, default)

  /** Boolean persistent setting builder. */
  implicit val booleanBuilder: PersistentSettingBuilder[Boolean] =
    (settings: BaseSettings, path: String, default: Boolean) => new PersistentBooleanSetting(settings, path, default)

  /** Int persistent setting builder. */
  implicit val intBuilder: PersistentSettingBuilder[Int] =
    (settings: BaseSettings, path: String, default: Int) => new PersistentIntSetting(settings, path, default)

  /** Long persistent setting builder. */
  implicit val longBuilder: PersistentSettingBuilder[Long] =
    (settings: BaseSettings, path: String, default: Long) => new PersistentLongSetting(settings, path, default)

  /** String persistent setting builder. */
  implicit val stringBuilder: PersistentSettingBuilder[String] =
    (settings: BaseSettings, path: String, default: String) => new PersistentStringSetting(settings, path, default)

  /**
   * Gets a persistent setting builder mapping between Outer and Inner types.
   *
   * Uses given conversion functions.
   * Note that given functions must handle possibly 'null' values.
   *
   * @param toInner function to convert value from Inner to Outer type
   * @param toOuter function to convert value from Outer to Inner type
   */
  def typeBuilder[Outer, Inner](toInner: Outer => Inner, toOuter: Inner => Outer)
    (implicit innerBuilder: PersistentSettingBuilder[Inner]): PersistentSettingBuilder[Outer] =
    (bsettings: BaseSettings, bpath: String, bdefault: Outer) => new PersistentSetting[Outer] {
      private val settingInner = innerBuilder.build(bsettings, bpath, toInner(bdefault))
      override protected val path: String = bpath
      override val default: Outer = bdefault
      override val settings: BaseSettings = bsettings
      override protected def prefsValue(default: Outer): Outer = settingInner.option.map(toOuter).getOrElse(default)
      override protected def updateValue(v: Outer): Unit = settingInner.updateValue(toInner(v))
      override protected def configValue: Outer = settingInner.configOption.map(toOuter).getOrElse(default)
    }

  /** Path persistent setting builder. */
  implicit val pathBuilder: PersistentSettingBuilder[Path] = typeBuilder[Path, String](
    { p => Option(p).map(_.toString).orNull },
    { s => Option(s).map(Paths.get(_)).orNull }
  )

}
