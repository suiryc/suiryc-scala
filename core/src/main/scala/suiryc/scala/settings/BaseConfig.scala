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

  /** Implicit function to get String config value. */
  implicit def string(config: Config, path: String): String =
    config.getString(path)

  /** Implicit function to get Boolean config value. */
  implicit def boolean(config: Config, path: String): Boolean =
    config.getBoolean(path)

  /** Implicit function to get Int config value. */
  implicit def int(config: Config, path: String): Int =
    config.getInt(path)

  /** Implicit function to get Long config value. */
  implicit def long(config: Config, path: String): Long =
    config.getLong(path)

  /** Implicit function to get Double config value. */
  implicit def double(config: Config, path: String): Double =
    config.getDouble(path)

}
