package suiryc.scala.javafx.scene.control

import com.sun.javafx.scene.control.VirtualScrollBar
import javafx.collections.ObservableList
import javafx.collections.transformation.SortedList
import javafx.scene.control._
import javafx.scene.control.skin.{TableViewSkin, TreeTableViewSkin, VirtualFlow}
import javafx.scene.layout.Region
import scala.collection.JavaConverters._
import scala.reflect._
import spray.json._
import suiryc.scala.javafx.beans.value.RichObservableValue

/** TableView (and TreeTableView) helpers. */
object TableViews {

  /**
   * Sets items, keeping table sort order.
   *
   * Upon changing items which are not in a SortedList, the table sort order is
   * cleared (see https://bugs.openjdk.java.net/browse/JDK-8092759).
   * This function re-applies the sorting order if necessary.
   */
  def setItems[A](table: TableView[A], items: ObservableList[A]): Unit = {
    val sortOrder = table.getSortOrder
    val restoreSortOrder =
      if (sortOrder.isEmpty || table.getItems.isInstanceOf[SortedList[A]]) None
      else Some(sortOrder)
    table.setItems(items)
    restoreSortOrder.foreach(table.getSortOrder.setAll(_))
  }

  /**
   * Sets root, keeping table sort order.
   *
   * Upon changing root, the table sort order is cleared (see
   * https://bugs.openjdk.java.net/browse/JDK-8092759).
   * This function re-applies the sorting order if necessary.
   */
  def setRoot[A](table: TreeTableView[A], root: TreeItem[A]): Unit = {
    val sortOrder = table.getSortOrder
    val restoreSortOrder =
      if (sortOrder.isEmpty) None
      else Some(sortOrder)
    table.setRoot(root)
    restoreSortOrder.foreach(table.getSortOrder.setAll(_))
  }

  /**
   * Scrolls so that requested index is visible (in the view).
   *
   * If target row (+ padding) is already visible, nothing is done.
   * Index outside the rows range is adjusted to see either first or last row.
   *
   * @param table table
   * @param index row index (0-based) to make visible
   * @param top whether the row is supposed to appear at the top (scroll up)
   *            or at the bottom (scroll down)
   * @param padding number of extra rows that should also be visible
   */
  def scrollTo(table: TableView[_], index: Int, top: Boolean, padding: Int): Unit = {
    // To control scrolling, we need to access the VirtualFlow which is in the
    // table skin (should be the first child, accessible once table is shown).
    table.getSkin.asInstanceOf[TableViewSkin[_]].getChildren.asScala.find(_.isInstanceOf[VirtualFlow[_]]).foreach {
      case flow: VirtualFlow[_] ⇒
        scrollTo(flow.asInstanceOf[VirtualFlow[_ <: IndexedCell[_]]], table.getItems.size, index, top, padding)
    }
  }

  /**
   * Scrolls so that requested index is visible (in the view).
   *
   * If target row (+ padding) is already visible, nothing is done.
   * Index outside the rows range is adjusted to see either first or last row.
   *
   * @param table table
   * @param index row index (0-based) to make visible
   * @param top whether the row is supposed to appear at the top (scroll up)
   *            or at the bottom (scroll down)
   * @param padding number of extra rows that should also be visible
   */
  def scrollTo(table: TreeTableView[_], index: Int, top: Boolean, padding: Int): Unit = {
    table.getSkin.asInstanceOf[TreeTableViewSkin[_]].getChildren.asScala.find(_.isInstanceOf[VirtualFlow[_]]).foreach {
      case flow: VirtualFlow[_] ⇒
        scrollTo(flow.asInstanceOf[VirtualFlow[_ <: IndexedCell[_]]], table.getRoot.getChildren.size, index, top, padding)
    }
  }

