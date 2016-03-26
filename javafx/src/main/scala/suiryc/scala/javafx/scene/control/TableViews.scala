package suiryc.scala.javafx.scene.control

import javafx.scene.control.{TableColumn, TableView}

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
   */
  def getColumnsView[A](table: TableView[A], columnsDesc: List[(String, TableColumn[A, _])]): String = {
    import scala.collection.JavaConversions._

    table.getColumns.toList.map { column =>
      val key = columnsDesc.find(_._2 eq column).get._1
      if (column.isVisible) {
        s"$key=${column.getWidth}"
      } else {
        s"$key=-${column.getWidth}"
      }
    }.mkString(";")
  }

  /**
   * Sets table columns view.
   *
   * Orders columns and set preferred width from given view.
   */
  def setColumnsView[A](table: TableView[A], columnsDesc: List[(String, TableColumn[A, _])], view: Option[String]): Unit = {
    val columns = view match {
      case Some(str) =>
        val params = str.split(';').toList.flatMap { param =>
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

        val columns1 = params.flatMap { param =>
          val (key, settings) = param
          columnsDesc.find(_._1 == key).map { case (_, column) =>
            settings.width.find(_ > 0).foreach(column.setPrefWidth)
            column.setVisible(settings.visible)
            column
          }
        }
        val keys = params.map(_._1).toSet
        val columns2 = columnsDesc.filterNot { case (key, _) =>
          keys.contains(key)
        }.map(_._2)
        columns1 ::: columns2

      case None =>
        columnsDesc.map(_._2)
    }

    table.getColumns.addAll(columns:_*)
    ()
  }

  case class ColumnSettings(visible: Boolean, width: Option[Double])

}
