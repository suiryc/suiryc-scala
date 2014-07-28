package suiryc.scala.settings

import suiryc.scala.misc.{Enumeration => sEnumeration}
import suiryc.scala.misc.EnumerationEx


abstract class PersistentSetting[T]
{

  protected val settings: BaseSettings

  protected val path: String

  val default: T

  protected def prefsValue(default: T): T

  protected def configValue: T

  protected def updateValue(v: T): Unit

  protected def removeValue() =
    settings.prefs.remove(path)

  protected def prefsOption: Option[T] =
    Option(settings.prefs.get(path, null)) match {
      case Some(_) =>
        Some(prefsValue(default))

      case None =>
        None
    }

  protected def configOption: Option[T] =
    if (!settings.config.hasPath(path)) None
    else Some(configValue)

  def option: Option[T] =
    prefsOption orElse(configOption)

  def apply(): T =
    prefsValue(configOption getOrElse(default))
    /* XXX - more efficient way to check whether path exists and only use 'config' if not ? */
    /*option getOrElse(default)*/

  def update(v: T): Unit =
    Option(v).fold {
      removeValue()
    } { v =>
      updateValue(v)
    }

}

class PersistentBooleanSetting(
  protected val path: String,
  val default: Boolean
)(implicit val settings: BaseSettings)
  extends PersistentSetting[Boolean]
{

  override protected def prefsValue(default: Boolean): Boolean =
    settings.prefs.getBoolean(path, default)

  override protected def configValue: Boolean =
    settings.config.getBoolean(path)

  override protected def updateValue(v: Boolean) =
    settings.prefs.putBoolean(path, v)

}

class PersistentLongSetting(
  protected val path: String,
  val default: Long
)(implicit val settings: BaseSettings)
  extends PersistentSetting[Long]
{

  override protected def prefsValue(default: Long): Long =
    settings.prefs.getLong(path, default)

  override protected def configValue: Long =
    settings.config.getLong(path)

  override protected def updateValue(v: Long) =
    settings.prefs.putLong(path, v)

}

class PersistentStringSetting(
  protected val path: String,
  val default: String
)(implicit val settings: BaseSettings)
  extends PersistentSetting[String]
{

  override protected def prefsValue(default: String): String =
    settings.prefs.get(path, default)

  override protected def configValue: String =
    settings.config.getString(path)

  override protected def updateValue(v: String) =
    settings.prefs.put(path, v)

}

class PersistentEnumerationExSetting[T <: EnumerationEx](
  protected val path: String,
  val default: T#Value
)(implicit val settings: BaseSettings, enum: T)
  extends PersistentSetting[T#Value]
{

  override protected def prefsValue(default: T#Value): T#Value =
    enum(settings.prefs.get(path, default.toString))

  override protected def configValue: T#Value =
    enum(settings.config.getString(path))

  override protected def updateValue(v: T#Value) =
    settings.prefs.put(path, v.toString)

}

class PersistentSEnumerationSetting[T <: sEnumeration](
  protected val path: String,
  val default: T#Value
)(implicit val settings: BaseSettings, enum: T)
  extends PersistentSetting[T#Value]
{

  override protected def prefsValue(default: T#Value): T#Value =
    enum.withName(settings.prefs.get(path, default.toString))

  override protected def configValue: T#Value =
    enum.withName(settings.config.getString(path))

  override protected def updateValue(v: T#Value) =
    settings.prefs.put(path, v.toString)

}

object PersistentSetting {

  import scala.language.implicitConversions

  implicit def toValue[T](p :PersistentSetting[T]): T = p()

  def forBoolean(path: String, default: Boolean)(implicit settings: BaseSettings): PersistentSetting[Boolean] =
    new PersistentBooleanSetting(path, default)

  def forLong(path: String, default: Long)(implicit settings: BaseSettings): PersistentSetting[Long] =
    new PersistentLongSetting(path, default)

  def forString(path: String, default: String)(implicit settings: BaseSettings): PersistentSetting[String] =
    new PersistentStringSetting(path, default)

  def forEnumerationEx[T <: EnumerationEx](path: String, default: T#Value)(implicit settings: BaseSettings, enum: T): PersistentSetting[T#Value] =
    new PersistentEnumerationExSetting[T](path, default)

  def forSEnumeration[T <: sEnumeration](path: String, default: T#Value)(implicit settings: BaseSettings, enum: T): PersistentSetting[T#Value] =
    new PersistentSEnumerationSetting[T](path, default)

}
