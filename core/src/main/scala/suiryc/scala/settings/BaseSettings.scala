package suiryc.scala.settings

import com.typesafe.config.Config
import java.util.prefs.Preferences


class BaseSettings(
  val config: Config,
  val prefs: Preferences
)
