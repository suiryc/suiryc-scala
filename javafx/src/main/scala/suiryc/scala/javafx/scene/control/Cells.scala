package suiryc.scala.javafx.scene.control

import javafx.beans.property.{BooleanProperty, SimpleBooleanProperty}
import javafx.scene.control.{Cell, ListCell, Separator, TableCell}
import javafx.scene.control.cell.CheckBoxListCell
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.util.Callback

/** Cell extension that knows how to update cell text. */
trait CellEx[A] extends Cell[A] {

  protected def itemText(item: A): String

  // scalastyle:off null
  override protected def updateItem(item: A, empty: Boolean) {
    super.updateItem(item, empty)
    if (empty) setText(null)
    else setText(itemText(item))
  }
  // scalastyle:on null

}

/**
 * Cell extension that can display a Separator.
 *
 * Items are Options; None is used to display a Separator.
 */
trait CellWithSeparator[A] extends Cell[Option[A]] {

  protected def itemText(item: A): String

  // scalastyle:off null
  override protected def updateItem(item: Option[A], empty: Boolean): Unit = {
    super.updateItem(item, empty)
    if (empty) setText(null)
    else {
      setText(item.map(itemText).orNull)
      if (item.isEmpty) {
        setDisable(true)
        setGraphic(new Separator())
      } else {
        // Don't forget to re-enable cell (and remove graphic), as it could have
        // previously been disabled (used as entries separator).
        setDisable(false)
        setGraphic(null)
      }
    }
  }
  // scalastyle:on null

}

/** ListCell extension with CellEx. */
trait ListCellEx[A] extends ListCell[A] with CellEx[A]

/** TableCell extension with CellEx. */
trait TableCellEx[A, B] extends TableCell[A, B] with CellEx[B]

/**
 * CheckBox ListCell extension.
 *
 * Automatically updates Cell according to content by setting text, checkbox
 * selection and cell disabling if value is locked.
 *
 * @tparam A cell data type
 */
abstract class CheckBoxListCellEx[A] extends CheckBoxListCell[A] {

  import CheckBoxListCellEx._

  /** Gets cell info for a given item. */
  protected def getInfo(item: A): CellInfo

  /** Callback to customize cell locking (other than disabling it). */
  protected def setLocked(locked: Boolean): Unit

  /** Callback for checkbox change. */
  protected def statusChanged(oldValue: Boolean, newValue: Boolean): Unit

  setSelectedStateCallback(Callback { item =>
    // We are supposed to have an item
    Option(item).map { _ =>
      val info = getInfo(item)
      val property = info.observable
      property.set(info.checked)
      property.listen { (_, v0, v1) =>
        statusChanged(v0, v1)
      }
      property
    }.getOrElse(new SimpleBooleanProperty())
  })

  // scalastyle:off null
  override protected def updateItem(item: A, empty: Boolean) {
    super.updateItem(item, empty)
    if (empty) setText(null)
    else {
      val info = getInfo(item)
      setText(info.text)
      setDisabled(info.locked)
      setLocked(info.locked)
    }
  }
  // scalastyle:on null

}

object CheckBoxListCellEx {
  /**
   * Cell info:
   *   - text to display
   *   - observable boolean that will be tied to checkbox
   *   - initial checkbox status
   *   - whether checkbox status can be changed (that is whether initial status is read-only)
   */
  case class CellInfo(text: String, observable: BooleanProperty, checked: Boolean, locked: Boolean)
}
