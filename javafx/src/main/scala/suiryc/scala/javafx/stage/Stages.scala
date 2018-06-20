package suiryc.scala.javafx.stage

import com.typesafe.config.Config
import javafx.geometry.BoundingBox
import javafx.scene.control.Dialog
import javafx.stage.Stage
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.settings.{BaseConfigImplicits, ConfigEntry, Preference, PreferenceBuilder}
import suiryc.scala.sys.OS

/** JavaFX Stage helpers. */
object Stages {

  /**
   * Tracks minimum stage dimensions.
   *
   * Sets stage minimum width and height according to its content.
   * If given, also sets stage size once done.
   *
   * @param stage stage to set minimum dimensions on
   * @param size initial stage size to set, or None
   */
  // scalastyle:off method.length
  def trackMinimumDimensions(stage: Stage, size: Option[(Double, Double)] = None): Unit = {
    // Notes:
    // The main problem is to determine the window decoration size: difference
    // between the stage and scene sizes.
    //
    // On Windows, letting JavaFX handle changes (by running the code with
    // Platform.runLater) before getting minimal scene root size and setting
    // stage minimal size (taking into account decoration size) works.
    //
    // But it does often not work on Linux (Ubuntu at least). Depending on the
    // situation (primary stage, modal window, stage scene root replacing, ...)
    // stage/scene dimensions may be unknown (NaN) and being changed more than
    // once while the stage is being built/displayed (0.0, 1.0, or the
    // decoration size).
    // Simply waiting on some properties like bounds does not always work:
    // sometimes it stays NaN until user interacts with the window.
    // Waiting on the scene/stage dimensions is not good either: sometimes it
    // changes in multiple steps (different values, only width or height, etc),
    // and sometimes the stage size does not include the window decoration
    // until user interacts with it ...
    // There does not seem to be a viable way to do it in Linux, so don't.

    if (!OS.isLinux) {
      // The actual tracking code
      def track(): Unit = {
        import suiryc.scala.javafx.concurrent.JFXSystem

        // To get the stage minimum dimensions we need to:
        // 1. Let JavaFX handle the changes: do our stuff with Platform.runLater
        JFXSystem.runLater {
          val scene = stage.getScene
          // 2. Get the decoration size: difference between the stage and the scene
          val decorationWidth = stage.getWidth - scene.getWidth
          val decorationHeight = stage.getHeight - scene.getHeight
          // 3. Ask JavaFX for the scene content minimum width and height
          // Note: '-1' is a special value to retrieve the current value
          val minWidth = scene.getRoot.minWidth(-1) + decorationWidth
          val minHeight = scene.getRoot.minHeight(-1) + decorationHeight

          // Now we can set the stage minimum dimensions.
          stage.setMinWidth(minWidth)
          stage.setMinHeight(minHeight)
          size match {
            case Some((width, height)) =>
              if (width > minWidth) stage.setWidth(width)
              if (height > minHeight) stage.setHeight(height)

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
  // scalastyle:on method.length

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
      stage.setWidth(loc.width)
      stage.setHeight(loc.height)
    }
    stage.setMaximized(loc.maximized)
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
