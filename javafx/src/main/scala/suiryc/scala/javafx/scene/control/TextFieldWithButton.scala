package suiryc.scala.javafx.scene.control

import javafx.beans.property._
import javafx.css.PseudoClass
import javafx.event.{ActionEvent, EventHandler}
import javafx.geometry.{HPos, VPos}
import javafx.scene.control.{Control, Skin, SkinBase, TextField}
import javafx.scene.input.{MouseButton, MouseEvent}
import javafx.scene.layout.{Region, StackPane}
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.event.EventHandler._

/**
 * TextField with button.
 *
 * Heavily based on JavaFX ComboBox, ripped off its actual choice node.<br>
 * This controls uses 'text-field-with-button' style class (based on original
 * 'combo-box-base', which gives a generic look) and the one provided through
 * 'customStyleClass' property (or upon creation) for customization.<br>
 * As for comboboxes, the control contains an 'arrow-button' element which
 * contains an 'arrow' element. Unlike comboboxes 'showing' and 'contains-focus'
 * pseudo-classes are not used. And like buttons the 'armed' pseudo-class is
 * used on 'arrow-button'.
 * <p>
 * Minimal customization requires to define the 'arrow' graphic content, e.g.
 * by giving it an SVG path as shape:
 * <pre>
 * .styleClass > .arrow-button > .arrow {
 *   -fx-shape: "M 0 0 h 7 l -3.5 4 z";
 * }
 * </pre>
 * <p>
 * It is possible to disable the button alone through the 'buttonDisable'
 * property.
 *
 * @note This control uses its own style class instead of relying on
 *       'combo-box-base' so that style sheets loading order does not matter.
 * param styleClass style class used for graphic customization
 */
class TextFieldWithButton() extends Control {

  def this(styleClass: String) {
    this()
    setCustomStyleClass(styleClass)
  }

  // The actual text field to which we want to associate a button.
  val textField = new TextField()

  // Use our style class (based on JavaFX 'combo-box-base', on which are built
  // combo-boxes and date-pickers).
  getStyleClass.add("text-field-with-button")

  // Propagate focus property from text field to our control, so that it is
  // visible on the whole control.
  textField.focusedProperty().listen { v =>
    setFocused(v)
  }

  // A property to set this control custom style class
  private val customStyleClass = new SimpleStringProperty(TextFieldWithButton.this, "customStyleClass")
  def customStyleClassProperty: StringProperty = customStyleClass
  def getCustomStyleClass: String = customStyleClassProperty.get
  def setCustomStyleClass(value: String): Unit = customStyleClassProperty.set(value)

  // Listen to custom style class to update this control style class
  customStyleClass.listen { (_, v0, v1) =>
    Option(v0).foreach(getStyleClass.remove)
    Option(v1).foreach(getStyleClass.add)
  }

  // A property to disable the button.
  private val buttonDisable = new SimpleBooleanProperty(TextFieldWithButton.this, "buttonDisable")
  def buttonDisableProperty: BooleanProperty = buttonDisable
  def isButtonDisable: Boolean = buttonDisableProperty.get
  def setButtonDisable(value: Boolean): Unit = buttonDisableProperty.set(value)

  // The event handler that can be set to be notified when the button is triggered.
  private val onButtonAction = new SimpleObjectProperty[EventHandler[ActionEvent]](TextFieldWithButton.this, "onButtonAction")
  def onButtonActionProperty: ObjectProperty[EventHandler[ActionEvent]] = onButtonAction
  def getOnButtonAction: EventHandler[ActionEvent] = onButtonActionProperty.get
  def setOnButtonAction(value: EventHandler[ActionEvent]): Unit = onButtonActionProperty.set(value)

  // This is how we define our own skin, in which the actual layout is done.
  override protected def createDefaultSkin: Skin[_] =
    new TextFieldWithButtonSkin(this, textField)

  // This is how JavaFX will automatically use our associated stylesheet.
  override def getUserAgentStylesheet: String =
    TextFieldWithButton.stylesheet

  // Helper functions to get/set text directly
  def getText: String = textField.getText
  def setText(value: String): Unit = textField.setText(value)

}

object TextFieldWithButton {

  private lazy val stylesheet = classOf[TextFieldWithButton].getResource("text-field-with-button.css").toExternalForm

}

/**
 * TextField skin with button.
 *
 * Heavily based on JavaFX ComboBox, ripped off its actual choice node.<br>
 * Note the code re-uses 'arrow' and 'arrow-button' class and id in order to
 * rely on the same CSS than ComboBox.
 */
class TextFieldWithButtonSkin(control: TextFieldWithButton, textField: TextField) extends SkinBase[TextFieldWithButton](control) {

  import TextFieldWithButtonSkin._

  // Note: most of the code (nodes creation, layout handling) comes from
  // com.sun.javafx.scene.control.skin.ComboBoxBaseSkin

