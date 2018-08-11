package suiryc.scala

import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}


object Configuration {

  /** Application configuration, with our reference.conf overriding. */
  val loaded: Config = {
    // Load configuration and have our own reference.conf overriding inserted at
    // the right place. See https://github.com/lightbend/config/issues/342
    // See how it is handled in Play:
    // https://github.com/playframework/playframework/blob/master/framework/src/play/src/main/scala/play/api/Configuration.scala
    // Note: parseResources do not resolve values, which is what we want here -
    // at least in our reference.conf we point to a value that is present in
    // our reference-overrides.conf.
    List(
      ConfigFactory.defaultOverrides(),
      ConfigFactory.defaultApplication(ConfigParseOptions.defaults.setAllowMissing(true)),
      ConfigFactory.parseResources("suiryc-scala/reference-overrides.conf"),
      ConfigFactory.parseResources("reference.conf"),
      ConfigFactory.defaultReference()
    ).reduceLeft(_.withFallback(_)).resolve()
  }

}
