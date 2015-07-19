package suiryc.scala.javafx.beans.property

import javafx.beans.property.SimpleObjectProperty
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.settings.PersistentSetting

/**
 * Persistent property.
 *
 * Property backed by a persistent setting.
 * Value is read from setting, and persisted when value is changed.
 */
class PersistentProperty[T](val setting: PersistentSetting[T])
  extends SimpleObjectProperty[T]
{

  // Set initial value
  reset()

  // And save any value change
  this.listen { (_, _, newValue) =>
    setting.update(newValue)
  }

  /** Gets value. */
  def apply() =
    getValue

  /** Updates value. */
  def update(value: T) =
    setValue(value)

  /**
   * Re-sets value.
   *
   * Reads the setting value and sets the property one.
   */
  def reset() =
    setValue(setting())

  /** Resets value toi default (setting). */
  def resetDefault() =
    setValue(setting.default)

}

object PersistentProperty {

  /** Builds a persistent property from a persistent setting. */
  def apply[T](setting: PersistentSetting[T]) =
    new PersistentProperty(setting)

}
