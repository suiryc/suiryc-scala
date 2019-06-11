package suiryc.scala.javafx.scene.text

import com.sun.javafx.tk.Toolkit
import javafx.scene.text.Font

/** Font helpers. */
object Fonts {

  /** Computes text width for the given font. */
  def textWidth(font: Font, s: String): Double = {
    s.map { c =>
      Toolkit.getToolkit.getFontLoader.getFontMetrics(font).getCharWidth(c).toDouble
    }.sum
  }

  /** Computes text height for the given font. */
  def textHeight(font: Font): Double = {
    Toolkit.getToolkit.getFontLoader.getFontMetrics(font).getLineHeight.toDouble
  }

}
