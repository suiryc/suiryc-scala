package suiryc.scala.settings

import com.typesafe.config.Config
import java.util.prefs.Preferences


class BaseSettings(
  protected[settings] val config: Config,
  protected[settings] val prefs: Preferences
)
