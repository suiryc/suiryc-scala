package suiryc.scala.javafx.scene.control

import com.typesafe.scalalogging.LazyLogging
import javafx.scene.control.SplitPane

/** Pane helpers. */
object Panes extends LazyLogging {

  /** Encode SplitPane divider positions into a String. */
  def encodeDividerPositions(pane: SplitPane): String = {
    pane.getDividerPositions.mkString(";")
  }

  /** Restores SplitPane divider positions from String. */
  def restoreDividerPositions(pane: SplitPane, dividerPositions: String): Unit = {
    try {
      val positions = dividerPositions.split(';').map(_.toDouble)
      pane.setDividerPositions(positions: _*)
    } catch {
      case ex: Exception ⇒ logger.warn(s"Could not restore SplitPane divider positions=<$dividerPositions>: ${ex.getMessage}")
    }
  }

}
