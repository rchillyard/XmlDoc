package com.phasmidsoftware.kmldoc

import com.phasmidsoftware.core.{Cartesian, Text, TryUsing}
import com.phasmidsoftware.kmldoc.KmlEdit.editFeatures
import com.phasmidsoftware.kmldoc.KmlRenderers.sequenceRendererFormatted
import com.phasmidsoftware.render._
import com.phasmidsoftware.xml.Extractor

import scala.reflect.ClassTag
import scala.util.matching.Regex
import scala.util.{Success, Try}
import scala.xml.NamespaceBinding

/**
 * The Helper object does nothing other than provide a name for this module.
 */
object Helper

/**
 * Case class Coordinate to represent a three-dimensional point.
 * Although this is part of the KML spec, it is treated somewhat differently than the others.
 *
 * @param long longitude.
 * @param lat  latitude.
 * @param alt  altitude.
 */
case class Coordinate(long: String, lat: String, alt: String) {
  /**
   * Represents the Cartesian coordinate of the current instance of `Coordinate`.
   *
   * This value is lazily computed and will return an `Option[Cartesian]` if the `long`, `lat`, and `alt` fields
   * of this instance can be successfully converted to `Double`.
   * If any of these conversions fail, the result is `None`.
   */
  lazy val geometry: Option[Cartesian] = for (x <- long.toDoubleOption; y <- lat.toDoubleOption; z <- alt.toDoubleOption) yield Cartesian(x, y, z)

  /**
   * Computes the vector difference between the current `Coordinate` instance and another given `Coordinate` instance.
   *
   * @param c a `Coordinate` instance for which the vector difference is calculated with respect to the current instance.
   * @return an `Option[Cartesian]` representing the vector difference if both coordinates can be converted to `Cartesian`,
   *         otherwise `None`.
   */
  infix def vector(c: Coordinate): Option[Cartesian] = for (a <- this.geometry; b <- c.geometry) yield a vector b

  /**
   * Calculates the distance between the current `Coordinate` instance and another given `Coordinate` instance.
   *
   * The distance is computed using the Cartesian coordinates of both instances, provided that they can be successfully
   * converted to their Cartesian representation.
   * If either coordinate cannot be converted, the result will be `None`.
   *
   * @param c the `Coordinate` instance to calculate the distance to.
   * @return an `Option[Double]` representing the Euclidean distance between the two coordinates, or `None` if the
   *         Cartesian conversion fails for either of the coordinates.
   */
  infix def distance(c: Coordinate): Option[Double] = for (a <- this.geometry; b <- c.geometry) yield a distance b
}

/**
 * Companion object for the `Coordinate` case class, providing methods to parse and render `Coordinate` objects.
 */
object Coordinate {

  // CONSIDER using Parser-combinators here.
  private val longLatAlt: Regex = """^\s*([\d\-\.]+),\s*([\d\-\.]+),\s*([\d\-\.]+)\s*$""".r
//    private val longLatAlt: Regex = """^\s*(((-)?(\d+(\.\d*)?)),\s*((-)?(\d+(\.\d*)?)),\s*((-)?(\d+(\.\d*)?)))\s*""".r

  /**
   * Parses a coordinate string and converts it into a `Coordinate` instance,
   * if the string matches the expected format.
   *
   * @param w the input string representing a coordinate in the format "longitude,latitude,altitude".
   * @return a `Coordinate` instance created from the parsed string.
   * @throws KmlException if the input string does not match the expected coordinate format.
   */
  def apply(w: String): Coordinate = w match {
    case longLatAlt(long, lat, alt) => Coordinate(long, lat, alt)
    case _ => throw KmlException(s"""bad coordinate string: "$w" """)
  }

  implicit val renderer: Renderer[Coordinate] = Renderer[Coordinate] { (t, _, _) => Success(s"${t.long},${t.lat},${t.alt}") } ^^ "rendererCoordinate"
  implicit val rendererSeq: Renderer[Seq[Coordinate]] = sequenceRendererFormatted[Coordinate](KmlRenderers.FormatCoordinate.apply) ^^ "rendererCoordinates1"
}

