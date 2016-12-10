package suiryc.scala.settings

import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences
import suiryc.scala.RichEnumeration
import suiryc.scala.misc.{Enumeration => sEnumeration}

/**
 * Persistent setting value.
 *
 * Relies on both config and preference value.
 * If a preference is known, it is used, otherwise the config is used.
 *
 * Persistence is done through Preferences node.
 */
trait PersistentSetting[T] extends Preference[T]
{

  // Note: re-defining protected functions from Preference is necessary to make
  // them accessible inside the typeBuilder generated builder.
  override protected def prefsValue(default: T): T
  override protected def updateValue(v: T): Unit

  /** Base settings. */
  protected val settings: BaseSettings

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
class PersistentBooleanSetting(override protected val path: String, override val default: Boolean)
  (implicit val settings: BaseSettings)
  extends BooleanPreference(path, default)(settings.prefs) with PersistentSetting[Boolean] {
  override protected def configValue: Boolean = settings.config.getBoolean(path)
}

/** Int persistent setting. */
class PersistentIntSetting(override protected val path: String, override val default: Int)
  (implicit val settings: BaseSettings)
  extends IntPreference(path, default)(settings.prefs) with PersistentSetting[Int] {
  override protected def configValue: Int = settings.config.getInt(path)
}

/** Long persistent setting. */
class PersistentLongSetting(override protected val path: String, override val default: Long)
  (implicit val settings: BaseSettings)
  extends LongPreference(path, default)(settings.prefs) with PersistentSetting[Long] {
  override protected def configValue: Long = settings.config.getLong(path)
}

/** String persistent setting. */
class PersistentStringSetting(override protected val path: String, override val default: String)
  (implicit val settings: BaseSettings)
  extends StringPreference(path, default)(settings.prefs) with PersistentSetting[String] {
  override protected def configValue: String = settings.config.getString(path)
}

/** Enumeration persistent setting. */
class PersistentEnumerationSetting[T <: Enumeration](override protected val path: String, override val default: T#Value)
  (implicit val settings: BaseSettings, enum: T)
  extends EnumerationPreference[T](path, default)(settings.prefs, enum) with PersistentSetting[T#Value] {
  override protected def configValue: T#Value = enum.byName(settings.config.getString(path))
}

/** Special Enumeration persistent setting. */
class PersistentSEnumerationSetting[T <: sEnumeration](override protected val path: String, override val default: T#Value)
  (implicit val settings: BaseSettings, enum: T)
  extends SEnumerationPreference[T](path, default)(settings.prefs, enum) with PersistentSetting[T#Value] {
  override protected def configValue: T#Value = enum.withName(settings.config.getString(path))
}

/** Persistent setting builder. */
trait PersistentSettingBuilder[T] {
  def build(path: String, default: T)(implicit settings: BaseSettings): PersistentSetting[T]
}

object PersistentSetting {

  import scala.language.implicitConversions

  /** Implicit function to get actual value from a persistent setting. */
  implicit def toValue[T](p: PersistentSetting[T]): T = p()

  /** Builds a persistent setting for a type with implicit builder. */
  def from[T](path: String, default: T)(implicit settings: BaseSettings, builder: PersistentSettingBuilder[T]): PersistentSetting[T] =
    builder.build(path, default)

  /** Builds an Enumeration persistent setting. */
  def from[T <: Enumeration](path: String, default: T#Value)(implicit settings: BaseSettings, enum: T): PersistentSetting[T#Value] =
    new PersistentEnumerationSetting[T](path, default)

  /** Builds a special Enumeration persistent setting. */
  def from[T <: sEnumeration](path: String, default: T#Value)(implicit settings: BaseSettings, enum: T): PersistentSetting[T#Value] =
    new PersistentSEnumerationSetting[T](path, default)

  /** Boolean persistent setting builder. */
  implicit val booleanBuilder = new PersistentSettingBuilder[Boolean] {
    def build(path: String, default: Boolean)(implicit settings: BaseSettings): PersistentSetting[Boolean] =
      new PersistentBooleanSetting(path, default)
  }

  /** Int persistent setting builder. */
  implicit val intBuilder = new PersistentSettingBuilder[Int] {
    def build(path: String, default: Int)(implicit settings: BaseSettings): PersistentSetting[Int] =
      new PersistentIntSetting(path, default)
  }

  /** Long persistent setting builder. */
  implicit val longBuilder = new PersistentSettingBuilder[Long] {
    def build(path: String, default: Long)(implicit settings: BaseSettings): PersistentSetting[Long] =
      new PersistentLongSetting(path, default)
  }

  /** String persistent setting builder. */
  implicit val stringBuilder = new PersistentSettingBuilder[String] {
    def build(path: String, default: String)(implicit settings: BaseSettings): PersistentSetting[String] =
      new PersistentStringSetting(path, default)
  }

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
    new PersistentSettingBuilder[Outer] {
      def build(bpath: String, bdefault: Outer)(implicit bsettings: BaseSettings): PersistentSetting[Outer] = new PersistentSetting[Outer] {
        private val settingInner = innerBuilder.build(bpath, toInner(bdefault))
        override protected val path: String = bpath
        override val default: Outer = bdefault
        override val prefs: Preferences = bsettings.prefs
        override val settings: BaseSettings = bsettings
        override protected def prefsValue(default: Outer): Outer = settingInner.option.map(toOuter).getOrElse(default)
        override protected def updateValue(v: Outer): Unit = settingInner.updateValue(toInner(v))
        override protected def configValue: Outer = settingInner.configOption.map(toOuter).getOrElse(default)
      }
    }

  /** Path persistent setting builder. */
  implicit val pathBuilder: PersistentSettingBuilder[Path] = typeBuilder[Path, String](
    { p => Option(p).map(_.toString).orNull },
    { s => Option(s).map(Paths.get(_)).orNull }
  )

}
