package suiryc.scala.javafx.scene

import javafx.scene.Node

/**
 * Node helpers.
 *
 * If used, manages a node user data as a Map so that we can attach various
 * unrelated objects.
 */
object Nodes {

  /** Sets key-value pair in user data map. */
  def setUserData[A](node: Node, key: String, data: A): Unit = {
    Option(node.getUserData) match {
      case None               ⇒ node.setUserData(Map[String, Any](key → data))
      case Some(m: Map[_, _]) ⇒ node.setUserData(m.asInstanceOf[Map[String, Any]] + (key → data))
      case v                  ⇒ throw new Exception(s"Node has unhandled user data: $v")
    }
  }

  /** Removes key in user data map. */
  def removeUserData(node: Node, key: String): Unit = {
    Option(node.getUserData) match {
      case None               ⇒ // Nothing to do
      case Some(m: Map[_, _]) ⇒ node.setUserData(m.asInstanceOf[Map[String, Any]] - key)
      case v                  ⇒ throw new Exception(s"Node has unhandled user data: $v")
    }
  }

  /** Gets optional value from user data map. */
  def getUserDataOpt[A](node: Node, key: String): Option[A] = {
    Option(node.getUserData) match {
      case None               ⇒ None
      case Some(m: Map[_, _]) ⇒ m.asInstanceOf[Map[String, A]].get(key)
      case v                  ⇒ throw new Exception(s"Node has unhandled user data: $v")
    }
  }

  /** Gets value from user data map. */
  def getUserData[A](node: Node, key: String): A = getUserDataOpt(node, key).get

}
