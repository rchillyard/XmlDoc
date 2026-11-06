package com.phasmidsoftware.xmldoc.kml

import com.phasmidsoftware.xmldoc.core.*
import com.phasmidsoftware.xmldoc.kml.HasFeatures.editHasFeaturesToOption
import com.phasmidsoftware.xmldoc.kml.KmlEdit.{JOIN, JOINX}
import com.phasmidsoftware.xmldoc.core.Mergeable.{mergeOptions, mergeOptionsBiased, mergeSequence, mergeStringsDelimited}
import com.phasmidsoftware.xmldoc.render.*
import com.phasmidsoftware.xmldoc.render.Renderers.{booleanRenderer, doubleRenderer, enumAttributeRenderer, enumObjectRenderer, intRenderer}
import com.phasmidsoftware.xmldoc.xml.*
import com.phasmidsoftware.xmldoc.xml.MultiExtractorBase.Positive

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
case class KmlData(__id: Option[String]) extends Mergeable[KmlData] derives Extractor, Renderer {

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
object KmlData {

  def nemo: KmlData = KmlData(None)
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
 * - clampToSeaFloor, relativeToSeaFloor: gx: namespace extensions (see the `gx:altitudeMode`
 * alias registered in AltitudeMode's companion object) - relative to the sea floor rather than
 * the ground, for underwater features. Not part of plain KML's own altitudeMode enum.
 *
 * This object extends the capabilities of Enumeration to include Extractors and Renderers.
 *
 * Implicit members:
 * - extractor: Provides an Extractor instance for extracting values of AltitudeModeEnum.
 * - renderer: Provides a Renderer instance for rendering values of AltitudeModeEnum.
 *
 */
object AltitudeModeEnum extends Enumeration with Extractors with Renderers {
  val clampToGround, relativeToGround, absolute, clampToSeaFloor, relativeToSeaFloor = Value
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
sealed trait AbstractView extends KmlObject

/**
 * Companion object for `AbstractView`.
 * Its `MultiExtractor[Seq[AbstractView]]` and `Renderer[AbstractView]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 */
object AbstractView {

  implicit val extractorSeq: MultiExtractor[Seq[AbstractView]] = MultiExtractor.derived ^^ "multiExtractorAbstractView"
  implicit val renderer: Renderer[AbstractView] = Renderer.derived ^^ "rendererAbstractView"
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
 * Companion object for `AbstractViewData`.
 * Its only field, `kmlData`, is extracted from (and rendered into) the same element, as an auxiliary parameter is
 * (see `Extractor.embedded` and `Renderer.embedded`).
 */
object AbstractViewData {

  // NOTE: kmlData is extracted from (and rendered into) the same element, as an auxiliary parameter is.
  implicit val extractor: Extractor[AbstractViewData] = Extractor.embedded[KmlData, AbstractViewData](apply) ^^ "extractorAbstractViewData"
  implicit val renderer: Renderer[AbstractViewData] = Renderer.embedded[AbstractViewData, KmlData](_.kmlData) ^^ "rendererAbstractViewData"
}

/**
 * Case class Alias: sub-element of `ResourceMap`, mapping a texture reference used within a
 * `Model`'s COLLADA file (`sourceHref`) to where that resource actually is within the KML/KMZ
 * package (`targetHref`).
 * See [[https://developers.google.com/kml/documentation/kmlreference#model Model]]
 *
 * @param maybeTargetHref the actual location of the resource: Option[Text].
 * @param maybeSourceHref the texture reference as it appears in the COLLADA file: Option[Text].
 */
case class Alias(maybeTargetHref: Option[Text], maybeSourceHref: Option[Text]) derives Extractor, Renderer

/**
 * Case class representing an altitude value.
 * See [[https://developers.google.com/kml/documentation/kmlreference#altitude Altitude]]
 *
 * @param $ the altitude value, typically measured in meters.
 */
case class Altitude($: Double) derives Extractor, Renderer

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
case class AltitudeMode($: AltitudeModeEnum.Value) derives Extractor, Renderer

/**
 * Companion object for the AltitudeMode case class, providing extractors and renderers.
 *
 * This object is used for handling extraction and rendering operations related to AltitudeMode instances.
 *
 * It defines implicit values for extractors and renderers, enabling seamless interaction
 * with AltitudeMode objects in the context of data transformation and rendering pipelines.
 */
object AltitudeMode {

  // Issue #29: Google Earth's gx: namespace extension uses gx:altitudeMode, rather than plain
  // altitudeMode, specifically to carry the sea-floor-relative values (see AltitudeModeEnum) -
  // e.g. altitudemode_reference.kml. Rendered output always uses the plain tag, regardless of
  // which one was originally read; that's consistent with every other alias registered via
  // TagProperties.addAliases elsewhere in this codebase.
  TagProperties.addAliases("altitudeMode", Seq("gx:altitudeMode"))
}

/**
 * Case class AxisScale: a generic, single-value scale factor along one axis, used by `ModelScale`
 * for its `x`/`y`/`z` children.
 *
 * NOTE: see the comment on `RefreshInterval` for why this is wrapped rather than a bare `Double`.
 *
 * @param $ the scale factor.
 */
case class AxisScale($: Double) derives Extractor, Renderer

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
case class BalloonStyle(text: Text, maybeBgColor: Option[BgColor], maybeTextColor: Option[TextColor], maybeDisplayMode: Option[DisplayMode])(val colorStyleData: ColorStyleData) extends ColorStyle derives Extractor, Renderer

/**
 * This case class represents a background color element with a single parameter.
 * Used by `BalloonStyle` and `ListStyle`.
 * See [[https://developers.google.com/kml/documentation/kmlreference#bgcolor bgColor]]
 *
 * @param $ a hexadecimal string that defines the background color.
 */
case class BgColor($: Hex4) derives Extractor, Renderer

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
case class Camera(long: Longitude, lat: Latitude, alt: Altitude, heading: Heading, tilt: Tilt, roll: Roll, maybeAltitudeMode: Option[AltitudeMode])(val abstractViewData: AbstractViewData) extends AbstractView derives Extractor, Renderer

/**
 * Case class to define Color.
 * An element of ColorStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#colorstyle ColorStyle]]
 *
 * @param $ the color as a hexadecimal string.
 */
case class Color($: Hex4) derives Extractor, Renderer

/**
 * Case class to model ColorMode.
 * See [[https://developers.google.com/kml/documentation/kmlreference#colormode ColorMode]]
 *
 * `ColorMode` is defined using the `ColorModeEnum` enumeration, which specifies the
 * possible modes for handling colors. The two primary modes are:
 * - `normal`: Represents no effect or a base condition.
 * - `random`: Applies a random linear scale to the base color.
 */
case class ColorMode($: ColorModeEnum.Value) derives Extractor, Renderer

/**
 * Class to define ColorStyle which extends SubStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#colorstyle ColorStyle]]
 *
 * TODO add ColorStyleData
 *
 */
sealed trait ColorStyle extends SubStyle

/**
 * Companion object for `ColorStyle`.
 * Its `MultiExtractor[Seq[ColorStyle]]` and `Renderer[ColorStyle]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 *
 * TODO BalloonStyle and ListStyle don't belong here according to the KML Reference. ColorStyle is a sub-class of SubStyle.
 * TESTME
 */
object ColorStyle {

  implicit val extractorSeq: MultiExtractor[Seq[ColorStyle]] = MultiExtractor.derived ^^ "multiExtractorColorStyle"
  implicit val renderer: Renderer[ColorStyle] = Renderer.derived ^^ "rendererColorStyle"
}

/**
 * Case class ColorStyleData to represent the data associated with all ColorStyle elements.
 *
 * @param maybeColor     an optional Color.
 * @param maybeColorMode an optional ColorMode.
 * @param subStyleData   the SubStyleData.
 */
case class ColorStyleData(maybeColor: Option[Color], maybeColorMode: Option[ColorMode])(val subStyleData: SubStyleData) derives Extractor, Renderer

/**
 * Abstract Container element.
 * Container is a subtype of Feature and a super-type of Folder, Document.
 * See [[https://developers.google.com/kml/documentation/kmlreference#container Container]].
 *
 * A Container has no properties of its own.
 */
sealed trait Container extends Feature

/**
 * Companion object for `Container`.
 * Its `MultiExtractor[Seq[Container]]` and `Renderer[Container]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 */
object Container {

  implicit val extractorSeq: MultiExtractor[Seq[Container]] = MultiExtractor.derived ^^ "multiExtractorContainer"
  implicit val renderer: Renderer[Container] = Renderer.derived ^^ "rendererContainer"
}

/**
 * Properties of a Container (and therefore all its subtypes).
 *
 * @param featureData (auxiliary) member: FeatureData, shared by sub-elements.
 */
case class ContainerData(featureData: FeatureData)

/**
 * Companion object for `ContainerData`.
 * Its only field, `featureData`, is extracted from (and rendered into) the same element, as an auxiliary parameter is
 * (see `Extractor.embedded` and `Renderer.embedded`).
 */
object ContainerData {

  // NOTE: featureData is extracted from (and rendered into) the same element, as an auxiliary parameter is.
  implicit val extractor: Extractor[ContainerData] = Extractor.embedded[FeatureData, ContainerData](apply) ^^ "extractorContainerData"
  implicit val renderer: Renderer[ContainerData] = Renderer.embedded[ContainerData, FeatureData](_.featureData) ^^ "rendererContainerData"
}

/**
 * Represents a sequence of `Coordinate` objects and provides operations for merging, reversing, and computing
 * geometric properties such as gaps and directions.
 * See [[https://developers.google.com/kml/documentation/kmlreference#coordinates Linear Ring coordinates]]
 *
 * @constructor Creates an instance of `Coordinates` with the specified sequence of `Coordinate` objects.
 * @param coordinates A sequence of `Coordinate` objects representing this collection.
 */
case class Coordinates(coordinates: Seq[Coordinate]) extends Mergeable[Coordinates] derives Renderer {

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
   * The result is oriented the same way as `this` (this's own direction is authoritative and it
   * always comes first) - `other` is attached at whichever of its own ends is closer to this
   * one's last coordinate, reversing it first if that yields a closer join (Issue #54: previously,
   * this only ever compared the two straight concatenations, this++other vs other++this, so a
   * pair of lines that should join end-to-end or start-to-start - needing a reversal - was never
   * actually found).
   *
   * @param other     the other `Coordinates` instance to be merged with this instance.
   * @param mergeName a boolean indicating whether to merge the names of the two `Coordinates` instances. Defaults to true.
   * @return an `Option` containing the resulting merged `Coordinates`, or `None` if the merge cannot be performed.
   */
  infix def merge(other: Coordinates, mergeName: Boolean = true): Option[Coordinates] = {
    KMLCompanion.logger.info(s"merge $this with $other")
    Some(Coordinates(Mergeable.joinOrdered(coordinates, other.coordinates)((a, b) => a distance b)))
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
case class Data(_name: CharSequence, maybeDisplayName: Option[Text], value: Text) derives Extractor, Renderer

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
case class DisplayMode($: DisplayModeEnum.Value) derives Extractor, Renderer

/**
 * Case class Document: sub-element of Container.
 * See [[https://developers.google.com/kml/documentation/kmlreference#document Document]].
 *
 * TODO add Schemas to this case class
 *
 * @param features      a sequence of Features.
 * @param containerData ContainerData (auxiliary property).
 */
case class Document(features: Seq[Feature])(val containerData: ContainerData) extends Container with HasFeatures with HasName derives Extractor, Renderer {
  /**
   * Retrieves the name property from the feature data encapsulated within the container data.
   *
   * @return The name as a `Text` instance.
   */
  def name: Text = containerData.featureData.name

  override def toString: String = s"Document: name=${name.$} with ${features.size} features"
}

/**
 * Represents a draw order value as an Int.
 * See [[https://developers.google.com/kml/documentation/kmlreference#draworder draworder]]
 *
 */
case class DrawOrder($: Int) derives Extractor, Renderer


/**
 * Case class ExtendedData: optional sub-element of Feature, providing custom data in addition to
 * the standard KML fields.
 * See [[https://developers.google.com/kml/documentation/kmlreference#extendeddata ExtendedData]]
 *
 * @param Data       a sequence of untyped name/value pairs: Seq[Data].
 * @param SchemaData a sequence of Schema-typed name/value pairs: Seq[SchemaData].
 */
case class ExtendedData(Data: Seq[Data], SchemaData: Seq[SchemaData]) derives Extractor, Renderer

/**
 * Class Extrude which represents a `Boolean`.
 * Used by `LineString` and `Polygon`.
 * Similar to `Tessellate`--and in fact they often go together.
 * See [[https://developers.google.com/kml/documentation/kmlreference#extrude Linear Ring extrude]]
 *
 * @param $ the value.
 */
case class Extrude($: Boolean) derives Extractor, Renderer

/**
 * Trait Feature: abstract sub-element of KmlObject.
 * Feature is a sub-type of Object and a super-type of Placemark, Container.
 * See [[https://developers.google.com/kml/documentation/kmlreference#feature Feature]].
 *
 * TODO add Overlay, NetworkLink.
 */
sealed trait Feature extends KmlObject {

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
 * Companion object for `Feature`.
 * Its `MultiExtractor[Seq[Feature]]` and `Renderer[Feature]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 */
object Feature {

  implicit val extractorSeq: MultiExtractor[Seq[Feature]] = MultiExtractor.derived ^^ "multiExtractorFeature"
  implicit val renderer: Renderer[Feature] = Renderer.derived ^^ "rendererFeature"
}

/**
 * Properties of a Feature (and therefore all its sub-types).
 *
 * CONSIDER redefining abstractView so that it can have only one optional AbstractView (according to KML spec.)
 *
 * @param name             the name (a Text).
 * @param maybeDescription an optional description: Option[Text].
 * @param maybeStyleUrl    an optional style URL: Option[StyleURL].
 * @param maybeOpen        an optional openness designation: Option[Int].
 * @param maybeExtendedData an optional ExtendedData: Option[ExtendedData].
 * @param maybeTimePrimitive an optional TimeStamp or TimeSpan, kept generic (see GenericElement) since
 *                            TimePrimitive is an abstract type and its real tag is never literally
 *                            "TimePrimitive": Option[GenericElement].
 * @param StyleSelectors   a sequence of StyleSelectors: Seq[StyleSelector].
 * @param kmlData          (auxiliary) member: KmlData.
 */
case class FeatureData(name: Text, maybeDescription: Option[Text], maybeStyleUrl: Option[StyleURL], maybeOpen: Option[Open], maybeVisibility: Option[Visibility], maybeExtendedData: Option[ExtendedData], maybeTimePrimitive: Option[GenericElement], StyleSelectors: Seq[StyleSelector], abstractView: Seq[AbstractView])(val kmlData: KmlData) extends Mergeable[FeatureData] with HasName derives Extractor, Renderer {
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
    } yield FeatureData(n, d, maybeStyleUrl, maybeOpen, maybeVisibility, maybeExtendedData, maybeTimePrimitive, StyleSelectors, abstractView)(z) // TODO: not all fields are properly merged
  }
}

/**
 * Companion object for `FeatureData` (its `Extractor` and `Renderer` are derived: see `derives` on the case class).
 */
object FeatureData {

  // TimePrimitive's real tag is TimeStamp or TimeSpan, never "timePrimitive"/"TimePrimitive" -
  // see doExtractField's optional case in core's Extractor.scala.
  TagProperties.addAliases("timePrimitive", Seq("TimeStamp", "TimeSpan"))
}

/**
 * Fill.
 * NOTE that this is a boolean that is represented by 0 or 1.
 * See [[https://developers.google.com/kml/documentation/kmlreference#fill fill]]
 *
 * @param boolean whether to fill or not.
 */
case class Fill(boolean: Boolean) derives Extractor, Renderer

/**
 * A case class representing FlyToView in KML documents.
 * TESTME
 *
 * This case class encapsulates a `Boolean` value that signifies FlyToView attributes in KML data.
 * FlyToView settings often dictate A value of 1 causes Google Earth to fly to the view of the LookAt or Camera in the NetworkLinkControl (if it exists).
 */
case class FlyToView($: Boolean) derives Extractor, Renderer


/**
 * Case class Folder: subelement of Container.
 * See [[https://developers.google.com/kml/documentation/kmlreference#container Folder]].
 *
 * @param features      a sequence of Feature elements (where Feature is an abstract super-type).
 * @param containerData the ContainerData (auxiliary property).
 */
case class Folder(features: Seq[Feature])(val containerData: ContainerData) extends Container with HasFeatures with HasName derives Extractor, Renderer {
  /**
   * Retrieves the name feature from the container data's feature data.
   *
   * @return The name represented as a `Text` object extracted from the container data.
   */
  def name: Text = containerData.featureData.name

  override def toString: String = s"Folder: name=${name.$} with ${features.size} features"
}

/**
 * Trait Geometry: abstract subelement of KmlObject.
 * Geometry is the super-type of LineString, Polygon, LinearRing, and Point.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#geometry Geometry]]
 */
sealed trait Geometry extends KmlObject with Mergeable[Geometry] with Invertible[Geometry] {

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
 * Companion object for `Geometry`.
 * Its `MultiExtractor[Seq[Geometry]]` and `Renderer[Geometry]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 */
object Geometry {

  // NOTE: MultiGeometry recursively contains Geometry, so this and MultiGeometry's own
  // extractor/renderer are mutually dependent - exactly the same shape as Feature/Container
  // (a Container holds Seq[Feature], and Folder/Document are themselves Features).
  // Derived instances find the instances they depend on lazily (on first use), so no createLazy is needed.
  implicit val extractorSeq: MultiExtractor[Seq[Geometry]] = MultiExtractor.derived ^^ "multiExtractorGeometry"
  implicit val renderer: Renderer[Geometry] = Renderer.derived ^^ "rendererGeometry"
}

/**
 * Properties of Geometry.
 * There are no properties specific to Geometry.
 * See [[https://developers.google.com/kml/documentation/kmlreference#geometry Geometry]]
 *
 * @param kmlData source of properties.
 */
case class GeometryData(maybeExtrude: Option[Extrude], maybeAltitudeMode: Option[AltitudeMode])(val kmlData: KmlData) extends Mergeable[GeometryData] derives Extractor, Renderer {
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
case class GroundOverlay(maybeAltitude: Option[Altitude], maybeAltitudeMode: Option[AltitudeMode], LatLonBox: LatLonBox)(val overlayData: OverlayData) extends BaseOverlay(overlayData) derives Extractor, Renderer

/**
 * Case class to represent a Heading which is represented in XML as, for example: <heading>1.1</heading>
 * See [[https://developers.google.com/kml/documentation/kmlreference#heading heading]]
 *
 * @param $ the value of the heading (a Double).
 */
case class Heading($: Double) derives Extractor, Renderer

/**
 * Case class to model a HotSpot.
 * See [[https://developers.google.com/kml/documentation/kmlreference#hotspot hotSpot]]
 *
 * @param _x      optional x field.
 * @param _xunits optional xunits field.
 * @param _y      optional y field.
 * @param _yunits optional yunits field.
 */
case class HotSpot(_x: Int, _xunits: UnitsEnum.Value, _y: Int, _yunits: UnitsEnum.Value) derives Extractor, Renderer

/**
 * Case class Icon.
 * NOTE: we do not currently support Link.
 * See [[https://developers.google.com/kml/documentation/kmlreference#icon Icon]]
 *
 * @param href a URL reference to an image.
 */
case class Icon(href: Text) derives Extractor, Renderer

/**
 * This case class represents the inner boundary of a polygon in KML (Keyhole Markup Language) files.
 * The inner boundary is defined by a `LinearRing`,
 * which specifies a closed, linear path (a closed line with four or more positions).
 * See [[https://developers.google.com/kml/documentation/kmlreference#innerboundaryis InnerBoundaryIs]]
 *
 * @param LinearRing The `LinearRing` defining the closed linear path representing the inner boundary.
 */
case class InnerBoundaryIs(LinearRing: LinearRing) derives Extractor, Renderer

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
case class ItemIcon(state: State, href: Text) derives Extractor, Renderer

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
case class IconStyle(maybeScale: Option[Scale], Icon: Icon, maybeHotSpot: Option[HotSpot], maybeHeading: Option[Heading])(val colorStyleData: ColorStyleData) extends ColorStyle derives Extractor, Renderer

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
case class ImagePyramid(tileSize: Int, maxWidth: Int, maxHeight: Int, gridOrigin: Boolean) derives Extractor, Renderer

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
case class Key($: StyleStateEnum.Value) derives Extractor, Renderer

/**
 * Case class to define a KML object.
 *
 * NOTE WELL: do not be tempted to add "_xmlns" as a member.
 * If you do, you will run into the undocumented(?) "feature"
 * of the Xml library that "xmlns" is a reserved attribute name.
 *
 * @param features a sequence of Feature (typically Document).
 */
case class KML(features: Seq[Feature]) extends HasFeatures derives Extractor, Renderer {

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
object KML {

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

  // The (derived) renderer, but preceded by the XML declaration.
  private val rendererSpecial: Renderer[KML] = Renderer[KML] {
    (kml, format, stateR) => implicitly[Renderer[KML]].render(kml, format, stateR) map (w => prefix + "\n" + w)
  } ^^ "rendererKml"
}

/**
 * Case class LabelStyle which extends ColorStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#labelstyle Label Style]]
 *
 * @param scale          the scale.
 * @param colorStyleData the (auxiliary) color style properties.
 */
case class LabelStyle(scale: Scale)(val colorStyleData: ColorStyleData) extends ColorStyle derives Extractor, Renderer

/**
 * Represents a latitude value as a Double, an angle90.
 *
 * Latitude values are commonly used in geographic coordinate systems
 * to specify the north-south position of a point on the Earth's surface.
 *
 * This case class is typically accompanied by extractors and renderers
 * to facilitate parsing and rendering operations as part of a larger system.
 */
case class Latitude($: Double) derives Extractor, Renderer

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
case class LatLonBox(north: Latitude, south: Latitude, east: Longitude, west: Longitude, rotation: Rotation) derives Extractor, Renderer
/**
 * Case class LineString which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#linearring LinearRing]]
 *
 * @param maybeTessellate the (optional) tessellation.
 * @param coordinates     a sequence of Coordinates objects.
 */
case class LinearRing(maybeTessellate: Option[Tessellate], coordinates: Seq[Coordinates])(val geometryData: GeometryData) extends Geometry derives Extractor, Renderer

/**
 * Case class LineString which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#linestring LineString]]
 *
 * @param maybeTessellate the (optional) tessellation.
 * @param coordinates     a sequence of Coordinates objects.
 */
case class LineString(maybeTessellate: Option[Tessellate], coordinates: Seq[Coordinates])(val geometryData: GeometryData) extends Geometry derives Extractor, Renderer {

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
        g <- geometryData merge l.geometryData
        c <- mergeSequence(coordinates)(empty).headOption
        d <- mergeSequence(l.coordinates)(empty).headOption
        z <- c merge d
      } yield LineString(mergeOptions(maybeTessellate, l.maybeTessellate)((t1, t2) => t1 merge t2), Seq(z))(g)
    case _ => None
  }

  /**
   * Returns an inverted version of this `LineString`, where the order of the coordinates
   * in each sequence is reversed.
   *
   * @return an `Option` containing the inverted `LineString` instance if successful.
   */
  override def invert: Option[LineString] = Some(LineString(maybeTessellate, for (c <- coordinates) yield c.reverse)(geometryData))

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
case class LineStyle(maybeWidth: Option[Width])(val colorStyleData: ColorStyleData) extends ColorStyle derives Extractor, Renderer

/**
 * Case class Link, used by `NetworkLink` and `Model` to reference an external resource, with
 * control over how and when it's refreshed.
 * See [[https://developers.google.com/kml/documentation/kmlreference#link Link]]
 *
 * @param maybeHref            the location of the resource: Option[Text].
 * @param maybeRefreshMode     an optional refresh mode: Option[RefreshMode].
 * @param maybeRefreshInterval an optional refresh interval: Option[RefreshInterval].
 * @param maybeViewRefreshMode an optional view refresh mode: Option[ViewRefreshMode].
 * @param maybeViewRefreshTime an optional view refresh time: Option[ViewRefreshTime].
 * @param maybeViewBoundScale  an optional scale factor for the view bounding box: Option[ViewBoundScale].
 * @param maybeViewFormat      an optional format string for the view: Option[Text].
 * @param maybeHttpQuery       an optional format string for an HTTP query: Option[Text].
 */
case class Link(maybeHref: Option[Text], maybeRefreshMode: Option[RefreshMode], maybeRefreshInterval: Option[RefreshInterval], maybeViewRefreshMode: Option[ViewRefreshMode], maybeViewRefreshTime: Option[ViewRefreshTime], maybeViewBoundScale: Option[ViewBoundScale], maybeViewFormat: Option[Text], maybeHttpQuery: Option[Text]) derives Extractor, Renderer

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
case class ListItemType($: ListItemTypeEnum.Value) derives Extractor, Renderer

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
case class ListStyle(bgColor: BgColor, maybeListItemType: Option[ListItemType], maybeItemIcon: Option[ItemIcon])(val colorStyleData: ColorStyleData) extends ColorStyle derives Extractor, Renderer

/**
 * Case class Location: sub-element of `Model`, specifying where the model is draped onto the
 * Earth's surface.
 * See [[https://developers.google.com/kml/documentation/kmlreference#model Model]]
 *
 * @param longitude    the longitude of the model's origin.
 * @param latitude     the latitude of the model's origin.
 * @param maybeAltitude an optional altitude of the model's origin: Option[Altitude].
 */
case class Location(longitude: Longitude, latitude: Latitude, maybeAltitude: Option[Altitude]) derives Extractor, Renderer

/**
 * Case class representing a geographical longitude (an angle180).
 *
 * @param $ the longitude value in degrees as a Double.
 */
case class Longitude($: Double) derives Extractor, Renderer

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
case class LookAt(longitude: Longitude, latitude: Latitude, maybeAltitude: Option[Altitude], heading: Heading, tilt: Tilt, range: Range, maybeAltitudeMode: Option[AltitudeMode])(val abstractViewData: AbstractViewData) extends AbstractView derives Extractor, Renderer

/**
 * Case class Model, which extends Geometry: a 3D object described in a COLLADA file, placed at a
 * given location and orientation, and scaled.
 * See [[https://developers.google.com/kml/documentation/kmlreference#model Model]]
 *
 * NOTE: altitude mode is not one of this class's own fields, unlike the KML spec's own grouping -
 * it's already provided by every Geometry subtype via the shared `geometryData` aux (see
 * `GeometryData.maybeAltitudeMode`), so adding it again here would render it twice.
 *
 * @param maybeLocation      an optional location on the Earth's surface: Option[Location].
 * @param maybeOrientation   an optional orientation: Option[Orientation].
 * @param maybeScale         an optional scale factor along each axis: Option[ModelScale].
 * @param maybeLink          an optional reference to the COLLADA (.dae) file: Option[Link].
 * @param maybeResourceMap   an optional map from the COLLADA file's own texture references to
 *                           where those resources actually are in this KML/KMZ package: Option[ResourceMap].
 * @param geometryData       the other properties of the Model.
 */
case class Model(maybeLocation: Option[Location], maybeOrientation: Option[Orientation], maybeScale: Option[ModelScale], maybeLink: Option[Link], maybeResourceMap: Option[ResourceMap])(val geometryData: GeometryData) extends Geometry derives Extractor, Renderer

/**
 * Case class ModelScale: sub-element of `Model`, a scale factor to apply along each of the three
 * axes. Named `ModelScale` (not `Scale`, which already exists elsewhere in this file for the
 * unrelated, single-value icon/label scale factor) since the two are structurally different -
 * only the actual XML tag ("Scale") is shared between them, which is fine since it's `Model`'s
 * own field name (not this class's name) that determines the tag used.
 * See [[https://developers.google.com/kml/documentation/kmlreference#model Model]]
 *
 * @param x the scale factor along the x axis: AxisScale.
 * @param y the scale factor along the y axis: AxisScale.
 * @param z the scale factor along the z axis: AxisScale.
 */
case class ModelScale(x: AxisScale, y: AxisScale, z: AxisScale) derives Extractor, Renderer

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
case class MultiGeometry(Geometry: Seq[Geometry])(val geometryData: GeometryData) extends Geometry derives Extractor, Renderer

/**
 * Case class NetworkLink: subtype of Feature, referencing a KML file or KMZ archive on a local
 * or remote network, optionally with rules for automatically refreshing it.
 * See [[https://developers.google.com/kml/documentation/kmlreference#networklink NetworkLink]]
 *
 * @param maybeRefreshVisibility whether to reset the visibility of features when the file is
 *                               refreshed: Option[RefreshVisibility].
 * @param maybeFlyToView         whether a view change is limited to the extent of the referenced
 *                               feature: Option[FlyToView].
 * @param maybeLink              the location of, and refresh rules for, the referenced resource:
 *                               Option[Link].
 * @param featureData            the (auxiliary) FeatureData, shared by sub-elements.
 */
case class NetworkLink(maybeRefreshVisibility: Option[RefreshVisibility], maybeFlyToView: Option[FlyToView], maybeLink: Option[Link])(val featureData: FeatureData) extends Feature with HasName derives Extractor, Renderer {
  def name: Text = featureData.name

  override def toString: String = s"NetworkLink: name=${name.$}"
}

/**
 * Represents an `Open` state with a single boolean parameter.
 *
 * @param $ A boolean value indicating the open state.
 */
case class Open($: Boolean) derives Extractor, Renderer

/**
 * Case class Orientation: sub-element of `Model`, describing the rotations to apply to the
 * COLLADA model to position it correctly.
 * See [[https://developers.google.com/kml/documentation/kmlreference#model Model]]
 *
 * @param heading the rotation about the z axis (normal to the Earth's surface).
 * @param tilt    the rotation about the x axis.
 * @param roll    the rotation about the y axis.
 */
case class Orientation(heading: Heading, tilt: Tilt, roll: Roll) derives Extractor, Renderer

/**
 * Represents the outer boundary of a polygon in KML format.
 * This case class holds a `LinearRing`, which defines the coordinates
 * that create the outer boundary.
 * See [[https://developers.google.com/kml/documentation/kmlreference#outerboundaryis outerBoundaryIs]]
 *
 * @param LinearRing the `LinearRing` instance that specifies the coordinates
 *                   of the outer boundary of a polygon.
 */
case class OuterBoundaryIs(LinearRing: LinearRing) derives Extractor, Renderer

/**
 * Outline.
 * NOTE that this is a boolean that is represented by 0 or 1.
 *
 * @param boolean whether to outline or not.
 */
case class Outline(boolean: Boolean) derives Extractor, Renderer

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
sealed trait Overlay extends Feature

/**
 * Companion object for `Overlay`.
 * Its `MultiExtractor[Seq[Overlay]]` and `Renderer[Overlay]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 */
object Overlay {

  implicit val extractorSeq: MultiExtractor[Seq[Overlay]] = MultiExtractor.derived ^^ "multiExtractorOverlay"
  implicit val renderer: Renderer[Overlay] = Renderer.derived ^^ "rendererOverlay"
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
sealed abstract class BaseOverlay(overlayData: OverlayData) extends Overlay with HasName {
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
case class OverlayData(Icon: Icon, maybeDrawOrder: Option[DrawOrder], maybeColor: Option[Color])(val featureData: FeatureData) derives Extractor, Renderer

/**
 * Represents a 2D overlay point with defined coordinates and their corresponding units.
 *
 * @param _x      The x-coordinate value.
 * @param _y      The y-coordinate value.
 * @param _xunits The unit of measurement for the x-coordinate, defined in the UnitsEnum enumeration.
 * @param _yunits The unit of measurement for the y-coordinate, defined in the UnitsEnum enumeration.
 */
case class OverlayXY(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value) derives Extractor, Renderer

/**
 * A case class representing a key-style URL pair mapping in the KML document context.
 *
 * This class encapsulates a `Key` and a `StyleURL` to represent associations typically used
 * in KML processing frameworks for styling and representation of geographic data.
 *
 * @param key      the symbolic identifier as a `Key`, encapsulating a character sequence.
 * @param styleUrl the associated style reference as a `StyleURL`, providing a mapping to style definitions.
 */
case class Pair(key: Key, styleUrl: StyleURL) derives Extractor, Renderer

/**
 * Companion object for `Pair` (its `Extractor` and `Renderer` are derived: see `derives` on the case class).
 */
object Pair extends Extractors with Renderers {

  implicit val extractorSeq: MultiExtractor[Seq[Pair]] = multiExtractorBase[Pair](Positive) ^^ "multiExtractorPair"
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
case class PhotoOverlay(rotation: Rotation, ViewVolume: ViewVolume, ImagePyramid: ImagePyramid, Point: Point, shape: Shape)(val overlayData: OverlayData) extends BaseOverlay(overlayData) derives Extractor, Renderer

/**
 * Placemark: subtype of Feature.
 * See [[https://developers.google.com/kml/documentation/kmlreference#placemark Placemark]].
 *
 * CONSIDER restricting the number of Geometry elements to one.
 *
 * @param Geometry    a sequence of Geometry elements (where Geometry is an abstract super-type).
 * @param featureData the (auxiliary) FeatureData, shared by sub-elements.
 */
case class Placemark(Geometry: Seq[Geometry])(val featureData: FeatureData) extends Feature with HasName with Mergeable[Placemark] with Invertible[Placemark] derives Extractor, Renderer {

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
        // NOTE: Issue #20. If no sibling matches nameToMatch2, joinMatchedPlacemarks yields None,
        // and mapping (rather than unconditionally wrapping in Some) keeps this None as the
        // outer result too - which editToOption's caller treats as "this edit doesn't apply,
        // leave unchanged" rather than "delete this Placemark". Previously, an unmatched partner
        // silently deleted this Placemark even though it was never actually joined with anything.
        joinMatchedPlacemarks(fs, nameToMatch2, command == JOIN) map Some.apply
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
    // NOTE: this sibling isn't the join partner - not itself a problem, since
    // joinMatchedPlacemarks tries every sibling and only cares whether any of them matched.
    case _ => None
  }
}

/**
 * Case class Point which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#point Point]]
 *
 * @param coordinates  a sequence of Coordinates objects.
 * @param geometryData the other properties of the Point.
 */
case class Point(coordinates: Seq[Coordinates])(val geometryData: GeometryData) extends Geometry derives Extractor, Renderer

/**
 * Case class Polygon which extends Geometry.
 *
 * See [[https://developers.google.com/kml/documentation/kmlreference#polygon Polygon]]
 *
 * @param maybeTessellate the tessellation.
 * @param outerBoundaryIs an OuterBoundaryIs object.
 * @param innerBoundaryIs a sequence of InnerBoundaryIs objects.
 */
case class Polygon(maybeTessellate: Option[Tessellate], outerBoundaryIs: OuterBoundaryIs, innerBoundaryIs: Seq[InnerBoundaryIs])(val geometryData: GeometryData) extends Geometry derives Extractor, Renderer

/**
 * Case class PolyStyle which extends ColorStyle.
 * See [[https://developers.google.com/kml/documentation/kmlreference#polystyle Poly Style]]
 *
 * @param maybeFill      optional value of fill.
 * @param maybeOutline   optional value of outline.
 * @param colorStyleData the (auxiliary) color style properties.
 */
case class PolyStyle(maybeFill: Option[Fill], maybeOutline: Option[Outline])(val colorStyleData: ColorStyleData) extends ColorStyle derives Extractor, Renderer

/**
 * A case class representing a Range with a single value.
 *
 * @param $ the value representing the range.
 */
case class Range($: Double) derives Extractor, Renderer

/**
 * RefreshInterval represents the number of seconds to wait before refreshing a `Link`.
 * See [[https://developers.google.com/kml/documentation/kmlreference#link Link]]
 *
 * NOTE: this is a plain Double wrapped in its own class - like `Rotation`/`Range`/`Heading` etc.
 * elsewhere in this file - rather than a bare `Double` field, because `doubleRenderer` always
 * renders itself as attribute-shaped text (`name="value"`), which is only ever correct when the
 * enclosing field name starts with "_" (a genuine XML attribute); for an ordinary child element
 * like this one, it has to go through the same single-field-wrapper machinery as those others.
 *
 * @param $ the refresh interval, in seconds.
 */
case class RefreshInterval($: Double) derives Extractor, Renderer

/**
 * RefreshMode which has values "onChange" , "onInterval" or "onExpire".
 * Used by Link, Icon.
 *
 * @param $ the mode of Refresh
 */
case class RefreshMode($: RefreshModeEnum.Value) derives Extractor, Renderer

/**
 * A case class representing RefreshVisibility in KML documents.
 *
 * This case class encapsulates a `Boolean` value that signifies RefreshVisibility attributes in KML data.
 * RefreshVisibility settings often dictate when the NetworkLink is refreshed, whether a specific KML element should be displayed in a viewer.
 */
case class RefreshVisibility($: Boolean) derives Extractor, Renderer

/**
 * Case class ResourceMap: sub-element of `Model`, mapping each texture reference used within the
 * COLLADA file to where that resource actually is within this KML/KMZ package.
 * See [[https://developers.google.com/kml/documentation/kmlreference#model Model]]
 *
 * @param Alias a sequence of source-to-target reference mappings: Seq[Alias].
 */
case class ResourceMap(Alias: Seq[Alias]) derives Extractor, Renderer

/**
 * Represents the roll angle in degrees or radians, depending on the specific context of usage.
 *
 * The `Roll` class is a wrapper for a `Double` value that signifies the roll angle.
 * Instances of this class can be used in contexts that involve angle measurements, transformations, or calculations.
 *
 * @param $ the numeric value of the roll angle in degrees or radians.
 */
case class Roll($: Double) derives Extractor, Renderer

/**
 * Case class representing a rotation (an angle180).
 *
 * @param $ the rotation value in degrees as a Double.
 */
case class Rotation($: Double) derives Extractor, Renderer

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
case class RotationXY(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value) derives Extractor, Renderer

/**
 * Scale element: subelement of Object in the Kml reference.
 * Case class to represent a Scale which is represented in XML as, for example: <scale>1.1</scale>
 * See [[https://developers.google.com/kml/documentation/kmlreference#scale Scale]]
 *
 * @param $ the value of the scale (a Double).
 */
case class Scale($: Double)(val kmlData: KmlData) extends KmlObject derives Extractor, Renderer

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
object Scale {

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
case class SchemaData(_schemaUrl: CharSequence, SimpleData: Seq[SimpleData]) derives Extractor, Renderer

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
case class ScreenOverlay(overlayXY: OverlayXY, screenXY: ScreenXY, rotationXY: RotationXY, size: Size, maybeRotation: Option[Rotation])(val overlayData: OverlayData) extends BaseOverlay(overlayData) derives Extractor, Renderer

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
case class ScreenXY(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value) derives Extractor, Renderer

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
 * Its `Extractor` and `Renderer` are derived.
 */
case class Shape($: ShapeEnum.Value) derives Extractor, Renderer

/**
 * Case class SimpleData: sub-element of SchemaData, a single typed name/value pair.
 * See [[https://developers.google.com/kml/documentation/kmlreference#extendeddata SimpleData]]
 *
 * @param _name the name of this SimpleData element, referencing a SimpleField of the
 *              corresponding Schema (an XML attribute).
 * @param $     the value, as the text content of this element.
 */
case class SimpleData(_name: CharSequence, $: CharSequence) derives Extractor, Renderer

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
case class Size(_x:Double, _y:Double, _xunits:UnitsEnum.Value, _yunits: UnitsEnum.Value) derives Extractor, Renderer
/**
 * State
 * CONSIDER this should be an enumerated type with values: open, closed, error, fetching0, fetching1, or fetching2
 *
 * @param $ the value.
 */
case class State($: ItemIconModeEnum.Value) derives Extractor, Renderer

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
case class Style(Styles: Seq[SubStyle])(val styleSelectorData: StyleSelectorData) extends StyleSelector derives Extractor, Renderer

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
case class StyleMap(Pairs: Seq[Pair])(val styleSelectorData: StyleSelectorData) extends StyleSelector derives Extractor, Renderer

/**
 * Trait StyleSelector is a sub-trait of KmlObject.
 * It is extended by Style and StyleMap.
 * See [[https://developers.google.com/kml/documentation/kmlreference#styleselector StyleSelector]]
 */
sealed trait StyleSelector extends KmlObject

/**
 * Companion object for `StyleSelector`.
 * Its `MultiExtractor[Seq[StyleSelector]]` and `Renderer[StyleSelector]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 */
object StyleSelector {

  implicit val extractorSeq: MultiExtractor[Seq[StyleSelector]] = MultiExtractor.derived ^^ "multiExtractorStyleSelector"
  implicit val renderer: Renderer[StyleSelector] = Renderer.derived ^^ "rendererStyleSelector"
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
 * Companion object for `StyleSelectorData`.
 * Its only field, `kmlData`, is extracted from (and rendered into) the same element, as an auxiliary parameter is
 * (see `Extractor.embedded` and `Renderer.embedded`).
 */
object StyleSelectorData {

  // NOTE: kmlData is extracted from (and rendered into) the same element, as an auxiliary parameter is.
  implicit val extractor: Extractor[StyleSelectorData] = Extractor.embedded[KmlData, StyleSelectorData](apply) ^^ "extractorStyleSelectorData"
  implicit val renderer: Renderer[StyleSelectorData] = Renderer.embedded[StyleSelectorData, KmlData](_.kmlData) ^^ "rendererStyleSelectorData"
}

/**
 * A case class representing a style URL in the KML document context.
 *
 * This class encapsulates a `java.net.URI` (Issue #44: not a bare `CharSequence`) that serves as
 * the identifier or reference to a style within a KML structure - typically a fragment-only
 * reference such as "#some-style", hence `URI` rather than the stricter `URL`.
 */
case class StyleURL($: java.net.URI) derives Extractor, Renderer

/**
 * Companion object for `StyleURL` (its `Extractor` and `Renderer` are derived: see `derives` on the case class).
 */
object StyleURL {

  /**
   * Convenience factory for constructing a `StyleURL` directly from a `String`, e.g. in tests.
   */
  def apply(s: String): StyleURL = StyleURL(new java.net.URI(s))
}

/**
 * Trait SubStyle which, according to the KML reference extends Object and is extended by ColorStyle, BalloonStyle and ListStyle.
 * Reality seems otherwise, however.
 * See [[https://developers.google.com/kml/documentation/kmlreference KML]]
 */
sealed trait SubStyle extends KmlObject

/**
 * Companion object for `SubStyle`.
 * Its `MultiExtractor[Seq[SubStyle]]` and `Renderer[SubStyle]` are derived from its (sealed) subtypes,
 * with the class name of each subtype as its tag.
 */
object SubStyle {

  // CONSIDER invoking ColorStyle
  implicit val extractorSeq: MultiExtractor[Seq[SubStyle]] = MultiExtractor.derived ^^ "multiExtractorSubStyle"
  implicit val renderer: Renderer[SubStyle] = Renderer.derived ^^ "rendererSubStyle"
}

/**
 * Represents a substyle data container, which encapsulates KML (Keyhole Markup Language) data.
 * This class wraps an instance of KmlData, allowing additional processing or organization of KML metadata.
 *
 * @param kmlData an instance of KmlData containing metadata for the substyle.
 */
case class SubStyleData(kmlData: KmlData)

/**
 * Companion object for `SubStyleData`.
 * Its only field, `kmlData`, is extracted from (and rendered into) the same element, as an auxiliary parameter is
 * (see `Extractor.embedded` and `Renderer.embedded`).
 */
object SubStyleData {

  // NOTE: kmlData is extracted from (and rendered into) the same element, as an auxiliary parameter is.
  implicit val extractor: Extractor[SubStyleData] = Extractor.embedded[KmlData, SubStyleData](apply) ^^ "extractorSubStyleData"
  implicit val renderer: Renderer[SubStyleData] = Renderer.embedded[SubStyleData, KmlData](_.kmlData) ^^ "rendererSubStyleData"
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
case class Tessellate($: Boolean) extends Mergeable[Tessellate] derives Extractor, Renderer {
  infix def merge(t: Tessellate, mergeName: Boolean = true): Option[Tessellate] = ($, t.$) match {
    case (a, b) if a == b => Some(Tessellate(a))
    case _ => None
  }
}

/**
 * TextColor
 * Used by BalloonStyle.
 *
 * @param $ a hexadecimal string that defines the color of Text.
 */
case class TextColor($: Hex4) derives Extractor, Renderer

/**
 * Represents a tilt with a single numeric value.
 *
 * This case class is utilized to model the notion of tilt in a KML or similar domain representation.
 * It includes a single parameter `$` which stores the tilt value.
 * The class is utilized along with its
 * companion object that provides implicit extractors and renderers for serialization and deserialization purposes.
 */
case class Tilt($: Double) derives Extractor, Renderer

/**
 * ViewBoundScale represents a scale factor to apply to the current view bounding box, used by
 * `Link` when constructing the `BBOX` parameters of `viewFormat`.
 * See [[https://developers.google.com/kml/documentation/kmlreference#link Link]]
 *
 * NOTE: see the comment on `RefreshInterval` for why this is wrapped rather than a bare `Double`.
 *
 * @param $ the scale factor (1 leaves the bounding box unchanged).
 */
case class ViewBoundScale($: Double) derives Extractor, Renderer

/**
 * ViewRefreshMode which has values "never" , "onStop", "onRequest" or "onRegion".
 * Used by Link, Icon.
 *
 * @param $ the mode of View Refresh
 */
case class ViewRefreshMode($: ViewRefreshEnum.Value) derives Extractor, Renderer


/**
 * ViewRefreshTime represents the number of seconds to wait after the view stops moving before
 * refreshing a `Link`'s view-dependent content.
 * See [[https://developers.google.com/kml/documentation/kmlreference#link Link]]
 *
 * NOTE: see the comment on `RefreshInterval` for why this is wrapped rather than a bare `Double`.
 *
 * @param $ the wait time, in seconds.
 */
case class ViewRefreshTime($: Double) derives Extractor, Renderer

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
case class ViewVolume(leftFov:Longitude, rightFov: Longitude, bottomFov: Latitude, topFov: Latitude, near: Double) derives Extractor, Renderer

/**
 * A case class representing visibility in KML documents.
 *
 * This case class encapsulates a `Boolean` value that signifies visibility attributes in KML data.
 * Visibility settings often dictate whether a specific KML element should be displayed in a viewer.
 */
case class Visibility($: Boolean) derives Extractor, Renderer

/**
 * A case class representing the width of an object.
 *
 * @param $ the width value as a Double
 */
case class Width($: Double) derives Extractor, Renderer