  // The region containing the graphic 'arrow'
  protected val arrow = new Region
  arrow.setFocusTraversable(false)
  arrow.getStyleClass.setAll("arrow")
  arrow.setId("arrow")
  arrow.setMaxWidth(Region.USE_PREF_SIZE)
  arrow.setMaxHeight(Region.USE_PREF_SIZE)
  arrow.setMouseTransparent(true)

  // The actual 'arrow-button'
  protected val arrowButton = new StackPane
  arrowButton.setFocusTraversable(false)
  arrowButton.setId("arrow-button")
  arrowButton.getStyleClass.setAll("arrow-button")
  arrowButton.getChildren.add(arrow)

  // Bind the control 'buttonDisable' property to disable 'arrow-button'.
  arrowButton.disableProperty.bind(control.buttonDisableProperty)

  getChildren.addAll(textField, arrowButton)
  textField.applyCss()

  // Note: 'DatePickerSkin' also listens to 'arrow.paddingProperty' changes
  // in order to apply rounded padding values. This does not seem necessary.

  // Listen for some mouse events, in order to detect when the 'arrow-button'
  // is actually triggered.
  // Code based on button behavior: we keep an 'armed' property that is set
  // when button is about to be triggered, and 'fire' is called when trigger
  // happens. Leaving the button without releasing disarms, and going back
  // without releasing re-arms.
  // See: javafx.scene.control.ButtonBase,
  // com.sun.javafx.scene.control.skin.BehaviorSkinBase and
  // com.sun.javafx.scene.control.behavior.ButtonBehavior
  arrowButton.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEntered _)
  arrowButton.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExited _)
  arrowButton.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed _)
  arrowButton.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleased _)

  // TODO: should we also handle TouchEvents (at least pressed/released)
  // explicitly ? Or does it triggers 'synthesized' MouseEvents ?

  protected def fire(): Unit =
    Option(control.getOnButtonAction).foreach { handler =>
      // scalastyle:off null
      handler.handle(new ActionEvent(arrowButton, null))
      // scalastyle:on null
    }

  // The 'armed' property.
  protected val armed = new SimpleBooleanProperty() {
    override protected def invalidated(): Unit = arrowButton.pseudoClassStateChanged(ARMED_PSEUDOCLASS_STATE, get())
  }
  protected def isArmed = armed.get()
  protected def arm() = armed.set(true)
  protected def disarm() = armed.set(false)

  protected def mousePressed(e: MouseEvent) {
    // Arms button upon actual standard click on the button.
    val valid = (e.getButton == MouseButton.PRIMARY) && !(e.isMiddleButtonDown ||
      e.isSecondaryButtonDown || e.isShiftDown || e.isControlDown || e.isAltDown || e.isMetaDown)
    if (!isArmed && valid) arm()
  }

  // Fires (and disarms button) if triggered.
  protected def mouseReleased(e: MouseEvent): Unit =
    if (isArmed) {
      fire()
      disarm()
    }

  // Re-arms button if we get back to the button while still pressing.
  protected def mouseEntered(e: MouseEvent): Unit =
    if (arrowButton.isPressed) arm()

  // Disarms button if we leave the button (when armed, that is while still pressing).
  protected def mouseExited(e: MouseEvent): Unit =
    if (isArmed) disarm()


  override protected def layoutChildren(x: Double, y: Double, w: Double, h: Double): Unit = {
    val arrowWidth = snapSize(arrow.prefWidth(-1))
    val arrowButtonWidth = arrowButton.snappedLeftInset + arrowWidth + arrowButton.snappedRightInset
    textField.resizeRelocate(x, y, w - arrowButtonWidth, h)
    arrowButton.resize(arrowButtonWidth, h)
    positionInArea(arrowButton, (x + w) - arrowButtonWidth, y, arrowButtonWidth, h, 0, HPos.CENTER, VPos.CENTER)
  }

  override protected def computePrefWidth(height: Double, topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double = {
    val arrowWidth = snapSize(arrow.prefWidth(-1))
    val arrowButtonWidth = arrowButton.snappedLeftInset + arrowWidth + arrowButton.snappedRightInset
    val displayNodeWidth = textField.prefWidth(height)
    val totalWidth = displayNodeWidth + arrowButtonWidth
    leftInset + totalWidth + rightInset
  }

  override protected def computePrefHeight(width: Double, topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double =
    topInset + textField.prefHeight(width) + bottomInset

  override protected def computeMaxWidth(height: Double, topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double =
    getSkinnable.prefWidth(height)

  override protected def computeMaxHeight(width: Double, topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double =
    getSkinnable.prefHeight(width)

  override protected def computeBaselineOffset(topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double =
    textField.getLayoutBounds.getMinY + textField.getLayoutY + textField.getBaselineOffset

}

object TextFieldWithButtonSkin {

  // Pseudo-class set when 'arrow-button' is armed.
  val ARMED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("armed")

}
