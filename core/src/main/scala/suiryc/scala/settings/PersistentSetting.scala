package suiryc.scala.settings

import suiryc.scala.misc.EnumerationEx


abstract class PersistentSetting[T]
{

  protected val settings: BaseSettings

  protected val path: String

  protected val default: T

  protected def prefsValue(default: T): T

  protected def configValue: T

  protected def configOption: Option[T] =
    if (!settings.config.hasPath(path)) None
    else Some(configValue)

  def apply(): T =
    /* XXX - more efficient way to check whether path exists and only use 'config' if not ? */
    prefsValue(configOption getOrElse(default))

  def update(v: T): Unit

}

class PersistentStringSetting(
  protected val path: String,
  protected val default: String
)(implicit val settings: BaseSettings)
  extends PersistentSetting[String]
{

  override protected def prefsValue(default: String): String =
    settings.prefs.get(path, default)

  override protected def configValue: String =
    settings.config.getString(path)

  def update(v: String) =
    settings.prefs.put(path, v)

}

class PersistentEnumerationExSetting[T <: EnumerationEx](
  protected val path: String,
  protected val default: T#Value
)(implicit val settings: BaseSettings, enum: T)
  extends PersistentSetting[T#Value]
{

  override protected def prefsValue(default: T#Value): T#Value =
    enum(settings.prefs.get(path, default.toString))

  override protected def configValue: T#Value =
    enum(settings.config.getString(path))

  def update(v: T#Value) =
    settings.prefs.put(path, v.toString)

}

object PersistentSetting {

  import scala.language.implicitConversions

  implicit def toValue[T](p :PersistentSetting[T]): T = p()

  def forString(path: String, default: String)(implicit settings: BaseSettings): PersistentSetting[String] =
    new PersistentStringSetting(path, default)

  def forEnumerationEx[T <: EnumerationEx](path: String, default: T#Value)(implicit settings: BaseSettings, enum: T): PersistentSetting[T#Value] =
    new PersistentEnumerationExSetting[T](path, default)

}
