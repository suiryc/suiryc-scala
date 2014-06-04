package suiryc.scala.settings

import com.typesafe.config.Config


class BaseConfig(val config: Config) {

  def value[T](path: String, config: Config = config)(implicit t: (Config, String) => T): T =
    t(config, path)

  def option[T](path: String, config: Config = config)(implicit t: (Config, String) => T): Option[T] =
    if (config.hasPath(path)) Some(t(config, path)) else None

}

object BaseConfig {

  implicit def string(config: Config, path: String): String =
    config.getString(path)

  implicit def boolean(config: Config, path: String): Boolean =
    config.getBoolean(path)

  implicit def int(config: Config, path: String): Int =
    config.getInt(path)

  implicit def long(config: Config, path: String): Long =
    config.getLong(path)

  implicit def double(config: Config, path: String): Double =
    config.getDouble(path)

}
