package suiryc.scala.javafx.stage

import com.typesafe.config.Config
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.geometry.BoundingBox
import javafx.scene.control.Dialog
import javafx.stage.Stage
import scala.concurrent.{ExecutionContext, Promise}
import scala.concurrent.duration._
import suiryc.scala.akka.CoreSystem
import suiryc.scala.concurrent.RichFuture
import suiryc.scala.concurrent.RichFuture._
import suiryc.scala.javafx.beans.value.RichObservableValue
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.settings.{BaseConfigImplicits, ConfigEntry, Preference, PreferenceBuilder}
import suiryc.scala.sys.OS

/** JavaFX Stage helpers. */
object Stages {

  // Notes (Java 10):
  // Steps to display a stage depends on the OS, and also whether this is the
  // first stage to be displayed (for the running application).
  // Before showing, stage has NaN dimensions and scene has 0 dimensions.
  // Then, upon 'show',
  //
  // On Windows 10 build 1803:
  //  1. Scene dimension changes (preferred)
  //  2. Stage dimension changes (final, with decorations)
  //  3. Stage 'showing' property changes to 'true'
  // (scene dimension changes to 'minimum' if 'preferred' is smaller)
  //
  // On Gnome 3.28, for the first stage to be shown:
  //  1. Scene dimension changes two times (1x1 then preferred)
  //  2. Stage dimension changes a first time (not final)
  //  3. Stage 'showing' property changes to 'true'
  //  4. Stage dimension changes again (final, with decorations)
  // The second change (4.) appears to be queued sometime after 3.: any action
  // queued through JFXSystem.schedule or Platform.runLater after 3. may be
  // executed before 4. happens.
  // For a second showing (same stage or new one), the first change of stage
  // dimension is the final one; the second change appears to still be there,
  // only setting the final dimension a second time.
  //
  // In any case, the stage 'showing' property changes to 'true' at the same
  // time than 'show' call returns.
  //
  // When the application modifies the stage size, first the stage size is
  // changed then the scene one.
  // On Gnome 3.28 if the application modifies the dimension before the second
  // stage change (4.), the stage dimension changes three times: once to
  // the wanted dimension, then to the dimension it would have been changed to
  // (step 4. above), then again to the wanted dimension. The scene still
  // changes only once, but it happens before the last stage dimension change.
  // Since using JFXSystem.schedule or Platform.runLater does not help (unless
  // delaying for a fixed duration like 200ms), it is better to prevent doing
  // stuff relying on stage dimension at the same time (e.g. checking stage
  // minimum dimension).
  //
  // Setting minimum stage dimensions also result in a different behaviour
  // depending on the OS:
  // On Windows 10 build 1803: nothing happens unless current size is smaller.
  // On Gnome 3.28: the stage dimension changes to the minimum dimension
  // even if smaller than the current one.

  /**
   * Executes code once stage is "ready".
   *
   * Stage is considered ready when shown and in its final dimension (along
   * with its scene). How and when this happens depends on the OS. Caller
   * must not be setting the stage dimension in parallel as it may interfere;
   * instead it should e.g. do this inside the code to execute.
   *
   * If stage is already ready, execution is done right away (in the current
   * thread), otherwise execution is performed when applicable using the given
   * execution context.
   *
   * @param stage stage to check
   * @param first whether this is the first (ever) stage to be shown
   * @param timeout time limit before executing the code
   * @param f code to execute
   * @param ec execution context
   */
  def onStageReady(stage: Stage, first: Boolean, timeout: FiniteDuration = 1.second)(f: ⇒ Unit)(implicit ec: ExecutionContext): Unit = {
    def call(): Unit = {
      if (first && OS.isLinux) {
        val promise = Promise[Unit]()
        val width = stage.getWidth
        val height = stage.getHeight
        val cancellable = RichObservableValue.listen(Seq(stage.widthProperty, stage.heightProperty), {
          if ((stage.getWidth != width) && (stage.getHeight != height)) promise.trySuccess(())
          ()
        })
        val future = promise.future.withTimeout(timeout)
        future.onComplete { _ =>
          cancellable.cancel()
          f
        }
      } else {
        f
      }
    }

    if (stage.isShowing) call()
    else {
      stage.showingProperty.listen2 { (cancellable, showing) ⇒
        if (showing) {
          cancellable.cancel()
          call()
        }
      }
    }
    ()
  }

