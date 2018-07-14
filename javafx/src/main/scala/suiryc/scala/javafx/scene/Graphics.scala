package suiryc.scala.javafx.scene

import javafx.geometry._
import javafx.scene.image.{Image, WritableImage}
import javafx.scene._
import javafx.scene.layout._
import javafx.scene.paint.{Color, Paint}
import javafx.scene.shape.SVGPath
import javafx.scene.transform.{Scale, Translate}
import scala.collection.JavaConverters._

/** Graphics helpers. */
object Graphics {

  /** Standard icon size. */
  val iconSize = 16.0

  /**
   * Builds a SVG path.
   *
   * @param content the SVG path itself
   * @param styleClass optional style classes to add
   * @param fill optional filling (can still be overridden by CSS)
   * @param style optional style
   */
  def svgPath(content: String, styleClass: List[String] = Nil, fill: Option[Paint] = None, style: Option[String] = None): SVGPath = {
    val path = new SVGPath
    path.getStyleClass.add("svg-path")
    styleClass.foreach(path.getStyleClass.add)
    fill.foreach(path.setFill)
    style.foreach(path.setStyle)
    path.setContent(content)
    path
  }

  /**
   * SVG group parameters.
   *
   * The most useful combinations are:
   *   - SVG size set: keeps space around shape
   *   - SVG size unset and cropping enabled: removes space around shape
   * Ratio can be set to apply scaling relatively to initial size.
   * Then parent target size can be set (higher than scaled SVG size) along with
   * horizontal and vertical positions (where to put shape in pane).
   */
  case class SVGGroupParams(
    // Expected view top/left position
    viewTop: Double = 0,
    viewLeft: Double = 0,
    // Initial SVG size (for both width and height, or separately)
    svgSize: Double = -1,
    svgWidth: Double = -1,
    svgHeight: Double = -1,
    // Whether to crop SVG shape (drop space around it)
    crop: Boolean = false,
    // Whether to keep ratio between width and height when scaling
    keepRatio: Boolean = true,
    // Target SVG size (both width and height, or separately)
    targetSvgSize: Double = -1,
    targetSvgWidth: Double = -1,
    targetSvgHeight: Double = -1,
    // Ratio (scaling) between target SVG size and initial SVG size (for both
    // width and height, or separately).
    // Target SVG size takes precedence when both are set.
    ratio: Double = -1,
    widthRatio: Double = -1,
    heightRatio: Double = -1,
    // Target pane size (both width and height, or separately)
    targetSize: Double = -1,
    targetWidth: Double = -1,
    targetHeight: Double = -1,
    // When target pane size is bigger than scaled SVG size, where to put the
    // shape
    hpos: HPos = HPos.CENTER,
    vpos: VPos = VPos.CENTER,
    // Pane additional style classes
    styleClass: List[String] = Nil,
    // Pane background fill
    backgroundFill: BackgroundFill = new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY),
    // Pane style
    style: Option[String] = None,
    // SVG group style
    groupStyle: Option[String] = None
  )

  /**
   * SVG 'group'.
   *
   * Creates a Group containing SVG paths (and with Transforms applied), placed
   * inside a FlowPane.
   * "svg-pane" style is added to the Pane.
   * "svg-group" style is added to the Group.
   * "svg-path" style is added to each SVG path.
   */
  case class SVGGroup(params: SVGGroupParams, path: SVGPath*) {

    val group = new Group
    group.getStyleClass.add("svg-group")
    params.groupStyle.foreach(group.setStyle)
    group.getChildren.addAll(path:_*)
    private val groupBounds = group.getBoundsInLocal

    private def paramOrElse(v: Double, fb: Option[Double]) = if (v > 0) Some(v) else fb
    private def paramOrNone(v: Double) = paramOrElse(v, None)
    private def max(v1: Double, v2: Option[Double]*): Double = {
      val flat = v2.flatten
      if (flat.isEmpty) v1
      else math.max(v1, flat.max)
    }

    // Notes:
    // We want to constrain one or more SVGPath to a given width and height.
    // Setting scaleX/scaleY directly applies scale from the center of the shape.
    // Adding a Scale transform allows to specify the pivot point.
    // Not setting the parent (pane) width/height usually results in it using
    // the actual shape size (unscaled).
    // If we scale the group, the pivot to use must be the top and left borders
    // of the shape (and not 0), otherwise we can get unwanted offsets.
    // If scale is applied on parent pane, the default (0,0) pivot is fine, but
    // the resulting pane space (in parent) is not scaled.
    //
    // Transforms are not only applied on the node one after another, they also
    // applies to the next transformation(s).
    // e.g. applying scale (x=0.5,y=1) then translate (x=100,y=0) results in
    // actual translation being (x=50,y=0).
    // or applying rotate(90) then translate(x=100,y=0) results in actual
    // translation being (x=0,y=100).
    //
    // When placed in parent, SVG top/left bounding box is aligned on parent.
    // So the original SVG top/left position (local bounding box) must be
    // applied as translation.
    //
    // Thus we:
    // 1. apply scaling, with pivot on group top/left bounding box (which is
    //    the same as using a pivot of 0 when placed in a parent pane).
    // 2. apply translation, keeping into account group top/left bounding box
    //    position and any needed offset; scaling is applied on it, so values
    //    must be unscaled (group position already is, offset has to be also).

    // SVG group top/left position
    private val svgTop = groupBounds.getMinY
    private val svgLeft = groupBounds.getMinX
    // View top/left position. Can be given, in which case we make sure it is
    // consistent with the group top/left position.
    private val viewTop = math.min(params.viewTop, svgTop)
    private val viewLeft = math.min(params.viewLeft, svgLeft)
    // Padding between view and group top/left
    private val svgPaddingTop = svgTop - viewTop
    private val svgPaddingLeft = svgLeft - viewLeft
    // SVG top/left offset (padding, or none if cropped)
    private val svgOffsetTop = if (params.crop) 0 else svgPaddingTop
    private val svgOffsetLeft = if (params.crop) 0 else svgPaddingLeft
    // SVG group size (including offset)
    private val groupWidth = groupBounds.getWidth + svgOffsetLeft
    private val groupHeight = groupBounds.getHeight + svgOffsetTop
    // SVG size. Can be given, in which case we make sure it is consistent with
    // the SVG group size.
    private val svgWidth = math.max(math.max(groupWidth, params.svgWidth), params.svgSize)
    private val svgHeight = math.max(math.max(groupHeight, params.svgHeight), params.svgSize)
    // Target SVG size.
    private val targetSvgSize = paramOrNone(params.targetSvgSize)
    private val targetSvgWidth = paramOrElse(params.targetSvgWidth, targetSvgSize)
    private val targetSvgHeight = paramOrElse(params.targetSvgHeight, targetSvgSize)
    // Target pane size
    private val targetSize = paramOrNone(params.targetSize)
    private val targetWidth = paramOrElse(params.targetWidth, targetSize)
    private val targetHeight = paramOrElse(params.targetHeight, targetSize)
    // Ratio between target SVG size and initial SVG size (none if not applicable)
    private val widthRatio = paramOrNone(params.widthRatio).orElse(if (params.crop && (svgWidth > 0)) targetWidth.map(_ / svgWidth) else None)
    private val heightRatio = paramOrNone(params.heightRatio).orElse(if (params.crop && (svgHeight > 0)) targetHeight.map(_ / svgHeight) else None)
    // Overall ratio (for both width and height) if applicable.
    // If either width or height ratio is set, and we want to keep the initial
    // ratio between width and height, then use the minimum one (to stay in
    // target size).
    private val ratio =
      if (params.ratio > 0) Some(params.ratio)
      else if (!params.keepRatio || widthRatio.orElse(heightRatio).isEmpty) None
      else Some(math.min(widthRatio.orElse(heightRatio).get, heightRatio.orElse(widthRatio).get))
    // Scale to apply for target SVG size: based on target SVG size or ratio
    private val scaleSvgWidth = targetSvgWidth.map(_ / svgWidth)
    private val scaleSvgHeight = targetSvgHeight.map(_ / svgHeight)
    private val scale = if (params.keepRatio) {
      val scales = scaleSvgWidth.toList ::: scaleSvgHeight.toList
      if (scales.nonEmpty) Some(scales.min)
      else None
    } else None
    private val scaleX = scale.orElse(targetSvgWidth.map(_ / svgWidth)).orElse(ratio).orElse(widthRatio)
    private val scaleY = scale.orElse(targetSvgHeight.map(_ / svgHeight)).orElse(ratio).orElse(heightRatio)
    // The actual scaled SVG size
    private val scaledWidth = svgWidth * scaleX.getOrElse(1.0)
    private val scaledHeight = svgHeight * scaleY.getOrElse(1.0)
    // The pane size. Can be given, in which case we make sure it is consistent
    // with the scaled SVG size.
    private val paneWidth = max(scaledWidth, targetWidth)
    private val paneHeight = max(scaledHeight, targetHeight)
    // Horizontal offset to apply if pane size is bigger than SVG scaled size.
    private val offsetLeft = if (paneWidth > scaledWidth) {
      // The offset depends on the target position inside the parent pane:
      // 1. left: no offset
      // 2. center: half the space between the parent pane and the SVG group
      // 3. right: the space between the parent pane and the SVG group
      // Pane size being the target (scaled) value, we need to get the initial
      // (unscaled) value when comparing it with the (also initial) SVG size.
      val unscaledPadding = paneWidth / scaleX.getOrElse(1.0) - svgWidth
      params.hpos match {
        case HPos.LEFT   => 0.0
        case HPos.CENTER => unscaledPadding / 2
        case HPos.RIGHT  => unscaledPadding
      }
    } else {
      0.0
    }
    // The actual translation also needs to apply the SVG offset (space between
    // the SVG group and the view top/left).
    private val translateX = svgOffsetLeft + offsetLeft
    // Vertical offset: similar to what is done for horizontal offset.
    private val offsetTop = if (paneHeight > scaledHeight) {
      val unscaledPadding = paneHeight / scaleY.getOrElse(1.0) - svgHeight
      params.vpos match {
        case VPos.TOP      => 0.0
        case VPos.CENTER   => unscaledPadding / 2
        case VPos.BOTTOM   => unscaledPadding
        case VPos.BASELINE => unscaledPadding
      }
    } else {
      0.0
    }
    private val translateY = svgOffsetTop + offsetTop

    // Scaling if any.
    private val scaleOpt = if (scaleX.isDefined || scaleY.isDefined) {
      Some(new Scale(scaleX.getOrElse(1), scaleY.getOrElse(1), svgLeft, svgTop))
    } else {
      None
    }

    // Translation if any.
    // Note: adding a Translate to the pane transforms may induce off-by-one
    // sizes, and also moves background.
    private val translateOpt = if ((translateY > 0) || (translateX > 0)) {
      Some(new Translate(translateX, translateY))
    } else {
      None
    }

    // Scaling applied before translation (taken into account in computations).
    private val transforms = scaleOpt.toList ::: translateOpt.toList
    group.getTransforms.setAll(transforms:_*)

    // The target bounds.
    val bounds = new BoundingBox(0.0, 0.0, paneWidth, paneHeight)

    // Note: use a FlowPane to easily position group on top/left.
    // (e.g. StackPane centers it)
    lazy val pane: Pane = {
      val pane = new FlowPane
      pane.getStyleClass.add("svg-pane")
      params.styleClass.foreach(pane.getStyleClass.add)
      params.style.foreach(pane.setStyle)
      if (paneWidth > 0) {
        pane.setMinWidth(paneWidth)
        pane.setPrefWidth(paneWidth)
        pane.setMaxWidth(paneWidth)
      }
      if (paneHeight > 0) {
        pane.setMinHeight(paneHeight)
        pane.setPrefHeight(paneHeight)
        pane.setMaxHeight(paneHeight)
      }

      pane.setBackground(new Background(params.backgroundFill))
      pane.getChildren.add(group)
      pane
    }

    def copy(params: SVGGroupParams = params): SVGGroup = {
      val c = path.map { path ⇒
        svgPath(
          content = path.getContent,
          styleClass = path.getStyleClass.asScala.toList.filterNot(_ == "svg-path"),
          fill = Option(path.getFill),
          style = Option(path.getStyle)
        )
      }
      SVGGroup(params, c:_*)
    }

  }

  /** Builds an Image from a Node. */
  def buildImage(node: Parent, fill: Option[Paint] = None): Image = {
    // For proper painting, the node needs to be inside a Scene; otherwise the
    // image appears to be drawn in the right/bottom corner.
    // We assume it is inside a scene if the node has a parent already.
    // If necessary, create a temporary scene to set the node inside. But since
    // setting its parent here may interfere later, we need to reset it before
    // leaving. Since we cannot set a null root for the scene, use an intermediary
    // pane.
    val parentOpt = if (node.getParent == null) {
      val parent = new FlowPane(node)
      new Scene(parent)
      Some(parent)
    } else {
      None
    }
    val snapshotParams = new SnapshotParameters()
    snapshotParams.setFill(fill.getOrElse(Color.TRANSPARENT))
    // The default 'snapshot' pixel format may not be suitable (e.g. as stage
    // icon). Creating a new WritableImage from the snapshot fixes this.
    // scalastyle:off null
    val image0 = node.snapshot(snapshotParams, null)
    // scalastyle:on null
    val img = new WritableImage(image0.getPixelReader, math.round(image0.getWidth).toInt, math.round(image0.getHeight).toInt)
    // Reset the node parent if necessary.
    parentOpt.foreach { parent ⇒
      parent.getChildren.clear()
    }
    img
  }

  /** Simple function to build an SVGGroup */
  protected type BuilderFunc = (List[String], Double) ⇒ SVGGroup

  /**
   * Simple SVGGroup builder with default parameters.
   * See: https://stackoverflow.com/a/25235029
   */
  trait Builder extends BuilderFunc {
    protected val defaultStyleClass: String
    protected val defaultTargetSvgSize: Double
    def apply(styleClass: List[String] = Nil, targetSvgSize: Double = defaultTargetSvgSize): SVGGroup
  }

  /**
   * Helper for icon builders.
   * Gives builders with standard icon size as default.
   */
  trait IconBuilders {

    def iconBuilder(styleClass: String, targetSvgSize: Double = iconSize)(f: BuilderFunc): Builder = {
      new Builder {
        override protected val defaultStyleClass: String = styleClass
        override protected val defaultTargetSvgSize: Double = targetSvgSize
        override def apply(styleClass: List[String], targetSvgSize: Double): SVGGroup = f(defaultStyleClass :: styleClass, targetSvgSize)
      }
    }

  }

}