  private def scrollTo(flow: VirtualFlow[_ <: IndexedCell[_]], itemsCount: Int, index: Int, top: Boolean, padding: Int): Unit = {
    // A row is considered visible starting with its very first pixel. We want
    // most of the row to be seen, so the easiest solution is to make sure it is
    // fully visible by targeting the row next to the one we want (+ padding).
    // We determine two targets to see above (top) and below (bottom) the wanted
    // one; padding is applied in the scrolling direction.
    val targetTop = if (top) {
      if (index > Int.MinValue + padding) index - (padding + 1) else Int.MinValue
    } else {
      index - 1
    }
    val targetBottom = if (top) {
      index + 1
    } else {
      if (index < Int.MaxValue - padding) index + (padding + 1) else Int.MaxValue
    }
    @scala.annotation.tailrec
    def adjust(top: Boolean, movedUp: Boolean, movedDown: Boolean): Unit = {
      if (top) {
        // Scroll up.
        if (targetTop < 0) {
          // Scroll to the top.
          flow.scrollPixels(Double.MinValue)
          ()
        } else if (flow.getFirstVisibleCell.getIndex > targetTop) {
          // Target row (first pixel) not yet visible: scroll 1px and loop.
          flow.scrollPixels(-1)
          adjust(top = top, movedUp = true, movedDown = movedDown)
        } else if (!movedUp) {
          // Now that top side is visible, switch to bottom side; ensure we did
          // not scroll down yet (to prevent possible infinite loop).
          adjust(top = !top, movedUp = true, movedDown = true)
        }
      } else {
        // Scroll down.
        if (targetBottom >= itemsCount) {
          // Scroll to the bottom.
          flow.scrollPixels(Double.MaxValue)
          ()
        } else if (flow.getLastVisibleCell.getIndex < targetBottom) {
          flow.scrollPixels(1)
          adjust(top = top, movedUp = movedUp, movedDown = true)
        } else if (!movedDown) {
          adjust(top = !top, movedUp = true, movedDown = true)
        }
      }
    }
    adjust(top = top, movedUp = false, movedDown = false)
  }

  /**
   * Automatically adjusts column width to fill the table size.
   *
   * The target column is expected to be one at the root of the table, not a
   * nested one.
   * Listeners are used to adjust width when other elements change.
   *
   * @param column the column to adjust
   */
  def autowidthColumn[S](column: TableColumn[S, _]): Unit = {
    val tableView = column.getTableView
    val otherColumns = tableView.getColumns.asScala.toList.filterNot(_ eq column)
    autowidthColumn(tableView, column, otherColumns)
  }

  /**
   * Automatically adjusts column width to fill the table size.
   *
   * The target column is expected to be one at the root of the table, not a
   * nested one.
   * Listeners are used to adjust width when other elements change.
   *
   * @param column the column to adjust
   */
  def autowidthColumn[S](column: TreeTableColumn[S, _]): Unit = {
    val tableView = column.getTreeTableView
    val otherColumns = tableView.getColumns.asScala.toList.filterNot(_ eq column)
    autowidthColumn(tableView, column, otherColumns)
  }

