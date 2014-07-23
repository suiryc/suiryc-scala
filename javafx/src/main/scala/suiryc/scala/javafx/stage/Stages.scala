package suiryc.scala.javafx.stage

import grizzled.slf4j.Logging
import javafx.beans.property.{DoubleProperty, ReadOnlyDoubleProperty}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.stage.Stage
import suiryc.scala.javafx.concurrent.JFXSystem


object Stages
  extends Logging
{

  def trackMinimumDimensions(stage: Stage) {
    val title = stage.getTitle

    /* After show(), the stage dimension returned by JavaFX does not seem to
     * include the platform decorations (at least under Linux). Somehow those
     * appear to be included later, once we return.
     * However changes in those dimensions can be tracked, so as a hack we can
     * still wait a bit to get them.
     *
     * Notes to take into account:
     *  - it appears JavaFX do not go directly to the actual size, but
     *    shrinks down before
     *  - when stage is not yet showing, initial stage value is NaN and
     *    scene is 0; actual initial value is set upon first observed change
     */
    def trackMinimumDimension(label: String, stageMinProp: DoubleProperty,
      stageProp: ReadOnlyDoubleProperty, sceneProp: ReadOnlyDoubleProperty)
    {
      import scala.concurrent.duration._

      var sceneValue = sceneProp.get()
      var stageValue = stageProp.get()

      logger trace(s"Initial '$title' minimum $label stage[$stageValue] scene[$sceneValue]")
      if (!stageValue.isNaN())
        stageMinProp.set(stageValue)
      if ((stageProp.get() <= sceneValue) || stageValue.isNaN()) {
        val changeListener = new ChangeListener[Number] {
          override def changed(arg0: ObservableValue[_ <: Number], arg1: Number, arg2: Number) {
            if (stageValue.isNaN()) {
              stageValue = stageProp.get()
              sceneValue = sceneProp.get()
              logger trace(s"Actualized '$title' minimum $label stage[$stageValue] scene[$sceneValue]")
            }
            if ((sceneProp.get() == sceneValue) && (stageProp.get() > sceneValue)) {
              logger trace(s"Retained '$title' minimum $label stage[${stageProp.get()}] scene[${sceneProp.get()}]")
              stageProp.removeListener(this)
              stageMinProp.set(stageProp.get())
            }
          }
        }
        stageProp.addListener(changeListener)
        /* Make sure to unregister ourself in any case */
        JFXSystem.scheduleOnce(1.seconds) {
          stageProp.removeListener(changeListener)
        }
      }
    }

    trackMinimumDimension("width", stage.minWidthProperty, stage.widthProperty, stage.getScene().widthProperty)
    trackMinimumDimension("height", stage.minHeightProperty, stage.heightProperty, stage.getScene().heightProperty)
  }

}
