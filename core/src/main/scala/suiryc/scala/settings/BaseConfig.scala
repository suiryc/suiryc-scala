package suiryc.scala.settings

import com.typesafe.config.Config
import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}
import suiryc.scala.misc.Util

/**
 * Base config helper.
 *
 * Get typed value (optional or not) from config using implicit functions.
 */
class BaseConfig(val config: Config) extends BaseConfigImplicits {

  /** Get typed config value. */
  def value[T](path: String, config: Config = config)(implicit t: (Config, String) => T): T =
    t(config, path)

  /** Get typed optional config value. */
  def option[T](path: String, config: Config = config)(implicit t: (Config, String) => T): Option[T] =
    if (config.hasPath(path)) Some(t(config, path)) else None

}

object BaseConfig {

  def toDuration(v: java.time.Duration): FiniteDuration = Duration.fromNanos(v.toNanos)

}

trait BaseConfigImplicits {

  // Note: implicit functions don't work when chosen name is used in imported
  // context (e.g. as variable name).

  /** Implicit function to get Config config value. */
  implicit def configGetConfig(config: Config, path: String): Config =
    config.getConfig(path)

  /** Implicit function to get Config List config value. */
  implicit def configGetConfigList(config: Config, path: String): List[Config] =
    config.getConfigList(path).asScala.toList

  /** Implicit function to get String config value. */
  implicit def configGetString(config: Config, path: String): String =
    config.getString(path)

  /** Implicit function to get String List config value. */
  implicit def configGetStringList(config: Config, path: String): List[String] =
    config.getStringList(path).asScala.toList

  /** Implicit function to get Boolean config value. */
  implicit def configGetBoolean(config: Config, path: String): Boolean =
    config.getBoolean(path)

  /** Implicit function to get Boolean List config value. */
  implicit def configGetBooleanList(config: Config, path: String): List[Boolean] =
    config.getBooleanList(path).asScala.toList.map(Boolean.unbox)

  /** Implicit function to get Int config value. */
  implicit def configGetInt(config: Config, path: String): Int =
    config.getInt(path)

  /** Implicit function to get Int List config value. */
  implicit def configGetIntList(config: Config, path: String): List[Int] =
    config.getIntList(path).asScala.toList.map(Int.unbox)

  /** Implicit function to get Long config value. */
  implicit def configGetLong(config: Config, path: String): Long =
    config.getLong(path)

  /** Implicit function to get Long List config value. */
  implicit def configGetLongList(config: Config, path: String): List[Long] =
    config.getLongList(path).asScala.toList.map(Long.unbox)

  /** Implicit function to get Double config value. */
  implicit def configGetDouble(config: Config, path: String): Double =
    config.getDouble(path)

  /** Implicit function to get Double List config value. */
  implicit def configGetDoubleList(config: Config, path: String): List[Double] =
    config.getDoubleList(path).asScala.toList.map(Double.unbox)

  /** Implicit function to get Duration (as scala) config value. */
  implicit def configGetDuration(config: Config, path: String): FiniteDuration = {
    // Try to parse duration as-is (best effort to keep original unit), and
    // fallbacks to Config duration parsing upon issue.
    try {
      Util.parseDuration(config.getString(path)).asInstanceOf[FiniteDuration]
    } catch {
      case _: Exception ⇒ BaseConfig.toDuration(config.getDuration(path))
    }
  }

  /** Implicit function to get Duration (as scala) List config value. */
  implicit def configGetDurationList(config: Config, path: String): List[FiniteDuration] = {
    // Try to parse duration as-is (best effort to keep original unit), and
    // fallbacks to Config duration parsing upon issue.
    try {
      config.getStringList(path).asScala.toList.map(Util.parseDuration(_).asInstanceOf[FiniteDuration])
    } catch {
      case _: Exception ⇒ config.getDurationList(path).asScala.toList.map(BaseConfig.toDuration)
    }
  }

}