  /**
   * Sets minimum stage dimensions.
   *
   * Sets stage minimum width and height according to its content.
   * Caller is expected to have waited for the stage dimension to be final; if
   * stage (and scene) dimensions are currently being changed, the minimum
   * dimension computation may be wrong.
   *
   * Minimum dimension is not changed if already set, unless requested.
   *
   * @param stage stage to set minimum dimensions on
   * @param reset whether to reset minimum dimension (if already set)
   */
  def setMinimumDimensions(stage: Stage, reset: Boolean = false): Unit = {
    val setMinWidth = reset || (stage.getMinWidth <= 0)
    val setMinHeight = reset || (stage.getMinHeight <= 0)
    if (setMinWidth ||setMinHeight) {
      // To get the stage minimum dimensions we need to:
      // 1. Wait for the stage to be ready (precondition)
      val scene = stage.getScene
      // 2. Get the decoration size: difference between the stage and the scene
      val decorationWidth = stage.getWidth - scene.getWidth
      val decorationHeight = stage.getHeight - scene.getHeight
      // 3. Ask JavaFX for the scene content minimum width and height
      // Note: '-1' is a special value to retrieve the current value
      val minWidth = scene.getRoot.minWidth(-1) + decorationWidth
      val minHeight = scene.getRoot.minHeight(-1) + decorationHeight

      // Now we can set the stage minimum dimensions.
      if (setMinWidth) stage.setMinWidth(minWidth)
      if (setMinHeight) stage.setMinHeight(minHeight)
    }
  }

  /**
   * Tracks minimum stage dimensions.
   *
   * Sets stage minimum width and height according to its content.
   * If given, also sets stage size once done.
   *
   * @param stage stage to set minimum dimensions on
   * @param size initial stage size to set, or None
   */
  def trackMinimumDimensions(stage: Stage, size: Option[(Double, Double)] = None): Unit = {
    if (!OS.isLinux) {
      // The actual tracking code
      def track(): Unit = {
        import suiryc.scala.javafx.concurrent.JFXSystem

        JFXSystem.runLater {
          setMinimumDimensions(stage)
          size match {
            case Some((width, height)) =>
              if (width > stage.getMinWidth) stage.setWidth(width)
              if (height > stage.getMinHeight) stage.setHeight(height)

            case None =>
          }
        }
      }

      // Track now if stage is showing, or wait for it
      if (stage.isShowing) track()
      else stage.showingProperty.listen2 { (cancellable, showing) =>
        if (showing) {
          cancellable.cancel()
          track()
        }
      }
      ()
    }
  }

  /** Keeps stage bounds (x, y, width, height) upon hiding/showing. */
  def keepBounds(stage: Stage): Unit = {
    var box: Option[BoundingBox] = None

    // Notes:
    //  - when showing again stage after hiding, position and size are resetted
    //  - to prevent artifacts (black areas on top or side of scene), it is
    //    better to set position and size after showing stage; which is the case
    //    when listening to 'showing' changes
    stage.showingProperty.listen { showing =>
      if (showing) box.foreach { box =>
        stage.setX(box.getMinX)
        stage.setY(box.getMinY)
        stage.setWidth(box.getWidth)
        stage.setHeight(box.getHeight)
      }
      else box = Some(new BoundingBox(stage.getX, stage.getY, stage.getWidth, stage.getHeight))
    }
    ()
  }

  /**
   * Gets dialog stage.
   *
   * See: http://code.makery.ch/blog/javafx-dialogs-official/
   */
  def getStage(dialog: Dialog[_]): Stage =
    dialog.getDialogPane.getScene.getWindow.asInstanceOf[Stage]

