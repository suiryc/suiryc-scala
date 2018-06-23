package suiryc.scala.javafx.stage

import javafx.beans.property.{ObjectProperty, ReadOnlyBooleanProperty}
import javafx.event.{Event, EventHandler}
import suiryc.scala.javafx.beans.value.RichObservableValue._

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
      onCloseRequestProperty.set { event ⇒
        view.persistView()
        onCloseRequest.foreach(_.handle(event))
      }
    }

    if (restore) {
      // Trigger restore when stage is showing.
      if (showingProperty.get) {
        view.restoreView()
      } else {
        showingProperty.listen2 { (cancellable, showing) ⇒
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