  /**
   * Automatically adjusts column width to fill the table size.
   *
   * The target column is expected to be one at the root of the table, not a
   * nested one.
   * Listeners are used to adjust width when other elements change.
   *
   * @param table the table owning the column
   * @param column the column to adjust
   * @param otherColumns the other columns
   */
  // scalastyle:off method.length
  def autowidthColumn[S](table: Control, column: TableColumnBase[S, _], otherColumns: List[TableColumnBase[S, _]]): Unit = {
    // Now what we want is for all columns to occupy the whole table width.
    // Using a constrained resizing policy gets in the way of restoring the
    // view, so a solution is to create a binding through which we set the
    // target column (preferred) width according to the width of other
    // elements:
    //  columnWidth = tableWidth - tablePadding - otherColumnsWidth
    // However the vertical scrollbar which may appear is not taken into
    // account in table width. It is in the "clipped-container" that is a
    // Region of the viewed content:
    //  columnWidth = containerWidth - otherColumnsWidth
    // (the container has 0 width when there is no content in the table)
    //
    // The table width is changed before the container one, which triggers
    // glitches when resizing down using the second formula: the horizontal
    // scrollbar appears (and disappears upon interaction or resizing up).
    // Requesting layout (in 'runLater') makes it disappear right away.
    //
    // One solution, to apply correct width while preventing the horizontal
    // scrollbar to appear, is to:
    //  - listen to table width (changed before container one)
    //  - take into account the scrollbar width when visible, similarly to
    //    what is done for the container; eliminating the need to take into
    //    account the container
    // The scrollbar width happens to change the first time it appears;
    // instead of listening for the scrollbar visibility and width, it is
    // easier to listen to the container width.
    // Belt and suspenders: we also keep the floor value of the target width,
    // since it is a decimal value and rounding may make the scrollbar appear.
    // Note: in some versions of JavaFX, it may have been necessary to also
    // take into account the container in order to prevent the horizontal
    // scrollbar from appearing in some corner cases.
    //val tableView = column.getTableView
    //val otherColumns = tableView.getColumns.asScala.toList.filterNot(_ eq column)

    val clippedContainer = table.lookup(".clipped-container").asInstanceOf[Region]
    val scrollBar = table.lookupAll(".scroll-bar").asScala.collect {
      case scrollBar: VirtualScrollBar if scrollBar.getPseudoClassStates.asScala.map(_.getPseudoClassName).contains("vertical") ⇒ scrollBar
    }.head

    def updateColumnWidth(): Unit = {
      val insets = table.getPadding
      val padding = insets.getLeft + insets.getRight
      val scrollbarWidth = if (!scrollBar.isVisible) 0 else scrollBar.getWidth
      val viewWidth0 = table.getWidth - padding - scrollbarWidth
      // It should not be necessary to take into account the container.
      val viewWidth = viewWidth0
      //val viewWidth =
      //  if (clippedContainer.getWidth > 0) math.min(viewWidth0, clippedContainer.getWidth)
      //  else viewWidth0
      // Belt and suspenders: floor value to eliminate possible corner cases.
      val columnWidth = (viewWidth - otherColumns.map(_.getWidth).sum).floor
      // Setting max width helps applying target width in some cases (minimum
      // width changed to a lower value after re-setting table items).
      val columnMaxWidth = math.max(columnWidth, column.getMinWidth)
      // Since current maxWidth may be lower than the one we want to apply, we
      // need to update it first (otherwise the preferred width may not be
      // applied as wanted).
      // Note: *do not* delay setting width (e.g. through runLater) since it
      // will make the scrollbar appear+disappear rapidly when resizing down.
      column.setMaxWidth(columnMaxWidth)
      column.setPrefWidth(columnWidth)
    }

    // Listening to the table and other columns width is necessary.
    // We may also listen to the scrollbar visibility and width, but it's
    // simpler to listen to the container width (impacted by both).
    // Since the target column minimum width may change, listen to it to
    // enforce value when necessary.
    val listenTo = List(
      table.widthProperty,
      clippedContainer.widthProperty,
      column.minWidthProperty
    ) ::: otherColumns.map(_.widthProperty())
    RichObservableValue.listen[AnyRef](listenTo)(updateColumnWidth())
    updateColumnWidth()
  }
  // scalastyle:on method.length

