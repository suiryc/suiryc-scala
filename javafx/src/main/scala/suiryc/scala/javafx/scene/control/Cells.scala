package suiryc.scala.javafx.scene.control

import javafx.beans.property.{BooleanProperty, SimpleBooleanProperty}
import javafx.scene.Node
import javafx.scene.control.{Cell, ListCell, Separator, TableCell}
import javafx.scene.control.cell.CheckBoxListCell
import suiryc.scala.concurrent.Cancellable
import suiryc.scala.javafx.beans.value.RichObservableValue._

/** Cell extension that knows how to update cell text/graphic. */
trait CellEx[A] extends Cell[A] {

  protected def itemText(item: A): String
  // scalastyle:off null
  protected def itemGraphic(@deprecated("unused","") item: A): Node = null
  // scalastyle:on null

  // scalastyle:off null
  override protected def updateItem(item: A, empty: Boolean) {
    super.updateItem(item, empty)
    if (empty) {
      setText(null)
      setGraphic(null)
    } else {
      setText(itemText(item))
      setGraphic(itemGraphic(item))
    }
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
  protected def itemGraphic(@deprecated("unused","") item: A): Node = null
  // scalastyle:on null

  // scalastyle:off null
  override protected def updateItem(item: Option[A], empty: Boolean): Unit = {
    super.updateItem(item, empty)
    // Don't forget to re-enable cell (and remove graphic) when applicable as
    // it could have previously been disabled (used as entries separator).
    if (empty) {
      setText(null)
      setGraphic(null)
      setDisable(false)
    } else {
      setText(item.map(itemText).orNull)
      setGraphic(item.map(itemGraphic).getOrElse(new Separator()))
      setDisable(item.isEmpty)
    }
  }
  // scalastyle:on null

}

/** ListCell extension with CellEx. */
trait ListCellEx[A] extends ListCell[A] with CellEx[A]

/** TableCell extension with CellEx. */
trait TableCellEx[A, B] extends TableCell[A, B] with CellEx[B]

/**
 * CheckBox ListCell with information.
 *
 * Automatically updates Cell according to content by setting text, checkbox
 * selection and cell disabling if value is locked.
 *
 * @tparam A cell data type
 */
trait CheckBoxListCellWithInfo[A] extends CheckBoxListCell[A] {

  import CheckBoxListCellWithInfo._

  protected var propertyListener: Option[Cancellable] = None

  /** Whether there is an actual item. */
  protected def hasActualItem(@deprecated("unused","") item: A): Boolean = true

  /** Gets cell info for a given item. */
  protected def getInfo(item: A): CellInfo

  /** Callback to customize cell locking (other than disabling it). */
  protected def setLocked(locked: Boolean): Unit

  /** Callback for checkbox change. */
  protected def statusChanged(oldValue: Boolean, newValue: Boolean): Unit

  // Link the checkbox state to the cell info property.
  // Note this must be set before any call to 'updateItem' as it is needed when
  // an item is present. It is called each time item is updated and not empty.
  setSelectedStateCallback(item => {
    // We are supposed to have an item, but it may not hold actual data, in
    // which case there is no link to create.
    Option(item).filter(hasActualItem).map { _ =>
      val info = getInfo(item)
      val property = info.observable
      // Listen before setting property as it may be useful to trigger change
      // from here right now for caller.
      propertyListener = Some(property.listen { (_, v0, v1) =>
        statusChanged(v0, v1)
      })
      property.set(info.checked)
      property
    }.getOrElse(new SimpleBooleanProperty())
  })

  // scalastyle:off null
  override protected def updateItem(item: A, empty: Boolean): Unit = {
    // Cancel previous listener if any
    propertyListener.foreach(_.cancel())
    propertyListener = None

    // Do the standard update (note: CheckBoxListCell does reset text and
    // graphic for empty items).
    super.updateItem(item, empty)

    // Then do our specific update
    if (!empty) {
      if (!hasActualItem(item)) {
        setText(null)
        setGraphic(null)
        setDisable(true)
      } else {
        val info = getInfo(item)
        setText(info.text)
        setDisable(info.locked)
        setLocked(info.locked)
      }
    }
  }
  // scalastyle:on null

}

/**
 * CheckBox ListCell with information that can display a Separator.
 *
 * Items are Options; None is used to display a Separator.
 *
 * @tparam A actual data type
 */
trait CheckBoxListCellWithSeparator[A] extends CheckBoxListCellWithInfo[Option[A]] {

  override protected def hasActualItem(item: Option[A]): Boolean = item.isDefined

  override protected def updateItem(item: Option[A], empty: Boolean): Unit = {
    super.updateItem(item, empty)
    if (!empty && !hasActualItem(item)) setGraphic(new Separator())
  }

}

object CheckBoxListCellWithInfo {
  /**
   * Cell info:
   *   - text to display
   *   - observable boolean that will be tied to checkbox
   *   - initial checkbox status
   *   - whether checkbox status can be changed (that is whether initial status is read-only)
   */
  case class CellInfo(text: String, observable: BooleanProperty, checked: Boolean, locked: Boolean)
}
