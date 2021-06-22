package suiryc.scala

import com.typesafe.config.{Config, ConfigFactory}
import java.nio.file.Path


object Configuration {

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

}
