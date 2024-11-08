package suiryc.scala.javafx.stage

import java.util.concurrent.atomic.AtomicBoolean
import javafx.beans.property.{ObjectProperty, ReadOnlyBooleanProperty}
import javafx.event.{Event, EventHandler}
import javafx.stage.Stage
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.stage.Stages.StageLocation
import suiryc.scala.settings.ConfigEntry

/** Adds functions to persist and restore view. */
trait StagePersistentView {

  /** Persists view (e.g. stage location and size). */
  protected def persistView(): Unit

  /** Restores view (e.g. stage location and size). */
  protected def restoreView(): Unit

}

object StagePersistentView {

  /**
   * Hookups persistence.
   *
   * Wraps onCloseRequest to prepend persistence if requested.
   * Waits for 'showing' to trigger restoring.
   */
  protected[javafx] def hookup[A <: Event](onCloseRequestProperty: ObjectProperty[EventHandler[A]],
    showingProperty: ReadOnlyBooleanProperty, view: StagePersistentView,
    persist: Boolean, restore: Boolean): Unit =
  {
    if (persist) {
      // Wrap current onCloseRequest handler and prepend persistence.
      val onCloseRequest = Option(onCloseRequestProperty.get)
      onCloseRequestProperty.set { event =>
        view.persistView()
        onCloseRequest.foreach(_.handle(event))
      }
    }

    if (restore) {
      // Trigger restore when stage is showing.
      if (showingProperty.get) {
        view.restoreView()
      } else {
        showingProperty.listen2 { (cancellable, showing) =>
          if (showing) {
            cancellable.cancel()
            view.restoreView()
          }
        }
        ()
      }
    }
  }

}

/**
 * Simple view to persist/restore stage location.
 *
 * @param stageLocation persistent stage location value
 * @param first whether this is the first (ever) stage to be shown
 * @param setMinimumDimensions whether to set stage minimum dimensions
 * @param setSize whether to set stage size
 */
abstract class StageLocationPersistentView(
  stageLocation: ConfigEntry[StageLocation],
  first: Boolean = false,
  setMinimumDimensions: Boolean = true,
  setSize: Boolean = true
) extends StagePersistentView {

  protected def stage: Stage

  override protected def restoreView(): Unit = {
    // Fix HiDPI is needed.
    Stages.fixHiDPI(stage)
    Stages.onStageReady(stage, first = first && StageLocationPersistentView.checkFirstStage) {
      restoreViewOnStageReady()
    }
  }

  protected def restoreViewOnStageReady(): Unit = {
    // Restore stage location
    if (setMinimumDimensions) Stages.setMinimumDimensions(stage)
    stageLocation.opt.foreach { loc =>
      Stages.setLocation(stage, loc, setSize = setSize)
    }
  }

  override protected def persistView(): Unit = {
    // Persist stage location
    // Note: if iconified, resets it
    stageLocation.set(Stages.getLocation(stage).orNull)
  }

}

object StageLocationPersistentView {

  private val firstStage = new AtomicBoolean(true)

  /** Checks this is the first stage (only first call succeeds). */
  def checkFirstStage: Boolean = firstStage.compareAndSet(true, false)

  /** Builds a simple persistent view for an existing stage. */
  def apply(
    stage: Stage,
    stageLocation: ConfigEntry[StageLocation],
    first: Boolean = false,
    setMinimumDimensions: Boolean = true,
    setSize: Boolean = true): StageLocationPersistentView =
  {
    val stage0 = stage
    new StageLocationPersistentView(stageLocation, first, setMinimumDimensions, setSize) {
      protected val stage: Stage = stage0
    }
  }

}
