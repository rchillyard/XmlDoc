package com.phasmidsoftware.xmldoc.kml

import com.phasmidsoftware.xmldoc.core.*
import com.phasmidsoftware.xmldoc.kml.HasFeatures.editHasFeaturesToOption
import com.phasmidsoftware.xmldoc.kml.KmlEdit.{JOIN, JOINX}
import com.phasmidsoftware.xmldoc.core.Mergeable.{mergeOptions, mergeOptionsBiased, mergeSequence, mergeStringsDelimited}
import com.phasmidsoftware.xmldoc.render.*
import com.phasmidsoftware.xmldoc.render.Renderers.{booleanRenderer, doubleRenderer, enumAttributeRenderer, enumObjectRenderer, intRenderer}
import com.phasmidsoftware.xmldoc.xml.*
import com.phasmidsoftware.xmldoc.xml.MultiExtractorBase.{NonNegative, Positive}

import scala.util.*

/**
 * Trait KmlObject: abstract super-element of all KML elements. Known in the reference document as Object.
 * See [[https://developers.google.com/kml/documentation/kmlreference KML Reference]]
 *
 * NOTE: abstract elements cannot be represented in Kml documents directly but are always super-elements (and therefore ordinary classes).
 * Their usage is in the properties of other elements where more than one possible element type is allowed.
 *
 * NOTE: there's some argument for having KmlObject extend scala.xml.Node but it's not compelling.
 *
 * Actual Kml elements (the leaf classes) are all represented here as case classes, which cannot be extended.
 *
 * The arrangement of the traits and classes within this module follows the KML Reference starting with Object and then doing breadth-first top to bottom.
 */
trait KmlObject

/**
 * Properties of KMLObject
 *
 * @param __id an optional identifier.
 */
case class KmlData(__id: Option[String]) extends Mergeable[KmlData] {

  /**
   * Method to merge KmlData objects.
   *
   * @param k a KmlData object.
   * @return the merged value of KmlData.
   */
  infix def merge(k: KmlData, mergeName: Boolean = true): Option[KmlData] = Some(KmlData(mergeStringsDelimited(__id, k.__id)("#")))
}

/**
 * Companion object for the `KmlData` case class.
 *
 * This object provides utility methods and implicit definitions to facilitate the use of
 * `KmlData`, including extractors and renderers that integrate with the `Extractors`
 * and `Renderers` frameworks.
 *
 * Defines an implicit `Extractor` for deserializing `KmlData` instances, and an implicit
 * `Renderer` for serializing `KmlData` instances.
 *
 * @see `KmlData` case class for additional details on properties and functionality.
 */
object KmlData extends Extractors with Renderers {
  def nemo: KmlData = KmlData(None)

  import Extractors.*
  import Renderers.*

  implicit val extractor: Extractor[KmlData] = extractor10(apply) ^^ "extractorKmlData"
  implicit val renderer: Renderer[KmlData] = renderer1(apply) ^^ "rendererKmlData"
}

// ================ The following are enumerated types
// ================ See https://developers.google.com/kml/documentation/kmlreference#kml-fields

/**
 * Enumeration representing various methods how the `altitude` is interpreted
 *
 * The available altitudeMode in this enumeration are:
 * - clampToGround: (default) Represents to ignore the altitude specification
 * and drape the overlay over the terrain.
 * - relativeToGround: Represents the altitude of the element relative to the actual ground elevation of a particular location.
 * - absolute: Represents the altitude of the coordinate relative to sea level
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of AltitudeModeEnum.
 * - renderer: Provides a Renderer instance for rendering values of AltitudeModeEnum.
 *
 */
object AltitudeModeEnum extends Enumeration with Extractors with Renderers {
  val clampToGround, relativeToGround, absolute = Value
  implicit val extractor: Extractor[AltitudeModeEnum.Value] = extractorEnum[Value, this.type](this)(identity)
  implicit val renderer: Renderer[AltitudeModeEnum.Value] = enumObjectRenderer
}

/**
 * Enumeration representing Values for ColorMode are normal (no effect) and random
 *
 * The available ColorMode in this enumeration are:
 * - normal: no effect, base condition
 * - random: applies a random linear scale to the base `color` as follows.
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of ColorModeEnum.
 * - renderer: Provides a Renderer instance for rendering values of ColorModeEnum.
 */
object ColorModeEnum extends Enumeration with Extractors with Renderers {
  val normal, random = Value
  implicit val extractor: Extractor[ColorModeEnum.Value] = extractorEnum[Value, this.type](this)(identity)
  implicit val renderer: Renderer[ColorModeEnum.Value] = enumObjectRenderer
}

/**
 * Enumeration representing Values for DisplayMode are default and hide.
 *
 * The available DisplayMode in this enumeration are:
 * - default: Google Earth uses the information supplied in `text` to create a balloon
 * - hide: Google Earth does not display the balloon.
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of DisplayModeEnum.
 * - renderer: Provides a Renderer instance for rendering values of DisplayModeEnum.
 */
object DisplayModeEnum extends Enumeration with Extractors with Renderers {
  val default, hide = Value
  implicit val extractor: Extractor[DisplayModeEnum.Value] = extractorEnum[Value, this.type](this)(identity)
  implicit val renderer: Renderer[DisplayModeEnum.Value] = enumObjectRenderer
}

/**
 * Enumeration representing Values for ItemIconModeEnum are open, closed, error, fetching0, fetching1 or fetching2.
 *
 * The available ItemIconModeEnum in this enumeration are:
 * - open
 * - closed
 * - error
 * - fetching0
 * - fetching1
 * - fetching2
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of ItemIconModeEnum.
 * - renderer: Provides a Renderer instance for rendering values of ItemIconModeEnum.
 */
object ItemIconModeEnum extends Enumeration with Extractors with Renderers {
  val open, closed, error, fetching0, fetching1, fetching2 = Value
  implicit val extractor: Extractor[ItemIconModeEnum.Value] = extractorEnum[Value, this.type](this)(identity)
  implicit val renderer: Renderer[ItemIconModeEnum.Value] = enumObjectRenderer
}


/**
 * Enumeration representing different types of list item behaviors or attributes.
 *
 * The `ListItemTypeEnum` object provides a set of predefined values:
 * - `check`: Represents a list item that can be checked.
 * - `checkOffOnly`: Represents a list item that can only be unchecked.
 * - `checkHideChildren`: Represents a list item that hides its children when checked.
 * - `radioFolder`: Represents a list item with behavior similar to a radio button within a folder.
 *
 * This object extends `Enumeration` to define the set of values, and integrates
 * with the `Extractors` and `Renderers` traits to provide functionality for
 * extracting and rendering enumeration values.
 *
 * Contains implicit components:
 * - An extractor for converting to and from `ListItemTypeEnum.Value`.
 * - A renderer for rendering `ListItemTypeEnum.Value`.
 */
object ListItemTypeEnum extends Enumeration with Extractors with Renderers {
  val check, checkOffOnly, checkHideChildren, radioFolder = Value
  implicit val extractor: Extractor[ListItemTypeEnum.Value] = extractorEnum[Value, this.type](this)(identity)
  implicit val renderer: Renderer[ListItemTypeEnum.Value] = enumObjectRenderer
}

/**
 * An enumeration object representing a collection of shapes.
 * `ShapeEnum` supports extraction and rendering functionalities.
 *
 * ShapeEnum include:
 * - rectangle
 * - cylinder
 * - sphere
 *
 * This object provides implicit values for extraction and rendering:
 * - `extractor`: Extracts instances of `ShapeEnum.Value` from a defined context.
 * - `renderer`: Renders `ShapeEnum.Value` instances as their string representations.
 *
 * The `ShapeEnum.Value` type is an alias for the `Value` type in this enumeration.
 */
object ShapeEnum extends Enumeration with Extractors with Renderers {
  val rectangle, cylinder, sphere = Value
  implicit val extractor: Extractor[ShapeEnum.Value] = extractorEnum[Value, this.type](this)(s => s.toLowerCase)
  implicit val renderer: Renderer[ShapeEnum.Value] = enumObjectRenderer
}

/**
 * Enumeration representing a time-based refresh mode.
 *
 * The available refreshMode in this enumeration are:
 * - onChange: refresh when the file is loaded
 *             and whenever the Link parameters change (the default).
 * - onInterval: refresh every n seconds (specified in `refreshInterval`)
 * - onExpire:  refresh the file when the expiration time is reached.
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of RefreshModeEnum.
 * - renderer: Provides a Renderer instance for rendering values of RefreshModeEnum.
 */
object RefreshModeEnum extends Enumeration with Extractors with Renderers {
  val onChange, onInterval,onExpire = Value
  implicit val extractor: Extractor[RefreshModeEnum.Value] = extractorEnum[Value,this.type](this)(identity)
  implicit val renderer: Renderer[RefreshModeEnum.Value] = enumObjectRenderer
}

/**
 * Enumeration representing two different Styles of `StyleMap`.
 *
 * The available StyleState in this enumeration are:
 * - normal: a normal styles version for a placemark
 * - highlight: the highlighted version appears when the user mouses over the icon in Google Earth.
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of StyleStateEnum.
 * - renderer: Provides a Renderer instance for rendering values of StyleStateEnum.
 */
object StyleStateEnum extends Enumeration with Extractors with Renderers {
  val normal, highlight = Value
  implicit val extractor: Extractor[StyleStateEnum.Value] = extractorEnum[Value, this.type](this)(identity)
  implicit val renderer: Renderer[StyleStateEnum.Value] = enumObjectRenderer
}

/**
 * Enumeration representing various units that can be used.
 *
 * The available units in this enumeration are:
 * - fraction: Represents fractional units
 * - pixels: Represents pixel-based units
 * - insetPixels: Represents pixel units used for insets
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of UnitsEnum.
 * - renderer: Provides a Renderer instance for rendering values of UnitsEnum.
 */
object UnitsEnum extends Enumeration with Extractors with Renderers {
  val fraction, pixels, insetPixels = Value
  implicit val extractor: Extractor[UnitsEnum.Value] = extractorEnum[Value, this.type](this)(identity)
  implicit val renderer: Renderer[UnitsEnum.Value] = enumAttributeRenderer
}

/**
 * Enumeration representing Specifies how the link is refreshed when the "camera" changes.
 *
 * The available ViewRefreshEnum in this enumeration are:
 * - never: (default) Ignore changes in the view.
 * - onRequest: Refresh the file only when the user explicitly requests it.
 * - onStop: Refresh the file n seconds after movement stops.
 * - onRegion: Refresh the file when the Region becomes active.
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of ViewRefreshEnum.
 * - renderer: Provides a Renderer instance for rendering values of ViewRefreshEnum.
 */
object ViewRefreshEnum extends Enumeration with Extractors with Renderers {
  val never, onRequest, onStop, onRegion = Value
  implicit val extractor: Extractor[ViewRefreshEnum.Value] = extractorEnum[Value,this.type](this)(identity)
  implicit val renderer: Renderer[ViewRefreshEnum.Value] = enumObjectRenderer
}

// ================ From here on, classes (objects) are grouped alphabetically

/**
 * Trait AbstractView: abstract subelement of KmlObject.
 * AbstractView is the super-type of Camera and LookAt.
 * See [[https://developers.google.com/kml/documentation/kmlreference#abstractview AbstractView]]
 */
trait AbstractView extends KmlObject

/**
 * AbstractView serves as an object companion to the AbstractView trait,
 * which represents an abstract subelement of KmlObject.
 * It is the base type for more specific views such as Camera and LookAt.
 *
 * This object includes implicit utilities for extracting and rendering
 * AbstractView instances and sequences of them:
 *
 * - `extractorSeq`: MultiExtractor for extracting sequences of AbstractView elements.
 * - `renderer`: Renderer for rendering individual AbstractView instances.
 * - `rendererSeq`: Renderer for rendering sequences of AbstractView instances.
 *
 * The extraction and rendering follows the defined relationships and properties
 * described for AbstractView and its subtypes.
 */
object AbstractView extends Extractors with Renderers {
  implicit val extractorSeq: MultiExtractor[Seq[AbstractView]] =
    multiExtractor2[AbstractView, (Camera, LookAt), Camera, LookAt]((l, p) => (l, p), Seq("Camera", "LookAt")) ^^ "multiExtractorAbstractView"
  implicit val renderer: Renderer[AbstractView] = rendererSuper2[AbstractView, Camera, LookAt] ^^ "rendererAbstractView"
  implicit val rendererSeq: Renderer[Seq[AbstractView]] = sequenceRenderer[AbstractView] ^^ "rendererAbstractViews"
}

/**
 * Properties of AbstractView.
 * There are no properties specific to AbstractView.
 * See [[https://developers.google.com/kml/documentation/kmlreference#abstractview AbstractView]]
 *
 * @param kmlData source of properties.
 */
case class AbstractViewData(kmlData: KmlData)

/**
 * Companion object for the AbstractViewData case class.
 * Provides extractors and renderers
 * for handling AbstractViewData objects.
 *
 * NOTE: not currently used.
 *
 * Extends the Extractors and Renderers traits to facilitate extraction and rendering of
 * AbstractViewData instances.
 * Contains implicit values for extracting and rendering
 * AbstractViewData, as well as a partial extractor for more specific use cases.
 */
object AbstractViewData extends Extractors with Renderers {
  private val applyFunction: KmlData => AbstractViewData = new AbstractViewData(_)
  implicit val extractor: Extractor[AbstractViewData] =
    extractorPartial[KmlData, AbstractViewData](extractorPartial0(applyFunction))
  implicit val renderer: Renderer[AbstractViewData] =
    renderer0Super(apply)(_.kmlData) ^^ "rendererAbstractViewData"
}

/**
 * Case class representing an altitude value.
 * See [[https://developers.google.com/kml/documentation/kmlreference#altitude Altitude]]
 *
 * @param $ the altitude value, typically measured in meters.
 */
case class Altitude($: Double)

/**
 * Object representing utilities for working with the Altitude case class.
 * See [[https://developers.google.com/kml/documentation/kmlreference#altitude Altitude]]
 *
 * Provides implementations for `Extractor` and `Renderer` type classes,
 * enabling extraction and rendering functionality for `Altitude` and `Option[Altitude]`.
 */
object Altitude extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Altitude] = extractor10(apply) ^^ "extractorAltitude"
  implicit val extractorOpt: Extractor[Option[Altitude]] = extractor.lift ^^ "extractorOptAltitude"
  implicit val renderer: Renderer[Altitude] = renderer1(apply) ^^ "rendererAltitude"
  implicit val rendererOpt: Renderer[Option[Altitude]] = renderer.lift ^^ "rendererOptAltitude"
}

/**
 * Represents the mode by which altitude is specified and interpreted.
 * See [[https://developers.google.com/kml/documentation/kmlreference#camera Camera]]
 *
 * This case class wraps a value from the AltitudeModeEnum enumeration, which specifies
 * how the altitude should behave relative to the terrain or sea level.
 *
 * The possible altitude modes are defined in the AltitudeModeEnum object and include:
 *  - clampToGround: Ignores the altitude value and drapes the element on the ground.
 *  - relativeToGround: Specifies the altitude as relative to the ground elevation.
 *  - absolute: Specifies the altitude relative to sea level.
 *
 * The AltitudeMode class serves as a wrapper, enabling seamless integration with extractors
 * and renderers for handling data transformations and operations within systems that support
 * AltitudeModeEnum values.
 */
case class AltitudeMode($: AltitudeModeEnum.Value)

/**
 * Companion object for the AltitudeMode case class, providing extractors and renderers.
 *
 * This object is used for handling extraction and rendering operations related to AltitudeMode instances.
 *
 * It defines implicit values for extractors and renderers, enabling seamless interaction
 * with AltitudeMode objects in the context of data transformation and rendering pipelines.
 */
object AltitudeMode extends Extractors with Renderers {

  private val extractorAltitudeMode: Extractor[AltitudeMode] = extractor10(apply) ^^ "extractorAltitudeMode"
  implicit val extractorOpt: Extractor[Option[AltitudeMode]] = extractorAltitudeMode.lift ^^ "extractorOptAltitudeMode"
  implicit val rendererOpt: Renderer[Option[AltitudeMode]] = renderer1(apply).lift ^^ "rendererOptAltitudeMode"
}

/**
 * Case class BalloonStyle which extends ColorStyle.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#balloonstyle Balloon Style]]
 *
 * NOTE Use of the color element has been deprecated (use bgColor instead.)
 * NOTE According to the current KML reference, this object extends SubStyle or ColorStyle (it's not clear which).
 *
 * @param text             the balloon text.
 * @param maybeBgColor     optional background color (maybe it isn't optional if there's no color element).
 * @param maybeTextColor   optional text color.
 * @param maybeDisplayMode optional display mode.
 * @param colorStyleData   the (auxiliary) color style properties.
 */
case class BalloonStyle(text: Text, maybeBgColor: Option[BgColor], maybeTextColor: Option[TextColor], maybeDisplayMode: Option[DisplayMode])(val colorStyleData: ColorStyleData) extends ColorStyle

/**
 * Object BalloonStyle provides extractors and renderers for the `BalloonStyle` case class.
 *
 * This object includes functionalities for extracting and rendering `BalloonStyle`, allowing conversions and
 * serializations between the case class and its representations in other formats.
 * It leverages the `Extractors`
 * and `Renderers` traits, and provides several implicit instances for convenience.
 *
 * It defines:
 *
 * - `extractorPartial`: A partial extractor for constructing `BalloonStyle` from `ColorStyleData`.
 * - `extractor`: An implicit extractor for `BalloonStyle` objects.
 * - `renderer`: An implicit renderer for `BalloonStyle` objects.
 * - `rendererOpt`: An implicit renderer for optional `BalloonStyle` instances (`Option[BalloonStyle]`).
 */
object BalloonStyle extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[BalloonStyle] = extractorPartial[ColorStyleData, BalloonStyle](extractorPartial40(apply)) ^^ "extractorBalloonStyle"
  implicit val renderer: Renderer[BalloonStyle] = renderer4Super(apply)(_.colorStyleData) ^^ "rendererBalloonStyle"
}

/**
 * This case class represents a background color element with a single parameter.
 * Used by `BalloonStyle` and `ListStyle`.
 * See [[https://developers.google.com/kml/documentation/kmlreference#bgcolor bgColor]]
 *
 * @param $ a hexadecimal string that defines the background color.
 */
case class BgColor($: Hex4)

/**
 * Companion object for the BgColor case class.
 * This object provides utilities
 * to extract and render BgColor instances.
 * It includes implicit definitions
 * for handling BgColor objects and optional BgColor instances using the Extractor
 * and Renderer type classes respectively.
 *
 * The Extractor instances are used to parse data into BgColor instances, while
 * the Renderer instances manage the conversion of BgColor objects back to their
 * desired output formats.
 *
 * Key functionalities:
 * - Provides an implicit extractor for BgColor to facilitate data extraction.
 * - Supplies an optional extractor to handle optional BgColor values.
 * - Defines a renderer for BgColor to support data transformation and output.
 * - Includes an optional renderer for handling optional BgColor values.
 */
object BgColor extends Extractors with Renderers {

  implicit val extractor: Extractor[BgColor] = extractor10(apply) ^^ "extractorBgColor"
  implicit val extractorOpt: Extractor[Option[BgColor]] = extractor.lift ^^ "extractorOptionBgColor"
  implicit val renderer: Renderer[BgColor] = renderer1(apply) ^^ "rendererBgColor"
  implicit val rendererOpt: Renderer[Option[BgColor]] = renderer.lift ^^ "rendererOptionBgColor"
}

