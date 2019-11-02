package suiryc.scala

import com.typesafe.config.{Config, ConfigFactory}
import java.nio.file.Path


object Configuration {

  private def getDefaultResourceName(resourceName: Option[String]): String = {
    resourceName.getOrElse("application-override.conf")
  }

  /**
   * Sets default application configuration.
   *
   * Useful for libraries/frameworks that do use ConfigFactory.load() without
   * possibility to pass a specific Config instance.
   * Must be called first, before any library/framework tries to load its
   * configuration.
   */
  def setDefaultApplication(confPath: Option[Path] = None, resourceName: Option[String] = None): Unit = {
    // Change the default application configuration unless already done. This
    // is needed for libraries that use ConfigFactory.load() because some of
    // them use 'application.conf' instead of 'reference.conf' and we usually
    // have no guarantee that an application.conf embedded in our resources
    // would actually override 3rd-party ones.
    // We thus use an application-override.conf which must be included from
    // the actual application configuration file, or can at least be used as
    // default application configuration resource.
    if ((System.getProperty("config.file") == null) &&
      (System.getProperty("config.resource") == null) &&
      (System.getProperty("config.url") == null)) {
      confPath match {
        case Some(p) => System.setProperty("config.file", p.toString)
        case None    => System.setProperty("config.resource", getDefaultResourceName(resourceName))
      }
      ConfigFactory.invalidateCaches()
    }
  }

  /**
   * Loads a specific application configuration, with our overridings.
   *
   * Sets the default application configuration before loading it.
   * Better called first, before any library/framework tries to load its
   * configuration.
   */
  def load(confPath: Option[Path] = None, resourceName: Option[String] = None): Config = {
    // Use requested configuration file and fallback to usual application and
    // reference.conf.
    // Either we load the file from an explicit path, or from the classpath.
    setDefaultApplication(confPath, resourceName)
    val appConf = confPath.map { path =>
      ConfigFactory.parseFile(path.toFile)
    }.getOrElse {
      ConfigFactory.parseResources(getDefaultResourceName(resourceName))
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
    List(
      ConfigFactory.defaultOverrides(),
      appConf,
      ConfigFactory.defaultApplication(),
      ConfigFactory.parseResources("reference.conf")
    ).reduceLeft(_.withFallback(_)).resolve()
  }

  /** Default application configuration, with overridings. */
  val loaded: Config = load()

}
