package suiryc.scala.javafx.scene.control

import javafx.beans.property._
import javafx.css.PseudoClass
import javafx.event.EventHandler
import javafx.geometry.{HPos, Pos, VPos}
import javafx.scene.control._
import javafx.scene.input.{InputEvent, MouseEvent}
import javafx.scene.layout.{Region, StackPane}
import javafx.scene.text.Font
import scala.annotation.unused
import suiryc.scala.javafx.beans.value.RichObservableValue._

/**
 * TextField with button(s).
 *
 * Heavily based on JavaFX ComboBox, ripped off its actual choice node.<br>
 * This control uses 'text-field-with-button' style class (based on original
 * 'combo-box-base', which gives a generic look) and the one provided through
 * 'customStyleClass' property (or upon creation) for customization.<br>
 * As for comboboxes, the control contains an 'arrow-button' element which
 * contains an 'arrow' element. Unlike comboboxes 'showing' and 'contains-focus'
 * pseudo-classes are not used. And like buttons the 'armed' pseudo-class is
 * used on 'arrow-button'.<br>
 * More than one 'arrow-button'/'arrow' can be set through the buttons property,
 * each one having its own 'arrow-button-n'/'arrow-n' id and style, with 'n'
 * starting from 0.
 * <p>
 * Minimal customization requires to define the 'arrow' graphic content, e.g.
 * by giving it an SVG path as shape:
 * <pre>
 * .styleClass > .arrow-button > .arrow {
 *   -fx-shape: "M 0 0 h 7 l -3.5 4 z";
 * }
 * </pre>
 * Alternatively a background image can be set on the 'arrow-button':
 * <pre>
 * .styleClass > .arrow-button {
 *   -fx-background-image: url("/path/to/image");
 *   -fx-background-repeat: no-repeat;
 *   -fx-background-position: center;
 * }
 * </pre>
 * <p>
 * It is possible to disable the button alone through the 'buttonDisable'
 * property.
 *
 * @note This control uses its own style class instead of relying on
 *       'combo-box-base' so that style sheets loading order does not matter.
 */
// scalastyle:off number.of.methods
class TextFieldWithButton() extends Control {

  /**
   * @param styleClass style class used for graphic customization
   */
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

  // Helpers to disable the (first and others) button.
  def buttonDisableProperty(n: Int): BooleanProperty = buttons(n).buttonDisableProperty
  def isButtonDisable(n: Int): Boolean = buttons(n).isButtonDisable
  def setButtonDisable(n: Int, value: Boolean): Unit = buttons(n).setButtonDisable(value)
  def buttonDisableProperty: BooleanProperty = buttonDisableProperty(0)
  def isButtonDisable: Boolean = isButtonDisable(0)
  def setButtonDisable(value: Boolean): Unit = setButtonDisable(0, value)

  // Helpers to set the (first and others) button tooltip.
  def buttonTooltipProperty(n: Int): StringProperty = buttons(n).buttonTooltipProperty
  def getButtonTooltip(n: Int): String = buttons(n).getButtonTooltip
  def setButtonTooltip(n: Int, value: String): Unit = buttons(n).setButtonTooltip(value)
  def buttonTooltipProperty: StringProperty = buttonTooltipProperty(0)
  def getButtonTooltip: String = getButtonTooltip(0)
  def setButtonTooltip(value: String): Unit = setButtonTooltip(0, value)

  // Helpers to set the event handler to be notified when the (first and others) button is triggered.
  def onButtonActionProperty(n: Int): ObjectProperty[EventHandler[InputEvent]] = buttons(n).onButtonActionProperty
  def getOnButtonAction(n: Int): EventHandler[InputEvent] = buttons(n).getOnButtonAction
  def setOnButtonAction(n: Int)(value: EventHandler[InputEvent]): Unit = buttons(n).setOnButtonAction(value)
  def onButtonActionProperty: ObjectProperty[EventHandler[InputEvent]] = onButtonActionProperty(0)
  def getOnButtonAction: EventHandler[InputEvent] = getOnButtonAction(0)
  def setOnButtonAction(value: EventHandler[InputEvent]): Unit = setOnButtonAction(0)(value)

  // The number of buttons to set (minimum 1)
  private val buttonsCount = new SimpleIntegerProperty(TextFieldWithButton.this, "buttonsCount", 1)
  def buttonsCountProperty: IntegerProperty = buttonsCount
  def getButtonsCount: Int = buttonsCountProperty.get
  def setButtonsCount(value: Int): Unit = buttonsCountProperty.set(math.max(value, 1))

  // The buttons
  private var buttons = List.tabulate(getButtonsCount) { idx =>
    new TextFieldButton(this, idx)
  }
  def getButtons: List[TextFieldButton] = buttons

  // Declare our children
  getChildren.add(textField)
  getButtons.foreach { button =>
    getChildren.add(button.arrowButton)
  }

