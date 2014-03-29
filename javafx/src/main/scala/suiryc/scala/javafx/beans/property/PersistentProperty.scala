package suiryc.scala.javafx.beans.property

import scalafx.beans.property.ObjectProperty
import suiryc.scala.settings.PersistentSetting


class PersistentProperty[T](val setting: PersistentSetting[T])
  extends ObjectProperty[T]
{

  /* Set initial value */
  update(setting())

  /* And save any value change */
  onChange { (_, _, newValue) =>
    setting.update(newValue)
  }

}

object PersistentProperty {

  def apply[T](setting: PersistentSetting[T]) =
    new PersistentProperty(setting)

}