/**
 * Case class Camera represents a viewpoint from which a KML feature is viewed.
 * See [[https://developers.google.com/kml/documentation/kmlreference#camera Camera]]
 *
 * The Camera defines a viewpoint using geographic coordinates and orientation information,
 * and it is a concrete implementation of the `AbstractView` trait.
 *
 * @param long              the longitude of the Camera's location.
 * @param lat               the latitude of the Camera's location.
 * @param alt               the altitude of the Camera's location.
 * @param heading           the direction that the Camera is facing, in degrees (0 = North).
 * @param tilt              the vertical angle between the Camera's direction and the surface, in degrees.
 * @param roll              the rotation about the Z-axis (normal to the screen), in degrees.
 * @param maybeAltitudeMode an optional altitude mode specifying how the altitude is interpreted.
 * @param abstractViewData  additional data associated with the Camera's AbstractView.
 */
case class Camera(long: Longitude, lat: Latitude, alt: Altitude, heading: Heading, tilt: Tilt, roll: Roll, maybeAltitudeMode: Option[AltitudeMode])(val abstractViewData: AbstractViewData) extends AbstractView

/**
 * Companion object for the Camera case class, providing Extractor and Renderer implementations.
 *
 * This object includes functionality to extract and render Camera instances using the defined
 * extractorPartial, extractor, renderer, and rendererOpt members.
 *
 * - `extractorPartial`: Represents a partial extractor for creating Camera instances based
 * on abstract view data.
 * - `extractor`: Implicitly provides the complete extractor for Camera instances.
 * - `renderer`: Implicitly provides the renderer for Camera instances, facilitating
 * conversion to a different representation.
 * - `rendererOpt`: Implicitly provides the renderer for optional Camera instances.
 */
object Camera extends Extractors with Renderers {

  implicit val extractor: Extractor[Camera] =
    extractorPartial[AbstractViewData, Camera](extractorPartial70(apply)) ^^ "extractorCamera"
  implicit val renderer: Renderer[Camera] =
    renderer7Super(apply)(_.abstractViewData) ^^ "rendererCamera"
}

/**
 * Case class to define Color.
 * An element of ColorStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#colorstyle ColorStyle]]
 *
 * @param $ the color as a hexadecimal string.
 */
case class Color($: Hex4)

/**
 * The `Color` object provides implicit extractors and renderers for the `Color` case class.
 * It enhances the functional processing of `Color` instances, enabling extraction and rendering capabilities.
 *
 * Implicit extractors provided:
 * - `extractor`: Extracts a `Color` instance.
 * - `extractorOpt`: Extracts an optional `Color` instance.
 *
 * Implicit renderers provided:
 * - `renderer`: Renders a `Color` instance.
 * - `rendererOpt`: Renders an optional `Color` instance.
 *
 * This object is designed for integration with systems requiring model extraction and rendering logic.
 */
object Color extends Extractors with Renderers {

  implicit val extractor: Extractor[Color] = extractor10(apply) ^^ "extractColor"
  implicit val optExtractor: Extractor[Option[Color]] = extractor.lift ^^ "extractOptionColor"
  implicit val renderer: Renderer[Color] = renderer1(apply) ^^ "rendererColor"
  implicit val optRenderer: Renderer[Option[Color]] = renderer.lift ^^ "rendererOptionColor"
}

/**
 * Case class to model ColorMode.
 * See [[https://developers.google.com/kml/documentation/kmlreference#colormode ColorMode]]
 *
 * `ColorMode` is defined using the `ColorModeEnum` enumeration, which specifies the
 * possible modes for handling colors. The two primary modes are:
 * - `normal`: Represents no effect or a base condition.
 * - `random`: Applies a random linear scale to the base color.
 */
case class ColorMode($: ColorModeEnum.Value)

/**
 * Object companion for the `ColorMode` case class that provides auxiliary utilities.
 *
 * This object includes implicit `Extractor` and `Renderer` instances for `ColorMode`.
 *
 * ColorMode represents the mode of color used, typically defined as either "normal" or "random."
 *
 * Key functionalities:
 * - Implicit `Extractor[ColorMode]`: Used to extract `ColorMode` values.
 * - Implicit `Extractor[Option[ColorMode]]`: Handles optional extraction for `ColorMode`.
 * - Implicit `Renderer[ColorMode]`: Converts `ColorMode` to a renderable form.
 * - Implicit `Renderer[Option[ColorMode]]`: Renders optional `ColorMode` values.
 */
object ColorMode extends Extractors with Renderers {

  private val extractorColorMode: Extractor[ColorMode] = extractor10(apply) ^^ "extractorColorMode"
  implicit val extractorOpt: Extractor[Option[ColorMode]] = extractorColorMode.lift ^^ "extractMaybeColorMode"
  implicit val rendererOpt: Renderer[Option[ColorMode]] = renderer1(apply).lift ^^ "rendererOptionColorMode"
}

/**
 * Class to define ColorStyle which extends SubStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#colorstyle ColorStyle]]
 *
 * TODO add ColorStyleData
 *
 */
trait ColorStyle extends SubStyle

/**
 * Object that extends `Extractors` and `Renderers` to provide implicit extraction and rendering capabilities for `ColorStyle`.
 *
 * This object facilitates working with collections of `ColorStyle` objects and specific rendering configurations.
 *
 * === Features ===
 * - Provides an implicit `MultiExtractor` for extracting sequences of `ColorStyle` objects from their component styles.
 * - Includes rendering capabilities for individual `ColorStyle` objects and sequences of `ColorStyle` objects.
 * - Constructs extractors and renderers for all supported `ColorStyle` components including `BalloonStyle`, `ListStyle`, `PolyStyle`, `LineStyle`, `IconStyle`, and `LabelStyle`.
 * - All operations are tagged with appropriate identifiers for enhanced traceability and debugging.
 *
 * TODO BalloonStyle and ListStyle don't belong here according to the KML Reference. ColorStyle is a sub-class of SubStyle.
 * TESTME
 */
object ColorStyle extends Extractors with Renderers {
  implicit val extractorSeq: MultiExtractor[Seq[ColorStyle]] =
    multiExtractor6[ColorStyle, (BalloonStyle, ListStyle, PolyStyle, LineStyle, IconStyle, LabelStyle), BalloonStyle, ListStyle, PolyStyle, LineStyle, IconStyle, LabelStyle](
      (p1, p2, p3, p4, p5, p6) => (p1, p2, p3, p4, p5, p6), Seq("BalloonStyle", "ListStyle", "PolyStyle", "LineStyle", "IconStyle", "LabelStyle")
    ) ^^ "multiExtractorColorStyle"
  implicit val renderer: Renderer[ColorStyle] = rendererSuper6[ColorStyle, IconStyle, ListStyle, BalloonStyle, LabelStyle, LineStyle, PolyStyle] ^^ "rendererColorStyle"
  implicit val rendererSeq: Renderer[Seq[ColorStyle]] = sequenceRenderer[ColorStyle] ^^ "rendererColorStyles"
}

/**
 * Case class ColorStyleData to represent the data associated with all ColorStyle elements.
 *
 * @param maybeColor     an optional Color.
 * @param maybeColorMode an optional ColorMode.
 * @param subStyleData   the SubStyleData.
 */
case class ColorStyleData(maybeColor: Option[Color], maybeColorMode: Option[ColorMode])(val subStyleData: SubStyleData)

/**
 * Companion object for the `ColorStyleData` case class.
 *
 * Provides Extractors and Renderers required to efficiently process `ColorStyleData`.
 *
 * @define extractorPartial Defines a partial extractor for `ColorStyleData`
 *                          by converting `SubStyleData` into `ColorStyleData` with the help of the `apply` method.
 * @define extractor        An implicit full extractor for `ColorStyleData`, built atop the partial extractor to facilitate conversions.
 * @define renderer         An implicit renderer for `ColorStyleData`, mapping it back to its super representation.
 */
object ColorStyleData extends Extractors with Renderers {

  implicit val extractor: Extractor[ColorStyleData] =
    extractorPartial[SubStyleData, ColorStyleData](extractorPartial20(apply)) ^^ "extractorColorStyleData"
  implicit val renderer: Renderer[ColorStyleData] =
    renderer2Super(apply)(x => x.subStyleData) ^^ "rendererColorStyleData"
}

/**
 * Abstract Container element.
 * Container is a subtype of Feature and a super-type of Folder, Document.
 * See [[https://developers.google.com/kml/documentation/kmlreference#container Container]].
 *
 * A Container has no properties of its own.
 */
trait Container extends Feature

/**
 * Container object that extends functionalities from Extractors and Renderers for processing Container elements.
 *
 * This object defines:
 *   - An implicit MultiExtractor for sequences of Container types, facilitating extraction operations involving Folder and Document elements.
 *   - An implicit Renderer for rendering Container elements, leveraging provided Folder and Document data.
 */
object Container extends Extractors with Renderers {
  implicit val extractorSeq: MultiExtractor[Seq[Container]] =
    multiExtractor2[Container, (Folder, Document), Folder, Document]((f, d) => (f, d), Seq("Folder", "Document")) ^^ "multiExtractorContainer"
  implicit val renderer: Renderer[Container] = rendererSuper2[Container, Folder, Document] ^^ "rendererContainer"
}

/**
 * Properties of a Container (and therefore all its subtypes).
 *
 * @param featureData (auxiliary) member: FeatureData, shared by sub-elements.
 */
case class ContainerData(featureData: FeatureData)

/**
 * The `ContainerData` object serves as a companion object to the `ContainerData` case class.
 * It provides utility features for extracting and rendering instances of `ContainerData`.
 *
 * This object mixes in the `Extractors` and `Renderers` traits, which provide the foundational
 * behavior for extraction and rendering operations.
 *
 * It defines and initializes the following:
 *
 * - `applyFunction`: A private function used internally to create `ContainerData` instances from `FeatureData`.
 * - `extractorPartial`: A partially defined extractor for converting `FeatureData` into `ContainerData`.
 * - `extractor`: An implicit extractor instance for `ContainerData`.
 * - `renderer`: An implicit renderer instance for `ContainerData` that manages the rendering of
 * `ContainerData` instances back into their constituent elements.
 */
object ContainerData extends Extractors with Renderers {
  private val applyFunction: FeatureData => ContainerData = new ContainerData(_)
  implicit val extractor: Extractor[ContainerData] = extractorPartial[FeatureData, ContainerData](extractorPartial0(applyFunction)) ^^ "extractorContainerData"
  implicit val renderer: Renderer[ContainerData] = renderer0Super(applyFunction)(_.featureData) ^^ "rendererContainerData"
}

/**
 * Represents a sequence of `Coordinate` objects and provides operations for merging, reversing, and computing
 * geometric properties such as gaps and directions.
 * See [[https://developers.google.com/kml/documentation/kmlreference#coordinates Linear Ring coordinates]]
 *
 * @constructor Creates an instance of `Coordinates` with the specified sequence of `Coordinate` objects.
 * @param coordinates A sequence of `Coordinate` objects representing this collection.
 */
case class Coordinates(coordinates: Seq[Coordinate]) extends Mergeable[Coordinates] {

  /**
   * Computes the direction as the distance between the first and last coordinates in this `Coordinates` instance,
   * if possible.
   *
   * This value is computed lazily and is wrapped in an `Option`.
   * The computation involves retrieving the first and last coordinates from the sequence of coordinates within the `Coordinates` instance.
   * If either the first or last coordinate is missing, or if the distance between them cannot be computed, the result will be `None`.
   * TESTME
   *
   * @return an `Option` containing the computed distance as a `Double`, or `None` if the distance cannot be determined.
   */
  lazy val direction: Option[Double] = for (last <- coordinates.lastOption; first <- coordinates.headOption; d <- first distance last) yield d

  override def toString: String = {
    val sb = new StringBuilder("Coordinates{")
    sb.append(s"# coordinates: ${coordinates.size}, ")
    sb.append(s"from: ${coordinates.head}, ")
    sb.append(s"to: ${coordinates.last}")
    sb.append("}").toString()
  }

  private val result: Option[Cartesian] = for (last <- coordinates.lastOption; first <- coordinates.headOption; v <- first vector last) yield v

  /**
   * Computes the gap (distance) between this Coordinates instance and another Coordinates instance, if possible.
   *
   * @param other the other Coordinates instance to compute the gap with
   * @return an Option containing the distance as a Double, or None if the distance cannot be calculated
   */
  def gap(other: Coordinates): Option[Double] = gapInternal(coordinates, other.coordinates)

  /**
   * Merges this `Coordinates` instance with another `Coordinates` instance.
   *
   * @param other     the other `Coordinates` instance to be merged with this instance.
   * @param mergeName a boolean indicating whether to merge the names of the two `Coordinates` instances. Defaults to true.
   * @return an `Option` containing the resulting merged `Coordinates`, or `None` if the merge cannot be performed.
   */
  infix def merge(other: Coordinates, mergeName: Boolean = true): Option[Coordinates] = {
    KMLCompanion.logger.info(s"merge $this with $other")
    // CONSIDER rejuvenating the following code to try to deal automatically with inversions.
//    val xo = vector
//    val yo: Option[Cartesian] = other.vector
//    val zo: Option[Double] = for (x <- xo; y <- yo) yield x dotProduct y
//    val q: Option[Coordinates] = for (z <- zo) yield if (z >= 0) other else other.reverse // no longer used
    mergeInternal(Some(other))
  }

  /**
   * Reverses the order of the coordinates contained in this `Coordinates` instance.
   * TESTME
   *
   * @return a new `Coordinates` instance with the coordinates in reversed order.
   */
  def reverse: Coordinates = {
    KMLCompanion.logger.info(s"reversing $this")
    Coordinates(coordinates.reverse)
  }

  /**
   * Computes the gap (distance) between the last coordinate of the first sequence
   * and the first coordinate of the second sequence, if possible.
   *
   * @param cs1 a sequence of coordinates, where the last coordinate is considered.
   * @param cs2 a sequence of coordinates, where the first coordinate is considered.
   * @return an Option containing the distance as a Double, or None if the distance cannot be calculated.
   */
  private def gapInternal(cs1: Seq[Coordinate], cs2: Seq[Coordinate]): Option[Double] =
    for {
      a <- cs1.lastOption
      b <- cs2.headOption
      q <- a.distance(b)
    } yield q

  /**
   * Merges the current `Coordinates` instance with another optional `Coordinates` instance.
   * The merge operation is based on a comparison of distances (gaps) between the current
   * and the provided `Coordinates` instances.
   *
   * @param co an `Option` containing the `Coordinates` instance to be merged with the current instance
   * @return an `Option` containing the resulting merged `Coordinates`, or `None` if the merge cannot be performed
   */
  private def mergeInternal(co: Option[Coordinates]): Option[Coordinates] =
    for {
      c <- co
      r <- gap(c)
      s <- c.gap(this)
    } yield
      Coordinates(if (r < s) coordinates ++ c.coordinates else c.coordinates ++ coordinates)
}

/**
 * Companion object for the `Coordinates` class.
 *
 * This object provides utilities for extracting, rendering, and manipulating instances of `Coordinates`.
 * It includes implicit extractors and renderers for `Coordinates` objects and sequences of `Coordinates`.
 * Additionally, it provides methods for parsing and handling operations related to `Coordinates` instances.
 */
object Coordinates extends Extractors with Renderers {
  implicit val extractor: Extractor[Coordinates] = Extractor(node => Success(Coordinates.parse(node.text))) ^^ "extractorCoordinates"
  implicit val extractorSeq: MultiExtractor[Seq[Coordinates]] = multiExtractorBase[Coordinates](Positive) ^^ "multiExtractorCoordinates"
  implicit val renderer: Renderer[Coordinates] = renderer1(apply) ^^ "rendererCoordinates"
  implicit val rendererSeq: Renderer[Seq[Coordinates]] = sequenceRendererFormatted[Coordinates](FormatXML(_)) ^^ "rendererCoordinates_s"

  /**
   * Parses a given string representation of coordinates and returns a `Coordinates` instance.
   *
   * Per the KML spec, tuples within a `coordinates` element are separated by any number of
   * spaces or newlines, not necessarily one tuple per line - real-world (tool-generated) KML
   * commonly packs several tuples onto a single line, so this splits on any run of whitespace
   * rather than only on line breaks.
   *
   * @param w a string containing the representation of one or more coordinate tuples, separated
   *          by whitespace (spaces and/or newlines).
   * @return a `Coordinates` instance containing the parsed `Coordinate` objects extracted
   *         from the input string.
   */
  def parse(w: String): Coordinates = Coordinates(w.trim.split("\\s+").filter(_.nonEmpty).map(Coordinate(_)).toSeq)

  /**
   * Represents an instance of `Coordinates` without any elements.
   *
   * This value is used as an empty/default representation of the `Coordinates` class,
   * created with an empty sequence of `Coordinate` objects.
   */
  val empty: Coordinates = Coordinates(Nil)
}

/**
 * Case class Data: sub-element of ExtendedData, representing an untyped name/value pair.
 * See [[https://developers.google.com/kml/documentation/kmlreference#extendeddata Data]]
 *
 * @param _name            the name of this Data element (an XML attribute).
 * @param maybeDisplayName an optional user-friendly display name: Option[Text].
 * @param value            the value: Text.
 */
case class Data(_name: CharSequence, maybeDisplayName: Option[Text], value: Text)

/**
 * Companion object for the `Data` case class, providing extractors and renderers for `Data`
 * instances and sequences thereof.
 */
object Data extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Data] = extractor30(apply) ^^ "extractorData"
  implicit val extractorSeq: MultiExtractor[Seq[Data]] = multiExtractorBase[Data](NonNegative) ^^ "multiExtractorData"
  implicit val renderer: Renderer[Data] = renderer3(apply) ^^ "rendererData"
  implicit val rendererSeq: Renderer[Seq[Data]] = sequenceRenderer[Data] ^^ "rendererDatas"
}

/**
 * DisplayMode represents a display preference in association with the DisplayModeEnum enumeration.
 * See [[https://developers.google.com/kml/documentation/kmlreference#displaymode displayMode]]
 *
 * It is defined as a case class wrapping a value from the DisplayModeEnum enumeration.
 * The DisplayMode can be rendered or extracted for usage, utilizing the implicit values
 * defined in its companion object.
 *
 * The case class is closely associated with the DisplayModeEnum which provides the following options:
 * - default: Uses the provided information to generate a visible representation.
 * - hide: Suppresses the display of the associated representation.
 */
case class DisplayMode($: DisplayModeEnum.Value)

/**
 * Companion object for the DisplayMode case class.
 * Provides extractors and renderers for handling DisplayMode instances.
 *
 * Contains implicit values for:
 * - Extracting a DisplayMode instance.
 * - Extracting an optional DisplayMode instance.
 * - Rendering a DisplayMode instance.
 * - Rendering an optional DisplayMode instance.
 */
object DisplayMode extends Extractors with Renderers {

  private val extractorDisplayMode: Extractor[DisplayMode] = extractor10(apply) ^^ "extractorDisplayMode"
  implicit val extractorOpt: Extractor[Option[DisplayMode]] = extractorDisplayMode.lift ^^ "extractMaybeDisplayMode"
  implicit val rendererOpt: Renderer[Option[DisplayMode]] = renderer1(apply).lift ^^ "rendererOptionDisplayMode"
}

