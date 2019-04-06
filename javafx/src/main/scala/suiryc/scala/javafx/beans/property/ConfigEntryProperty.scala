package suiryc.scala.javafx.beans.property

import javafx.beans.property.SimpleObjectProperty
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.settings.ConfigEntry

/**
 * Config entry property.
 *
 * Property backed by a config entry.
 * Value is read from entry, and persisted when property is changed.
 */
class ConfigEntryProperty[A](entry: ConfigEntry[A])
  extends SimpleObjectProperty[A]
{

  // Set initial value
  refresh()

  // And save any value change
  this.listen { (_, _, newValue) =>
    entry.set(newValue)
  }

  /**
   * Refreshes (re-sets) value.
   *
   * Reads the setting value and sets the property one.
   */
  def refresh(): Unit =
    set(entry.get)

  /** Resets value to default (setting). */
  def reset(): Unit = {
    entry.reset()
    refresh()
  }

}

object ConfigEntryProperty {

  /** Builds a property from a config entry. */
  def apply[A](entry: ConfigEntry[A]): ConfigEntryProperty[A] =
    new ConfigEntryProperty(entry)

}
