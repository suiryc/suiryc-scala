package suiryc.scala.javafx.scene.control

import javafx.scene.control.DatePicker
import suiryc.scala.javafx.beans.value.RichObservableValue._

/** DatePicker helpers. */
object DatePickers {

  /**
   * Tracks edition in date picker.
   *
   * Editing picker value (in the field editor) is usually not taken into
   * account until an action is performed like pressing 'Enter', which may at
   * the same time validate the form the field is in.
   * Here we track focus being lost to check the editor value and try to apply
   * it as field value if necessary and possible.
   * We also track the button being pressed to do the same before showing the
   * calendar to pick a date; thus the selected value is the currently edited
   * one if applicable.
   *
   * @param field to track
   */
  def trackEdition(field: DatePicker): Unit = {
    def checkEdited(): Unit = {
      val converter = field.getConverter
      val editedOpt = Option(field.getEditor.getText).map(_.trim).filterNot(_.isEmpty)
      val valueOpt = Option(field.getValue).map(converter.toString)
      if (editedOpt != valueOpt) {
        // Edited value differs from selected one, so check whether we can
        // apply the edited value, or need to reset it.
        val newValueOpt = editedOpt match {
          case Some(edited) =>
            try {
              Some(field.getConverter.fromString(edited))
            } catch {
              case _: Exception => None
            }

          case None =>
            None
        }
        field.setValue(newValueOpt.orNull)
        field.getEditor.setText(newValueOpt.map(converter.toString).orNull)
      }
    }

    // We want to check for the edited value when focus is lost
    field.getEditor.focusedProperty.listen { focused =>
      if (!focused) checkEdited()
    }

    // We also want to check for edited value right before picking a date.
    // When skin is applied, the 'arrow-button' node is the button used to
    // pick a date. It is not accessible until the skin is set.
    field.skinProperty.listen {
      Option(field.lookup("#arrow-button")).foreach { button =>
        if (Option[Any](button.getOnMousePressed).isEmpty) {
          button.setOnMousePressed { _ =>
            checkEdited()
          }
        }
      }
    }
    ()
  }

}
