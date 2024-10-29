package suiryc.scala

import com.typesafe.config.{Config, ConfigFactory, ConfigValue}

import java.nio.file.Path
import scala.concurrent.duration._


object Configuration {

  val KEY_LIB = "suiryc-scala"

  private val knownExtensions = Set(".conf", ".json", ".properties")

  private def withoutKnownExtension(name: String): String = {
    val nameLC = name.toLowerCase()
    if (knownExtensions.exists(nameLC.endsWith)) {
      // Strip known extension.
      name.replaceFirst("""\.[^.]*$""", "")
    } else name
  }

  private def withExtension(name: String): String = {
    val nameLC = name.toLowerCase()
    if (knownExtensions.exists(nameLC.endsWith)) {
      name
    } else {
      s"$name.conf"
    }
  }

  /**
   * Sets default application configuration.
   *
   * Does nothing if already done, which is the case when either 'config.file',
   * 'config.resource' or 'config.url' environment variable has been set.
   *
   * @param confPath explicit path, when applicable
   * @param resourceName resource name or base name, when applicable
   */
  def setDefaultApplication(confPath: Option[Path] = None, resourceName: Option[String] = None): Unit = {
    // Change the default application configuration unless already done.
    // This is needed when a specific path or resource name is used, instead of
    // the standard 'application.conf'.
    // This only applies to new configuration objects being loaded, and thus
    // *must* be done ASAP so that external libraries will use the wanted
    // configuration.
    if (
      (System.getProperty("config.file") == null) &&
        getConfigResource.isEmpty &&
        (System.getProperty("config.url") == null)
    ) {
      // Reminder: 'config.resource' must be a filename with extension.
      confPath match {
        case Some(p) => System.setProperty("config.file", p.toString)
        case None    => resourceName.foreach(name => System.setProperty("config.resource", withExtension(name)))
      }
      ConfigFactory.invalidateCaches()
    }
  }

  private def getConfigResource: Option[String] = {
    Option(System.getProperty("config.resource"))
  }

  /** Loads a specific application configuration, with our overriding. */
  def load(confPath: Option[Path] = None, resourceName: Option[String] = None): Config = {
    // If given resource name is "application" (with optional known extension),
    // ignore it since this is the default already.
    val actualResourceName = resourceName.filterNot { name =>
      withoutKnownExtension(name) == "application"
    }
    // Use given configuration file/resource, and fallback to usual application
    // and reference.conf.
    setDefaultApplication(confPath, actualResourceName)

    // Notes:
    // Unlike in previous versions, Config now properly handle overriding
    // 'reference.conf' values from 'application.conf' (or any other explicit
    // file used) before resolving.
    // See: https://github.com/lightbend/config/commit/ab890103dd86542c26440371ea26cae4a00a7a22
    // Thus we don't have to build the configuration layers ourselves: we can
    // use configuration overriding, and it will work in libraries also relying
    // on Config.

    // Then build real (resolved) configuration, the usual way.
    ConfigFactory.load()
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
