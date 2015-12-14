package suiryc.scala.javafx.beans.property

import javafx.beans.property.SimpleObjectProperty
import suiryc.scala.javafx.beans.value.RichObservableValue._
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
  def apply(): T =
    getValue

  /** Updates value. */
  def update(value: T): Unit =
    setValue(value)

  /**
   * Re-sets value.
   *
   * Reads the setting value and sets the property one.
   */
  def reset(): Unit =
    setValue(setting())

  /** Resets value toi default (setting). */
  def resetDefault(): Unit =
    setValue(setting.default)

}

object PersistentProperty {

  /** Builds a persistent property from a persistent setting. */
  def apply[T](setting: PersistentSetting[T]): PersistentProperty[T] =
    new PersistentProperty(setting)

}