/**
 * Case class Document: sub-element of Container.
 * See [[https://developers.google.com/kml/documentation/kmlreference#document Document]].
 *
 * TODO add Schemas to this case class
 *
 * @param features      a sequence of Features.
 * @param containerData ContainerData (auxiliary property).
 */
case class Document(features: Seq[Feature])(val containerData: ContainerData) extends Container with HasFeatures with HasName {
  /**
   * Retrieves the name property from the feature data encapsulated within the container data.
   *
   * @return The name as a `Text` instance.
   */
  def name: Text = containerData.featureData.name

  override def toString: String = s"Document: name=${name.$} with ${features.size} features"
}

/**
 * Object companion for the `Document` case class, providing extractors and renderers.
 *
 * This object serves as a utility for working with `Document`, offering:
 * - Partial and complete extractors for deserializing `Document` instances.
 * - Renderers for serializing `Document` instances or sequences of `Document` objects.
 *
 * Members:
 * - `extractorPartial`: A partial extractor for transforming `ContainerData` to `Document`.
 * - `extractor`: An implicit complete extractor for `Document` instances.
 * - `renderer`: An implicit renderer for serializing `Document` to a string representation.
 * - `rendererSeq`: An implicit renderer for serializing a sequence of `Document` instances.
 *
 * Extraction and rendering processes are defined through the `Extractors` and `Renderers` mixins.
 */
object Document extends Extractors with Renderers {
  private val documentExtractor: Extractor[ContainerData => Document] = extractorPartial01(apply) ^^ "documentExtractor"
  implicit val extractor: Extractor[Document] = extractorPartial(documentExtractor) ^^ "extractorDocument"
  implicit val renderer: Renderer[Document] = renderer1Super(apply)(_.containerData) ^^ "rendererDocument"
  implicit val rendererSeq: Renderer[Seq[Document]] = sequenceRenderer[Document] ^^ "rendererDocuments"
}

/**
 * Represents a draw order value as an Int.
 * See [[https://developers.google.com/kml/documentation/kmlreference#draworder draworder]]
 *
 */
case class DrawOrder($: Int)

/**
 * Companion object for the `DrawOrder` case class, providing utility methods
 * and implicit instances for extraction and rendering.
 *
 * These utilities enable seamless integration of the `DrawOrder` type with
 * systems that require parsing, rendering, or manipulation of draw order values.
 */
object DrawOrder extends Extractors with Renderers {

  import Renderers.*

  private val drawOrderExtractor: Extractor[DrawOrder] = extractor10(apply) ^^ "drawOrderExtractor"
  implicit val extractorOpt: Extractor[Option[DrawOrder]] = drawOrderExtractor.lift ^^ "extractorOptionDrawOrder"
  implicit val rendererOpt: Renderer[Option[DrawOrder]] = renderer1(apply).lift ^^ "rendererOptionDrawOrder"
}


/**
 * Case class ExtendedData: optional sub-element of Feature, providing custom data in addition to
 * the standard KML fields.
 * See [[https://developers.google.com/kml/documentation/kmlreference#extendeddata ExtendedData]]
 *
 * @param Data       a sequence of untyped name/value pairs: Seq[Data].
 * @param SchemaData a sequence of Schema-typed name/value pairs: Seq[SchemaData].
 */
case class ExtendedData(Data: Seq[Data], SchemaData: Seq[SchemaData])

/**
 * Companion object for the `ExtendedData` case class.
 * Provides extractor and renderer instances for working with the `ExtendedData` type and
 * `Option[ExtendedData]`.
 */
object ExtendedData extends Extractors with Renderers {

  implicit val extractor: Extractor[ExtendedData] = extractor02(apply) ^^ "extractorExtendedData"
  implicit val extractorOpt: Extractor[Option[ExtendedData]] = extractor.lift ^^ "extractorOptionExtendedData"
  implicit val renderer: Renderer[ExtendedData] = renderer2(apply) ^^ "rendererExtendedData"
  implicit val rendererOpt: Renderer[Option[ExtendedData]] = renderer.lift ^^ "rendererOptionExtendedData"
}

/**
 * Class Extrude which represents a `Boolean`.
 * Used by `LineString` and `Polygon`.
 * Similar to `Tessellate`--and in fact they often go together.
 * See [[https://developers.google.com/kml/documentation/kmlreference#extrude Linear Ring extrude]]
 *
 * @param $ the value.
 */
case class Extrude($: Boolean)

/**
 * The `Extrude` object serves as a companion to the `Extrude` case class.
 * It extends the `Extractors` and `Renderers` traits to provide both
 * extraction and rendering capabilities for the `Extrude` type.
 *
 * This object includes implicit definitions for various extractors and renderers,
 * making it suitable for transforming and handling operations with `Extrude`
 * instances and their optional counterparts.
 *
 * Implicit extractors:
 * - `extractor`: Provides extraction logic for `Extrude` using `extractor10`.
 * - `extractorOpt`: Provides optional extraction logic for `Extrude` using `extractorOption`.
 *
 * Implicit renderers:
 * - `renderer`: Supplies rendering capabilities for `Extrude` instances.
 * - `rendererOpt`: Supplies rendering capabilities for optional `Extrude` instances.
 */
object Extrude extends Extractors with Renderers {

  import Renderers.*

  private val extrudeExtractor: Extractor[Extrude] = extractor10(apply) ^^ "extrudeExtractor"
  implicit val extractorOpt: Extractor[Option[Extrude]] = extrudeExtractor.lift ^^ "extractorOptionExtrude"
  implicit val rendererOpt: Renderer[Option[Extrude]] = renderer1(apply).lift ^^ "rendererOptionExtrude"
}

/**
 * Trait Feature: abstract sub-element of KmlObject.
 * Feature is a sub-type of Object and a super-type of Placemark, Container.
 * See [[https://developers.google.com/kml/documentation/kmlreference#feature Feature]].
 *
 * TODO add Overlay, NetworkLink.
 */
trait Feature extends KmlObject {

  /**
   * Method to edit this Feature, given a sequence of sibling Features such that we may be able to combine features.
   * TESTME
   *
   * @param e  the edit to apply.
   * @param fs the siblings (which actually do include this itself).
   * @return an optional Feature.
   */
  def editToOption(e: KmlEdit, fs: Seq[Feature]): Option[Feature] =
    this match {
      case p: Placemark => p.editToOptionOption(e, fs).getOrElse(Some(this)) // CONSIDER returning None
      case d: Document => editHasFeaturesToOption(d)(e)(_fs => d.copy(features = _fs)(d.containerData))
      case x: Folder => editHasFeaturesToOption(x)(e)(_fs => x.copy(features = _fs)(x.containerData))
      case _ => Some(this) // Container // CONSIDER returning None
    }

}

/**
 * The `Feature` object provides implementations for extractors and renderers related to the `Feature` trait.
 *
 * It includes implicit definitions for processing sequences of `Feature` instances and rendering them.
 * This object is intended to support operations and transformations on `Feature` and its subtypes.
 *
 * @see `Feature` trait for more details on the `Feature` type hierarchy.
 */
object Feature extends Extractors with Renderers {

  implicit val extractorSeq: MultiExtractor[Seq[Feature]] =
    MultiExtractor.createLazy(multiExtractor6[Feature, (Folder, Document, Placemark, GroundOverlay, PhotoOverlay, ScreenOverlay), Folder, Document, Placemark, GroundOverlay, PhotoOverlay, ScreenOverlay]((f, d, p, go, po, so) =>
      (f, d, p, go, po, so), Seq("Folder", "Document", "Placemark", "GroundOverlay", "PhotoOverlay", "ScreenOverlay")) ^^ "multiExtractorFeature")
  implicit val renderer: Renderer[Feature] = Renderer.createLazy(rendererSuper5[Feature, Placemark, Container, GroundOverlay, PhotoOverlay, ScreenOverlay] ^^ "rendererFeature")
  implicit val rendererSeq: Renderer[Seq[Feature]] = sequenceRenderer[Feature] ^^ "rendererFeatures"
}

/**
 * Properties of a Feature (and therefore all its sub-types).
 *
 * CONSIDER use StyleURL type for maybeStyleURL.
 *
 * CONSIDER redefining abstractView so that it can have only one optional AbstractView (according to KML spec.)
 *
 * @param name             the name (a Text).
 * @param maybeDescription an optional description: Option[Text].
 * @param maybeStyleUrl    an optional style URL: Option[String].
 * @param maybeOpen        an optional openness designation: Option[Int].
 * @param maybeExtendedData an optional ExtendedData: Option[ExtendedData].
 * @param StyleSelectors   a sequence of StyleSelectors: Seq[StyleSelector].
 * @param kmlData          (auxiliary) member: KmlData.
 */
case class FeatureData(name: Text, maybeDescription: Option[Text], maybeStyleUrl: Option[Text], maybeOpen: Option[Open], maybeVisibility: Option[Visibility], maybeExtendedData: Option[ExtendedData], StyleSelectors: Seq[StyleSelector], abstractView: Seq[AbstractView])(val kmlData: KmlData) extends Mergeable[FeatureData] with HasName {
  /**
   * Method to merge FeatureData objects.
   *
   * @param f a FeatureData object.
   * @return the merged value of FeatureData.
   */
  def merge(f: FeatureData, mergeName: Boolean = true): Option[FeatureData] = {
    // TODO warn if styles are not the same.
    for {
      n <- if (mergeName) name merge f.name else Some(name)
      d = mergeOptions(maybeDescription, f.maybeDescription)((t1, t2) => t1 merge t2)
      z <- kmlData merge f.kmlData
    } yield FeatureData(n, d, maybeStyleUrl, maybeOpen, maybeVisibility, maybeExtendedData, StyleSelectors, abstractView)(z) // TODO: not all fields are properly merged
  }
}

/**
 * Companion object for `FeatureData` case class.
 *
 * The `FeatureData` object provides extractor and renderer implementations for `FeatureData` instances,
 * allowing the transformation and processing of `FeatureData` objects.
 *
 * It extends the functionality of both `Extractors` and `Renderers` traits to enable
 * flexible extraction and rendering of `FeatureData`.
 *
 * Members:
 * - `extractorPartial`: A partial extractor defined for `FeatureData` using `KmlData => FeatureData`.
 * - `extractor`: An implicit extractor for `FeatureData` based on `extractorPartial`.
 * - `renderer`: An implicit renderer for `FeatureData` facilitating serialization.
 */
object FeatureData extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[FeatureData] =
    extractorPartial[KmlData, FeatureData](extractorPartial62(apply)) ^^ "extractorFeatureData"
  implicit val renderer: Renderer[FeatureData] =
    renderer8Super(apply)(_.kmlData) ^^ "rendererFeatureData"
}

/**
 * Fill.
 * NOTE that this is a boolean that is represented by 0 or 1.
 * See [[https://developers.google.com/kml/documentation/kmlreference#fill fill]]
 *
 * @param boolean whether to fill or not.
 */
case class Fill(boolean: Boolean)

/**
 * The `Fill` object provides extractor and renderer implementations for the `Fill` case class.
 * It offers mechanisms to extract `Fill` instances and render them as representations
 * suitable for processing or serialization.
 *
 * The object includes implicit extractors and renderers for both `Fill` and `Option[Fill]` types:
 * - Extractors parse and convert input representations into `Fill` instances.
 * - Renderers generate output representations from `Fill` instances.
 *
 * Operations within this object make use of the provided mechanisms in the `Extractors`
 * and `Renderers` traits for consistent and flexible processing pipelines.
 */
object Fill extends Extractors with Renderers {

  import Renderers.*

  private val fillExtractor: Extractor[Fill] = extractor10(apply) ^^ "fillExtractor"
  implicit val extractorOpt: Extractor[Option[Fill]] = fillExtractor.lift ^^ "extractMaybeFill"
  implicit val rendererOpt: Renderer[Option[Fill]] = renderer1(apply).lift ^^ "rendererOptionFill"
}

/**
 * A case class representing FlyToView in KML documents.
 * TESTME
 *
 * This case class encapsulates a `Boolean` value that signifies FlyToView attributes in KML data.
 * FlyToView settings often dictate A value of 1 causes Google Earth to fly to the view of the LookAt or Camera in the NetworkLinkControl (if it exists).
 */
case class FlyToView($: Boolean)

/**
 * Companion object for the `FlyToView` case class.
 *
 * This object provides implicits and utilities for serializing and deserializing `FlyToView` instances.
 * It extends traits `Extractors` and `Renderers` to support extraction and rendering functionalities.
 *
 * Implicit extractors and renderers are provided for both `FlyToView` and `Option[FlyToView]`, enabling their usage
 * in data processing pipelines that require standardized extraction or rendering logic.
 */
object FlyToView extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[FlyToView] = extractor10(apply) ^^ "extractorFlyToView"
  implicit val renderer: Renderer[FlyToView] = renderer1(apply) ^^ "rendererFlyToView"
}


/**
 * Case class Folder: subelement of Container.
 * See [[https://developers.google.com/kml/documentation/kmlreference#container Folder]].
 *
 * @param features      a sequence of Feature elements (where Feature is an abstract super-type).
 * @param containerData the ContainerData (auxiliary property).
 */
case class Folder(features: Seq[Feature])(val containerData: ContainerData) extends Container with HasFeatures with HasName {
  /**
   * Retrieves the name feature from the container data's feature data.
   *
   * @return The name represented as a `Text` object extracted from the container data.
   */
  def name: Text = containerData.featureData.name

  override def toString: String = s"Folder: name=${name.$} with ${features.size} features"
}

/**
 * Folder object provides extractors and renderers for the `Folder` case class.
 *
 * - Extractors are used to parse or transform `Folder` objects.
 * - Renderers are used to serialize `Folder` objects and sequences of them.
 *
 * Members:
 * - `extractorPartial`: A partial extractor function for transforming `ContainerData` into `Folder`.
 * - `extractor`: Implicit extractor for `Folder`.
 * - `renderer`: Implicit renderer for a single `Folder`.
 * - `rendererSeq`: Implicit renderer for sequences of `Folder` instances.
 */
object Folder extends Extractors with Renderers {
  implicit val extractor: Extractor[Folder] = extractorPartial(extractorPartial01(apply)) ^^ "extractorFolder"
  implicit val renderer: Renderer[Folder] = renderer1Super(apply)(_.containerData) ^^ "rendererFolder"
  implicit val rendererSeq: Renderer[Seq[Folder]] = sequenceRenderer[Folder] ^^ "rendererFolders"
}

/**
 * Trait Geometry: abstract subelement of KmlObject.
 * Geometry is the super-type of LineString, Polygon, LinearRing, and Point.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#geometry Geometry]]
 */
trait Geometry extends KmlObject with Mergeable[Geometry] with Invertible[Geometry] {

  /**
   * Merge this mergeable object with <code>t</code>.
   *
   * @param t the object to be merged with this.
   * @return the merged value of T.
   */
  def merge(t: Geometry, mergeName: Boolean = true): Option[Geometry] = None

  /**
   * Inverts the current Geometry instance.
   * This operation typically reverses the orientation or nature of the Geometry,
   * depending on its specific subtype (e.g., LineString, Polygon, etc.).
   *
   * @return an Option containing the inverted Geometry if applicable; otherwise, None.
   */
  def invert: Option[Geometry] = None
}

/**
 * Object Geometry serves as a utility and companion object for the `Geometry` trait and its related subtypes
 * (LineString, Point, Polygon, LinearRing). It provides implicit extractors and renderers to facilitate
 * operations such as multi-extraction, rendering, and rendering sequences of `Geometry` instances.
 *
 * It leverages the `Extractors` and `Renderers` traits to abstract the functionality of parsing and generating
 * `Geometry` instances while adhering to the expected formats.
 *
 * Members:
 * - `extractorSeq`: An implicit utility that facilitates the extraction of sequences of `Geometry` instances,
 * supporting parsing of its subtypes (LineString, Point, Polygon, LinearRing).
 * - `renderer`: An implicit utility for rendering instances of `Geometry` into their expected formats.
 * - `rendererSeq`: An implicit utility for rendering sequences of `Geometry` instances.
 */
object Geometry extends Extractors with Renderers {
  // NOTE: MultiGeometry recursively contains Geometry, so this and MultiGeometry's own
  // extractor/renderer are mutually dependent implicits - exactly the same shape as
  // Feature/Container (a Container holds Seq[Feature], and Folder/Document are themselves
  // Features). Following that precedent: createLazy's argument is by-name, so it's not
  // evaluated at all until the returned Extractor/Renderer is actually invoked, by which point
  // both objects are fully constructed.
  implicit val extractorSeq: MultiExtractor[Seq[Geometry]] =
    MultiExtractor.createLazy(multiExtractor5[Geometry, (LineString, Point, Polygon, LinearRing, MultiGeometry), LineString, Point, Polygon, LinearRing, MultiGeometry]((ls, pt, pg, lr, mg) => (ls, pt, pg, lr, mg), Seq("LineString", "Point", "Polygon", "LinearRing", "MultiGeometry")) ^^ "multiExtractorGeometry")
  implicit val renderer: Renderer[Geometry] =
    Renderer.createLazy(rendererSuper5[Geometry, Point, LineString, Polygon, LinearRing, MultiGeometry] ^^ "rendererGeometry")
  implicit val rendererSeq: Renderer[Seq[Geometry]] = sequenceRenderer[Geometry] ^^ "rendererGeometrys"
}

/**
 * Properties of Geometry.
 * There are no properties specific to Geometry.
 * See [[https://developers.google.com/kml/documentation/kmlreference#geometry Geometry]]
 *
 * @param kmlData source of properties.
 */
case class GeometryData(maybeExtrude: Option[Extrude], maybeAltitudeMode: Option[AltitudeMode])(val kmlData: KmlData) extends Mergeable[GeometryData] {
  /**
   * Merges the current GeometryData instance with another GeometryData instance.
   *
   * This method combines the properties of the current instance and the given instance
   * using defined merge rules.
   * The merging process considers attributes such as extrude
   * and altitude mode, while also merging underlying KML data.
   *
   * @param g         the GeometryData instance to merge with.
   * @param mergeName a Boolean flag indicating whether to merge names (default is true).
   * @return an Option of GeometryData, representing the merged result, or None if merging fails.
   */
  infix def merge(g: GeometryData, mergeName: Boolean = true): Option[GeometryData] =
    for {
      k <- kmlData merge g.kmlData
    } yield GeometryData(mergeOptionsBiased(maybeExtrude, g.maybeExtrude), mergeOptionsBiased(maybeAltitudeMode, g.maybeAltitudeMode))(k)
}

/**
 * Companion object for the `GeometryData` case class.
 *
 * Provides utilities and functionality for working with `GeometryData` such as
 * extractors and renderers which are essential for converting to and from external formats,
 * as well as rendering it into desired representations.
 *
 * Extends `Extractors` and `Renderers` to leverage common extraction and rendering utilities.
 *
 * Functionality:
 * - Defines a private partial extractor, `extractorPartial`, for constructing `GeometryData` instances.
 * - Provides an implicit `Extractor` instance for `GeometryData` to enable its seamless extraction.
 * - Provides an implicit `Renderer` instance for `GeometryData` for rendering purposes, tagged as "rendererGeometryData".
 */
