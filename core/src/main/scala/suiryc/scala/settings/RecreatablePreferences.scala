package suiryc.scala.settings

import java.util.prefs.Preferences


class RecreatablePreferences(_prefs: Preferences) {

  private var node = _prefs

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

  private def recreate() = {
    val (root, ancestors) = hierarchy

    node = ancestors.foldLeft(root) { (root, name) =>
      root.node(name)
    }
  }

  def prefs = {
    if (!node.nodeExists("")) {
      recreate()
    }

    node
  }

}
