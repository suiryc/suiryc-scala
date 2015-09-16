package suiryc.scala.javafx.scene.control

import javafx.scene.control.{TableColumn, TableView}

/** TableView helpers. */
object TableViews {

  /**
   * Gets table columns view.
   *
   * Formats columns order and width into a string.
   */
  def getColumnsView[A](table: TableView[A], columnsDesc: List[(String, TableColumn[A, _])]): String = {
    import scala.collection.JavaConversions._

    table.getColumns.toList.map { column =>
      val key = columnsDesc.find(_._2 eq column).get._1
      s"$key=${column.getWidth}"
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
              try { Some(key -> value.toDouble) }
              catch { case ex: Throwable => Some(key -> 0.0) }

            case _ =>
              None
          }
        }

        val columns1 = params.flatMap { param =>
          val (key, width) = param
          columnsDesc.find(_._1 == key).map { case (_, column) =>
            if (width > 0) column.setPrefWidth(width)
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
  }

}