/**
 * Represents a color in the RGBA hexadecimal format using four bytes.
 *
 * @param alpha The alpha (transparency) component of the color, represented as a byte.
 * @param red   The red component of the color, represented as a byte.
 * @param green The green component of the color, represented as a byte.
 * @param blue  The blue component of the color, represented as a byte.
 *
 *              Overrides the `toString` method to return the color as a hexadecimal string with each byte formatted as a two-digit hexadecimal number.
 */
case class Hex4(alpha: Byte, red: Byte, green: Byte, blue: Byte) {
  override def toString: String = f"$alpha%02x$red%02x$green%02x$blue%02x"
}

/**
 * Companion object for the `Hex4` case class that provides utility methods and functionality
 * for working with `Hex4` instances, including creation, parsing, and rendering.
 */
object Hex4 {

  /**
   * Constructs a `Hex4` instance using the specified alpha, red, green, and blue color components.
   *
   * @param alpha the alpha component represented as a string
   * @param red   the red component represented as a string
   * @param green the green component represented as a string
   * @param blue  the blue component represented as a string
   * @return a `Hex4` instance created from the provided color component values
   */
  def apply(alpha: String, red: String, green: String, blue: String): Hex4 = Hex4(byte(alpha), byte(red), byte(green), byte(blue))

  /**
   * Constructs a `Hex4` instance by parsing a single string containing the concatenated
   * two-character hexadecimal representations of the alpha, red, green, and blue components.
   *
   * @param s A string containing exactly 8 characters, where the first two characters
   *          represent the alpha component, the next two represent the red component,
   *          the following two represent the green component, and the last two represent the blue component.
   * @return A `Hex4` instance created from the parsed hexadecimal component values.
   */
  def apply(s: String): Hex4 = apply(s.substring(0, 2), s.substring(2, 4), s.substring(4, 6), s.substring(6, 8))

  /**
   * Provides an implicit `Extractor[Hex4]` instance for converting a `CharSequence` into
   * a `Hex4` instance. The conversion is performed by mapping the `CharSequence` to its string
   * representation and then creating a `Hex4` using the string constructor of `Hex4`.
   *
   * This extractor is named "extractorHex4".
   */
  implicit val extractor: Extractor[Hex4] =
    implicitly[Extractor[CharSequence]].map(s => Hex4(s.toString)) ^^ "extractorHex4"
  /**
   * An implicit `Renderer` instance for the `Hex4` type.
   *
   * This renderer transforms a `Hex4` object into its hexadecimal string representation
   * by invoking the overridden `toString` method of `Hex4`. The process is wrapped in a `Try`
   * to handle potential exceptions during rendering.
   *
   * The `^^` operator is used to assign the name "rendererHex4" to the renderer instance.
   */
  implicit val renderer: Renderer[Hex4] = Renderer[Hex4] {
    (t, _, _) => Try(t.toString)
  } ^^ "rendererHex4"

  /**
   * Parses a hexadecimal string to a byte value.
   *
   * @param w the string representing a two-character hexadecimal value
   * @return the byte value corresponding to the parsed hexadecimal string
   */
  private def byte(w: String): Byte = Integer.parseInt(w, 16).toByte
}

/**
 * Trait to define the property of owning features.
 */
trait HasFeatures {
  /**
   * Represents a sequence of `Feature` objects associated with a given entity.
   * This field is part of classes or traits that extend `HasFeatures`.
   *
   * Features are core abstract elements defined under the `Feature` trait,
   * which is a sub-type of `KmlObject` and serves as a super-type for elements
   * such as `Placemark` and `Container`.
   * They encapsulate KML-specific entities
   * and their behaviors, including the ability to edit with regard to sibling features.
   *
   * The `features` sequence typically includes all such `Feature` subtypes relevant
   * to the containing object.
   */
  val features: Seq[Feature]
}

/**
 * Companion object HasFeatures provides utilities for editing objects that have a property of owning features.
 */
object HasFeatures {

  /**
   * Method to edit an object that has features.
   *
   * @param edit the edit to be applied.
   * @param t    the object to edit.
   * @param g    a function which takes a Seq[Feature] and creates an optional new copy of the T based on the provided Feature sequence.
   * @tparam T the type of <code>t</code>.
   * @return an optional copy of <code>t</code> with a (potentially) new set of features.
   */
  def editHasFeaturesToOption[T](t: T)(edit: KmlEdit)(g: Seq[Feature] => T): Option[T] = t match {
    case h: HasFeatures => Some(g(editFeatures(edit, h.features)))
    case _ => throw new Exception(s"editHasFeaturesToOption: parameter t does not extend HasFeatures: ${t.getClass}")
  }

}

