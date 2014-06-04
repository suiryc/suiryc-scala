package suiryc.scala.settings

import com.typesafe.config.Config
import java.util.prefs.Preferences


class BaseSettings(
  override val config: Config,
  _prefs: Preferences
) extends BaseConfig(config)
{

  private val recreatable = new RecreatablePreferences(_prefs)

  def prefs = recreatable.prefs

}
