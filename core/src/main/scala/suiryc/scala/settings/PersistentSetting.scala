package suiryc.scala.settings

import com.typesafe.config.ConfigException
import suiryc.scala.misc.{Enumeration => sEnumeration}
import suiryc.scala.misc.EnumerationEx

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

  /** Base settings. */
  protected val settings: BaseSettings

  /**
   * Gets the config value.
   *
   * @throws ConfigException.Missing if the value is not configured
   * @throws ConfigException.WrongType if the value is not convertible to the requested type
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
    // XXX - more efficient way to check whether path exists and only use 'config' if not ?
    //option getOrElse(default)

}

/** Boolean persistent setting. */
class PersistentBooleanSetting(
  override protected val path: String,
  override val default: Boolean
)(implicit val settings: BaseSettings)
  extends BooleanPreference(path, default)(settings.prefs)
  with PersistentSetting[Boolean]
{

  override protected def configValue: Boolean =
    settings.config.getBoolean(path)

}

/** Long persistent setting. */
class PersistentLongSetting(
  override protected val path: String,
  override val default: Long
)(implicit val settings: BaseSettings)
  extends LongPreference(path, default)(settings.prefs)
  with PersistentSetting[Long]
{

  override protected def configValue: Long =
    settings.config.getLong(path)

}

/** String persistent setting. */
class PersistentStringSetting(
  override protected val path: String,
  override val default: String
)(implicit val settings: BaseSettings)
  extends StringPreference(path, default)(settings.prefs)
  with PersistentSetting[String]
{

  override protected def configValue: String =
    settings.config.getString(path)

}

/** EnumerationEx persistent setting. */
class PersistentEnumerationExSetting[T <: EnumerationEx](
  override protected val path: String,
  override val default: T#Value
)(implicit val settings: BaseSettings, enum: T)
  extends EnumerationExPreference[T](path, default)(settings.prefs, enum)
  with PersistentSetting[T#Value]
{

  override protected def configValue: T#Value =
    enum(settings.config.getString(path))

}

/** Scala Enumeration persistent setting. */
class PersistentSEnumerationSetting[T <: sEnumeration](
  override protected val path: String,
  override val default: T#Value
)(implicit val settings: BaseSettings, enum: T)
  extends SEnumerationPreference[T](path, default)(settings.prefs, enum)
  with PersistentSetting[T#Value]
{

  override protected def configValue: T#Value =
    enum.withName(settings.config.getString(path))

}

object PersistentSetting {

  import scala.language.implicitConversions

  /** Implicit function to get actual value from a persistent setting. */
  implicit def toValue[T](p: PersistentSetting[T]): T = p()

  /** Builds a Boolean persistent setting. */
  def forBoolean(path: String, default: Boolean)(implicit settings: BaseSettings): PersistentSetting[Boolean] =
    new PersistentBooleanSetting(path, default)

  /** Builds a Long persistent setting. */
  def forLong(path: String, default: Long)(implicit settings: BaseSettings): PersistentSetting[Long] =
    new PersistentLongSetting(path, default)

  /** Builds a String persistent setting. */
  def forString(path: String, default: String)(implicit settings: BaseSettings): PersistentSetting[String] =
    new PersistentStringSetting(path, default)

  /** Builds an EnumerationEx persistent setting. */
  def forEnumerationEx[T <: EnumerationEx](path: String, default: T#Value)(implicit settings: BaseSettings, enum: T): PersistentSetting[T#Value] =
    new PersistentEnumerationExSetting[T](path, default)

  /** Builds a scala Enumeration persistent setting. */
  def forSEnumeration[T <: sEnumeration](path: String, default: T#Value)(implicit settings: BaseSettings, enum: T): PersistentSetting[T#Value] =
    new PersistentSEnumerationSetting[T](path, default)

}
