package suiryc.scala.javafx.beans.property

import javafx.beans.property.SimpleObjectProperty
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.settings.PersistentSetting


class PersistentProperty[T](val setting: PersistentSetting[T])
  extends SimpleObjectProperty[T]
{

  /* Set initial value */
  reset()

  /* And save any value change */
  this.listen { (_, _, newValue) =>
    setting.update(newValue)
  }

  def apply() =
    getValue()

  def update(value: T) =
    setValue(value)

  def reset() =
    setValue(setting())

}

object PersistentProperty {

  def apply[T](setting: PersistentSetting[T]) =
    new PersistentProperty(setting)

}
