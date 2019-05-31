package suiryc.scala.javafx.css

import javafx.css.Styleable

/** Styleable helpers. */
object Styleables {

  /** Toggles style class. */
  def toggleStyleClass(styleable: Styleable, styleClass: String, set: Boolean): Unit = {
    if (set && !styleable.getStyleClass.contains(styleClass)) styleable.getStyleClass.add(styleClass)
    else if (!set) styleable.getStyleClass.remove(styleClass)
    ()
  }

}
