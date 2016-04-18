package suiryc.scala.javafx.scene.control

import javafx.collections.ObservableList
import javafx.scene.control._

/** TableView (and TreeTableView) helpers. */
object TableViews {

  /**
   * Gets table columns view.
   *
   * Formats columns order, width and visibility into a string.
   * Settings are derived from given columns description. Format associates
   * key and column width pairs, e.g. "key1=column1.width;key2=..." for
   * List("key1" -> column1, "key2" -> column2, ...). Negative width indicates
   * column is not visible.
   *
   * Works with nested columns (only inner-most columns are processed).
   */
  def getColumnsView[A](handler: ViewHandler[A], columnsDesc: List[(String, TableColumnBase[A, _])]): String = {
    import scala.collection.JavaConversions._

    @scala.annotation.tailrec
    def processColumns(columns: List[TableColumnBase[A, _]], acc: List[String]): List[String] = {
      columns match {
        case column :: tail =>
          val children = handler.getColumns(column)
          if (children.size > 0) {
            processColumns(children.toList ::: tail, acc)
          } else {
            val key = columnsDesc.find(_._2 eq column).get._1
            val value = if (handler.isVisible(column)) {
              s"$key=${handler.getWidth(column)}"
            } else {
              s"$key=-${handler.getWidth(column)}"
            }
            processColumns(tail, acc :+ value)
          }

        case Nil => acc
      }
    }

    processColumns(handler.getColumns.toList, Nil).mkString(";")
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
      val owner = Option(handler.getParentColumn(column)).map(_.getColumns).getOrElse(handler.getColumns).asInstanceOf[ObservableList[ViewHandler[A]#Column]]
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

    // First extract columns settings from view
    val columnsSettings = view match {
      case Some(str) =>
        str.split(';').toList.flatMap { param =>
          param.split('=').toList match {
            case key :: value :: Nil =>
              val settings = try {
                if (value.startsWith("-")) {
                  ColumnSettings(visible = false, Some(value.substring(1).toDouble))
                } else {
                  ColumnSettings(visible = true, Some(value.toDouble))
                }
              } catch {
                case ex: Throwable => ColumnSettings(visible = true, None)
              }
              Some(key -> settings)

            case _ =>
              None
          }
        }

      case None =>
        Nil
    }

    // Then order (and set width/visibility) known columns.
    columnsSettings.foreach { case (key, settings) =>
      columnsDesc.find(_._1 == key).foreach { case (_, column) =>
        settings.width.find(_ > 0).foreach(handler.setPrefWidth(column, _))
        handler.setVisible(column, settings.visible)
        orderColumn(column)
      }
    }

    // Finally order remaining columns.
    val keys = columnsSettings.map(_._1).toSet
    columnsDesc.filterNot { case (key, _) =>
      keys.contains(key)
    }.foreach { case (_, column) =>
      orderColumn(column)
    }
  }
  // scalastyle:on method.length

  case class ColumnSettings(visible: Boolean, width: Option[Double])

  /**
   * View handler.
   *
   * Defines how to manipulate a view.
   */
  trait ViewHandler[A] {
    type Column = TableColumnBase[A, _]
    def getColumns: ObservableList[Column]
    def getParentColumn(column: Column): Column
    def getColumns(column: Column): ObservableList[Column]
    def isVisible(column: Column): Boolean
    def setVisible(column: Column, v: Boolean): Unit
    def getWidth(column: Column): Double
    def setPrefWidth(column: Column, v: Double): Unit
  }

  /** TableView handler. */
  implicit class TableViewDesc[A](table: TableView[A]) extends ViewHandler[A] {
    override def getColumns: ObservableList[Column] =
      table.getColumns.asInstanceOf[ObservableList[Column]]
    override def getParentColumn(column: Column): Column =
      column.getParentColumn
    override def getColumns(column: Column): ObservableList[Column] =
      column.getColumns.asInstanceOf[ObservableList[Column]]
    override def isVisible(column: Column): Boolean =
      column.isVisible
    override def setVisible(column: Column, v: Boolean): Unit =
      column.setVisible(v)
    override def getWidth(column: Column): Double =
      column.getWidth
    override def setPrefWidth(column: Column, v: Double): Unit =
      column.setPrefWidth(v)
  }

  /** TreeTableView handler. */
  implicit class TreeTableViewDesc[A](table: TreeTableView[A]) extends ViewHandler[TreeItem[A]] {
    override def getColumns: ObservableList[Column] =
      table.getColumns.asInstanceOf[ObservableList[Column]]
    override def getParentColumn(column: Column): Column =
      column.getParentColumn
    override def getColumns(column: Column): ObservableList[Column] =
      column.getColumns.asInstanceOf[ObservableList[Column]]
    override def isVisible(column: Column): Boolean =
      column.isVisible
    override def setVisible(column: Column, v: Boolean): Unit =
      column.setVisible(v)
    override def getWidth(column: Column): Double =
      column.getWidth
    override def setPrefWidth(column: Column, v: Double): Unit =
      column.setPrefWidth(v)
  }

}
