package suiryc.scala.javafx.scene.control

import javafx.scene.control.{Spinner, SpinnerValueFactory}
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.util.StringConverter
import scala.concurrent.duration._
import suiryc.scala.concurrent.duration.Durations
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.misc.Units

/** Spinner helpers. */
object Spinners {

  private val PAGE_STEPS = 10

  /**
   * Handle useful events on spinner.
   *
   * Editable spinners don't increment/decrement when key up/down is pressed,
   * but let the editor field handle it (moves the cursor inside the editor).
   * We override this to have the same behavior as non-editable spinners.
   */
  def handleEvents(spinner: Spinner[_], pageSteps: Int = PAGE_STEPS): Unit = {
    // Notes:
    // Adding an event handler (compared to an event filter) is enough for what
    // we need. When we handle an event, consume it too (so that the nominal
    // behaviour is not triggered). We need to target both the spinner and its
    // editor: events are handled by the editor for editable spinners, or the
    // spinner itself otherwise.
    // Actually non-editable spinners already properly handle the up/down keys.
    // Only editable ones don't increment/decrement but move the cursor inside
    // the editor field.
    List(spinner, spinner.getEditor).foreach { control ⇒
      control.addEventHandler(KeyEvent.KEY_PRESSED, (event: KeyEvent) ⇒ {
        event.getCode match {
          case KeyCode.PAGE_UP ⇒
            event.consume()
            spinner.increment(pageSteps)

          case KeyCode.UP ⇒
            event.consume()
            spinner.increment()

          case KeyCode.DOWN ⇒
            event.consume()
            spinner.decrement()

          case KeyCode.PAGE_DOWN ⇒
            event.consume()
            spinner.decrement(pageSteps)

          case _ ⇒
        }
      })
    }
  }

}

/**
 * Spinner value factory handling Int values.
 *
 * Compared to the standard IntegerSpinnerValueFactory, we handle invalid values
 * (that cannot be parsed as integers) by reverting back to the current (valid)
 * value instead of throwing an exception.
 * We also allow empty value (meaning no value is set).
 */
class IntSpinnerValueFactory(spinner: Spinner[Option[Int]], initialValue: Option[Int] = None,
  min: Int = Int.MinValue, max: Int = Int.MaxValue, private var mandatory: Boolean = true
) extends SpinnerValueFactory[Option[Int]] {

  import IntSpinnerValueFactory._

  private def clampValue(value: Int): Int = {
    if (value < min) min
    else if (value > max) max
    else value
  }

  private def cleanValue(oldValue: Option[Int], newValue: Option[Int]): Option[Int] = {
    if (mandatory && newValue.isEmpty) {
      // If there is no more value while it is mandatory, get back to the
      // previous value. If there was none either (happens when setting the
      // first value), fallback to the initial value or default one.
      // Clamps the value too, so that when we get back here (since we will
      // change the value again), we already have the correct value.
      Some(clampValue(oldValue.orElse(initialValue).getOrElse(DEFAULT_VALUE)))
    } else {
      // Clamp value.
      newValue.map(clampValue)
    }
  }

  def setMandatory(mandatory: Boolean): Unit = {
    if (mandatory != this.mandatory) {
      this.mandatory = mandatory
      if (mandatory && getValue.isEmpty) setValue(cleanValue(None, Some(DEFAULT_VALUE)))
    }
  }

  // Set initial (cleaned) value.
  setValue(cleanValue(None, initialValue))

  // Listen to value changes in order to enforce minimum, maximum and mandatory
  // value. When applicable, change the value to the new corrected one.
  // This ensures that we eventually store and display the correct value: if
  // value needs to be corrected, since we change it, we store the corrected
  // value, and its representation will be updated accordingly.
  valueProperty.listen { (_, oldValue, newValue) ⇒
    val actual = cleanValue(oldValue, newValue)
    // If value needs to be corrected, change it (which means we will eventually
    // recursively ends up here again).
    if (actual != newValue) setValue(actual)
  }

  setConverter {
    // Notes:
    // When converting to string, don't return 'null' for None since in Spinner
    // it is a special value used when there is no factory or string converter
    // (happens during initialization), in which case 'value.toString' is used
    // instead.
    // Since we listen to value changes in order to enforce minimum, maximum and
    // mandatory value, we are sure that eventually we will convert/set the
    // correct value.
    // If value cannot be parsed, prefer cancelling edit (restores previous
    // value and representation) over returning None: that should not matter
    // much when value is mandatory (we would reset it ourselves above), but
    // when not mandatory 'None' is valid (and we prefer keeping on using the
    // previous value).
    // We need to make sure that the stored value matches its displayed string
    // representation. This means that when the value does not match the
    // constraints, either we need to cancel the edit (as is done upon parsing
    // issue) or explicitly change the value (as is done upon value change).
    // e.g. since we already properly clamp upon value change, it's easier to
    // not also do it right here because we would need to also 'setValue'.
    new StringConverter[Option[Int]] {
      override def toString(v: Option[Int]): String = v.map(_.toString).getOrElse("")
      override def fromString(s: String): Option[Int] = {
        try {
          Option(s).filterNot(_.trim.isEmpty).map(_.toInt)
        } catch {
          case _: Exception ⇒
            spinner.cancelEdit()
            getValue
        }
      }
    }
  }

  override def decrement(steps: Int): Unit =
    setValue(Some(clampValue(getValue.getOrElse(DEFAULT_VALUE) - steps)))

  override def increment(steps: Int): Unit =
    setValue(Some(clampValue(getValue.getOrElse(DEFAULT_VALUE) + steps)))

}