  // This is how we define our own skin, in which the actual layout is done.
  override protected def createDefaultSkin: Skin[_] =
    new TextFieldWithButtonSkin(this, textField)

  // This is how JavaFX will automatically use our associated stylesheet.
  override def getUserAgentStylesheet: String =
    TextFieldWithButton.stylesheet

  // Helper functions to get/set text directly
  // Node: having the 'Property' right here is not strictly necessary, but
  // still useful.
  def textProperty: StringProperty = textField.textProperty
  def getText: String = textField.getText
  def setText(value: String): Unit = textField.setText(value)

  // Functions to access some useful properties directly in scene builder
  // Note: the 'font' is currently not useable in scene builder.
  def promptTextProperty: StringProperty = textField.promptTextProperty
  def getPromptText: String = textField.getPromptText
  def setPromptText(value: String): Unit = textField.setPromptText(value)
  def fontProperty: ObjectProperty[Font] = textField.fontProperty
  def getFont: Font = textField.getFont
  def setFont(value: Font): Unit = textField.setFont(value)
  def editableProperty: BooleanProperty = textField.editableProperty
  def isEditable: Boolean = textField.isEditable
  def setEditable(value: Boolean): Unit = textField.setEditable(value)
  def alignmentProperty: ObjectProperty[Pos] = textField.alignmentProperty
  def getAlignment: Pos = textField.getAlignment
  def setAlignment(value: Pos): Unit = textField.setAlignment(value)

  // Add/remove buttons as necessary
  buttonsCountProperty.listen { (_, v0, v1) =>
    if (v1.intValue < v0.intValue) {
      // Removing last buttons
      val (remaining, removed) = buttons.splitAt(v1.intValue)
      removed.foreach { button =>
        getChildren.remove(button.arrowButton)
      }
      buttons = remaining
    } else if (v1.intValue > v0.intValue) {
      // Adding buttons
      buttons = buttons ::: (v0.intValue until v1.intValue).toList.map { idx =>
        val button = new TextFieldButton(this, idx)
        getChildren.add(button.arrowButton)
        button
      }
    }
  }

}
// scalastyle:on number.of.methods

object TextFieldWithButton {

  private lazy val stylesheet = classOf[TextFieldWithButton].getResource("text-field-with-button.css").toExternalForm

}

/** Class holding resources for one button set in TextFieldWithButton. */
class TextFieldButton(control: TextFieldWithButton, idx: Int) {

  import TextFieldWithButtonSkin._

  // Note: nodes create code comes from
  // javafx.scene.control.skin.ComboBoxBaseSkin

  // The region containing the graphic 'arrow'
  val arrow: Region = {
    val arrow = new Region
    val id = s"arrow-$idx"
    arrow.setFocusTraversable(false)
    arrow.setId(id)
    arrow.getStyleClass.setAll("arrow", id)
    arrow.setMinWidth(Region.USE_PREF_SIZE)
    arrow.setMinHeight(Region.USE_PREF_SIZE)
    arrow.setMaxWidth(Region.USE_PREF_SIZE)
    arrow.setMaxHeight(Region.USE_PREF_SIZE)
    arrow.setMouseTransparent(true)
    arrow
  }

