package suiryc.scala.settings

import com.typesafe.config.Config

/**
 * Base config helper.
 *
 * Get typed value (optional or not) from config using implicit functions.
 */
class BaseConfig(val config: Config) {

  /** Get typed config value. */
  def value[T](path: String, config: Config = config)(implicit t: (Config, String) => T): T =
    t(config, path)

  /** Get typed optional config value. */
  def option[T](path: String, config: Config = config)(implicit t: (Config, String) => T): Option[T] =
    if (config.hasPath(path)) Some(t(config, path)) else None

}

object BaseConfig {

  // Note: implicit functions don't work when chosen name is used in imported
  // context (e.g. as variable name).

  /** Implicit function to get Config config value. */
  implicit def configGetConfig(config: Config, path: String): Config =
    config.getConfig(path)

  /** Implicit function to get String config value. */
  implicit def configGetString(config: Config, path: String): String =
    config.getString(path)

  /** Implicit function to get Boolean config value. */
  implicit def configGetBoolean(config: Config, path: String): Boolean =
    config.getBoolean(path)

  /** Implicit function to get Int config value. */
  implicit def configGetInt(config: Config, path: String): Int =
    config.getInt(path)

  /** Implicit function to get Long config value. */
  implicit def configGetLong(config: Config, path: String): Long =
    config.getLong(path)

  /** Implicit function to get Double config value. */
  implicit def configGetDouble(config: Config, path: String): Double =
    config.getDouble(path)

}