object IntSpinnerValueFactory {

  private val DEFAULT_VALUE: Int = 0

}

/** Spinner value factory handling FiniteDuration values. */
class FiniteDurationSpinnerValueFactory(spinner: Spinner[Option[FiniteDuration]], initialValue: Option[FiniteDuration] = None,
  min: FiniteDuration = 0.second, max: Option[FiniteDuration] = None, private var mandatory: Boolean = true
) extends SpinnerValueFactory[Option[FiniteDuration]] {

  import FiniteDurationSpinnerValueFactory._

  // Raw unit (extracted from text) when applicable. Includes any blank prefix.
  private var rawUnit = Option.empty[String]

  private def clampValue(value: FiniteDuration): FiniteDuration = {
    val unit = value.unit
    if (value < min) {
      // Ensure minimum value when applicable, and try to keep the requested
      // time unit.
      val value1 = FiniteDuration(math.round(min.toUnit(unit)), unit)
      if (value1 < min) {
        // If unit cannot be kept, use real minimum value and reset raw unit.
        rawUnit = None
        min
      } else value1
    } else {
      max.filter(_ < value).map { v ⇒
        // Ensure minimum value when applicable, and try to keep the requested
        // time unit.
        val value1 = FiniteDuration(math.round(v.toUnit(unit)), unit)
        if (value1 > v) {
          // If unit cannot be kept, use real maximum value and reset raw unit.
          rawUnit = None
          v
        } else value1
      }.getOrElse(value)
    }
  }

  private def cleanValue(oldValue: Option[FiniteDuration], newValue: Option[FiniteDuration]): Option[FiniteDuration] = {
    if (mandatory && newValue.isEmpty) {
      Some(clampValue(oldValue.orElse(initialValue).getOrElse(DEFAULT_VALUE)))
    } else {
      newValue.map(clampValue)
    }
  }

  def setMandatory(mandatory: Boolean): Unit = {
    if (mandatory != this.mandatory) {
      this.mandatory = mandatory
      if (mandatory && getValue.isEmpty) setValue(cleanValue(None, Some(DEFAULT_VALUE)))
    }
  }

  setValue(cleanValue(None, initialValue))

  valueProperty.listen { (_, oldValue, newValue) ⇒
    val actual = cleanValue(oldValue, newValue)
    if (actual != newValue) setValue(actual)
  }

  setConverter {
    new StringConverter[Option[FiniteDuration]] {
      override def toString(v: Option[FiniteDuration]): String = {
        v.map { v ⇒
          // Keep raw unit or fallback to short representation.
          rawUnit.map { unit ⇒
            s"${v.length}$unit"
          }.getOrElse {
            s"${v.length}${Durations.shortUnit(v.unit)}"
          }
        }.getOrElse("")
      }
      override def fromString(s: String): Option[FiniteDuration] = {
        if (Option(s).forall(_.trim.isEmpty)) None
        else {
          Durations.parseFinite(s).map { value ⇒
            // Value is valid: extract raw unit if possible.
            rawUnit = Option(s.trim.replaceFirst("^[+-]?[0-9]+", "")).filter(_.nonEmpty)
            Some(value)
          }.getOrElse {
            spinner.cancelEdit()
            getValue
          }
        }
      }
    }
  }

  /** Sets RAW (string) value and apply it. */
  def setValue(v: String): Unit = {
    // Update editor field.
    spinner.getEditor.setText(v)
    // Apply value.
    setValue(getConverter.fromString(v))
  }

  override def decrement(steps: Int): Unit = {
    val value0 = getValue.getOrElse(DEFAULT_VALUE)
    val unit = value0.unit
    setValue(Some(clampValue(value0 - FiniteDuration(steps.toLong, unit))))
  }

  override def increment(steps: Int): Unit = {
    val value0 = getValue.getOrElse(DEFAULT_VALUE)
    val unit = value0.unit
    setValue(Some(clampValue(value0 + FiniteDuration(steps.toLong, unit))))
  }

}

