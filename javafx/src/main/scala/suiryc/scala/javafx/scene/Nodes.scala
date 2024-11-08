package suiryc.scala.javafx.scene

import javafx.scene.control.{CheckBox, Labeled, RadioButton}
import javafx.scene.layout.Region
import javafx.scene.text.Text
import javafx.scene.{Node, Parent}

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

  /**
   * Fixes HiDPI glitches.
   *
   * If output scale is not 1.0, fixes possible visual glitches.
   * When radio button/checkbox preferred width is set to 'computed', actual
   * rendering may truncate (ellipsis) text if scale is not 100%.
   * Whether it happens may depend on:
   *  - the actual scaling; e.g. non-fractional scaling like 200% usually is ok
   *  - the OS UI: Linux GTK 150% is ok unlike 125%; Windows is not ok for
   *    neither 150% nor 125%
   * Manually interacting with the node may fix the glitch (size recomputed, also
   * affecting layout): does it on Windows, not Linux GTK.
   *
   * A workaround is to forcibly change (then reset) the text: node size is
   * recomputed appropriately.
   */
  def fixHiDPI(node: Node): Unit = {
    // Fix (maybe) needed if scale is not 100%.
    val needFix = Option(node.getScene).flatMap { scene =>
      Option(scene.getWindow)
    }.exists(_.getOutputScaleX != 1.0)
    if (needFix) {
      def loop(node: Node): Unit = {
        node match {
          case checkBox: CheckBox =>
            fixHiDPINode(checkBox)

          case radioButton: RadioButton =>
            fixHiDPINode(radioButton)

          case parent: Parent =>
            parent.getChildrenUnmodifiable.forEach(loop)

          case _ =>
          // Nothing to do.
        }
      }
      loop(node)
    }
  }

  private def fixHiDPINode(labeled: Labeled): Unit = {
    // Fix is needed if all conditions are met:
    //  - displayed text is not empty
    //  - node preferred width is "computed size"
    //  - node does not use wrapping
    //  - Text child (which actually displays the text) content differs from
    //    node text: this is the case if it does not fit (and ellipsis is used
    //    when setup for this node)
    val text = Option(labeled.getText).getOrElse("")

    lazy val isCut: Boolean = {
      val it = labeled.getChildrenUnmodifiable.iterator()
      @scala.annotation.tailrec
      def loop(): Boolean = {
        if (it.hasNext) {
          it.next() match {
            case t: Text => t.getText != text
            case _       => loop()
          }
        } else false
      }
      loop()
    }

    if (text.nonEmpty && (labeled.getPrefWidth == Region.USE_COMPUTED_SIZE) && !labeled.isWrapText && isCut) {
      // Belt and suspenders:
      // We don't expect the temporary text we set to be displayed, since we
      // immediately reset it. But just in case, do the bare minimum of change:
      //  - drop last space if applicable
      //  - replace last character by space otherwise
      labeled.setText(s"${text.dropRight(1)}${if (text.endsWith(" ")) "" else " "}")
      labeled.setText(text)
    }
  }

}
