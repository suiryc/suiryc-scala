package suiryc.scala.settings

import com.typesafe.config.Config
import java.util.prefs.Preferences

/**
 * Base settings helper.
 *
 * Relies on both config and Preferences sources.
 * Also automatically re-creates Preferences node if necessary (prevents
 * triggering exceptions when clearing node at runtime).
 */
class BaseSettings(
  override val config: Config,
  _prefs: Preferences
) extends BaseConfig(config)
{

  private val recreatable = new RecreatablePreferences(_prefs)

  /** Gets (automatically re-created if necessary) underlying Preferences node. */
  def prefs = recreatable.prefs

}