object FiniteDurationSpinnerValueFactory {

  private val DEFAULT_VALUE = 0.second

}

/** Spinner value factory handling size (bytes) values. */
class ByteSizeSpinnerValueFactory(spinner: Spinner[Option[Long]], initialValue: Option[Long] = None,
  min: Long = 0, max: Long = Long.MaxValue, private var mandatory: Boolean = true
) extends SpinnerValueFactory[Option[Long]] {

  import ByteSizeSpinnerValueFactory._

  // Unit (extracted from text).
  private var unit = Units.storage.unity
  // Any blank infix between value and unit.
  private var infix = ""

  private def clampValue(value: Long): Long = {
    def adjust(v: Long): Long = v - (v % unit.factor)
    if (value < min) adjust(min + unit.factor - 1)
    else if (value > max) adjust(max)
    else value
  }

  private def cleanValue(oldValue: Option[Long], newValue: Option[Long]): Option[Long] = {
    if (mandatory && newValue.isEmpty) {
      Some(clampValue(oldValue.orElse(initialValue).getOrElse(DEFAULT_VALUE)))
    } else {
      newValue.map(clampValue)
    }
  }

  def setUnit(unit: Units.Unit): Unit = {
    if (unit != this.unit) {
      this.unit = unit
      getValue.foreach { v ⇒
        setValue(Some(clampValue(Units.storage.toUnit(v, unit, 0).toLong * unit.factor)))
      }
    }
  }

  def setMandatory(mandatory: Boolean): Unit = {
    if (mandatory != this.mandatory) {
      this.mandatory = mandatory
      if (mandatory && getValue.isEmpty) setValue(cleanValue(None, Some(DEFAULT_VALUE)))
    }
  }

  setValue(cleanValue(None, initialValue))

  valueProperty.listen { (_, oldValue, newValue) ⇒
    val actual = cleanValue(oldValue, newValue)
    if (actual != newValue) setValue(actual)
  }

  setConverter {
    new StringConverter[Option[Long]] {
      override def toString(v: Option[Long]): String = {
        v.map { v ⇒
          s"${Units.storage.toUnit(v, unit, 0)}$infix${unit.label}"
        }.getOrElse("")
      }
      override def fromString(s: String): Option[Long] = {
        try {
          Option(s).filterNot(_.isEmpty).map { s ⇒
            val value = Units.storage.fromHumanReadable(s)
            val s1 = s.replaceFirst("^[+-]?[0-9]+", "")
            unit = Units.storage.findUnit(s1.trim).get
            infix = s1.take(s1.indexOf(s1.trim))
            value
          }
        } catch {
          case _: Exception ⇒
            spinner.cancelEdit()
            getValue
        }
      }
    }
  }

  def setValue(v: String): Unit = {
    spinner.getEditor.setText(v)
    setValue(getConverter.fromString(v))
  }

  override def decrement(steps: Int): Unit =
    setValue(Some(clampValue(getValue.getOrElse(DEFAULT_VALUE) - steps * unit.factor)))

  override def increment(steps: Int): Unit =
    setValue(Some(clampValue(getValue.getOrElse(DEFAULT_VALUE) + steps * unit.factor)))

}

object ByteSizeSpinnerValueFactory {

  private val DEFAULT_VALUE: Long = 0

}
