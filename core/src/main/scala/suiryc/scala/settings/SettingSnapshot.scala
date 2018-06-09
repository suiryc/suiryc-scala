package suiryc.scala.settings

import com.typesafe.config.ConfigValue
import javafx.beans.property.Property

/**
 * Setting snapshot.
 *
 * Allows to determine if a setting was changed and reset it.
 */
trait SettingSnapshot[T] {

  /** Original value. */
  protected val originalValue: T =
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
      override protected def getValue: T = preference()
      override protected def setValue(v: T): Unit = preference() = v
    }

  /** Builds a snapshot from a property. */
  def apply[T](property: Property[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {
      override protected def getValue: T = property.getValue
      override protected def setValue(v: T): Unit = property.setValue(v)
    }

  /** Builds a snapshot from a persistent setting. */
  def apply[T](setting: PersistentSetting[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {
      override protected def getValue: T = setting()
      override protected def setValue(v: T): Unit = setting() = v
    }

  /** Builds a snapshot from a (portable setting) config entry. */
  // Note: unlike other kinds of settings, Config does not use null values, so
  // handle actual values as Options. Also use actual ConfigValue for easier
  // handling.
  def apply[A](setting: ConfigEntry[A]): SettingSnapshot[Option[ConfigValue]] =
    new SettingSnapshot[Option[ConfigValue]] {
      override protected def getValue: Option[ConfigValue] = {
        val config = setting.settings.config
        if (config.hasPath(setting.path)) {
          Some(config.getValue(setting.path))
        } else {
          None
        }
      }
      override protected def setValue(v: Option[ConfigValue]): Unit = {
        v match {
          case Some(value) => setting.settings.withValue(setting.path, value)
          case None => setting.settings.withoutPath(setting.path)
        }
        ()
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
