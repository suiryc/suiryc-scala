package suiryc.scala.javafx.stage

import grizzled.slf4j.Logging
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.stage.Stage
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.javafx.concurrent.JFXSystem


object Stages
  extends Logging
{

  def trackMinimumDimensions(stage: Stage, size: Option[(Double, Double)] = None) {
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
    def trackMinimumDimension(label: String, setStageMin: Double => Unit,
      stageProp: ReadOnlyDoubleProperty, sceneProp: ReadOnlyDoubleProperty,
      setStageValue: Double => Unit, endValue: Option[Double])
    {
      import scala.concurrent.duration._

      var sceneValue = sceneProp.get()
      var stageValue = stageProp.get()

      logger.trace(s"Initial '$title' minimum $label stage[$stageValue] scene[$sceneValue]")
      if (!stageValue.isNaN)
        setStageMin(stageValue)
      if ((stageProp.get() <= sceneValue) || stageValue.isNaN) {
        val subscription = stageProp.listen2 { subscription =>
          if (stageValue.isNaN) {
            stageValue = stageProp.get()
            sceneValue = sceneProp.get()
            logger.trace(s"Actualized '$title' minimum $label stage[$stageValue] scene[$sceneValue]")
          }
          if ((sceneProp.get() == sceneValue) && (stageProp.get() > sceneValue)) {
            logger.trace(s"Retained '$title' minimum $label stage[${stageProp.get()}] scene[${sceneProp.get()}]")
            subscription.unsubscribe()
            setStageMin(stageProp.get())
            endValue foreach { v =>
              if (v >= stageProp.get())
                setStageValue(v)
            }
          }
        }
        /* Make sure to unregister ourself in any case */
        JFXSystem.scheduleOnce(1.seconds) {
          subscription.unsubscribe()
        }
      }
    }

    trackMinimumDimension("width", stage.setMinWidth, stage.widthProperty, stage.getScene.widthProperty, stage.setWidth, size.map(_._1))
    trackMinimumDimension("height", stage.setMinHeight, stage.heightProperty, stage.getScene.heightProperty, stage.setHeight, size.map(_._2))
  }

}
