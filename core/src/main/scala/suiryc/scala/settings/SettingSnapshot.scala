package suiryc.scala.settings

import javafx.beans.property.Property


trait SettingSnapshot {

  def changed(): Boolean

  def reset(): Unit

}

trait SettingValueSnapshot[T] extends SettingSnapshot {

  protected val originalValue =
    getValue

  protected def getValue: T

  protected def setValue(v: T): Unit

  override def changed() =
    originalValue != getValue

  override def reset() {
    if (changed()) setValue(originalValue)
  }

}

object SettingSnapshot {

  def apply[T](setting: PersistentSetting[T]): SettingSnapshot =
    new SettingValueSnapshot[T] {

      override protected def getValue =
        setting()

      override protected def setValue(v: T) {
        setting() = v
      }

    }

  def apply[T](property: Property[T]): SettingSnapshot =
    new SettingValueSnapshot[T] {

      override protected def getValue =
        property.getValue

      override protected def setValue(v: T) {
        property.setValue(v)
      }

    }

}


class SettingsSnapshot {

  private var snapshots: List[SettingSnapshot] = Nil

  def add(others: SettingSnapshot*) {
    snapshots ++= others.toList
  }

  def changed() =
    snapshots.exists(_.changed())

  def reset() {
    snapshots.foreach(_.reset())
  }

}