  /** Stage location. */
  case class StageLocation(x: Double, y: Double, width: Double, height: Double, maximized: Boolean)

  /**
   * Gets stage location.
   *
   * There is no valid location if stage is minimized.
   */
  def getLocation(stage: Stage): Option[StageLocation] =
    if (stage.isIconified) None
    else Some(StageLocation(stage.getX, stage.getY, stage.getWidth, stage.getHeight, stage.isMaximized))

  /** Sets stage location. */
  def setLocation(stage: Stage, loc: StageLocation, setSize: Boolean): Unit = {
    stage.setX(loc.x)
    stage.setY(loc.y)
    if (setSize) {
      if (OS.isLinux) {
        // On Gnome 3.28, many things can change the stage dimension (see Notes
        // above). There does not seem to be a sure way to know when this is the
        // right moment to set the dimension. The easiest way is to force the
        // dimension to remain the same for a little while.
        def reset(v: Double, prop: ReadOnlyDoubleProperty, set: Double ⇒ Unit): Unit = {
          set(v)
          val cancellable = prop.listen { changed ⇒
            if (changed.doubleValue() != v) set(v)
          }
          RichFuture.timeout(500.millis).onComplete { _ ⇒
            cancellable.cancel()
          }(CoreSystem.system.dispatcher)
        }
        reset(loc.width, stage.widthProperty, stage.setWidth)
        reset(loc.height, stage.heightProperty, stage.setHeight)
      } else {
        stage.setWidth(loc.width)
        stage.setHeight(loc.height)
      }
    }
    stage.setMaximized(loc.maximized)
  }

  /**
   * Adds persistence to a Stage.
   *
   * @param stage the stage to add persistence to
   * @param view the persistence handler (usually the controller)
   * @param persist whether to persist
   * @param restore whether to restore
   */
  def addPersistence(stage: Stage, view: StagePersistentView, persist: Boolean = true, restore: Boolean = true): Unit = {
    StagePersistentView.hookup(stage.onCloseRequestProperty, stage.showingProperty, view, persist, restore)
  }

  /**
   * Stage location preference builder.
   *
   * Saves x/y/width/height/maximized information in a string using
   * "x=?;y=?;w=?;h=?;m=?" format.
   */
  implicit val locationBuilder: PreferenceBuilder[StageLocation] = {
    import Preference._
    Preference.typeBuilder[StageLocation, String](
      { l => Option(l).map(fromLocation).orNull },
      { s => Option(s).map(toLocation).orNull }
    )
  }

  implicit val locationHandler: ConfigEntry.Handler[StageLocation] = new ConfigEntry.Handler[StageLocation] with BaseConfigImplicits {
    override def get(config: Config, path: String): StageLocation = toLocation(configGetString(config, path))
    override def getList(config: Config, path: String): List[StageLocation] = configGetStringList(config, path).map(toLocation)
    override def toInner(v: StageLocation): String = fromLocation(v)
  }

  private def fromLocation(loc: StageLocation): String =
    s"x=${loc.x};y=${loc.y};w=${loc.width};h=${loc.height};m=${loc.maximized}"

  private def toLocation(str: String): StageLocation = {
    val params = str.split(';').flatMap { param =>
      param.split('=').toList match {
        case key :: value :: Nil => Some(key -> value)
        case _                   => None
      }
    }.toMap

    def getDoubleParam(key: String): Double =
      try { params.get(key).map(_.toDouble).getOrElse(0.0) }
      catch { case _: Exception => 0.0 }

    def getBooleanParam(key: String): Boolean =
      try { params.get(key).exists(_.toBoolean) }
      catch { case _: Exception => false }

    val x = getDoubleParam("x")
    val y = getDoubleParam("y")
    val width = getDoubleParam("w")
    val height = getDoubleParam("h")
    val maximized = getBooleanParam("m")

    StageLocation(x, y, width, height, maximized)
  }

}