  // The actual 'arrow-button'
  val arrowButton: StackPane = {
    val arrowButton = new StackPane
    val id = s"arrow-button-$idx"
    arrowButton.setFocusTraversable(false)
    arrowButton.setId(id)
    arrowButton.getStyleClass.setAll("arrow-button", id)
    arrowButton.getChildren.add(arrow)

    arrowButton.setUserData(this)

    // Listen for some mouse events, in order to detect when the 'arrow-button'
    // is actually triggered.
    // Code based on button behavior: we keep an 'armed' property that is set
    // when button is about to be triggered, and 'fire' is called when trigger
    // happens. Leaving the button without releasing disarms, and going back
    // without releasing re-arms.
    // See: javafx.scene.control.ButtonBase and
    // com.sun.javafx.scene.control.behavior.ButtonBehavior
    arrowButton.addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEntered _)
    arrowButton.addEventHandler(MouseEvent.MOUSE_EXITED, mouseExited _)
    arrowButton.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed _)
    arrowButton.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleased _)

    arrowButton
  }

  // A property to disable the button.
  private val buttonDisable = new SimpleBooleanProperty(control, s"buttonDisable-$idx")
  def buttonDisableProperty: BooleanProperty = buttonDisable
  def isButtonDisable: Boolean = buttonDisableProperty.get
  def setButtonDisable(value: Boolean): Unit = buttonDisableProperty.set(value)

  // A property to set a tooltip.
  private var tooltip: Option[Tooltip] = None
  private val buttonTooltip = new SimpleStringProperty(control, s"buttonTooltip-$idx")
  def buttonTooltipProperty: StringProperty = buttonTooltip
  def getButtonTooltip: String = buttonTooltip.get
  def setButtonTooltip(value: String): Unit = buttonTooltip.set(value)

  // The event handler that can be set to be notified when the button is triggered.
  private val onButtonAction = new SimpleObjectProperty[EventHandler[InputEvent]](control, s"onButtonAction-$idx")
  def onButtonActionProperty: ObjectProperty[EventHandler[InputEvent]] = onButtonAction
  def getOnButtonAction: EventHandler[InputEvent] = onButtonActionProperty.get
  def setOnButtonAction(value: EventHandler[InputEvent]): Unit = onButtonActionProperty.set(value)

  // Bind the 'buttonDisable' property to disable the 'arrow-button'.
  arrowButton.disableProperty.bind(buttonDisableProperty)
  // Update tooltip upon change.
  buttonTooltipProperty.listen { value =>
    tooltip.foreach(Tooltip.uninstall(arrowButton, _))
    tooltip = Option(value).filterNot(_.trim.isEmpty).map(new Tooltip(_))
    tooltip.foreach(Tooltip.install(arrowButton, _))
  }

  protected def fire(event: InputEvent): Unit =
    Option(getOnButtonAction).foreach(_.handle(event))

  // The 'armed' property.
  protected val armed: SimpleBooleanProperty = new SimpleBooleanProperty() {
    override protected def invalidated(): Unit = arrowButton.pseudoClassStateChanged(ARMED_PSEUDOCLASS_STATE, get())
  }
  protected def isArmed: Boolean = armed.get()
  protected def arm(): Unit = armed.set(true)
  protected def disarm(): Unit = armed.set(false)

  // Arms button if triggered
  protected def mousePressed(@unused e: MouseEvent): Unit =
    if (!isArmed) arm()

  // Fires (and disarms button) if triggered.
  protected def mouseReleased(e: MouseEvent): Unit =
    if (isArmed) {
      fire(e)
      disarm()
    }

  // Re-arms button if we get back to the button while still pressing.
  protected def mouseEntered(@unused e: MouseEvent): Unit =
    if (arrowButton.isPressed) arm()

  // Disarms button if we leave the button (when armed, that is while still pressing).
  protected def mouseExited(@unused e: MouseEvent): Unit =
    if (isArmed) disarm()

}

/**
 * TextField skin with button(s).
 *
 * Heavily based on JavaFX ComboBox, ripped off its actual choice node.<br>
 * Note the code re-uses 'arrow' and 'arrow-button' class and id in order to
 * rely on the same CSS than ComboBox.
 */
class TextFieldWithButtonSkin(control: TextFieldWithButton, textField: TextField) extends SkinBase[TextFieldWithButton](control) {

  import TextFieldWithButtonSkin._

  // Note: most of the code (nodes creation, layout handling) comes from
  // javafx.scene.control.skin.ComboBoxBaseSkin, and a little bit from
  // javafx.scene.control.skin.ComboBoxListViewSkin

  getChildren.setAll(textField :: control.getButtons.map(_.arrowButton) : _*)
  textField.applyCss()

  // Update control when necessary
  control.buttonsCountProperty.listen { _ =>
    getChildren.setAll(textField :: control.getButtons.map(_.arrowButton) : _*)
    control.requestLayout()
  }

  // Note: 'DatePickerSkin' also listens to 'arrow.paddingProperty' changes
  // in order to apply rounded padding values. This does not seem necessary.


  override protected def layoutChildren(x: Double, y: Double, w: Double, h: Double): Unit = {
    // Buttons are placed relatively to the right side of the control.
    control.getButtons.reverse.foldLeft(0.0) { (arrowButtonsWidth, button) =>
      val arrowButton = button.arrowButton
      val arrowButtonWidth = snapSizeX(arrowButton.prefWidth(-1))
      textField.resizeRelocate(x, y, w - arrowButtonsWidth - arrowButtonWidth, h)
      arrowButton.resize(arrowButtonWidth, h)
      positionInArea(arrowButton, (x + w) - arrowButtonsWidth - arrowButtonWidth, y, arrowButtonWidth, h, 0, HPos.CENTER, VPos.CENTER)
      arrowButtonsWidth + arrowButtonWidth
    }
    ()
  }

  // Note: not setting a minimum width prevents the control to be properly
  // resized: can grow (when setting max width to max value) but cannot shrink
  // back afterwards.
  override protected def computeMinWidth(height: Double, topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double =
    MIN_WIDTH

  override protected def computePrefWidth(height: Double, topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double = {
    val arrowButtonsWidth = control.getButtons.map { button =>
      snapSizeX(button.arrowButton.prefWidth(-1))
    }.sum
    val displayNodeWidth = textField.prefWidth(height)
    val totalWidth = displayNodeWidth + arrowButtonsWidth
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
  val ARMED_PSEUDOCLASS_STATE: PseudoClass = PseudoClass.getPseudoClass("armed")

  // Minimum width.
  // See: javafx.scene.control.skin.ComboBoxListViewSkin
  val MIN_WIDTH: Double = 50

}