object GeometryData extends Extractors with Renderers {

  implicit val extractor: Extractor[GeometryData] = extractorPartial[KmlData, GeometryData](extractorPartial20(apply))
  implicit val renderer: Renderer[GeometryData] = renderer2Super(apply)(_.kmlData) ^^ "rendererGeometryData"
}

/**
 * Represents a GroundOverlay, which is a KML feature that overlays an image on the surface
 * of the Earth. The image is geographically bound to a `LatLonBox` and can have an optional
 * altitude and altitude mode.
 * See [[https://developers.google.com/kml/documentation/kmlreference#groundoverlay GroundOverlay]]
 *
 * A `GroundOverlay` extends the `BaseOverlay` class to include additional geographic attributes
 * such as the bounding box and altitude-related data.
 *
 * @param maybeAltitude     An optional `Altitude` value that specifies the elevation of the
 *                          overlay, typically measured in meters.
 * @param maybeAltitudeMode An optional `AltitudeMode` value indicating how the altitude of
 *                          the overlay is interpreted (e.g., relative to the ground or absolute).
 * @param LatLonBox         Defines the geographic bounding rectangle where the overlay image
 *                          is positioned, including its rotation.
 * @param overlayData       The overlay-specific data encapsulated in `OverlayData`, containing
 *                          attributes such as the icon, draw order, and associated feature data.
 */
case class GroundOverlay(maybeAltitude: Option[Altitude], maybeAltitudeMode: Option[AltitudeMode], LatLonBox: LatLonBox)(val overlayData: OverlayData) extends BaseOverlay(overlayData)

/**
 * The `GroundOverlay` object serves as a companion to the `GroundOverlay` case class and
 * provides functionality for extracting and rendering instances of `GroundOverlay`.
 *
 * It contains predefined extractors and renderers to facilitate the conversion and
 * representation of `GroundOverlay` objects.
 *
 * Members:
 *
 * - `extractorPartial`: A partial extractor function that processes `OverlayData`
 * and produces `GroundOverlay`.
 * It is labeled with the identifier "extractorCD2GroundOverlay".
 *
 * - `extractor`: An implicit extractor for `GroundOverlay` that utilizes `extractorPartial`
 * to produce complete instances of `GroundOverlay`. It is labeled with the identifier
 * "extractorGroundOverlay".
 *
 * - `renderer`: An implicit renderer for `GroundOverlay` that manages the representation
 * of instances, delegating to a predefined rendering function. It is labeled with the
 * identifier "renderGroundOverlay".
 *
 * - `renderSeq`: An implicit renderer for a sequence of `GroundOverlay`, enabling the
 * structured representation of a collection of `GroundOverlay` instances. It is labeled
 * with the identifier "rendererGroundOverlays".
 */
object GroundOverlay extends Extractors with Renderers {
  implicit val extractor: Extractor[GroundOverlay] = extractorPartial(extractorPartial30(apply)) ^^ "extractorGroundOverlay"
  implicit val renderer: Renderer[GroundOverlay] = renderer3Super(apply)(_.overlayData) ^^ "renderGroundOverlay"
  implicit val renderSeq: Renderer[Seq[GroundOverlay]] = sequenceRenderer[GroundOverlay] ^^ "rendererGroundOverlays"
}

/**
 * Case class to represent a Heading which is represented in XML as, for example: <heading>1.1</heading>
 * See [[https://developers.google.com/kml/documentation/kmlreference#heading heading]]
 *
 * @param $ the value of the heading (a Double).
 */
case class Heading($: Double)

/**
 * Provides extractor and renderer instances for the Heading case class.
 *
 * This object facilitates the conversion of Heading instances to and from different formats,
 * enabling seamless interaction between data representations.
 * It includes implicit implementations
 * of extractors and renderers for both Heading and Option[Heading] types.
 *
 * The extractors and renderers adhere to a standardized naming convention:
 * - Extractor: Converts external data into Heading or Option[Heading] instances.
 * - Renderer: Converts Heading or Option[Heading] instances into a rendered form.
 */
object Heading extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Heading] = extractor10(apply) ^^ "extractorHeading"
  implicit val extractorOpt: Extractor[Option[Heading]] = extractor.lift ^^ "extractMaybeHeading"
  implicit val renderer: Renderer[Heading] = renderer1(apply) ^^ "rendererHeading"
  implicit val rendererOpt: Renderer[Option[Heading]] = renderer.lift ^^ "rendererOptionHeading"
}

/**
 * Case class to model a HotSpot.
 * See [[https://developers.google.com/kml/documentation/kmlreference#hotspot hotSpot]]
 *
 * @param _x      optional x field.
 * @param _xunits optional xunits field.
 * @param _y      optional y field.
 * @param _yunits optional yunits field.
 */
case class HotSpot(_x: Int, _xunits: UnitsEnum.Value, _y: Int, _yunits: UnitsEnum.Value)

/**
 * The `HotSpot` object serves as a companion to the `HotSpot` case class.
 * It extends the `Extractors` and `Renderers` traits, providing implicit extractors and renderers
 * for `HotSpot` and `Option[HotSpot]`.
 * These implicits allow for streamlined extraction
 * and rendering of `HotSpot` instances using predefined extraction and rendering methods.
 *
 * `extractor`, `extractorOpt`, `renderer`, and `rendererOpt` are implicitly available
 * for parsing and generating the corresponding representations of `HotSpot`.
 */
object HotSpot extends Extractors with Renderers {

  import Renderers.*

  // XXX we need the (plain) extractor as a val only because it is required in KML_Spec
  implicit val extractor: Extractor[HotSpot] = extractor40(apply) ^^ "extractorHotspot"
  implicit val extractorOpt: Extractor[Option[HotSpot]] = extractor.lift ^^ "extractMaybeHotSpot"
  implicit val renderer: Renderer[HotSpot] = renderer4(apply) ^^ "rendererHotSpot"
  implicit val rendererOpt: Renderer[Option[HotSpot]] = renderer.lift ^^ "rendererOptionHotSpot"
}

/**
 * Case class Icon.
 * NOTE: we do not currently support Link.
 * See [[https://developers.google.com/kml/documentation/kmlreference#icon Icon]]
 *
 * @param href a URL reference to an image.
 */
case class Icon(href: Text)

/**
 * Companion object for the case class Icon.
 *
 * Extends functionality with Extractors and Renderers to enable safe conversion
 * and rendering for the Icon case class.
 * The object defines implicit extractor
 * and renderer instances for handling the Icon type.
 * The extractor and renderer
 * are utilized for serializing and deserializing Icon objects.
 */
object Icon extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Icon] = extractor10(apply) ^^ "extractorIcon"
  implicit val renderer: Renderer[Icon] = renderer1(apply) ^^ "rendererIcon"
}

/**
 * This case class represents the inner boundary of a polygon in KML (Keyhole Markup Language) files.
 * The inner boundary is defined by a `LinearRing`,
 * which specifies a closed, linear path (a closed line with four or more positions).
 * See [[https://developers.google.com/kml/documentation/kmlreference#innerboundaryis InnerBoundaryIs]]
 *
 * @param LinearRing The `LinearRing` defining the closed linear path representing the inner boundary.
 */
case class InnerBoundaryIs(LinearRing: LinearRing)

/**
 * Companion class to InnerBoundaryIs.
 */
object InnerBoundaryIs extends Extractors with Renderers {
  // NOTE: InnerBoundaryIs and OuterBoundaryIs are structurally identical (both just wrap a
  // LinearRing) - they're distinguished only by their own element's tag, not by shape. When no
  // literal <innerBoundaryIs> tag is present, extractChildren's generic extractAll fallback tries
  // to structurally match every child of the enclosing Polygon against InnerBoundaryIs's shape,
  // with no idea that a sibling <outerBoundaryIs> is spoken for by a different field of the same
  // Polygon - and since it has the same shape, it wrongly "matches" too. Registering this tag as
  // a must-match tag skips that fallback for this field entirely: a missing literal tag just
  // means zero results, as it should, rather than borrowing a sibling field's content.
  TagProperties.addMustMatch("innerBoundaryIs")

  implicit val extractor: Extractor[InnerBoundaryIs] = extractor10(apply) ^^ "extractorInnerBoundaryIs"
  implicit val extractorSeq: MultiExtractor[Seq[InnerBoundaryIs]] = multiExtractorBase[InnerBoundaryIs](NonNegative) ^^ "multiExtractorInnerBoundaryIs"
  implicit val renderer: Renderer[InnerBoundaryIs] = renderer1(apply) ^^ "rendererInnerBoundaryIs"
  // NOTE: sequenceRenderer (not used here) lets each element render under its own class's name -
  // right for a polymorphic Seq, but here InnerBoundaryIs's class name doesn't match its actual
  // KML tag ("innerBoundaryIs", lower-case-initial), so the name has to be forced explicitly.
  implicit val rendererSeq: Renderer[Seq[InnerBoundaryIs]] = sequenceRendererNamed[InnerBoundaryIs]("innerBoundaryIs") ^^ "rendererInnerBoundaryIs"
}

/**
 * Case class representing an icon associated with an item, characterized by its state and a hyperlink reference.
 *
 * @param state the state of the ItemIcon, which should ideally represent one of several possible enumerated statuses
 *              such as "open," "closed," "error," "fetching0," "fetching1," or "fetching2."
 * @param href  the hyperlink reference (represented as Text) associated with the ItemIcon.
 */
case class ItemIcon(state: State, href: Text)

/**
 * Companion object for the ItemIcon case class that provides utilities for extracting,
 * rendering, and optionally managing ItemIcon instances.
 * This object facilitates the
 * interaction and transformation of ItemIcon objects through predefined extractors and renderers.
 *
 * Extractors and Renderers:
 * - Provides implicit definitions for extracting values of type `ItemIcon` and `Option[ItemIcon]`.
 * - Provides implicit definitions for rendering `ItemIcon` and `Option[ItemIcon]` objects.
 */
object ItemIcon extends Extractors with Renderers {

  import Renderers.*

  private val itemIconExtractor: Extractor[ItemIcon] = extractor20(apply) ^^ "itemIconExtractor"
  implicit val extractorOpt: Extractor[Option[ItemIcon]] = itemIconExtractor.lift ^^ "extractMaybeItemIcon"
  implicit val rendererOpt: Renderer[Option[ItemIcon]] = renderer2(apply).lift ^^ "rendererOptionItemIcon"
}

/**
 * Case class to model IconStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#iconstyle IconStyle]]
 *
 * @param maybeScale     optional Scale.
 * @param Icon           the Icon.
 * @param maybeHotSpot   optional HotSpot
 * @param maybeHeading   an optional Heading.
 * @param colorStyleData the (auxiliary) color style properties.
 */
case class IconStyle(maybeScale: Option[Scale], Icon: Icon, maybeHotSpot: Option[HotSpot], maybeHeading: Option[Heading])(val colorStyleData: ColorStyleData) extends ColorStyle

/**
 * An object that provides extraction and rendering utilities for the `IconStyle` case class.
 *
 * `IconStyle` represents styling information for icons, including color style data
 * and additional properties such as scale, icon details, hotspot, and heading.
 *
 * This object includes the following:
 *
 * - `extractorPartial`: A partial extractor that transforms `ColorStyleData` to `IconStyle`.
 * - `extractor`: An implicit extractor for extracting `IconStyle` instances.
 * - `renderer`: An implicit renderer for rendering `IconStyle` instances.
 * - `rendererOpt`: An implicit renderer for rendering optional `IconStyle` instances.
 */
object IconStyle extends Extractors with Renderers {
  implicit val extractor: Extractor[IconStyle] =
    extractorPartial[ColorStyleData, IconStyle](extractorPartial40(apply)) ^^ "extractorIconStyle"
  implicit val renderer: Renderer[IconStyle] =
    renderer4Super(apply)(x => x.colorStyleData) ^^ "rendererIconStyle"
  implicit val rendererOpt: Renderer[Option[IconStyle]] =
    renderer.lift ^^ "rendererOptionIconStyle"
}

/**
 * This case class represents a hierarchical set of images,
 * each of which is an increasingly lower resolution version of the original image.
 * used by PhotoOverlay.
 *
 * @param tileSize Size of the tiles, in pixels. Tiles must be square, and "tileSize" must be a power of 2.
 *                 A tile size of 256 (the default) or 512 is recommended.
 * @param maxWidth Width in pixels of the original image.
 * @param maxHeight Height in pixels of the original image.
 * @param gridOrigin Specifies where to begin numbering the tiles in each layer of the pyramid.
 *                   A value of lowerLeft specifies that row 1, column 1 of each layer is in the bottom left corner of the grid.
 *                   just exist two values, lowerLeft, upperLeft
 */
case class ImagePyramid(tileSize: Int, maxWidth: Int, maxHeight: Int, gridOrigin: Boolean)

/**
 * Companion object for the ImagePyramid case class.
 * Provides implicit instances for Extractor and Renderer typeclasses to facilitate
 */
object ImagePyramid extends Extractors with Renderers {
  implicit val extractor: Extractor[ImagePyramid] = extractor40(apply) ^^ "extractorImagePyramid"
  implicit val renderer: Renderer[ImagePyramid] = renderer4(apply) ^^ "rendererImagePyramid"
}

/**
 * Represents a `Key` that associates a style state with a style map.
 *
 * The `Key` case class encapsulates a value from the `StyleStateEnum`, which
 * defines the specific state of a style, such as `normal` or `highlight`. This
 * class is integral to managing and distinguishing between different visual
 * representations of styles, particularly in applications like Google Earth.
 *
 * @param $ Represents the state of the style and must be one of the values
 *          defined in the `StyleStateEnum` enumeration.
 */
case class Key($: StyleStateEnum.Value)

/**
 * Object `Key` serves as a companion to the `Key` case class and provides
 * implicit definitions for extractors and renderers, enabling the integration
 * of `Key` with parsing and formatting operations.
 *
 * This object mixes in the `Extractors` and `Renderers` traits to leverage
 * reusable methods and functionalities for defining its implicit members.
 *
 * Implicit Members:
 * - `extractor`: Defines an `Extractor` instance for `Key`, supporting its conversion
 * from data sources into the `Key` type.
 * - `renderer`: Provides a `Renderer` instance for `Key`, facilitating its conversion
 * from the `Key` type to human-readable or serialized formats.
 */
object Key extends Extractors with Renderers {

  implicit val extractor: Extractor[Key] = extractor10(apply) ^^ "extractorKey"
  implicit val renderer: Renderer[Key] = renderer1(apply) ^^ "rendererKey"
}

/**
 * Case class to define a KML object.
 *
 * NOTE WELL: do not be tempted to add "_xmlns" as a member.
 * If you do, you will run into the undocumented(?) "feature"
 * of the Xml library that "xmlns" is a reserved attribute name.
 *
 * @param features a sequence of Feature (typically Document).
 */
case class KML(features: Seq[Feature]) extends HasFeatures {

  /**
   * Method to edit a KML object.
   *
   * @param e an edit to be applied to the given KML.
   * @return an optional KML that, if defined, is different from the input.
   */
  def edit(e: KmlEdit): Option[KML] = for (z <- editHasFeaturesToOption(this)(e)(fs => copy(features = fs))) yield z

}

/**
 * Companion object for the KML case class. This object provides utilities for
 * rendering, extracting, and managing KML objects in various formats. Additionally,
 * it defines custom renderers and extractors tailored to KML handling.
 *
 * NOTE that there are two different methods for rendering a KML object.
 * One is to use the KML_Binding mechanism.
 * The other is to invoke the method below: renderKml.
 */
object KML extends Extractors with Renderers {

  /**
   * Initializes the KML object by adding a specific tag ("innerBoundaryIs")
   * to the must-match list of tags in the TagProperties object.
   * This ensures
   * that the specified tag must conform to certain matching criteria.
   *
   * NOTE: no longer necessary to call this explicitly - InnerBoundaryIs's own companion object
   * now registers this same tag as a side effect of being loaded, which happens automatically
   * the first time anything triggers Polygon's extractor. Kept for backward compatibility;
   * calling it is harmless (the registration is idempotent).
   *
   * @return Unit This method has no return value.
   */
  def init(): Unit = {
    TagProperties.addMustMatch("innerBoundaryIs")
  }

  implicit val extractor: Extractor[KML] = extractor01(apply) ^^ "extractorKml"
  implicit val extractorSeq: MultiExtractor[Seq[KML]] = multiExtractorBase[KML](NonNegative) ^^ "multiExtractorKml"
  implicit val renderer: Renderer[KML] = renderer1(apply) ^^ "rendererKml"

  /**
   * Method to render a KML using a fixed value for xmlns and a fixed prefix.
   *
   * @param kml    the KML object to be rendered.
   * @param format the format required.
   * @return a Try[String].
   */
  def renderKml(kml: KML, format: Format): Try[String] = {
    val r -> sR = format match {
      case _: FormatXML => rendererSpecial -> stateR
      case _ => implicitly[Renderer[KML]] -> StateR()
    }
    TryUsing(sR)(sr => r.render(kml, format, sr))
  }

  private val KMLTag = "kml"
  private val xmlnsAttribute = """xmlns="http://www.opengis.net/""" + KMLTag + """/2.2""""
  private val prefix = """<?xml version="1.0" encoding="UTF-8"?>"""

  /**
   * Method to create a new instance of `StateR` with a pre-configured state.
   * XXX Ensure that this remains a def
   *
   * This method initializes a `StateR` object with the following:
   * - `maybeName` set to `Some("kml")` to use "kml" as the default name for the state.
   * - `attributes` initialized as a `SmartBuffer` containing the string `xmlnsAttribute` to handle XML namespace configuration.
   * - `interior` set to `false` to indicate that the state is at the top level of an element and not within nested rendering.
   *
   * It is primarily used in the `renderKml` method of the `KML` object for XML rendering purposes.
   *
   * @return a new `StateR` instance configured for top-level KML rendering.
   */
  def stateR = new StateR(Some(KMLTag), SmartBuffer(new StringBuilder(xmlnsAttribute)), false)

  private val rendererSpecial: Renderer[KML] = renderer1Special(apply, prefix + "\n") ^^ "rendererKml"
}

/**
 * Case class LabelStyle which extends ColorStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#labelstyle Label Style]]
 *
 * @param scale          the scale.
 * @param colorStyleData the (auxiliary) color style properties.
 */
case class LabelStyle(scale: Scale)(val colorStyleData: ColorStyleData) extends ColorStyle

/**
 * Companion object for the LabelStyle case class.
 *
 * This object provides functionality for extracting, rendering, and
 * managing LabelStyle instances.
 * It extends Extractors and Renderers
 * to leverage shared utilities for handling data conversion and manipulation.
 *
 * Members:
 * - `extractorPartial`: A partial extractor from ColorStyleData to LabelStyle.
 * - `extractor`: Fully defined extractor to convert inputs into LabelStyle instances.
 * - `renderer`: Renderer implementation for LabelStyle instances.
 * - `rendererOpt`: Renderer for optional LabelStyle instances.
 */
object LabelStyle extends Extractors with Renderers {
  implicit val extractor: Extractor[LabelStyle] =
    extractorPartial[ColorStyleData, LabelStyle](extractorPartial10(apply)) ^^ "extractorLabelStyle"
  implicit val renderer: Renderer[LabelStyle] =
    renderer1Super(apply)(_.colorStyleData) ^^ "rendererLabelStyle"
  implicit val rendererOpt: Renderer[Option[LabelStyle]] =
    renderer.lift ^^ "rendererOptionLabelStyle"
}

