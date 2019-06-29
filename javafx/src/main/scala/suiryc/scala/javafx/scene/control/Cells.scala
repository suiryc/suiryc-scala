package suiryc.scala.javafx.scene.control

import javafx.beans.property.{BooleanProperty, SimpleBooleanProperty}
import javafx.scene.Node
import javafx.scene.control.{Cell, ListCell, Separator, TableCell}
import javafx.scene.control.cell.CheckBoxListCell
import suiryc.scala.concurrent.Cancellable
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.unused
import suiryc.scala.util.I18NLocale

/** Cell extension that knows how to update cell text/graphic. */
trait CellEx[A] extends Cell[A] {

  protected def itemText(item: A): String
  // scalastyle:off null
  protected def itemGraphic(@unused item: A): Node = null
  // scalastyle:on null

  // scalastyle:off null
  override protected def updateItem(item: A, empty: Boolean): Unit = {
    super.updateItem(item, empty)
    // Notes:
    // Changing the text will re-set the cell children (text and graphic).
    // It is thus better to change the graphic first, otherwise in some
    // situations (e.g. changing the order of items) this triggers
    // unwanted behaviour:
    //  -> cell C1 and C2 display items I1 and I2
    //  -> cell C1 is assigned item I2
    //   -> C1 text is set from I2
    //    -> C1 children are re-set: text and I1 icon
    //   -> C1 graphic is set to I2 icon
    //    -> I2 icon is removed from C2 (now a child of C1), while still
    //       being valued as C2 graphic
    //    -> I1 icon is now parentless
    //  -> cell C2 is assigned item I1
    //   -> C2 text is set from I1
    //    -> C2 children are re-set: text and I2 icon (still valued as
    //       graphic)
    //     -> I2 icon is removed from C1 (now child of C2), while still
    //        being valued as C1 graphic
    //   -> C2 graphic is set to I1 icon
    //    -> I2 icon is now parentless
    // In the end C1 displays I2 but C2 is missing the I1 icon.
    // Setting the graphic first prevents this.
    if (empty) {
      setGraphic(null)
      setText(null)
    } else {
      setGraphic(itemGraphic(item))
      setText(itemText(item))
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
  protected def itemGraphic(@unused item: A): Node = null
  // scalastyle:on null

  // scalastyle:off null
  override protected def updateItem(item: Option[A], empty: Boolean): Unit = {
    super.updateItem(item, empty)
    // Don't forget to re-enable cell (and remove graphic) when applicable as
    // it could have previously been disabled (used as entries separator).
    if (empty) {
      setGraphic(null)
      setText(null)
      setDisable(false)
    } else {
      setGraphic(item.map(itemGraphic).getOrElse(new Separator()))
      setText(item.map(itemText).orNull)
      setDisable(item.isEmpty)
    }
  }
  // scalastyle:on null

}

/** ListCell extension with CellEx. */
trait ListCellEx[A] extends ListCell[A] with CellEx[A]

/** TableCell extension with CellEx. */
trait TableCellEx[A, B] extends TableCell[A, B] with CellEx[B]

/** ListCell for I18Locale value. */
class I18NLocaleCell extends ListCellEx[I18NLocale] {
  override protected def itemText(item: I18NLocale): String = item.displayName
}

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
  protected def hasActualItem(@unused item: A): Boolean = true

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
