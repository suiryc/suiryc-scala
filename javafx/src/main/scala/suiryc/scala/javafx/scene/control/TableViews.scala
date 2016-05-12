package suiryc.scala.javafx.scene.control

import javafx.collections.ObservableList
import javafx.collections.transformation.SortedList
import javafx.scene.control._
import scala.collection.JavaConversions._
import scala.reflect._
import spray.json._

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
      else Some(sortOrder.toList)
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
      else Some(sortOrder.toList)
    table.setRoot(root)
    restoreSortOrder.foreach(table.getSortOrder.setAll(_))
  }

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
            processColumns(children.toList ::: tail, acc)
          } else {
            val key = columnsDesc.find(_._2 eq column).get._1
            val view = handler.getColumnView(column, key)
            processColumns(tail, acc :+ view)
          }

        case Nil => acc
      }
    }
    val columnViews = processColumns(handler.getColumns.toList, Nil)
    val sortOrder = handler.getSortOrder.toList.map { column =>
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
      val owner = Option(handler.getParentColumn(column)).map(handler.getColumns(_)).getOrElse(handler.getColumns)
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
            case ex: Exception => ColumnsView[handler.SortType](Nil, Nil)
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
                  case ex: Exception => ColumnView[handler.SortType](key, visible = true, -1, handler.defaultSortType)
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
            import scala.reflect._
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
    override def setSortOrder(order: List[Column]): Boolean = table.getSortOrder.setAll(order)
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
    val columnsViewFormat = JsonProtocol.columnsViewFormat[SortType]
  }

  /** TreeTableView handler. */
  implicit class TreeTableViewDesc[A](table: TreeTableView[A]) extends ViewHandler[TreeTableColumn[A,_ ]] {
    type SortType = TreeTableColumn.SortType
    val defaultSortType = TreeTableColumn.SortType.ASCENDING
    override def getColumns: ObservableList[Column] = table.getColumns
    override def getSortOrder: ObservableList[Column] = table.getSortOrder
    override def setSortOrder(order: List[Column]): Boolean = table.getSortOrder.setAll(order)
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
    val columnsViewFormat = JsonProtocol.columnsViewFormat[SortType]
  }

}
