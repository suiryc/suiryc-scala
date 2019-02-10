package suiryc.scala.settings

import com.typesafe.config.{ConfigValue, ConfigValueFactory}
import javafx.beans.property.SimpleObjectProperty

/**
 * Config entry snapshot.
 *
 * Unlike generic snapshot, we wish to be able to keep the 'raw' (often String)
 * representation of values (initial, default and changed). That way, what was
 * configured or changed is used as-is, instead of interpreting the values
 * (and altering the original visual representation).
 */
abstract class ConfigEntrySnapshot[Inner, Outer](setting: ConfigEntry[Inner]) extends SettingSnapshot[Outer] {

  /** Draft raw value (prepared to apply value change). */
  val rawDraft = new SimpleObjectProperty[ConfigValue]()

  /** Original raw value. */
  protected val rawOriginal: Option[ConfigValue] = {
    if (setting.exists) Some(setting.settings.config.getValue(setting.path))
    else None
  }
  // Set original raw value as draft raw value.
  rawOriginal.foreach(rawDraft.set)

  /** Default raw value. */
  protected val rawDefault: Option[ConfigValue] = {
    if (setting.refExists) Some(setting.settings.reference.getValue(setting.path))
    else None
  }

  /**
   * Sets draft raw value.
   *
   * Caller is expected to also change the draft value.
   */
  def setRawDraft(v: Any): Unit = rawDraft.set(ConfigValueFactory.fromAnyRef(v))

  override protected def applyChange(v: Outer): Unit = {
    // First apply the change
    super.applyChange(v)
    // But actually force the raw value if applicable
    Option(rawDraft.get).foreach { raw ⇒
      // Do not change raw value if either:
      //  - the real value is the same as the default one: the underlying config
      //    entry did 'reset' itself
      //  - nothing changed compared to original raw value: should mean caller
      //    only cared about (and changed) the real value
      if (!setting.refOpt.contains(v) && !rawOriginal.contains(raw)) {
        setting.settings.withValue(setting.path, raw)
      }
    }
    // Note: the side effect is that setting the raw value may make the
    // underlying portable settings saved a second time.
    // Since this may not happen too often, or may already happen in other
    // circumstances, it is better taken care of in the portable settings if
    // needed.
  }

  override def resetDraft(original: Boolean = true, refresh: Boolean = true): Unit = {
    // First reset draft value
    super.resetDraft(original, refresh)
    // Then also reset draft raw value
    if (original) rawDraft.set(rawOriginal.orNull)
    else rawDraft.set(rawDefault.orNull)
  }

}

// Snapshot for value itself.
abstract class ConfigStdEntrySnapshot[A](setting: ConfigEntry[A]) extends ConfigEntrySnapshot[A, A](setting) {
  // Automatically get default reference value when applicable.
  if (setting.refExists) withDefault(setting.refGet)
}

// Snapshot that works as optional value.
abstract class ConfigOptEntrySnapshot[A](setting: ConfigEntry[A]) extends ConfigEntrySnapshot[A, Option[A]](setting) {
  // Automatically get default reference value.
  withDefault(setting.refOpt)
}