/**
 * Represents a latitude value as a Double, an angle90.
 *
 * Latitude values are commonly used in geographic coordinate systems
 * to specify the north-south position of a point on the Earth's surface.
 *
 * This case class is typically accompanied by extractors and renderers
 * to facilitate parsing and rendering operations as part of a larger system.
 */
case class Latitude($: Double)

/**
 * Companion object for the `Latitude` case class, providing utility methods
 * and implicit instances for extraction and rendering.
 *
 * This object includes:
 * - An extractor for parsing `Latitude` instances.
 * - An optional extractor for handling optional `Latitude` values.
 * - A renderer for converting `Latitude` instances into a specific format.
 * - An optional renderer for handling optional `Latitude` values.
 *
 * These utilities enable seamless integration of the `Latitude` type with
 * systems that require parsing, rendering, or manipulation of latitude values.
 */
object Latitude extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Latitude] = extractor10(apply) ^^ "extractorLatitude"
  implicit val extractorOpt: Extractor[Option[Latitude]] = extractor.lift ^^ "extractorOptionLatitude"
  implicit val renderer: Renderer[Latitude] = renderer1(apply) ^^ "rendererLatitude"
  implicit val rendererOpt: Renderer[Option[Latitude]] = renderer.lift ^^ "rendererOptionLatitude"
}

/**
 * Represents a rectangular geographic bounding box defined by its northernmost, southernmost,
 * easternmost, and westernmost limits, along with an optional rotational value.
 * Used by GroundOverlay.
 *
 * @param north    The northern boundary of the box as a Latitude.
 * @param south    The southern boundary of the box as a Latitude.
 * @param east     The eastern boundary of the box as a Longitude.
 * @param west     The western boundary of the box as a Longitude.
 * @param rotation The rotational angle applied to the box as a Rotation.
 */
case class LatLonBox(north: Latitude, south: Latitude, east: Longitude, west: Longitude, rotation: Rotation)

/**
 * Companion object for the LatLonBox case class.
 * Provides implicit instances for Extractor and Renderer typeclasses to facilitate
 * parsing LatLonBox objects from XML nodes and rendering them to String representations.
 *
 * The implicit extractor uses `extractor50`, enabling the construction of a LatLonBox
 * from its five constituent members: north, south, east, west, and rotation.
 *
 * The implicit renderer uses `renderer5`, enabling the rendering of LatLonBox objects
 * by delegating to renderers for each of its five members.
 */
object LatLonBox extends Extractors with Renderers {
  implicit val extractor: Extractor[LatLonBox] = extractor50(apply)
  implicit val renderer: Renderer[LatLonBox] = renderer5(apply)

}
/**
 * Case class LineString which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#linearring LinearRing]]
 *
 * @param maybeTessellate the (optional) tessellation.
 * @param coordinates     a sequence of Coordinates objects.
 */
case class LinearRing(maybeTessellate: Option[Tessellate], coordinates: Seq[Coordinates])(val geometryData: GeometryData) extends Geometry

/**
 * Companion object for the LinearRing case class.
 *
 * This object provides extraction and rendering functionalities for LinearRing instances.
 * It defines an implicit extractor and renderer for working with LinearRing objects.
 * The extractorPartial method is used to create a partial extractor specific to the LinearRing type.
 * The extractor is responsible for extracting LinearRing instances using GeometryData.
 * The renderer is responsible for rendering LinearRing instances back into a specific representation.
 */
object LinearRing extends Extractors with Renderers {
  implicit val extractor: Extractor[LinearRing] =
    extractorPartial[GeometryData, LinearRing](extractorPartial11(apply)) ^^ "extractorLinearRing"
  implicit val renderer: Renderer[LinearRing] =
    renderer2Super(apply)(_.geometryData) ^^ "rendererLinearRing"
}

/**
 * Case class LineString which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#linestring LineString]]
 *
 * @param tessellate  the tessellation.
 * @param coordinates a sequence of Coordinates objects.
 */
case class LineString(tessellate: Tessellate, coordinates: Seq[Coordinates])(val geometryData: GeometryData) extends Geometry {

  import Coordinates.empty

  /**
   * Merges this `LineString` instance with the given `Geometry` instance.
   *
   * This method attempts to merge the attributes and data of the current `LineString` instance
   * with the provided `Geometry` instance if it is of type `LineString`.
   * The merging process involves combining tessellations, geometry data, and coordinate sequences,
   * with each step subject to specific merge rules.
   *
   * @param g         the `Geometry` instance to merge with.
   * @param mergeName a Boolean indicating whether to merge names (default is true).
   * @return an `Option` containing the merged `LineString` instance if successful, or `None` otherwise.
   */
  override def merge(g: Geometry, mergeName: Boolean = true): Option[Geometry] = g match {
    case l@LineString(_, _) =>
      for {
        t <- tessellate merge l.tessellate
        g <- geometryData merge l.geometryData
        c <- mergeSequence(coordinates)(empty).headOption
        d <- mergeSequence(l.coordinates)(empty).headOption
        z <- c merge d
      } yield LineString(t, Seq(z))(g)
  }

  /**
   * Returns an inverted version of this `LineString`, where the order of the coordinates
   * in each sequence is reversed.
   *
   * @return an `Option` containing the inverted `LineString` instance if successful.
   */
  override def invert: Option[LineString] = Some(LineString(tessellate, for (c <- coordinates) yield c.reverse)(geometryData))

}

/**
 * Object `LineString` serves as a companion to the `LineString` case class.
 * It provides extraction and rendering capabilities for `LineString` objects.
 *
 * The object includes implicit values for extraction and rendering to facilitate
 * seamless operations such as serialization, deserialization, and transformation
 * of `LineString` instances.
 *
 * This object is designed for internal configuration, such as connecting the
 * `Extractor` and `Renderer` abstractions with the `LineString` case class.
 */
object LineString extends Extractors with Renderers {
  implicit val extractor: Extractor[LineString] =
    extractorPartial[GeometryData, LineString](extractorPartial11(apply)) ^^ "extractorLineString"
  implicit val renderer: Renderer[LineString] =
    renderer2Super(apply)(_.geometryData) ^^ "rendererLineString"
  implicit val rendererSeq: Renderer[Seq[LineString]] =
    sequenceRenderer[LineString] ^^ "rendererLineStrings"
}

/**
 * Case class LineStyle which extends ColorStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#linestyle Line Style]]
 *
 * NOTE: the width is optional but I can't actually find an instance of LineStyle that doesn't have a width.
 *
 * @param maybeWidth     optional width of the line.
 * @param colorStyleData other properties.
 */
case class LineStyle(maybeWidth: Option[Width])(val colorStyleData: ColorStyleData) extends ColorStyle

/**
 * Companion object for the `LineStyle` case class, providing extractors and renderers.
 *
 * This object includes partial extractors, implicit extractors, and renderers for `LineStyle` instances.
 * It facilitates conversion to and from data structures in the context of `LineStyle`.
 *
 * Members:
 * - `extractorPartial`: Partial extractor for `ColorStyleData` */
object LineStyle extends Extractors with Renderers {

  implicit val extractor: Extractor[LineStyle] =
    extractorPartial[ColorStyleData, LineStyle](extractorPartial10(apply)) ^^ "extractorLineStyle"
  implicit val renderer: Renderer[LineStyle] =
    renderer1Super(apply)(_.colorStyleData) ^^ "rendererLineStyle"
  implicit val rendererOpt: Renderer[Option[LineStyle]] =
    renderer.lift ^^ "rendererOptionLineStyle"
}

/**
 * Represents a type of list item defined by the enumeration `ListItemTypeEnum`.
 *
 * The `ListItemType` case class encapsulates a value from `ListItemTypeEnum`, which defines
 * the behavior or attributes associated with various types of list items.
 *
 * This class is used in conjunction with the companion object and other utility traits to
 * facilitate rendering and extraction of `ListItemType` instances.
 *
 * @param $ The enumeration value specifying the type of list item. This value must be one
 *          of the predefined values in the `ListItemTypeEnum` object.
 */
case class ListItemType($: ListItemTypeEnum.Value)

/**
 * The `ListItemType` object provides extractors and renderers for the `ListItemType` case class.
 * It facilitates transformations to and from `ListItemType` instances.
 *
 * It includes implicit definitions for:
 * - `extractor`: Extracts `ListItemType` instances using the defined pattern.
 * - `extractorOpt`: Optionally extracts `ListItemType` instances.
 * - `renderer`: Converts `ListItemType` instances into their respective rendered form.
 * - `rendererOpt`: Renderers for optional `ListItemType` instances.
 */
object ListItemType extends Extractors with Renderers {

  private val listItemTypeExtractor: Extractor[ListItemType] = extractor10(apply) ^^ "listItemTypeExtractor"
  implicit val extractorOpt: Extractor[Option[ListItemType]] = listItemTypeExtractor.lift ^^ "extractOptionListItemType"
  implicit val rendererOpt: Renderer[Option[ListItemType]] = renderer1(apply).lift ^^ "rendererOptionListItemType"
}

/**
 * Case class ListStyle which extends ColorStyle.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#liststyle List Style]]
 *
 * NOTE According to the current KML reference, this object extends SubStyle or ColorStyle (it's not clear which).
 *
 * @param bgColor           optional background color (maybe it isn't optional if there's no color element).
 * @param maybeListItemType optional ListItemType.
 * @param maybeItemIcon     the display mode.
 * @param colorStyleData    the (auxiliary) color style properties.
 */
case class ListStyle(bgColor: BgColor, maybeListItemType: Option[ListItemType], maybeItemIcon: Option[ItemIcon])(val colorStyleData: ColorStyleData) extends ColorStyle

/**
 * Companion object for the `ListStyle` case class, providing extractors and renderers.
 *
 * This object includes functionality for extracting and rendering `ListStyle` instances,
 * utilizing partial extractors and renderers.
 * This allows seamless integration with
 * serialization or transformation frameworks.
 *
 * Members:
 * - `extractorPartial`: A partial extractor for converting `ColorStyleData` to `ListStyle`.
 * - `extractor`: An implicit extractor for `ListStyle`,
 * providing the ability to transform data into a `ListStyle` instance.
 * - `renderer`: An implicit renderer for `ListStyle`,
 * enabling transformation of `ListStyle` instances into a rendered format.
 */
object ListStyle extends Extractors with Renderers {
  implicit val extractor: Extractor[ListStyle] =
    extractorPartial[ColorStyleData, ListStyle](extractorPartial30(apply)) ^^ "extractorListStyle"
  implicit val renderer: Renderer[ListStyle] =
    renderer3Super(apply)(_.colorStyleData) ^^ "rendererListStyle"
}

/**
 * Case class representing a geographical longitude (an angle180).
 *
 * @param $ the longitude value in degrees as a Double.
 */
case class Longitude($: Double)

/**
 * Object representing Longitude functionalities, such as rendering and extraction.
 * Provides implicit definitions for rendering and extracting longitude values,
 * as well as optional longitude values.
 * Extends `Extractors` and `Renderers` to provide utility methods for longitude manipulation.
 */
object Longitude extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Longitude] = extractor10(apply) ^^ "extractorLongitude"
  implicit val extractorOpt: Extractor[Option[Longitude]] = extractor.lift ^^ "extractorOptionLongitude"
  implicit val renderer: Renderer[Longitude] = renderer1(apply) ^^ "rendererLongitude"
  implicit val rendererOpt: Renderer[Option[Longitude]] = renderer.lift ^^ "rendererOptionLongitude"
}

/**
 * Represents a "LookAt" view element in a KML document, describing a position to look at from a specific viewpoint,
 * with additional details like orientation, range, and optional altitude.
 * See [[https://developers.google.com/kml/documentation/kmlreference#lookat LookAt]]
 *
 * LookAt defines the geographic position (longitude, latitude), optional altitude, heading, tilt, and range
 * parameters for setting up a view in a geographic visualization context such as Google Earth.
 *
 * @param longitude         the longitude of the LookAt position in degrees.
 * @param latitude          the latitude of the LookAt position in degrees.
 * @param maybeAltitude     an optional altitude value for the LookAt position.
 * @param heading           the direction (in degrees) from the observer to the LookAt point.
 * @param tilt              the tilt angle (in degrees) of the view relative to the surface.
 * @param range             the distance (in meters) from the observer to the LookAt point.
 * @param maybeAltitudeMode an optional altitude mode specifying how the altitude is interpreted.
 * @param abstractViewData  additional data inherited from AbstractView.
 */
case class LookAt(longitude: Longitude, latitude: Latitude, maybeAltitude: Option[Altitude], heading: Heading, tilt: Tilt, range: Range, maybeAltitudeMode: Option[AltitudeMode])(val abstractViewData: AbstractViewData) extends AbstractView

/**
 * Companion object for the LookAt class, providing implicit extractors, renderers, and utilities
 * for working with LookAt instances.
 * LookAt represents a viewpoint in a KML visualization,
 * describing location, orientation, and viewing distance.
 *
 * It extends the functionality of Extractors and Renderers to allow for convenient serialization,
 * deserialization, and optional handling of LookAt objects.
 *
 * - `extractorPartial`: Defines a partial extractor for the LookAt class.
 * - `extractor`: An implicit extractor for deserializing LookAt instances.
 * - `extractorOpt`: An implicit extractor for handling optional LookAt instances.
 * - `renderer`: An implicit renderer for serializing LookAt instances.
 * - `rendererOpt`: An implicit renderer for serializing optional LookAt instances.
 */
object LookAt extends Extractors with Renderers {

  implicit val extractor: Extractor[LookAt] =
    extractorPartial[AbstractViewData, LookAt](extractorPartial70(apply)) ^^ "extractorLookAt"
  implicit val extractorOpt: Extractor[Option[LookAt]] =
    extractor.lift ^^ "extractorOptionLookAt"
  implicit val renderer: Renderer[LookAt] =
    renderer7Super(apply)(_.abstractViewData) ^^ "rendererLookAt"
  implicit val rendererOpt: Renderer[Option[LookAt]] =
    renderer.lift ^^ "rendererOptionLookAt"
}

/**
  * Case class MultiGeometry which extends Geometry: a collection of other Geometry elements
  * (Point, LineString, LinearRing, Polygon, and recursively MultiGeometry itself).
  *
  * See [[https://developers.google.com/kml/documentation/kmlreference#multigeometry MultiGeometry]]
  *
  * @param Geometry     the child Geometry elements. NOTE: capitalized (matching Placemark's
  *                     own field of the same type) rather than pluralized ("geometries"): a
  *                     plural-looking field name triggers extractChildren's stricter,
  *                     literal-tag-only lookup, which has no fallback to type-based matching -
  *                     this needs the fallback, since there's no literal "&lt;geometry&gt;" tag.
  * @param geometryData the other properties of the MultiGeometry.
  */
case class MultiGeometry(Geometry: Seq[Geometry])(val geometryData: GeometryData) extends Geometry

/**
  * Companion object for the case class MultiGeometry.
  *
  * This object provides utilities for working with the MultiGeometry type,
  * including extractors and renderers to handle serialization and parsing.
  *
  * Utilities:
  * - Provides an implicit extractor to parse MultiGeometry objects from GeometryData.
  * - Provides an implicit renderer for serializing MultiGeometry objects.
  * - Provides a renderer for sequences of MultiGeometry objects.
  */
object MultiGeometry extends Extractors with Renderers {
  // No deferral needed on this side: by the time anything here runs, Geometry.extractorSeq/
  // renderer are already fully constructed (see the NOTE there) - simple values holding
  // undeferred lambdas, safe to fetch eagerly.
  implicit val extractor: Extractor[MultiGeometry] =
    extractorPartial[GeometryData, MultiGeometry](extractorPartial01(apply)) ^^ "extractorMultiGeometry"
  implicit val renderer: Renderer[MultiGeometry] =
    renderer1Super(apply)(_.geometryData) ^^ "rendererMultiGeometry"
  implicit val rendererSeq: Renderer[Seq[MultiGeometry]] =
    sequenceRenderer[MultiGeometry] ^^ "rendererMultiGeometrys"
}

/**
 * Represents an `Open` state with a single boolean parameter.
 *
 * @param $ A boolean value indicating the open state.
 */
case class Open($: Boolean)

/**
 * Companion object for the `Open` case class.
 * Provides extractor and renderer instances for working with the `Open` type and `Option[Open]`.
 *
 * Mixes in functionality from the `Extractors` and `Renderers` traits to enable operations such as
 * extraction and rendering for instances of the `Open` class.
 */
object Open extends Extractors with Renderers {

  import Renderers.*

  private val openExtractor: Extractor[Open] = extractor10(apply) ^^ "openExtractor"
  implicit val extractorOpt: Extractor[Option[Open]] = openExtractor.lift ^^ "extractorOptionOpen"
  implicit val renderer: Renderer[Open] = renderer1(apply) ^^ "rendererOptionOpen"
  implicit val rendererOpt: Renderer[Option[Open]] = renderer.lift ^^ "rendererOptionOpen"
}

/**
 * Represents the outer boundary of a polygon in KML format.
 * This case class holds a `LinearRing`, which defines the coordinates
 * that create the outer boundary.
 * See [[https://developers.google.com/kml/documentation/kmlreference#outerboundaryis outerBoundaryIs]]
 *
 * @param LinearRing the `LinearRing` instance that specifies the coordinates
 *                   of the outer boundary of a polygon.
 */
case class OuterBoundaryIs(LinearRing: LinearRing)

/**
 * Companion object for the `OuterBoundaryIs` case class.
 * This object provides utilities for extracting and rendering `OuterBoundaryIs` instances.
 *
 * The `extractor` is an implicit value utilized for extracting `OuterBoundaryIs` instances,
 * and it uses the `extractor10` combinator along with the `apply` method of `OuterBoundaryIs`.
 *
 * The `renderer` is an implicit value used for rendering `OuterBoundaryIs` instances into
 * their corresponding KML representations.
 * It employs the `renderer1` combinator with the `apply` method */
object OuterBoundaryIs extends Extractors with Renderers {
  implicit val extractor: Extractor[OuterBoundaryIs] = extractor10(apply) ^^ "extractorOuterBoundaryIs"
  implicit val renderer: Renderer[OuterBoundaryIs] = renderer1(apply) ^^ "rendererOuterBoundaryIs"
}

/**
 * Outline.
 * NOTE that this is a boolean that is represented by 0 or 1.
 *
 * @param boolean whether to outline or not.
 */
case class Outline(boolean: Boolean)

/**
 * The `Outline` object provides implicit extractors and renderers for the `Outline` type.
 *
 * It facilitates the extraction and rendering of `Outline` objects and their `Option` counterparts.
 * The object extends `Extractors` and `Renderers` to leverage shared behavior for parsing and formatting.
 *
 * Implicit members include:
 * - An `Extractor` for `Outline` objects, allowing the transformation of data into an `Outline` instance.
 * - An `Extractor` for `Option[Outline]`, supporting optional */
object Outline extends Extractors with Renderers {

  import Renderers.*

  private val outlineExtractor: Extractor[Outline] = extractor10(apply) ^^ "outlineExtractor"
  implicit val extractorOpt: Extractor[Option[Outline]] = outlineExtractor.lift ^^ "extractOptionOutline"
  implicit val rendererOpt: Renderer[Option[Outline]] = renderer1(apply).lift ^^ "rendererOptionOutline"
}

