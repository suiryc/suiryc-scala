package suiryc.scala

import com.typesafe.config.{Config, ConfigFactory, ConfigValue}

import java.nio.file.Path
import scala.concurrent.duration._


object Configuration {

  val KEY_LIB = "suiryc-scala"

  /** Loads a specific application configuration, with our overridings. */
  def load(confPath: Option[Path] = None, resourceName: Option[String] = None): Config = {
    // Use requested configuration file and fallback to usual application and
    // reference.conf.
    // Either we load the file from an explicit path, or from the classpath.
    val appConf = confPath.map { path =>
      ConfigFactory.parseFile(path.toFile)
    }.orElse {
      resourceName.map(ConfigFactory.parseResources)
    }
    // Build the configuration layers ourselves mainly because we wish for our
    // configuration file (and application.conf) to truly override values in
    // reference.conf, including variables substitutions.
    // By default (e.g. ConfigFactory.load()), reference.conf is resolved first
    // then overridden with application.conf (which is then resolved too). For
    // both, environment variables can override values.
    // So if reference.conf contains 'key-b = ${key-a}', overriding 'key-a'
    // value only affects 'key-b' when done through environment variables, not
    // through application.conf.
    // Here we layer configuration files and only resolve the final result.
    // See https://github.com/lightbend/config/issues/342
    // In Play: https://github.com/playframework/playframework/blob/master/core/play/src/main/scala/play/api/Configuration.scala
    val confs =
      ConfigFactory.defaultOverrides() ::
      appConf.toList :::
      List(
        ConfigFactory.defaultApplication(),
        ConfigFactory.parseResources("reference.conf")
      )
    confs.reduceLeft(_.withFallback(_)).resolve()
  }

  /** Default application configuration, with overrides. */
  val loaded: Config = load()

  /** Library configuration ('suiryc-scala' path). */
  val libConfig: Config = loaded.getConfig(Configuration.KEY_LIB)

}

object ConfigurationConstants {

  val LOGGER = "logger"
  val LOGGER_RELOAD = s"$LOGGER.reload"
  val LOGGER_RELOAD_CHANGES = s"$LOGGER_RELOAD.changes"

}

/** Config helpers. */
object ConfigTools {

  implicit class RichConfig(private val config: Config) extends AnyVal {

    // Note: the 'optional' helpers should not be used if default value can
    // be configured in the reference configuration.

    @inline private final def getOptional[A](path: String)(f: String => A): Option[A] = {
      if (config.hasPath(path)) {
        Some(f(path))
      } else {
        None
      }
    }

    /** Gets value if path exists. */
    def getOptionalValue(path: String): Option[ConfigValue] = getOptional(path)(config.getValue)

    /** Gets boolean if path exists. */
    def getOptionalBoolean(path: String): Option[Boolean] = getOptional(path)(config.getBoolean)

    /** Gets int if path exists. */
    def getOptionalInt(path: String): Option[Int] = getOptional(path)(config.getInt)

    /** Gets int if path exists. */
    def getOptionalDouble(path: String): Option[Double] = getOptional(path)(config.getDouble)

    /** Gets string if path exists. */
    def getOptionalString(path: String): Option[String] = getOptional(path)(config.getString)

    /** Gets config if path exists. */
    def getOptionalConfig(path: String): Option[Config] = getOptional(path)(config.getConfig)

    /** Gets string and ensures it is non-empty. */
    def getNonEmptyString(path: String): String = {
      val v = config.getString(path)
      if (v.isEmpty) throw new Exception(s"'$path' must not be empty")
      v
    }

    /**
     * Gets duration as scala value.
     *
     * Note that original unit may be lost (60s gives 1 minute), which usually
     * is not an issue.
     */
    def getScalaDuration(path: String): FiniteDuration = {
      Duration.fromNanos(config.getDuration(path).toNanos)
    }

  }

}
