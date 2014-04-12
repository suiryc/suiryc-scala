package suiryc.scala.settings

import com.typesafe.config.Config
import java.util.prefs.Preferences


class BaseSettings(
  val config: Config,
  _prefs: Preferences
) {

  private val recreatable = new RecreatablePreferences(_prefs)

  def prefs = recreatable.prefs

}