/**
 *  Abstract Overlay element.
 *  Overlay is a subtype of Feature and a super-type of PhotoOverlay, ScreenOverlay, GroundOverlay.
 *  See [[https://developers.google.com/kml/documentation/kmlreference#overlay Overlay]].
 * The trait Overlay is extended, abstractly, by [[BaseOverlay]].
 * A Overlay has properties (color, maybeDrawOrder and Icon ) of its own.
 *  color The order of expression is aabbggrr in hexadecimal notation,
 *               where aa=alpha (00 to ff); bb=blue (00 to ff); gg=green (00 to ff); rr=red (00 to ff).
 * maybeDrawOrder This element defines the stacking order for the images in overlapping overlays.
 *  icon Defines the image associated with the Overlay.
 */
trait Overlay extends Feature

/**
 * Companion object for the Overlay trait, providing utilities for extraction and rendering of Overlay instances.
 *
 * The Overlay object leverages `Extractors` and `Renderers` to handle its subtypes,
 * which include GroundOverlay, PhotoOverlay, and ScreenOverlay.
 *
 * Key features:
 * - Implicit `extractorSeq` for extracting sequences of Overlay instances.
 * - Implicit `renderer` for rendering Overlay instances and its subtypes.
 */
object Overlay extends Extractors with Renderers {
  implicit val extractorSeq: MultiExtractor[Seq[Overlay]] =
    multiExtractor3[Overlay, (GroundOverlay, PhotoOverlay, ScreenOverlay), GroundOverlay, PhotoOverlay, ScreenOverlay]((go, po, so) => (go, po, so), Seq("GroundOverlay", "PhotoOverlay", "ScreenOverlay")) ^^"multiExtractorOverlay"
  implicit val renderer: Renderer[Overlay] = rendererSuper3[Overlay, PhotoOverlay, ScreenOverlay, GroundOverlay] ^^ "rendererOverlay"
}

/**
 * Abstract class `BaseOverlay` representing the base implementation for Overlay elements.
 * It extends the functionalities of the `Overlay` trait and the `HasName` trait.
 *
 * A `BaseOverlay` is associated with overlay-specific data defined by `OverlayData`.
 * It provides access to essential properties like `maybeColor`, `maybeDrawOrder`, `Icon`, and `name`.
 *
 * @constructor Creates a `BaseOverlay` instance with the specified overlay data.
 * @param overlayData The data associated with the overlay, encapsulated in the `OverlayData` instance.
 */
abstract class BaseOverlay(overlayData: OverlayData) extends Overlay with HasName {
  /**
   * Retrieves the optional color of the overlay.
   * The color is represented in hexadecimal notation as aabbggrr, where:
   * - aa: alpha (transparency) channel (00 to ff)
   * - bb: blue channel (00 to ff)
   * - gg: green channel (00 to ff)
   * - rr: red channel (00 to ff)
   *
   * @return An Option containing the color of the overlay if defined, otherwise None.
   */
  def maybeColor: Option[Color] = overlayData.maybeColor

  /**
   * Retrieves the optional draw order for the overlay.
   * The draw order defines the stacking order for images in overlapping overlays.
   * Higher values are drawn on top of lower values.
   *
   * @return An Option containing the draw order if defined, otherwise None.
   */
  def maybeDrawOrder: Option[DrawOrder] = overlayData.maybeDrawOrder

  /**
   * Retrieves the Icon associated with the Overlay.
   * The Icon defines the image representation of the Overlay.
   *
   * @return An Icon representing the image associated with the Overlay.
   */
  def Icon: Icon = overlayData.Icon

  /**
   * Retrieves the name as a `Text` value.
   *
   * @return the name represented as a `Text` instance.
   */
  def name: Text = overlayData.featureData.name
}

/**
 * Represents overlay data with associated properties including an icon, optional draw order,
 * optional color, and feature data. This case class is designed to encapsulate metadata
 * relevant to a KML (Keyhole Markup Language) overlay structure.
 *
 * @param Icon           the associated icon representing the overlay.
 * @param maybeDrawOrder an optional draw order, determining the rendering order of the overlay.
 * @param maybeColor     an optional color value for overlay customization.
 * @param featureData    the associated feature data providing contextual metadata.
 */
case class OverlayData(Icon: Icon, maybeDrawOrder: Option[DrawOrder], maybeColor: Option[Color])(val featureData: FeatureData)

/**
 * Provides utilities for extracting and rendering `OverlayData` instances. This object includes
 * an `Extractor` and `Renderer` for handling `OverlayData` serialization and deserialization
 * processes and serves as a companion to the `OverlayData` case class.
 *
 * `OverlayData` encapsulates overlay-specific metadata such as an icon, optional draw order,
 * optional color, and associated feature data. The provided extractors and renderers aim to
 * simplify working with data transformation and rendering tasks in context with `OverlayData`.
 *
 * - `extractorPartial`: Defines a partial extractor for transforming `FeatureData` into
 * `OverlayData`. It is a composition of utility methods supporting specialized extraction logic.
 * - `extractor`: An implicit full extractor taking advantage of `extractorPartial` to deserialize
 * `OverlayData` instances.
 * - `renderer`: An implicit renderer for serializing `OverlayData` objects, designed to include
 * relevant metadata for rendering tasks.
 */
object OverlayData extends Extractors with Renderers {
  implicit val extractor: Extractor[OverlayData] =
    extractorPartial[FeatureData, OverlayData](extractorPartial30(apply)) ^^ "extractorOverlayData"
  implicit val renderer: Renderer[OverlayData] =
    renderer3Super(apply)(_.featureData) ^^ "rendererOverlayData"
}

/**
 * Represents a 2D overlay point with defined coordinates and their corresponding units.
 *
 * @param _x      The x-coordinate value.
 * @param _y      The y-coordinate value.
 * @param _xunits The unit of measurement for the x-coordinate, defined in the UnitsEnum enumeration.
 * @param _yunits The unit of measurement for the y-coordinate, defined in the UnitsEnum enumeration.
 */
case class OverlayXY(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value)

/**
 * Companion object for the OverlayXY case class, providing implicit extractor and renderer instances.
 *
 * This object facilitates the serialization and deserialization of OverlayXY instances
 * by utilizing predefined extractor and renderer functionalities.
 *
 * It mixes in the Extractors and Renderers traits to provide support for data extraction and rendering.
 */
object OverlayXY extends Extractors with Renderers {
  implicit val extractor: Extractor[OverlayXY] = extractor40(apply) ^^ "extractorOverlayXY"
  implicit val renderer: Renderer[OverlayXY] = renderer4(apply) ^^ "rendererOverlayXY"
}

/**
 * A case class representing a key-style URL pair mapping in the KML document context.
 *
 * This class encapsulates a `Key` and a `StyleURL` to represent associations typically used
 * in KML processing frameworks for styling and representation of geographic data.
 *
 * @param key      the symbolic identifier as a `Key`, encapsulating a character sequence.
 * @param styleUrl the associated style reference as a `StyleURL`, providing a mapping to style definitions.
 */
case class Pair(key: Key, styleUrl: StyleURL)

/**
 * Companion object for the `Pair` case class, providing utilities for extraction and rendering.
 *
 * The `Pair` object extends `Extractors` and `Renderers`, offering implicit implementations
 * for extracting and rendering instances of `Pair`, as well as handling sequences of `Pair`.
 *
 * Implicit Utilities:
 * - `extractor`: Provides a mechanism to extract a single `Pair` instance.
 * - `extractorSeq`: Facilitates extraction of a sequence of `Pair` objects.
 * - `renderer`: Defines the rendering logic for a single `Pair` instance.
 * - `rendererSeq`: Handles the rendering of a sequence of `Pair` instances.
 */
object Pair extends Extractors with Renderers {

  implicit val extractor: Extractor[Pair] = extractor20(apply) ^^ "extractorPair"
  implicit val extractorSeq: MultiExtractor[Seq[Pair]] = multiExtractorBase[Pair](Positive) ^^ "multiExtractorPair"
  implicit val renderer: Renderer[Pair] = renderer2(apply) ^^ "rendererPair"
  implicit val rendererSeq: Renderer[Seq[Pair]] = sequenceRenderer[Pair] ^^ "rendererPairs"
}

/**
 * Represents a specialized overlay element that adds a photo overlay to a geographical location.
 * See [[https://developers.google.com/kml/documentation/kmlreference#photooverlay PhotoOverlay]]
 * The `PhotoOverlay` class is a concrete implementation of the `BaseOverlay` abstract class, combining additional
 * properties such as rotation and point location with the base overlay features from `OverlayData`.
 *
 * A `PhotoOverlay` specifies the placement and orientation of an image overlay in geographic space, allowing the image
 * to be placed over a specific location with a defined rotation. It is constructed with `Rotation`, `Point`, and
 * `OverlayData` parameters.
 *
 * @constructor Creates a new `PhotoOverlay` instance.
 * @param rotation the rotation angle of the photo overlay, represented as a `Rotation`.
 * @param ViewVolume Defines how much of the current scene is visible.
 * @param ImagePyramid a hierarchical set of images,
 *                     each of which is an increasingly lower resolution version of the original image.
 * @param Point the geographical position for the photo overlay, represented as a `Point`.
 * @param shape The PhotoOverlay is projected onto the <shape>.
 *              The <shape> can be one of the following:
 *              rectangle (default) - for an ordinary photo
 *              cylinder - for panoramas, which can be either partial or full cylinders
 *              sphere - for spherical panoramas
 * @param overlayData The data associated with the overlay, encapsulated in the `OverlayData` instance.
 */
case class PhotoOverlay(rotation: Rotation, ViewVolume: ViewVolume, ImagePyramid: ImagePyramid, Point: Point, shape: Shape)(val overlayData: OverlayData) extends BaseOverlay(overlayData)

/**
 * Object `PhotoOverlay` provides extractors and renderers for the case class `PhotoOverlay`.
 * It includes functionality for extracting, rendering, and handling sequences of `PhotoOverlay` instances.
 *
 * This object serves as a utility companion to the `PhotoOverlay` type.
 */
object PhotoOverlay extends Extractors with Renderers {
  implicit val extractor: Extractor[PhotoOverlay] =
    extractorPartial(extractorPartial50(apply)) ^^ "extractorPhotoOverlay"
  implicit val renderer: Renderer[PhotoOverlay] =
    renderer5Super(apply)(_.overlayData) ^^ "renderPhotoOverlay"
  implicit val renderSeq: Renderer[Seq[PhotoOverlay]] =
    sequenceRenderer[PhotoOverlay] ^^ "rendererPhotoOverlays"
}

/**
 * Placemark: subtype of Feature.
 * See [[https://developers.google.com/kml/documentation/kmlreference#placemark Placemark]].
 *
 * CONSIDER restricting the number of Geometry elements to one.
 *
 * @param Geometry    a sequence of Geometry elements (where Geometry is an abstract super-type).
 * @param featureData the (auxiliary) FeatureData, shared by sub-elements.
 */
case class Placemark(Geometry: Seq[Geometry])(val featureData: FeatureData) extends Feature with HasName with Mergeable[Placemark] with Invertible[Placemark] {

  /**
   * Inverts the current Placemark instance by reversing the orientation or nature
   * of its underlying geometric structure, if applicable.
   * This is achieved by attempting to invert each associated Geometry.
   * TESTME
   *
   * @return an Option containing a new Placemark with inverted Geometries,
   *         or None if inversion is not possible.
   */
  def invert: Option[Placemark] = {
    val gos: Seq[Option[Geometry]] = for (g <- Geometry) yield g.invert
    val gso: Option[Seq[Geometry]] = FP.sequence(gos)
    for (gs <- gso) yield Placemark(gs)(featureData)
  }

  /**
   * Merge this mergeable object with <code>t</code>.
   *
   * @param t the object to be merged with this.
   * @return the merged value of T.
   */
  infix def merge(t: Placemark, mergeName: Boolean = true): Option[Placemark] = {
    logger.info(s"joinPlacemarks: $name, ${t.name} with mergeName=$mergeName")
    val los: Seq[Option[Geometry]] = for (gp <- this.Geometry; gq <- t.Geometry) yield gp.merge(gq)
    val gs: Seq[Geometry] = los filter (_.isDefined) map (_.get)
    for (fd <- featureData.merge(t.featureData, mergeName)) yield Placemark(gs)(fd)
  }

  override def toString: String = s"Placemark: name=${name.$} with ${Geometry.size} geometries: $Geometry"

  /**
   * Retrieves the name of the feature data.
   *
   * @return The name of the feature as a `Text` instance.
   */
  def name: Text = featureData.name

  /**
   * Method to edit this Feature, given a sequence of sibling Features such that we may be able to combine features.
   * TESTME
   *
   * @param e  the edit which may (or may not) apply to <code>this</code>.
   * @param fs a sequence of Features which are the children of <code>this</code>'s family (including <code>p</code> itself).
   * @return an optional Feature.
   */
  def editToOptionOption(e: KmlEdit, fs: Seq[Feature]): Option[Option[Feature]] = e.operands match {
    case 1 => editMatching1(e)
    case 2 => editMatchingPlacemark2(e, fs)
  }

  /**
   * Method to edit the given Placemark with zero additional Features.
   * TESTME
   *
   * @param e the edit which may (or may not) apply to <code>this</code>.
   * @return an optional Feature.
   */
  private def editMatching1(e: KmlEdit) = (name, e) match {
    case (name, KmlEdit(KmlEdit.DELETE, _, Element(_, nameToMatch), None))
      if name.matches(nameToMatch) =>
      logger.info(s"delete: $nameToMatch")
      Some(None)
    case (_, KmlEdit(KmlEdit.DELETE, _, _, _)) =>
      Some(Some(this))
    case (_, KmlEdit(KmlEdit.INVERT, _, Element(_, nameToMatch), _))
      if name.matches(nameToMatch) =>
      Some(this.invert)
    case _ =>
      None
  }

  /**
   * Method to process the given Placemark with one additional Features.
   *
   * @param e  the edit which may (or may not) apply to <code>p</code>.
   * @param fs a sequence of Features which are the children of <code>p</code>'s family (including <code>p</code> itself).
   * @return an optional optional Feature.
   */
  private def editMatchingPlacemark2(e: KmlEdit, fs: Seq[Feature]) =
    e match {
      case KmlEdit(command@(JOIN | JOINX), _, Element("Placemark", nameToMatch1), Some(Element("Placemark", nameToMatch2)))
        if name.matches(nameToMatch1) =>
        Some(joinMatchedPlacemarks(fs, nameToMatch2, command == JOIN))
      case _ =>
        None
    }

  /**
   * Method to join two Placemarks together.
   *
   * @param fs          the potential features to be joined with <code>p</code>. These are the siblings of <code>p</code> itself.
   * @param nameToMatch the name of the feature to be joined, as defined by the edit.
   * @return an optional Feature which, if defined, is the new Placemark to be used instead of <code>p</code>.
   */
  private def joinMatchedPlacemarks(fs: Seq[Feature], nameToMatch: String, mergeName: Boolean) = {
    val zz = for (f <- fs if f != this) yield joinMatchingPlacemarks(nameToMatch, f, mergeName)
    for (z <- zz.find(_.isDefined); q <- z) yield q
  }

  /**
   * Attempts to join a matching `Placemark` associated with the provided `Feature` based on the specified name.
   * If the feature is a `Placemark` and its name matches the given string, it attempts to merge this `Placemark`
   * with the given one.
   * Otherwise, returns `None`.
   *
   * @param name      the name to match against the `Placemark`'s name.
   * @param feature   the `Feature` which may or may not be a `Placemark` to attempt merging.
   * @param mergeName a flag indicating whether to merge the names of the two `Placemark` objects during the merge operation.
   * @return an optional merged `Placemark` or `None` if the names do not match or the feature is not a `Placemark`.
   */
  private def joinMatchingPlacemarks(name: String, feature: Feature, mergeName: Boolean) = feature match {
    case q: Placemark if q.name.matches(name) => merge(q, mergeName)
    // FIXME Issue #20 can result in this Placemark being lost if name doesn't match q.name
    case _ => None
  }
}

/**
 * The companion object for the `Placemark` class, providing extractors, renderers, and various operations
 * associated with `Placemark` instances. This object extends traits for extracting, rendering,
 * and handling sequences of `Placemark` objects.
 *
 * It includes implicit instances of extractors and renderers for processing `Placemark` objects
 * and their sequences. The companion object facilitates functionality such as deserialization,
 * serialization, and manipulation of `Placemark` instances.
 *
 * Members:
 * - `extractorPartial`: A partial extractor for transforming `FeatureData` into a `Placemark`.
 * - `extractor`: An implicit full extractor for `Placemark` instances, utilizing the `extractorPartial`.
 * - `renderer`: An implicit renderer for converting `Placemark` objects into a specific representation.
 * - `rendererSeq`: An implicit renderer for handling sequences of `Placemark` instances.
 */
object Placemark extends Extractors with Renderers {
  implicit val extractor: Extractor[Placemark] =
    extractorPartial[FeatureData, Placemark](extractorPartial01(apply)) ^^ "extractorPlacemark"
  implicit val renderer: Renderer[Placemark] =
    renderer1Super(apply)(_.featureData) ^^ "rendererPlacemark"
  implicit val rendererSeq: Renderer[Seq[Placemark]] =
    sequenceRenderer[Placemark] ^^ "rendererPlacemarks"
}

/**
 * Case class Point which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#point Point]]
 *
 * @param coordinates  a sequence of Coordinates objects.
 * @param geometryData the other properties of the Point.
 */
case class Point(coordinates: Seq[Coordinates])(val geometryData: GeometryData) extends Geometry

/**
 * Companion object for the case class Point.
 *
 * This object provides utilities for working with the Point type,
 * including extractors and renderers to handle serialization and parsing.
 *
 * Utilities:
 * - Provides an implicit extractor to parse Point objects from GeometryData.
 * - Provides an implicit renderer for serializing Point objects.
 * - Provides a renderer for sequences of Point objects.
 */
object Point extends Extractors with Renderers {
  implicit val extractor: Extractor[Point] =
    extractorPartial[GeometryData, Point](extractorPartial01(apply)) ^^ "extractorPoint"
  implicit val renderer: Renderer[Point] =
    renderer1Super(apply)(_.geometryData) ^^ "rendererPoint"
  implicit val rendererSeq: Renderer[Seq[Point]] =
    sequenceRenderer[Point] ^^ "rendererPoints"
}

/**
 * Case class Polygon which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#polygon Polygon]]
 *
 * @param maybeTessellate the tessellation.
 * @param outerBoundaryIs an OuterBoundaryIs object.
 * @param innerBoundaryIs a sequence of InnerBoundaryIs objects.
 */
case class Polygon(maybeTessellate: Option[Tessellate], outerBoundaryIs: OuterBoundaryIs, innerBoundaryIs: Seq[InnerBoundaryIs])(val geometryData: GeometryData) extends Geometry

