package suiryc.scala.javafx.scene

import javafx.scene.Node

/**
 * Node helpers.
 *
 * If used, manages a node user data as a Map so that we can attach various
 * unrelated objects.
 */
object Nodes {

  /**
   * Computes pixel (left/top) edge.
   *
   * @param v pixel x or y position
   * @return pixel edge
   */
  def pixelEdge(v: Double): Double = math.floor(v)

  /**
   * Computes value to center on a pixel.
   *
   * See 'Coordinate System' section in JavaFX Node JavaDoc.<br>
   * The center of a pixel is at 0.5 past its index; e.g. top-left pixel center
   * is at x=0.5 and y=0.5 (and not x=0 y=0).
   * This function helps to get the center of pixel provided its index. This
   * is useful to draw sharp 1-pixel wide lines, otherwise antialiasing is used
   * to draw it 2-pixels wide.
   *
   * @param v pixel x or y position
   * @return pixel center
   */
  def pixelCenter(v: Double): Double = pixelEdge(v) + 0.5

  /** Sets key-value pair in user data map. */
  def setUserData[A](node: Node, key: String, data: A): Unit = {
    Option(node.getUserData) match {
      case None               => node.setUserData(Map[String, Any](key -> data))
      case Some(m: Map[_, _]) => node.setUserData(m.asInstanceOf[Map[String, Any]] + (key -> data))
      case v                  => throw new Exception(s"Node has unhandled user data: $v")
    }
  }

  /** Removes key in user data map. */
  def removeUserData(node: Node, key: String): Unit = {
    Option(node.getUserData) match {
      case None               => // Nothing to do
      case Some(m: Map[_, _]) => node.setUserData(m.asInstanceOf[Map[String, Any]] - key)
      case v                  => throw new Exception(s"Node has unhandled user data: $v")
    }
  }

  /** Gets optional value from user data map. */
  def getUserDataOpt[A](node: Node, key: String): Option[A] = {
    Option(node.getUserData) match {
      case None               => None
      case Some(m: Map[_, _]) => m.asInstanceOf[Map[String, A]].get(key)
      case v                  => throw new Exception(s"Node has unhandled user data: $v")
    }
  }

  /** Gets value from user data map. */
  def getUserData[A](node: Node, key: String): A = getUserDataOpt(node, key).get

}
