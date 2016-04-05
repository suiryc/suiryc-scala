package suiryc.scala.javafx.scene.control

import javafx.collections.ObservableList
import javafx.scene.control.{TableColumn, TableColumnBase, TableView}

/** TableView helpers. */
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
  def getColumnsView[A](table: TableView[A], columnsDesc: List[(String, TableColumn[A, _])]): String = {
    import scala.collection.JavaConversions._

    @scala.annotation.tailrec
    def processColumns(columns: List[TableColumn[A, _]], acc: List[String]): List[String] = {
      columns match {
        case column :: tail =>
          val children = column.getColumns
          if (children.size > 0) {
            processColumns(children.toList ::: tail, acc)
          } else {
            val key = columnsDesc.find(_._2 eq column).get._1
            val value = if (column.isVisible) {
              s"$key=${column.getWidth}"
            } else {
              s"$key=-${column.getWidth}"
            }
            processColumns(tail, acc :+ value)
          }

        case Nil => acc
      }
    }

    processColumns(table.getColumns.toList, Nil).mkString(";")
  }

  /**
   * Sets table columns view.
   *
   * Orders columns and set preferred width from given view.
   *
   * Works with nested columns (parent column must have been set beforehand).
   */
  // scalastyle:off method.length
  def setColumnsView[A](table: TableView[A], columnsDesc: List[(String, TableColumn[A, _])], view: Option[String]): Unit = {
    var alreadyOrdered = List[TableColumnBase[A, _]]()

    // Ordering a column being processed (in order) is 'simple': just add it at
    // the (current) end of its parent (either another column, or the table).
    @scala.annotation.tailrec
    def orderColumn(column: TableColumnBase[A, _]): Unit = {
      val owner = Option(column.getParentColumn).map(_.getColumns).getOrElse(table.getColumns).asInstanceOf[ObservableList[TableColumnBase[A, _]]]
      owner.remove(column)
      owner.add(column)
      // Order recursively so that parent column is ordered too, unless it has
      // already been done.
      Option(column.getParentColumn) match {
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
        settings.width.find(_ > 0).foreach(column.setPrefWidth)
        column.setVisible(settings.visible)
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

}