/**
 * Companion object for the `Polygon` case class.
 *
 * Provides extractors and renderers that are useful for working with the `Polygon` type.
 * Specifically, it allows for extracting `Polygon` instances from `GeometryData` and rendering
 * `Polygon` instances to their desired outputs.
 *
 * This object extends `Extractors` and `Renderers`, enabling integration with extraction
 * and rendering functionalities of the system.
 *
 * Members:
 *
 * - `extractorPartial`: A private partial extractor specifically for handling `Polygon` extraction logic.
 * - `extractor`: An implicit `Extractor[Polygon]` that utilizes `extractorPartial` for complete extraction functionality.
 * - `renderer`: An implicit `Renderer[Polygon]` that allows rendering a `Polygon` while leveraging its geometry data.
 */
object Polygon extends Extractors with Renderers {
  implicit val extractor: Extractor[Polygon] =
    extractorPartial[GeometryData, Polygon](extractorPartial21(apply)) ^^ "extractorPolygon"
  implicit val renderer: Renderer[Polygon] =
    renderer3Super(apply)(_.geometryData) ^^ "rendererPolygon"
}

/**
 * Case class PolyStyle which extends ColorStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#polystyle Poly Style]]
 *
 * @param maybeFill      optional value of fill.
 * @param maybeOutline   optional value of outline.
 * @param colorStyleData the (auxiliary) color style properties.
 */
case class PolyStyle(maybeFill: Option[Fill], maybeOutline: Option[Outline])(val colorStyleData: ColorStyleData) extends ColorStyle

/**
 * Companion object for PolyStyle which provides extractor and renderer utilities.
 * This object combines Extractors and Renderers traits to facilitate the transformation
 * and rendering of PolyStyle instances.
 *
 * Members:
 * - extractorPartial: Extractor for transforming ColorStyleData into PolyStyle.
 * - extractor: Implicit Extractor instance for PolyStyle.
 * - renderer: Implicit Renderer instance for PolyStyle.
 */
object PolyStyle extends Extractors with Renderers {
  implicit val extractor: Extractor[PolyStyle] =
    extractorPartial[ColorStyleData, PolyStyle](extractorPartial20(apply)) ^^ "extractorPolyStyle"
  implicit val renderer: Renderer[PolyStyle] =
    renderer2Super(apply)(_.colorStyleData) ^^ "rendererPolyStyle"
}

/**
 * A case class representing a Range with a single value.
 *
 * @param $ the value representing the range.
 */
case class Range($: Double)

/**
 * Companion object for the `Range` case class.
 * Provides utility functions for extracting and rendering `Range` objects.
 *
 * This object mixes in the `Extractors` and `Renderers` traits, enabling
 * serialization and deserialization functionality using implicit extractor
 * and renderer instances.
 */
object Range extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Range] = extractor10(apply) ^^ "extractorRange"
  implicit val renderer: Renderer[Range] = renderer1(apply) ^^ "rendererRange"
}

/**
 * RefreshMode which has values "onChange" , "onInterval" or "onExpire".
 * Used by Link, Icon.
 *
 * @param $ the mode of Refresh
 */
case class RefreshMode($: RefreshModeEnum.Value)

/**
 * Companion object for the RefreshMode case class.
 * Provides extractors and renderers for handling RefreshMode instances.
 *
 * Contains implicit values for:
 * - Extracting a RefreshMode instance.
 * - Extracting an optional RefreshMode instance.
 * - Rendering a RefreshMode instance.
 * - Rendering an optional RefreshMode instance.
 */
object RefreshMode extends Extractors with Renderers {

  implicit val extractor: Extractor[RefreshMode] = extractor10(apply) ^^ "extractRefreshMode"
  implicit val extractorOpt: Extractor[Option[RefreshMode]] = extractor.lift ^^ "extractOptionRefreshMode"
  implicit val renderer: Renderer[RefreshMode] = renderer1(apply) ^^ "rendererRefreshMode"
  implicit val rendererOpt: Renderer[Option[RefreshMode]] = renderer1(apply).lift ^^ "rendererOptionRefreshMode"
}

/**
 * A case class representing RefreshVisibility in KML documents.
 *
 * This case class encapsulates a `Boolean` value that signifies RefreshVisibility attributes in KML data.
 * RefreshVisibility settings often dictate when the NetworkLink is refreshed, whether a specific KML element should be displayed in a viewer.
 */
case class RefreshVisibility($: Boolean)

/**
 * Companion object for the `RefreshVisibility` case class.
 * TESTME
 *
 * This object provides implicits and utilities for serializing and deserializing `RefreshVisibility` instances.
 * It extends traits `Extractors` and `Renderers` to support extraction and rendering functionalities.
 *
 * Implicit extractors and renderers are provided for both `RefreshVisibility` and `Option[RefreshVisibility]`, enabling their usage
 * in data processing pipelines that require standardized extraction or rendering logic.
 */
object RefreshVisibility extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[RefreshVisibility] = extractor10(apply) ^^ "extractorRefreshVisibility"
  implicit val extractorOpt: Extractor[Option[RefreshVisibility]] = extractor.lift ^^ "extractorOptionRefreshVisibility"
  implicit val renderer: Renderer[RefreshVisibility] = renderer1(apply) ^^ "rendererRefreshVisibility"
  implicit val rendererOpt: Renderer[Option[RefreshVisibility]] = renderer1(apply).lift ^^ "rendererOptionRefreshVisibility"
}

/**
 * Represents the roll angle in degrees or radians, depending on the specific context of usage.
 *
 * The `Roll` class is a wrapper for a `Double` value that signifies the roll angle.
 * Instances of this class can be used in contexts that involve angle measurements, transformations, or calculations.
 *
 * @param $ the numeric value of the roll angle in degrees or radians.
 */
case class Roll($: Double)

/**
 * The `Roll` object provides utility methods and implicits for working with roll angles.
 *
 * This object extends both `Extractors` and `Renderers` and provides functionality for extracting
 * and rendering `Roll` instances.
 * It contains implicit values to facilitate these operations,
 * allowing seamless transformations between `Roll` objects and other representations.
 *
 * Implicit Values:
 * - `extractor`: An implicit `Extractor[Roll]` instance for extracting roll values.
 * - `renderer`: An implicit `Renderer[Roll]` instance for rendering roll values.
 */
object Roll extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Roll] = extractor10(apply) ^^ "extractorRoll"
  implicit val renderer: Renderer[Roll] = renderer1(apply) ^^ "rendererRoll"
}

/**
 * Case class representing a rotation (an angle180).
 *
 * @param $ the rotation value in degrees as a Double.
 */
case class Rotation($: Double)

/**
 * Object representing Rotation functionalities, such as rendering and extraction.
 * Of type angle180.
 */
object Rotation extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Rotation] = extractor10(apply) ^^ "extractorRotation"
  implicit val extractorOpt: Extractor[Option[Rotation]] = extractor.lift ^^ "extractorOptionRotation"
  implicit val renderer: Renderer[Rotation] = renderer1(apply) ^^ "rendererRotation"
  implicit val rendererOpt: Renderer[Option[Rotation]] = renderer.lift ^^ "rendererOptionRotation"
}

/**
 * Represent Point relative to the screen about which the screen overlay is rotated.
 *
 * @param _x the X value, according to the units specified in _xunits.
 * @param _y the Y value, according to the units specified in _yunits.
 * @param _xunits Units in which the x value is specified.
 *                A value of "fraction" indicates the x value is a fraction of the image.
 *                A value of "pixels" indicates the x value in pixels.
 *                A value of "insetPixels" indicates the indent from the right edge of the image.
 * @param _yunits Units in which the y value is specified.
 *                A value of "fraction" indicates the y value is a fraction of the image.
 *                A value of "pixels" indicates the y value in pixels.
 *                A value of "insetPixels" indicates the indent from the top edge of the image.
 */
case class RotationXY(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value)

/**
 * Object RotationXY provides implicit extractor and renderer instances for the RotationXY case class.
 *
 * RotationXY represents a point relative to the screen about which the screen overlay is rotated. The extractor
 * and renderer facilitate the serialization and deserialization of the RotationXY data type.
 *
 * The extractor is implemented as an `Extractor[RotationXY]`, which handles the deserialization
 * of the RotationXY case class from a structured source.
 *
 * The renderer is implemented as a `Renderer[RotationXY]`, which manages the serialization
 * of the RotationXY case class for output.
 */
object RotationXY extends Extractors with Renderers {
  implicit val extractor: Extractor[RotationXY] = extractor40(apply) ^^ "extractorRotationXY"
  implicit val renderer: Renderer[RotationXY] = renderer4(apply) ^^ "rendererRotationXY"
}

/**
 * Scale element: subelement of Object in the Kml reference.
 * Case class to represent a Scale which is represented in XML as, for example: <scale>1.1</scale>
 * See [[https://developers.google.com/kml/documentation/kmlreference#scale Scale]]
 *
 * @param $ the value of the scale (a Double).
 */
case class Scale($: Double)(val kmlData: KmlData) extends KmlObject

/**
 * Companion object for the Scale case class. Provides extractors, renderers, and utility methods
 * to facilitate the creation and manipulation of Scale objects in the Kml context.
 *
 * The Scale object is used to define the scale factor for the Kml representation. It manages both
 * serialization and deserialization processes between KmlData and Scale instances using provided
 * extractors and renderers.
 *
 * Functionality includes:
 * - Extractors to derive Scale objects from KmlData.
 * - Renderers for transforming Scale instances into their Kml representations.
 * - Utility factory methods for creating Scale objects.
 */
object Scale extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Scale] = extractorPartial[KmlData, Scale](extractorPartial10(apply)) ^^ "extractorScale"
  implicit val extractorOpt: Extractor[Option[Scale]] = extractor.lift ^^ "extractOptionScale"
  implicit val renderer: Renderer[Scale] = renderer1Super(apply)(_.kmlData) ^^ "rendererScale"
  implicit val rendererOpt: Renderer[Option[Scale]] = renderer.lift ^^ "rendererOptionScale"

  /**
   * Creates a new instance of the Scale class using the specified value and the `KmlData.nemo` context.
   *
   * @param x the value of the scale, represented as a Double.
   * @return a new instance of the Scale class configured with the provided value and `KmlData.nemo`.
   */
  def nemo(x: Double): Scale = new Scale(x)(KmlData.nemo)
}

/**
 * Case class SchemaData: sub-element of ExtendedData, providing typed name/value pairs whose
 * types are defined by a Schema element elsewhere in the document (that Schema definition itself
 * is not modeled here).
 * See [[https://developers.google.com/kml/documentation/kmlreference#extendeddata SchemaData]]
 *
 * @param _schemaUrl a reference to the Schema which defines this SchemaData's fields (an XML attribute).
 * @param SimpleData a sequence of typed name/value pairs: Seq[SimpleData].
 */
case class SchemaData(_schemaUrl: CharSequence, SimpleData: Seq[SimpleData])

/**
 * Companion object for the `SchemaData` case class, providing extractors and renderers for
 * `SchemaData` instances and sequences thereof.
 */
object SchemaData extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[SchemaData] = extractor11(apply) ^^ "extractorSchemaData"
  implicit val extractorSeq: MultiExtractor[Seq[SchemaData]] = multiExtractorBase[SchemaData](NonNegative) ^^ "multiExtractorSchemaData"
  implicit val renderer: Renderer[SchemaData] = renderer2(apply) ^^ "rendererSchemaData"
  implicit val rendererSeq: Renderer[Seq[SchemaData]] = sequenceRenderer[SchemaData] ^^ "rendererSchemaDatas"
}

/**
 * Represents a screen overlay, which combines graphical data with specific positional and rotation details.
 * See [[https://developers.google.com/kml/documentation/kmlreference#screenoverlay ScreenOverlay]]
 *
 * @constructor
 * Constructs a `ScreenOverlay` instance.
 * @param overlayXY an OverlayXY
 * @param screenXY a ScreenXY
 * @param rotationXY a RotationXY
 * @param size a Size
 * @param maybeRotation an optional Rotation
 * @param overlayData The data associated with the overlay, encapsulated in the `OverlayData` instance.
 */
case class ScreenOverlay(overlayXY: OverlayXY, screenXY: ScreenXY, rotationXY: RotationXY, size: Size, maybeRotation: Option[Rotation])(val overlayData: OverlayData) extends BaseOverlay(overlayData)

/**
 * Companion object for the `ScreenOverlay` class
 * providing utilities for extraction and rendering of ScreenOverlay objects.
 *
 * The companion object includes:
 *
 * - A partial extractor for `OverlayData` to `ScreenOverlay`.
 * - An implicit extractor to parse `ScreenOverlay` instances.
 * - An implicit renderer to render `ScreenOverlay` instances.
 * - An implicit renderer for sequences of `ScreenOverlay` instances.
 *
 * These utilities facilitate operations like deserialization, serialization, and transformation of `ScreenOverlay` objects,
 * and are intended to support working with structured representations of screen overlays.
 */
object ScreenOverlay extends Extractors with Renderers {
  implicit val extractor: Extractor[ScreenOverlay] = extractorPartial(extractorPartial50(apply)) ^^ "extractorScreenOverlay"
  implicit val renderer: Renderer[ScreenOverlay] = renderer5Super(apply)(_.overlayData) ^^ "renderScreenOverlay"
  implicit val renderSeq: Renderer[Seq[ScreenOverlay]] = sequenceRenderer[ScreenOverlay] ^^ "rendererScreenOverlays"
}

/**
 * Represent Specifies a point relative to the screen origin that the overlay image is mapped to.
 * used by ScreenOverlay.
 *
 * @param _x Either the number of pixels, a fractional component of the screen,
 *           or a pixel inset indicating the x component of a point on the screen.
 * @param _y Either the number of pixels, a fractional component of the screen,
 *           or a pixel inset indicating the y component of a point on the screen.
 * @param _xunits Units in which the x value is specified.
 *                A value of "fraction" indicates the x value is a fraction of the screen.
 *                A value of "pixels" indicates the x value in pixels.
 *                A value of "insetPixels" indicates the indent from the right edge of the screen.
 * @param _yunits Units in which the y value is specified.
 *                A value of "fraction" indicates the y value is a fraction of the screen.
 *                A value of "pixels" indicates the y value in pixels.
 *                A value of "insetPixels" indicates the indent from the top edge of the screen.
 */
case class ScreenXY(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value)

/**
 * Companion object for the `ScreenXY` case class that provides implicit extractors
 * and renderers for the `ScreenXY` type.
 *
 * This object includes:
 * - An implicit `Extractor` instance for parsing or extracting `ScreenXY` objects.
 * - An implicit `Renderer` instance for rendering `ScreenXY` objects into a specific format.
 *
 * It simplifies the process of transforming `ScreenXY` to and from other representations.
 */
object ScreenXY extends Extractors with Renderers {
  implicit val extractor: Extractor[ScreenXY] = extractor40(apply) ^^ "extractorScreenXY"
  implicit val renderer: Renderer[ScreenXY] = renderer4(apply) ^^ "rendererScreenXY"
}

/**
 * Represents a shape based on the `ShapeEnum.Value` enumeration.
 *
 * This case class encapsulates a single shape value, which can represent predefined
 * shapes such as `rectangle`, `cylinder`, or `sphere`.
 * The `shape` parameter is of type `Shapes.Value`,
 * which is a value from the `Shapes` enumeration.
 *
 * CONSIDER renaming shape as $ (for consistency with other similar Kml objects).
 *
 * This class integrates with the `Extractors` and `Renderers` frameworks to support:
 * - Automatic extraction using an implicit `Extractor[Shape]`.
 * - Custom rendering of shape instances through an implicit `Renderer[Shape]`.
 *
 * The companion object for this class provides the necessary extractor and renderer
 * implementations for seamless integration.
 */
case class Shape($: ShapeEnum.Value)

/**
 * Companion object for the `Shape` case class.
 *
 * This object provides integration with the `Extractors` and `Renderers` frameworks
 * by defining the necessary implicit instances for `Extractor` and `Renderer` specific
 * to the `Shape` type.
 *
 * - The `extractor` is an implicit instance of `Extractor[Shape]`, enabling the automatic
 * extraction of `Shape` instances.
 * - The `renderer` is an implicit instance of `Renderer[Shape]`, enabling the customized
 * rendering of `Shape` instances.
 */
object Shape extends Extractors with Renderers {
  implicit val extractor: Extractor[Shape] = extractor10(apply) ^^ "extractorShape"
  implicit val renderer: Renderer[Shape] = renderer1(apply) ^^ "rendererShape"
}

/**
 * Case class SimpleData: sub-element of SchemaData, a single typed name/value pair.
 * See [[https://developers.google.com/kml/documentation/kmlreference#extendeddata SimpleData]]
 *
 * @param _name the name of this SimpleData element, referencing a SimpleField of the
 *              corresponding Schema (an XML attribute).
 * @param $     the value, as the text content of this element.
 */
case class SimpleData(_name: CharSequence, $: CharSequence)

/**
 * Companion object for the `SimpleData` case class, providing extractors and renderers for
 * `SimpleData` instances and sequences thereof.
 */
object SimpleData extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[SimpleData] = extractor20(apply) ^^ "extractorSimpleData"
  implicit val extractorSeq: MultiExtractor[Seq[SimpleData]] = multiExtractorBase[SimpleData](NonNegative) ^^ "multiExtractorSimpleData"
  implicit val renderer: Renderer[SimpleData] = renderer2(apply) ^^ "rendererSimpleData"
  implicit val rendererSeq: Renderer[Seq[SimpleData]] = sequenceRenderer[SimpleData] ^^ "rendererSimpleDatas"
}

/**
 * Represents the dimensions of a given entity with units for both x and y components.
 * A value of −1 indicates to use the native dimension
 * A value of 0 indicates to maintain the aspect ratio
 * A value of n sets the value of the dimension
 *
 * @param _x      The measurement value along the x-axis.
 * @param _y      The measurement value along the y-axis.
 * @param _xunits The units in which the x-axis measurement is expressed.
 * @param _yunits The units in which the y-axis measurement is expressed.
 */
case class Size(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value)

/**
 * Companion object for the `Size` case class.
 * Provides implicit instances of `Extractor` and `Renderer` for the `Size` type.
 * The `Extractor` allows parsing and extracting `Size` values from a data source.
 * The `Renderer` facilitates rendering `Size` values into a specific output format.
 */
object Size extends Extractors with Renderers {
  implicit val extractor: Extractor[Size] = extractor40(apply) ^^ "extractorSize"
  implicit val renderer: Renderer[Size] = renderer4(apply) ^^ "rendererSize"
}
/**
 * State
 * CONSIDER this should be an enumerated type with values: open, closed, error, fetching0, fetching1, or fetching2
 *
 * @param $ the value.
 */
case class State($: ItemIconModeEnum.Value)

/**
 * Provides utility functions and implicits for the `State` case class.
 *
 * State is considered to potentially represent an enumerated type with values
 * such as: open, closed, error, fetching0, fetching1, or fetching2.
 *
 * This object extends Extractors and Renderers to leverage reusable components for
 * deserialization (extractors) and serialization (renderers).
 *
 * Implicit components include:
 *  - An extractor for deserializing a `State`.
 *  - A renderer for serializing a `State`.
 *  - A renderer for serializing an optional `State`.
 */
object State extends Extractors with Renderers {

  implicit val extractor: Extractor[State] = extractor10(apply) ^^ "extractorState"
  implicit val renderer: Renderer[State] = renderer1(apply) ^^ "rendererState"
  implicit val rendererOpt: Renderer[Option[State]] = renderer.lift ^^ "rendererOptionState"
}

