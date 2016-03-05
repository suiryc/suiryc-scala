package suiryc.scala.settings

import javafx.beans.property.Property

/**
 * Setting snapshot.
 *
 * Allows to determine if a setting was changed and reset it.
 */
trait SettingSnapshot[T] {

  /** Original value. */
  protected val originalValue =
    getValue

  /** Gets current value. */
  protected def getValue: T

  /** Sets value. */
  protected def setValue(v: T): Unit

  /** Gets whether the setting was changed. */
  def changed(): Boolean =
    originalValue != getValue

  /** Resets the setting to its initial value. */
  def reset(): Unit = {
    if (changed()) setValue(originalValue)
  }

}

object SettingSnapshot {

  /** Builds a snapshot from a Preference. */
  def apply[T](preference: Preference[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {

      override protected def getValue =
        preference()

      override protected def setValue(v: T) {
        preference() = v
      }

    }

  /** Builds a snapshot from a property. */
  def apply[T](property: Property[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {

      override protected def getValue =
        property.getValue

      override protected def setValue(v: T) {
        property.setValue(v)
      }

    }

  /** Builds a snapshot from a persistent setting. */
  def apply[T](setting: PersistentSetting[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {

      override protected def getValue =
        setting()

      override protected def setValue(v: T) {
        setting() = v
      }

    }

}

/**
 * Settings snapshot.
 *
 * Hold a list of snapshots. Allows to determine if any changed, and reset
 * them all.
 */
class SettingsSnapshot {

  /** Setting snapshots. */
  private var snapshots: List[SettingSnapshot[_]] = Nil

  /** Adds setting snapshots. */
  def add(others: Seq[SettingSnapshot[_]]): Unit =
    snapshots ++= others.toList

  /** Adds setting snapshots. (vararg variant) */
  def add(others: SettingSnapshot[_]*)(implicit d: DummyImplicit): Unit =
    add(others)

  /** Gets whether any setting changed. */
  def changed(): Boolean =
    snapshots.exists(_.changed())

  /** Resets all settings. */
  def reset(): Unit = {
    snapshots.foreach(_.reset())
  }

}
