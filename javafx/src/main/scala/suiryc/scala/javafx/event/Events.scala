package suiryc.scala.javafx.event

import javafx.event.Event
import javafx.scene.Node
import javafx.scene.input.MouseEvent

/** Events helpers. */
object Events {

  /**
   * Checks an event is on target node.
   *
   * Useful e.g. when ensuring a mouse releasing event is performed while still
   * over the target node.
   * Non-mouse events are always considered on target.
   */
  def isOnNode(event: Event): Boolean =
    event match {
      case event: MouseEvent => event.getTarget.asInstanceOf[Node].contains(event.getX, event.getY)
      case _ => true
    }

}