/**
 * Case class Style which extends StyleSelector.
 * See [[https://developers.google.com/kml/documentation/kmlreference#style Style]]
 *
 * It seems there are two completely different types of Style element, but they are not distinguished.
 * Type A has IconStyle, LabelStyle, BalloonStyle;
 * Type B has LineStyle.
 *
 * @param Styles a set of different types of Style.
 *               CONSIDER constraining this set to be distinct.
 */
case class Style(Styles: Seq[SubStyle])(val styleSelectorData: StyleSelectorData) extends StyleSelector

/**
 * Style object that provides utilities for extracting and rendering `Style` objects.
 * This object extends functionality provided by `Extractors` and `Renderers`.
 *
 * Extraction and rendering are achieved using partial extractors and renderers that align
 * with the handling of `StyleSelectorData` and `Style` instances.
 *
 * Members:
 * - `extractorPartial`: Defines a partial extractor for mapping `StyleSelectorData` to `Style`.
 * - `extractor`: An implicit full extractor for `Style` built from `extractorPartial`.
 * - `renderer`: An implicit full renderer for `Style` that uses its `styleSelectorData`.
 * - `rendererSeq`: An implicit renderer to handle sequences of `Style` objects.
 */
object Style extends Extractors with Renderers {
  implicit val extractor: Extractor[Style] =
    extractorPartial[StyleSelectorData, Style](extractorPartial01(apply)) ^^ "extractorStyle"
  implicit val renderer: Renderer[Style] =
    renderer1Super(apply)(_.styleSelectorData) ^^ "rendererStyle"
  implicit val rendererSeq: Renderer[Seq[Style]] =
    sequenceRenderer[Style] ^^ "rendererStyles"
}

/**
 * Case class StyleMap represents a KML StyleMap element, which acts as a mapping of key-value pairs,
 * where keys ('normal' or 'highlight') are mapped to specific styles using style URLs.
 * See [[https://developers.google.com/kml/documentation/kmlreference#stylemap StyleMap]]
 *
 * StyleMap is a subtype of StyleSelector, and it extends its functionality by providing a mechanism
 * to dynamically switch between styles based on the user interaction.
 *
 * @constructor Creates an instance of StyleMap with the given sequence of Pairs and a StyleSelectorData instance.
 * @param Pairs             a sequence of key-value pairs, where each key is an instance of Key (e.g., "normal", "highlight")
 *                          and each value is an instance of StyleURL pointing to the respective style.
 * @param styleSelectorData an instance of StyleSelectorData that associates this StyleMap with additional KML metadata.
 */
case class StyleMap(Pairs: Seq[Pair])(val styleSelectorData: StyleSelectorData) extends StyleSelector

/**
 * The `StyleMap` object provides utility methods and implicit definitions for working with the `StyleMap` class.
 * It includes extractors and renderers that facilitate parsing and formatting `StyleMap` instances.
 *
 * Features:
 * - Partial and full extractors for converting between `StyleSelectorData` and `StyleMap`.
 * - Implicit renderers for serializing `StyleMap` instances and sequences of `StyleMap` objects.
 */
object StyleMap extends Extractors {

  import KmlRenderers.*

  implicit val extractor: Extractor[StyleMap] =
    extractorPartial[StyleSelectorData, StyleMap](extractorPartial01(apply)) ^^ "extractorStyleMap"
  implicit val renderer: Renderer[StyleMap] =
    renderer1Super(apply)(_.styleSelectorData) ^^ "rendererStyleMap"
  implicit val rendererSeq: Renderer[Seq[StyleMap]] =
    sequenceRenderer[StyleMap] ^^ "rendererStyleMaps"
}

/**
 * Trait StyleSelector is a sub-trait of KmlObject.
 * It is extended by Style and StyleMap.
 * See [[https://developers.google.com/kml/documentation/kmlreference#styleselector StyleSelector]]
 */
trait StyleSelector extends KmlObject

/**
 * Companion object of StyleSelector
 * that provides extractors and renderers for the `StyleSelector` trait and its subtypes.
 *
 * The `StyleSelector` trait is a sub-trait of `KmlObject` and is extended by `Style` and `StyleMap`.
 * This object includes implicit definitions for extraction and rendering of `StyleSelector` instances
 * and sequences of `StyleSelector` instances.
 * These operations are used to transform KML `StyleSelector`
 * elements into their corresponding internal representations and vice versa.
 *
 * Included Implicit Definitions:
 * - `extractorSeq`: Extractor for sequences of `StyleSelector` instances.
 * Combines multiple elements,
 * such as `StyleMap` and `Style`, into a sequence.
 * - `renderer`: Renderer for an individual `StyleSelector` instance.
 * Transforms a single `StyleSelector`into a renderable form.
 * - `rendererSeq`: Renderer for sequences of `StyleSelector` instances. Transforms a list of
 * `StyleSelector` into a renderable form.
 */
object StyleSelector extends Extractors with Renderers {
  implicit val extractorSeq: MultiExtractor[Seq[StyleSelector]] =
    multiExtractor2[StyleSelector, (StyleMap, Style), StyleMap, Style]((s, m) => (s, m), Seq("StyleMap", "Style")) ^^ "multiExtractorStyleSelector"
  implicit val renderer: Renderer[StyleSelector] =
    rendererSuper2[StyleSelector, Style, StyleMap] ^^ "rendererStyleSelector"
  implicit val rendererSeq: Renderer[Seq[StyleSelector]] =
    sequenceRenderer[StyleSelector] ^^ "rendererStyleSelectors"
}

/**
 * Case class StyleSelectorData: properties of StyleSelector and its subclasses.
 * There are no properties specific to StyleSelector.
 * See [[https://developers.google.com/kml/documentation/kmlreference#styleselector StyleSelector]]
 *
 * @param kmlData the KmlData reference.
 */
case class StyleSelectorData(kmlData: KmlData)

/**
 * Object StyleSelectorData.
 *
 * A companion object for the `StyleSelectorData` case class, providing methods and implicits
 * for extracting and rendering `StyleSelectorData` instances.
 * It extends the `Extractors`
 * and `Renderers` traits to leverage their functionality for data extraction and
 * rendering operations.
 *
 * This object includes:
 * - An `Extractor` for converting KmlData into `StyleSelectorData`.
 * - A `Renderer` for rendering `StyleSelectorData` into its corresponding output representation.
 */
object StyleSelectorData extends Extractors with Renderers {
  private val applyFunction: KmlData => StyleSelectorData = new StyleSelectorData(_)
  private val styleSelectorExtractor: Extractor[KmlData => StyleSelectorData] = extractorPartial0[KmlData, StyleSelectorData](applyFunction) ^^ "styleSelectorExtractor"
  implicit val extractor: Extractor[StyleSelectorData] =
    extractorPartial[KmlData, StyleSelectorData](styleSelectorExtractor) ^^ "extractorStyleSelectorData"
  implicit val renderer: Renderer[StyleSelectorData] =
    renderer0Super(applyFunction)(_.kmlData) ^^ "rendererStyleSelectorData"
}

/**
 * A case class representing a style URL in the KML document context.
 *
 * This class encapsulates a character sequence
 * that serves as the identifier or reference to a style within a KML structure.
 * It provides a way to interact with style URLs within a KML processing framework.
 */
case class StyleURL($: CharSequence)

/**
 * Companion object for the `StyleURL` class, providing extractors and renderers for `StyleURL` instances.
 *
 * This object extends the `Extractors` and `Renderers` traits, enabling the extraction and rendering of `StyleURL`
 * objects in the context of data transformation and representation.
 *
 * Implicit members:
 * - `extractor`: Provides functionality for extracting `StyleURL` instances using the `extractor10` method.
 * - `renderer`: Provides functionality for rendering `StyleURL` instances using the `renderer1` method.
 */
object StyleURL extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[StyleURL] = extractor10(apply) ^^ "extractorStyleURL"
  implicit val renderer: Renderer[StyleURL] = renderer1(apply) ^^ "rendererStyleURL"
}

/**
 * Trait SubStyle which, according to the KML reference extends Object and is extended by ColorStyle, BalloonStyle and ListStyle.
 * Reality seems otherwise, however.
 * See [[https://developers.google.com/kml/documentation/kmlreference KML]]
 */
trait SubStyle extends KmlObject

/**
 * Companion object for the SubStyle trait, providing utilities for extracting and rendering
 * instances of SubStyle and sequences of SubStyle.
 *
 * This object includes implicit values for extractors and renderers, which support:
 * - Extracting SubStyle and its subtypes using a `MultiExtractor`.
 * - Rendering instances of SubStyle and sequences of SubStyle using `Renderer`.
 *
 * The extractors and renderers are specifically tailored for the properties and subtypes of SubStyle,
 * such as BalloonStyle, ListStyle, PolyStyle, LineStyle, IconStyle, and LabelStyle.
 *
 * Note: Consider invoking ColorStyle when utilizing this object.
 */
object SubStyle extends Extractors with Renderers {
  // CONSIDER invoking ColorStyle
  implicit val extractorSeq: MultiExtractor[Seq[SubStyle]] =
    multiExtractor6[SubStyle, (BalloonStyle, ListStyle, PolyStyle, LineStyle, IconStyle, LabelStyle), BalloonStyle, ListStyle, PolyStyle, LineStyle, IconStyle, LabelStyle](
      (p1, p2, p3, p4, p5, p6) => (p1, p2, p3, p4, p5, p6), Seq("BalloonStyle", "ListStyle", "PolyStyle", "LineStyle", "IconStyle", "LabelStyle")
    ) ^^ "multiExtractorSubStyle"
  implicit val renderer: Renderer[SubStyle] =
    rendererSuper6[SubStyle, IconStyle, ListStyle, BalloonStyle, LabelStyle, LineStyle, PolyStyle] ^^ "rendererSubStyle"
  implicit val rendererSeq: Renderer[Seq[SubStyle]] =
    sequenceRenderer[SubStyle] ^^ "rendererSubStyles"
}

/**
 * Represents a substyle data container, which encapsulates KML (Keyhole Markup Language) data.
 * This class wraps an instance of KmlData, allowing additional processing or organization of KML metadata.
 *
 * @param kmlData an instance of KmlData containing metadata for the substyle.
 */
case class SubStyleData(kmlData: KmlData)

/**
 * Companion object for the SubStyleData case class.
 *
 * Provides functionality for extracting and rendering SubStyleData instances.
 * Includes various utilities to work with SubStyleData in the context of KML processing,
 * such as extractors and renderers for the structured data representation.
 *
 * The object is built upon the Extractors and Renderers traits, which allow for functional composition
 * while interacting with KmlData.
 * It defines an implicit extractor for parsing SubStyleData,
 * and an implicit renderer for converting SubStyleData back into a format suitable for output or transmission.
 */
object SubStyleData extends Extractors with Renderers {
  private val applyFunction: KmlData => SubStyleData = new SubStyleData(_)
  implicit val extractor: Extractor[SubStyleData] =
    extractorPartial[KmlData, SubStyleData](extractorPartial0(applyFunction)) ^^ "extractorSubStyleData"
  implicit val renderer: Renderer[SubStyleData] =
    renderer0Super(apply)(_.kmlData) ^^ "rendererSubStyleData"
}

/**
 * Tessellate represents a case class with a Boolean value, extending the Mergeable trait.
 *
 * Instances of Tessellate can be merged using the `merge` method, provided the internal
 * Boolean state is compatible. The merge operation will return a new Tessellate instance
 * encapsulated in an Option if the merge criteria are satisfied, otherwise it returns None.
 *
 * CONSIDER making this part of GeometryData.
 *
 * @param $ a Boolean value encapsulated by the Tessellate instance.
 */
case class Tessellate($: Boolean) extends Mergeable[Tessellate] {
  infix def merge(t: Tessellate, mergeName: Boolean = true): Option[Tessellate] = ($, t.$) match {
    case (a, b) if a == b => Some(Tessellate(a))
    case _ => None
  }
}

/**
 * Object Tessellate provides extractors and renderers for instances of the Tessellate case class.
 *
 * This object contains implicit definitions for converting Tessellate instances
 * to and from various representations using Extractor and Renderer utilities.
 * It facilitates parsing and generation of Tessellate representations in a structured manner.
 */
object Tessellate extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Tessellate] = extractor10(apply) ^^ "extractorTessellate"
  implicit val extractorOpt: Extractor[Option[Tessellate]] = extractor.lift ^^ "extractorOptionTessellate"
  implicit val renderer: Renderer[Tessellate] = renderer1(apply) ^^ "rendererTessellate"
  implicit val rendererOpt: Renderer[Option[Tessellate]] = renderer.lift ^^ "rendererOptionTessellate"
}

/**
 * TextColor
 * Used by BalloonStyle.
 *
 * @param $ a hexadecimal string that defines the color of Text.
 */
case class TextColor($: Hex4)

/**
 * The TextColor object provides extractor and renderer capabilities for the TextColor case class.
 * It enables parsing, rendering, and optional handling of TextColor instances.
 *
 * This object includes the following:
 *   - An implicit extractor for parsing TextColor instances.
 *   - An implicit extractor for optional TextColor instances.
 *   - An implicit renderer for rendering TextColor instances.
 *   - An implicit renderer for rendering optional TextColor instances.
 *
 * TextColor is used to represent color information, often in contexts related to BalloonStyle.
 */
object TextColor extends Extractors with Renderers {

  private val extractor: Extractor[TextColor] = extractor10(apply) ^^ "textColorExtractor"
  implicit val extractorOpt: Extractor[Option[TextColor]] = extractor.lift ^^ "extractOptionTextColor"
  implicit val rendererOpt: Renderer[Option[TextColor]] = renderer1(apply).lift ^^ "rendererOptionTextColor"
}

/**
 * Represents a tilt with a single numeric value.
 *
 * This case class is utilized to model the notion of tilt in a KML or similar domain representation.
 * It includes a single parameter `$` which stores the tilt value.
 * The class is utilized along with its
 * companion object that provides implicit extractors and renderers for serialization and deserialization purposes.
 */
case class Tilt($: Double)

/**
 * Companion object for the Tilt case class.
 *
 * Provides implicit extractors and renderers for the Tilt type, allowing for convenient
 * serialization and deserialization of Tilt instances.
 * This includes support for optional Tilt values.
 * The extractors and renderers are defined in terms of the base traits and
 * utilities provided, ensuring seamless integration with parsing and rendering pipelines.
 */
object Tilt extends Extractors with Renderers {

  import Renderers.*

  implicit val extractor: Extractor[Tilt] = extractor10(apply) ^^ "extractorTilt"
  implicit val extractorOpt: Extractor[Option[Tilt]] = extractor.lift ^^ "extractorOptionTilt"
  implicit val renderer: Renderer[Tilt] = renderer1(apply) ^^ "rendererTilt"
  implicit val rendererOpt: Renderer[Option[Tilt]] = renderer.lift ^^ "rendererOptionTilt"
}

/**
 * ViewRefreshMode which has values "never" , "onStop", "onRequest" or "onRegion".
 * Used by Link, Icon.
 *
 * @param $ the mode of View Refresh
 */
case class ViewRefreshMode($: ViewRefreshEnum.Value)


/**
 * Companion object for the ViewRefreshMode case class.
 * Provides extractors and renderers for handling ViewRefreshMode instances.
 *
 * Contains implicit values for:
 * - Extracting a ViewRefreshMode instance.
 * - Extracting an optional ViewRefreshMode instance.
 * - Rendering a ViewRefreshMode instance.
 * - Rendering an optional ViewRefreshMode instance.
 */
object ViewRefreshMode extends Extractors with Renderers {

  private val extractor: Extractor[ViewRefreshMode] = extractor10(apply) ^^ "viewRefreshModeExtractor"
  implicit val extractorOpt: Extractor[Option[ViewRefreshMode]] = extractor.lift ^^ "extractMaybeViewRefreshMode"
  implicit val rendererOpt: Renderer[Option[ViewRefreshMode]] = renderer1(apply).lift ^^ "rendererOptionViewRefreshMode"
}

/**
 * Represent how much of the current scene is visible.
 * Specifying the field of view is analogous to specifying the lens opening in a physical camera.
 * A small field of view, like a telephoto lens, focuses on a small part of the scene.
 * A large field of view, like a wide-angle lens, focuses on a large part of the scene.
 * Used by PhotoOverlay.
 *
 * @param leftFov Angle, in degrees, between the camera's viewing direction and the left side of the view volume.
 * @param rightFov Angle, in degrees, between the camera's viewing direction and the right side of the view volume.
 * @param bottomFov Angle, in degrees, between the camera's viewing direction and the bottom side of the view volume.
 * @param topFov Angle, in degrees, between the camera's viewing direction and the top side of the view volume.
 * @param near Measurement in meters along the viewing direction from the camera viewpoint to the PhotoOverlay shape.
 */
case class ViewVolume(leftFov:Longitude, rightFov: Longitude, bottomFov: Latitude, topFov: Latitude, near: Double)

/**
 * Companion object for the ViewVolume case class.
 * Provides implicit instances for Extractor and Renderer typeclasses to facilitate
 */
object ViewVolume extends Extractors with Renderers {
  implicit val extractor: Extractor[ViewVolume] = extractor50(apply) ^^ "extractorViewVolume"
  implicit val renderer: Renderer[ViewVolume] = renderer5(apply) ^^ "rendererViewVolume"
}

/**
 * A case class representing visibility in KML documents.
 *
 * This case class encapsulates a `Boolean` value that signifies visibility attributes in KML data.
 * Visibility settings often dictate whether a specific KML element should be displayed in a viewer.
 */
case class Visibility($: Boolean)

/**
 * Companion object for the `Visibility` case class.
 *
 * This object provides implicits and utilities for serializing and deserializing `Visibility` instances.
 * It extends traits `Extractors` and `Renderers` to support extraction and rendering functionalities.
 *
 * Implicit extractors and renderers are provided for both `Visibility` and `Option[Visibility]`, enabling their usage
 * in data processing pipelines that require standardized extraction or rendering logic.
 */
object Visibility extends Extractors with Renderers {

  import Renderers.*

  private val extractor: Extractor[Visibility] = extractor10(apply) ^^ "visibilityExtractor"
  implicit val extractorOpt: Extractor[Option[Visibility]] = extractor.lift ^^ "extractorOptionVisibility"
  implicit val rendererOpt: Renderer[Option[Visibility]] = renderer1(apply).lift ^^ "rendererOptionVisibility"
}

/**
 * A case class representing the width of an object.
 *
 * @param $ the width value as a Double
 */
case class Width($: Double)

/**
 * The `Width` object provides essential functionality for extracting and rendering `Width` case class instances.
 *
 * This object includes implicit definitions for extractors and renderers,
 * enabling seamless operations like parsing and formatting `Width` instances.
 *
 * It extends both `Extractors` and `Renderers` traits,
 * leveraging their functionality to define utilities for the `Width` type.
 *
 * Implicit extractors allow extracting `Width` instances or optional `Width` instances from data sources.
 * Implicit renderers enable
 * creating representations of `Width` instances or optional `Width` instances in a particular format.
 */
object Width extends Extractors with Renderers {

  import Renderers.*

  private val extractor: Extractor[Width] = extractor10(apply) ^^ "widthExtractor"
  implicit val extractorOpt: Extractor[Option[Width]] = extractor.lift ^^ "extractMaybeWidth"
  implicit val rendererOpt: Renderer[Option[Width]] = renderer1(apply).lift ^^ "rendererOptionWidth"
}