/**
 * Trait representing an entity that has a name attribute.
 *
 * A `HasName` entity provides access to its name, defined as a `Text` type.
 */
trait HasName {
  /**
   * Retrieves the name as a `Text` value.
   *
   * @return the name represented as a `Text` instance.
   */
  def name: Text
}

/**
 * Represents a binding for a KML object with its corresponding XML namespace binding.
 *
 * NOTE this is a bit of a mystery.
 *
 * A KML_Binding associates a KML instance with its XML NamespaceBinding to manage XML namespaces.
 *
 * @constructor Constructs a new KML_Binding object with a given KML instance and its NamespaceBinding.
 * @param kml     the KML object representing the main geospatial data structure.
 * @param binding the NamespaceBinding for managing XML namespaces linked to the KML.
 */
case class KML_Binding(kml: KML, binding: NamespaceBinding)

/**
 * Companion object for the `KML_Binding` case class.
 * Provides implicit implementations for extracting and rendering `KML_Binding` instances.
 *
 * The object defines two implicit values:
 * 1. An `Extractor[KML_Binding]` to parse an XML `Node` and construct a `KML_Binding` instance.
 * 2. A `Renderer[KML_Binding]` to facilitate rendering of a `KML_Binding` instance to XML format.
 *
 * These implicits make the `KML_Binding` interoperable with XML nodes and compatible with processes
 * that work with Extraction and Rendering mechanisms.
 */
object KML_Binding {
  implicit val extractor: Extractor[KML_Binding] = Extractor {
    node =>
      Extractor.extract[KML](node) map (KML_Binding(_, node.scope))
  }
  implicit val renderer: Renderer[KML_Binding] = Renderer {
    (t: KML_Binding, format: Format, stateR: StateR) =>
      TryUsing(stateR.addAttribute(s"""${t.binding}"""))(rs => Renderer.render(t.kml, format, rs))
  } ^^ "rendererKml_Binding"
}

/**
 * Object KmlRenderers extends the Renderers base trait and serves as a collection
 * of formatting utilities specifically for KML (Keyhole Markup Language) rendering.
 *
 * This object contains nested case classes and methods for handling formatting operations.
 */
object KmlRenderers extends Renderers {

  /**
   * FormatCoordinate is a case class for handling coordinate-specific formatting in KML rendering.
   * It extends the BaseFormat abstraction, inheriting core formatting behavior while specializing in
   * coordinate-related formatting operations.
   *
   * @param indents The number of indentation levels used for formatting.
   */
  case class FormatCoordinate(indents: Int) extends BaseFormat(indents) {
    val name: String = "formatCoordinate"

    /**
     * Returns the current instance of the Format, as it represents the same formatting configuration.
     *
     * @return the current Format instance with unchanged characteristics.
     */
    def indent: Format = this

    /**
     * Formats the name based on the given state and an optional flag.
     *
     * @param open   an optional Boolean value. If Some(true), specific formatting is performed; otherwise, a default behavior is applied.
     * @param stateR the rendering state represented by the StateR instance, which provides context for formatting.
     * @tparam T a type parameter with an implicit ClassTag, allowing runtime inspection of type T.
     * @return a formatted string based on the input parameters and formatting logic.
     */
    def formatName[T: ClassTag](open: Option[Boolean], stateR: StateR): String = open match {
      case Some(true) =>
        ""
      case _ =>
        ""
    }

    /**
     * Determines the sequence string based on an optional Boolean input.
     *
     * @param open An Option of Boolean indicating the desired state. If Some(true), a newline with the specified indentation is returned.
     *             If Some(false), an empty string is returned. If None, a single newline character is returned.
     * @return A string representing the appropriate sequence based on the input parameter.
     */
    def sequencer(open: Option[Boolean]): String = open match {
      case Some(true) => newline
      case Some(false) => ""
      case _ => "\n"
    }
  }

  // TODO refactor the sequenceRendererFormatted method so that its parameter is a Format=>Format function.
}

/**
 * Represents an exception specific to KML processing errors.
 *
 * @param str A message describing the details of the exception.
 */
case class KmlException(str: String) extends Exception(str)
