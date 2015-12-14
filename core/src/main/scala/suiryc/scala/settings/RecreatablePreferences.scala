package suiryc.scala.settings

import java.util.prefs.Preferences

/** Wraps a Preferences node and recreates it if necessary. */
class RecreatablePreferences(_prefs: Preferences) {

  /** Root node. */
  private var node = _prefs

  /** Parent hierarchy nodes. */
  private val hierarchy = {
    @scala.annotation.tailrec
    def ancestor(prefs: Preferences, ancestors: List[String]): (Preferences, List[String]) = {
      Option(prefs.parent) match {
        case Some(parent) =>
          ancestor(parent, prefs.name :: ancestors)

        case None =>
          (prefs, ancestors)
      }
    }

    ancestor(node, Nil)
  }

  /** Re-creates parent hierarchy nodes. */
  private def recreate() = {
    val (root, ancestors) = hierarchy

    node = ancestors.foldLeft(root) { (root, name) =>
      root.node(name)
    }
  }

  /**
   * Gets the wrapped Preferences node.
   *
   * Re-creates parent hierarchy if necessary.
   */
  def prefs: Preferences = {
    if (!node.nodeExists("")) {
      recreate()
    }

    node
  }

}
