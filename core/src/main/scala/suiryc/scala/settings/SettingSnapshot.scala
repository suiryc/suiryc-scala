package suiryc.scala.settings

import javafx.beans.property.Property


trait SettingSnapshot {

  def changed(): Boolean

  def reset(): Unit

}

trait BasicSettingSnapshot extends SettingSnapshot {

  protected def resetValue(): Unit

  override def reset() {
    if (changed()) resetValue()
  }

}

object SettingSnapshot {

  def apply[T](setting: PersistentSetting[T]): SettingSnapshot =
    new BasicSettingSnapshot {

      val originalValue = setting()

      override def changed() =
        setting() != originalValue

      override protected def resetValue() {
        setting() = originalValue
      }

    }

  def apply[T](property: Property[T]): SettingSnapshot =
    new BasicSettingSnapshot {

      val originalValue = property.getValue()

      override def changed() =
        property.getValue() != originalValue

      override protected def resetValue() {
        property.setValue(originalValue)
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