  /**
   * Gets table columns view.
   *
   * Formats columns order, visibility, width and sort type into a string.
   * Settings are derived from given columns description. Format is the JSON
   * value of ColumnsView.
   *
   * Works with nested columns (only inner-most columns are processed).
   */
  def getColumnsView[A <: AnyRef](handler: ViewHandler[A], columnsDesc: List[(String, ViewHandler[A]#Column)]): String = {
    @scala.annotation.tailrec
    def processColumns(columns: List[ViewHandler[A]#Column], acc: List[handler.ColumnView]): List[handler.ColumnView] = {
      columns match {
        case column :: tail =>
          val children = handler.getColumns(column)
          if (children.size > 0) {
            processColumns(children.asScala.toList ::: tail, acc)
          } else {
            val key = columnsDesc.find(_._2 eq column).get._1
            val view = handler.getColumnView(column, key)
            processColumns(tail, acc :+ view)
          }

        case Nil => acc
      }
    }
    val columnViews = processColumns(handler.getColumns.asScala.toList, Nil)
    val sortOrder = handler.getSortOrder.asScala.toList.map { column =>
      columnsDesc.find(_._2 eq column).get._1
    }
    val columnsView = ColumnsView[handler.SortType](columnViews, sortOrder)
    handler.columnsViewFormat.write(columnsView).compactPrint
  }

  /**
   * Sets table columns view.
   *
   * Orders columns and set preferred width from given view.
   *
   * Works with nested columns (parent column must have been set beforehand).
   */
  // scalastyle:off method.length
  def setColumnsView[A](handler: ViewHandler[A], columnsDesc: List[(String, ViewHandler[A]#Column)], view: Option[String]): Unit = {
    var alreadyOrdered = List[ViewHandler[A]#Column]()

    // Ordering a column being processed (in order) is 'simple': just add it at
    // the (current) end of its parent (either another column, or the table).
    @scala.annotation.tailrec
    def orderColumn(column: ViewHandler[A]#Column): Unit = {
      val owner = Option(handler.getParentColumn(column)).map(handler.getColumns).getOrElse(handler.getColumns)
      owner.remove(column)
      owner.add(column)
      // Order recursively so that parent column is ordered too, unless it has
      // already been done.
      Option(handler.getParentColumn(column)) match {
        case Some(parent) =>
          if (!alreadyOrdered.contains(parent)) {
            alreadyOrdered ::= parent
            orderColumn(parent)
          }

        case None =>
      }
    }

    // First parse columns views
    val columnsView = view match {
      case Some(str) =>
        if (str.startsWith("{")) {
          try {
            handler.columnsViewFormat.read(str.parseJson)
          } catch {
            case _: Exception => ColumnsView[handler.SortType](Nil, Nil)
          }
        }
        else {
          // Old format
          val columnViews = str.split(';').toList.flatMap { param =>
            param.split('=').toList match {
              case key :: value :: Nil =>
                val columnView = try {
                  if (value.startsWith("-")) ColumnView[handler.SortType](key, visible = false, value.substring(1).toDouble, handler.defaultSortType)
                  else ColumnView[handler.SortType](key, visible = true, value.toDouble, handler.defaultSortType)
                } catch {
                  case _: Exception => ColumnView[handler.SortType](key, visible = true, -1, handler.defaultSortType)
                }
                Some(columnView)
              case _ => None
            }
          }
          ColumnsView[handler.SortType](columnViews, Nil)
        }

      case None =>
        ColumnsView[handler.SortType](Nil, Nil)
    }

    // Then order (and set width/visibility) known columns.
    columnsView.columns.foreach { columnView =>
      columnsDesc.find(_._1 == columnView.id).foreach { case (_, column) =>
        handler.setColumnView(column, columnView)
        orderColumn(column)
      }
    }

    // Finally order remaining columns.
    val keys = columnsView.columns.map(_.id).toSet
    columnsDesc.filterNot { case (key, _) =>
      keys.contains(key)
    }.foreach { case (_, column) =>
      orderColumn(column)
    }
    // And apply sort order
    val sortOrder = columnsView.sortOrder.flatMap { key =>
      columnsDesc.find(_._1 == key).map(_._2)
    }
    handler.setSortOrder(sortOrder)
    ()
  }
  // scalastyle:on method.length

  case class ColumnView[A <: Enum[A]](id: String, visible: Boolean, width: Double, sortType: A)
  case class ColumnsView[A <: Enum[A]](columns: List[ColumnView[A]], sortOrder: List[String])

  object JsonProtocol extends DefaultJsonProtocol {
    // TODO: move this where it can be shared ?
    implicit def enumFormat[E <: Enum[E] : ClassTag]: JsonFormat[E] = new JsonFormat[E] {
      def write(e: E): JsValue = JsString(e.toString)
      def read(value: JsValue): E = value match {
        case JsString(e) =>
          try {
            Enum.valueOf(classTag[E].runtimeClass.asInstanceOf[Class[E]], e)
          } catch {
            case ex: Exception => deserializationError(s"Invalid ${classTag[E]} format: $e", ex)
          }
        case _ => deserializationError(s"Expected ${classTag[E]} as JsString. Got $value")
      }
    }

    implicit def columnViewFormat[A <: Enum[A] : JsonFormat]: RootJsonFormat[ColumnView[A]] = jsonFormat4(ColumnView[A])
    def columnsViewFormat[A <: Enum[A] : JsonFormat]: RootJsonFormat[ColumnsView[A]] = jsonFormat2(ColumnsView[A])
  }

  /**
   * View handler.
   *
   * Defines how to manipulate a view.
   */
  trait ViewHandler[A] {
    type Column = A
    type SortType <: Enum[SortType]
    type ColumnView = TableViews.ColumnView[SortType]
    type ColumnsView = TableViews.ColumnsView[SortType]
    val defaultSortType: SortType
    def getColumns: ObservableList[Column]
    def getSortOrder: ObservableList[Column]
    def setSortOrder(order: List[Column]): Boolean
    def getParentColumn(column: Column): Column
    def getColumns(column: Column): ObservableList[Column]
    def getColumnView(column: Column, id: String): ColumnView
    def setColumnView(column: Column, view: ColumnView): Unit
    val columnsViewFormat: RootJsonFormat[ColumnsView]
  }

  /** TableView handler. */
  implicit class TableViewDesc[A](table: TableView[A]) extends ViewHandler[TableColumn[A, _]] {
    type SortType = TableColumn.SortType
    val defaultSortType = TableColumn.SortType.ASCENDING
    override def getColumns: ObservableList[Column] = table.getColumns
    override def getSortOrder: ObservableList[Column] = table.getSortOrder
    override def setSortOrder(order: List[Column]): Boolean = table.getSortOrder.setAll(order.asJava)
    override def getParentColumn(column: Column): Column = column.getParentColumn.asInstanceOf[Column]
    override def getColumns(column: Column): ObservableList[Column] = column.getColumns
    override def getColumnView(column: Column, id: String): ColumnView =
      ColumnView(id, column.isVisible, column.getWidth, column.getSortType)
    override def setColumnView(column: Column, view: ColumnView): Unit = {
      column.setVisible(view.visible)
      if (view.width > 0) column.setPrefWidth(view.width)
      column.setSortType(view.sortType)
    }
    import JsonProtocol._
    val columnsViewFormat: RootJsonFormat[ColumnsView] = JsonProtocol.columnsViewFormat[SortType]
  }

  /** TreeTableView handler. */
  implicit class TreeTableViewDesc[A](table: TreeTableView[A]) extends ViewHandler[TreeTableColumn[A,_ ]] {
    type SortType = TreeTableColumn.SortType
    val defaultSortType = TreeTableColumn.SortType.ASCENDING
    override def getColumns: ObservableList[Column] = table.getColumns
    override def getSortOrder: ObservableList[Column] = table.getSortOrder
    override def setSortOrder(order: List[Column]): Boolean = table.getSortOrder.setAll(order.asJava)
    override def getParentColumn(column: Column): Column = column.getParentColumn.asInstanceOf[Column]
    override def getColumns(column: Column): ObservableList[Column] = column.getColumns
    override def getColumnView(column: Column, id: String): ColumnView =
      ColumnView(id, column.isVisible, column.getWidth, column.getSortType)
    override def setColumnView(column: Column, view: ColumnView): Unit = {
      column.setVisible(view.visible)
      if (view.width > 0) column.setPrefWidth(view.width)
      column.setSortType(view.sortType)
    }
    import JsonProtocol._
    val columnsViewFormat: RootJsonFormat[ColumnsView] = JsonProtocol.columnsViewFormat[SortType]
  }

}
