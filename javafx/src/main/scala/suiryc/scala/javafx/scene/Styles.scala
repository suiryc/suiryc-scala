package suiryc.scala.javafx.scene

import javafx.css.PseudoClass
import javafx.scene.{Node, Scene}
import javafx.scene.control.{Control, Tooltip}

/** JavaFX styles helpers. */
object Styles extends StylesFeat {

  /** 'warning' pseudo class */
  val warningClass: PseudoClass = PseudoClass.getPseudoClass("warning")

  /** 'error' pseudo class */
  val errorClass: PseudoClass = PseudoClass.getPseudoClass("error")

  /** Dummy tooltip class to keep original tooltip when replaced. */
  class DummyTooltip(msg: String, val originalTooltip: Tooltip) extends Tooltip(msg)

  lazy val stylesheet: String = getClass.getResource("styles.css").toExternalForm

  // The node (internal, somehow private) property key used to store a Tooltip.
  // See javafx.scene.control.Tooltip.
  // For Controls, tooltip is a specific (handled and public) property.
  private[scene] val TOOLTIP_PROP_KEY = "javafx.scene.control.Tooltip"

}

/** JavaFX styles helpers. */
trait StylesFeat {

  import Styles._

  /**
   * Changes pseudo class state.
   *
   * @param node target node
   * @param pseudoClass pseudo class to change state
   * @param active whether to active state
   */
  def togglePseudoClass(node: Node, pseudoClass: PseudoClass, active: Boolean): Unit = {
    node.pseudoClassStateChanged(pseudoClass, active)
  }

  /**
   * Changes pseudo class state.
   *
   * @param node target node
   * @param pseudoClass pseudo class to change state
   * @param active whether to active state
   */
  def togglePseudoClass(node: Node, pseudoClass: String, active: Boolean): Unit = {
    togglePseudoClass(node, PseudoClass.getPseudoClass(pseudoClass), active)
  }

  /**
   * Changes tooltip.
   *
   * Upon activation, current tooltip is replaced by given message. For the very
   * first activation, the original tooltip is remembered so that it can be
   * restored upon deactivation.
   * Upon deactivation, current tooltip is removed, and replaced by original one
   * when applicable.
   *
   * @param node target node
   * @param active whether to active tooltip
   * @param msg tooltip message
   */
  def toggleTooltip(node: Node, active: Boolean, msg: String): Unit = {
    val controlOpt = node match {
      case control: Control ⇒ Some(control)
      case _ ⇒ Option.empty[Control]
    }
    val current = controlOpt.map(_.getTooltip).getOrElse {
      node.getProperties.get(TOOLTIP_PROP_KEY).asInstanceOf[Tooltip]
    }
    val original = current match {
      case d: DummyTooltip ⇒ d.originalTooltip
      case v ⇒ v
    }
    val next =
      if (active) new DummyTooltip(msg, original)
      else original
    if (!(next eq current)) {
      controlOpt match {
        case Some(control) ⇒
          // We can replace control tooltip in one step
          control.setTooltip(next)

        case None ⇒
          // For other nodes, we first uninstall the current tooltip before
          // installing the next one.
          // (uninstalling/installing a null tooltip is a noop)
          Tooltip.uninstall(node, current)
          Tooltip.install(node, next)
      }
    }
  }

  /**
   * Changes warning pseudo class state.
   *
   * @param node target node
   * @param active whether to active state
   * @param msgOpt optional tooltip message (to use upon activate)
   */
  def toggleWarning(node: Node, active: Boolean, msgOpt: Option[String] = None): Unit = {
    togglePseudoClass(node, warningClass, active)
    toggleTooltip(node, active && msgOpt.isDefined, msgOpt.orNull)
  }

  /**
   * Changes warning pseudo class state.
   *
   * @param node target node
   * @param active whether to active state
   * @param msg tooltip message (to use upon activate)
   */
  def toggleWarning(node: Node, active: Boolean, msg: String): Unit = {
    toggleWarning(node, active, if (active) Some(msg) else None)
  }

  /**
   * Changes error pseudo class state.
   *
   * @param node target node
   * @param active whether to active state
   * @param msgOpt optional tooltip message (to use upon activate)
   */
  def toggleError(node: Node, active: Boolean, msgOpt: Option[String] = None): Unit = {
    togglePseudoClass(node, errorClass, active)
    toggleTooltip(node, active && msgOpt.isDefined, msgOpt.orNull)
  }

  /**
   * Changes error pseudo class state.
   *
   * @param node target node
   * @param active whether to active state
   * @param msg tooltip message (to use upon activate)
   */
  def toggleError(node: Node, active: Boolean, msg: String): Unit = {
    toggleError(node, active, if (active) Some(msg) else None)
  }

  /** Helper to add stylesheet. */
  def addStylesheet(scene: Scene): Unit = {
    scene.getStylesheets.add(stylesheet)
    ()
  }

}
