package com.phasmidsoftware.render

import com.phasmidsoftware.core.FP.sequence
import com.phasmidsoftware.core.{SmartBuffer, TryUsing}
import com.phasmidsoftware.render.Renderer.maybeAttributeName
import com.phasmidsoftware.render.Renderers.logger
import com.phasmidsoftware.xml.{Extractor, NamedFunction}

import scala.reflect.ClassTag
import scala.util.{Success, Try}

/**
 * Typeclass to specify the required behavior of an object that you want to render as a String.
 *
 * @tparam T the type of the object.
 */
trait Renderer[T] extends NamedFunction[Renderer[T]] {

  /**
   * This is the method which renders an object t of type T as a String, given two other parameters.
   *
   * @param t      the object to be rendered.
   * @param format the format to render the object in.
   * @param stateR the state of rendering.
   * @return a String representation of t.
   */
  def render(t: T, format: Format, stateR: StateR): Try[String]

  /**
   * Transforms a Renderer for a type `T` into a Renderer for an `Option[T]`.
   * Returns an empty string for `None` and delegates rendering to the wrapped Renderer for `Some` values,
   * applying the necessary transformations based on rendering state.
   * If the member name is `maybeX` (as expected), this is transformed into `X`.
   *
   * @return a Renderer for `Option[T]`, capable of handling both `Some` and `None` cases.
   */
  def lift: Renderer[Option[T]] = Renderer {
    (ro, f, s) =>
      ro match {
        case Some(r) =>
          val wo = s.maybeName match {
            case Some(Extractor.optional(x)) => Some(x)
            case Some(x) => Some(x)
            case None => None
          }
          render(r, f, StateR(wo))
        case None => Success("")
      }
  }
}

/**
 * Utility object for working with the `Renderer` typeclass.
 * Provides methods to build, manipulate,
 * and use instances of `Renderer` for rendering various types as strings.
 */
object Renderer {
//    val flog: Flog = Flog[Renderer]

  /**
   * Method which allows us to wrap a function as a Renderer.
   *
   * @param function the function to be wrapped. It has the same signature as this apply method so it is simply a syntactic convenience.
   * @tparam T the type to be rendered.
   * @return a Renderer[T].
   */
  def apply[T](function: (T, Format, StateR) => Try[String]): Renderer[T] = (t, f, s) => function(t, f, s)

  /**
   * Render the T value using the implicitly found Renderer[T].
   * Debug-log the renderer and its result.
   *
   * @param t      the value to be rendered.
   * @param format the Format.
   * @param stateR the StateR.
   * @tparam T the type of t.
   * @return a String.
   */
  def render[T: Renderer](t: T, format: Format, stateR: StateR): Try[String] =
    implicitly[Renderer[T]].render(t, format, stateR)

  /**
   * Method to create a lazy Renderer[T] from an explicit Renderer[T] which is call-by-name.
   * The purpose of this method is to break the infinite recursion caused when implicit values are defined
   * recursively.
   * See the Play JSON library method in JsPath called lazyRead.
   *
   * @param tr a Renderer[T].
   * @tparam T the underlying type of the Renderer required.
   * @return a Renderer[T].
   */
  def createLazy[T](tr: => Renderer[T]): Renderer[T] = (t: T, format: Format, stateR: StateR) => tr.render(t, format, stateR)

  /**
   * Determines an attribute name based on the given index and product element name, with an
   * option to override behavior using a flag.
   *
   * @param r       the instance of a Product from which the attribute name is determined.
   * @param index   the zero-based index of the element to inspect in the product.
   * @param useName a flag indicating whether to use the element name directly if no attribute
   *                extraction is possible; defaults to false.
   * @return an Option containing the determined attribute name as a String, or None if no valid
   *         attribute name can be resolved.
   */
  def maybeAttributeName[R <: Product](r: R, index: Int, useName: Boolean = false): Option[String] =
    r.productElementName(index) match {
      case "$" => None
      case Extractor.optionalAttribute(x) => Some(x)
      case Extractor.attribute(x) => Some(x)
      case x => if (useName) Some(x) else None
    }

  /**
   * Renders an attribute in a string format based on the given name and value.
   * If a name is provided in `maybeName`, the result will be formatted as `name="value"`.
   * Otherwise, only the value will be returned as a string.
   *
   * @param w         the value to be rendered as a `CharSequence`.
   * @param maybeName an optional string representing the attribute name.
   * @return a `Try[String]` containing the rendered attribute or an exception if the operation fails.
   */
  def renderAttribute(w: CharSequence, maybeName: Option[String]): Try[String] = Try {
    maybeName match {
      case Some(name) => s"""$name="$w""""
      case None => w.toString
    }
  }

  /**
   * Renders an outer object in a specific format by utilizing a given state and renderer.
   *
   * @param r           the product instance which provides the basis for generating state attributes.
   * @param objectOuter the outer object to be rendered, which must have an implicit Renderer in scope.
   * @param indexOuter  the index of the element within the product, used to determine naming context.
   * @param format      the format in which the outer object should be rendered.
   * @tparam R the type of the product, constrained to types extending `Product`, with an implicit `ClassTag`.
   * @tparam P the type of the outer object, which must have an implicit Renderer.
   * @return a `Try[String]` containing the rendered result or a failure if the operation fails.
   */
  private[render] def renderOuter[R <: Product : ClassTag, P: Renderer](r: R, objectOuter: P, indexOuter: Int, format: Format): Try[String] =
    TryUsing(StateR().setName(r, indexOuter)) {
      sr =>
        for {
          x <- Renderer.render(objectOuter, format, sr)
        } yield sr.getAttributes + x
    }

  /**
   * Method which is called at the end of each renderN method (above).
   * Its purpose is to combine the textual information held in the following three parameters:
   * <ol>
   * <li>stateR: attributes are placed here temporarily;</li>
   * <li>wInner: a String generated by a call to a renderN method with the next lower number;</li>
   * <li>wOuter: a String based on the last member of this Product being rendered.</li>
   *
   * @param formatIn      the format in which the Product should be rendered.
   * @param stateR        the state of the rendition.
   * @param wInner        a string based on the first n-1 members of the n-ary Product being rendered.
   * @param wOuter        a string based on the last (nth) member of the n-ary Product being rendered.
   * @param attributeName the name of the last member (used internally to distinguish between attributes and elements).
   * @tparam R the Product type.
   * @return a String.
   */
  private[render] def doNestedRender[R: ClassTag](formatIn: Format, stateR: StateR, wInner: String, wOuter: String, attributeName: String): Try[String] = {
    // XXX: determine if attributeName corresponds to an optional attribute--Some(true), an attribute--Some(false), or a non-attribute: None.
    val maybeAttribute = Extractor.inferAttributeType(attributeName)
    val format = if (maybeAttribute.isDefined || attributeName == "$" || attributeName == "") formatIn.flatten else formatIn
    // XXX: if maybeAttribute is defined, then isInternal will usually be true
    //      (the exception being for the last attribute of an all-attribute element).
    if (maybeAttribute.isDefined)
      stateR.addAttribute(wOuter)
    val sb = SmartBuffer()
    if (!stateR.isInternal) {
      sb.appendPadded(format.formatName(open = Some(true), stateR))
      sb.appendPadded(stateR.getAttributes)
      if (maybeAttribute.isEmpty) sb.append(format.formatName(open = None, stateR))
    }
    if (maybeAttribute.isEmpty) {
      sb.appendPadded(wInner)
      sb.appendPadded(wOuter)
    }
    if (!stateR.isInternal) {
      if (maybeAttribute.isDefined) sb.append(format.formatName(open = None, stateR))
      sb.append(format.formatName(open = Some(false), stateR))
    }
    Try(sb.result)
  }

  /**
   * Renders a sequence of elements using the provided renderer, format, and an optional name.
   *
   * @param rs        the sequence of elements to render, each of which must have an implicit Renderer in scope.
   * @param format    the format in which the sequence should be rendered.
   * @param maybeName an optional name used to provide additional context to the rendered output.
   * @tparam R the type of the elements in the sequence, constrained by the implicit Renderer.
   * @return a Try containing the rendered string representation of the sequence, or a failure in case of an error.
   */
  private[render] def doRenderSequence[R: Renderer](rs: Seq[R], format: Format, maybeName: Option[String]): Try[String] = {
    val formatIndented = format.indent
    val separator = format.sequencer(None)
    val sep = if (separator == "\n") format.newline else separator

    def trim(sy: Try[String]): Try[String] = sy map (_.trim)

    def formatSequence(ws: Seq[String]) = ws.mkString(formatIndented.sequencer(Some(true)), sep, format.sequencer(Some(false)))

    // If the separator is newline, and if the elements generated from rs is non-empty then we trim all elements except for the first.
    val elements = (separator, rs map (renderR(_)(format, StateR(maybeName)))) match {
      case ("\n", h :: t) => h :: (t map trim)
      case (_, x) => x
    }
    sequence(elements) map formatSequence
  }

  /**
   * Renders an object of type R using an implicitly provided Renderer[R].
   *
   * @param r      the object to be rendered.
   * @param format the format to render the object in.
   * @param stateR the state of the rendering process.
   * @tparam R the type of the object to be rendered, constrained by an implicit Renderer.
   * @return a Try containing the rendered string, or a failure if the rendering process encounters an error.
   */
  private def renderR[R: Renderer](r: R)(format: Format, stateR: StateR): Try[String] =
    TryUsing(stateR) {
    sr =>
      for {
        result <- Renderer.render(r, format, sr)
      } yield result
  }
}

/**
 * Case class intended to take care of the state of rendering.
 * Rendering is complex for several reasons:
 * (1) a method such as render5 invokes render4, render3, etc. in order to process all of the members of a Product.
 * (2) attributes are special and need to be rendered within the opening tag of the top-level element.
 *
 * NOTE: attributes is mutable (it's a SmartBuffer). It is retained as we do operations such as setName, recurse.
 * However, we must be careful to ensure that no attribute gets left behind.
 *
 * @param maybeName  an optional String.
 * @param attributes a (private) SmartBuffer: accessible via addAttribute or getAttributes.
 * @param interior   false if we are at the top level of an element; true if we have been invoked from above.
 */
case class StateR(maybeName: Option[String], private val attributes: SmartBuffer, interior: Boolean) extends AutoCloseable {

  /**
   * Method to create a new StateR with a different name.
   *
   * @param name the new name.
   * @return a new StateR.
   */
  def setName(name: String): StateR = maybeName match {
    case Some(_) => this
    case None => copy(maybeName = Some(name))
  }

  /**
   * Method to create a new StateR with interior set to true.
   *
   * @return a new StateR.
   */
  def recurse: StateR = copy(interior = true)

  /**
   * Mutating method to create a this StateR but with attrString appended to the attributes.
   * CONSIDER why is this a mutating method?
   *
   * @param attrString an attribute to add to this StateR.
   * @return a mutated version of this StateR.
   */
  def addAttribute(attrString: String): StateR = {
    attributes.appendPadded(attrString)
    this
  }

  /**
   * Mutating method to retrieve the attributes of this StateR and to clear the attributes at the same time.
   *
   * @return the attributes originally stored in this StateR.
   */
  def getAttributes: String = attributes.clear

  /**
   * Updates the name of the current `StateR` instance based on the provided
   * product `r` and its element at the specified `index`.
   *
   * @param r     a product instance from which the new name is derived.
   * @param index the index of the element in the product used to set the name.
   * @return a new `StateR` instance with the updated name.
   */
  def setName[R <: Product](r: R, index: Int): StateR = copy(maybeName = maybeAttributeName(r, index, useName = true))

  /**
   * Checks if the current state is internal.
   *
   * @return true if the state is internal, false otherwise.
   */
  def isInternal: Boolean = interior

  /**
   * Closes the current `StateR` instance.
   *
   * Logs a warning if the `attributes.result` string, after being trimmed, is not empty.
   *
   * @return Unit. The method does not return a value.
   */
  def close(): Unit = {
    // CONSIDER we shouldn't need to trim
    if (attributes.result.trim.nonEmpty) {
      logger.warn(s"StateR.close: attributes not empty: '$attributes'")
    }
  }

  /**
   * Retrieves the name of the class for the specified type `T` as a string.
   * If the class name exists, it uses the name directly. Otherwise, it generates
   * the class name by converting the first character of the runtime class name
   * to lowercase and appending the rest.
   * The reason for needing this mechanism is that:
   * (1) Scala classes begin with an upper-case letter;
   * (2) On rendering, the tag is taken from the attribute name (no adjustment required)
   * but when rendering an object at the top level,
   * we need to force the first character to be lower-case.
   *
   * Normally, if `interior` is true, then `maybeName` is also defined.
   * When `interior` is false, we tend to generate upper-case tags,
   * which isn't always appropriate.
   * But that situation mostly occurs during testing so it's not so important'
   *
   * @tparam T the type for which the class name is to be retrieved.
   * @return the class name of the specified type `T` in a string format.
   */
  def getClassName[T: ClassTag]: String = maybeName.getOrElse {
    val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName
    if (interior)
      className.substring(0, 1).toLowerCase + className.substring(1)
    else
      className
  }
}

/**
 * The `StateR` companion object provides factory methods for creating instances
 * of the `StateR` class.
 * These methods allow for the creation of a `StateR`
 * instance either with a specified name or with no name.
 */
object StateR {
  /**
   * Creates a new instance of `StateR` with the specified optional name.
   *
   * @param maybeName an optional string representing the name for the `StateR` instance.
   *                  If not provided, the instance will have no name.
   * @return a new `StateR` instance initialized with the provided name, default attributes, and top-level state.
   */
  def apply(maybeName: Option[String]): StateR = new StateR(maybeName, SmartBuffer(), interior = false)

  /**
   * Creates a new instance of `StateR` with no name.
   *
   * @return a new `StateR` instance with default attributes and top-level state.
   */
  def apply(): StateR = apply(None)
}

/**
 * The `Format` trait defines a blueprint for handling various formatting operations such as indentation,
 * flattening, delimiters, sequencing, and line breaks.
 * It is designed as a flexible interface to be
 * implemented by specific formatting strategies.
 */
trait Format {
  /**
   * Transforms the current format into a flat format, eliminating any structural nesting
   * or additional indentation rules.
   * This is useful for scenarios where a compact, single-line
   * layout is required, disregarding hierarchical formatting.
   *
   * @return A new `Format` instance that represents the flat version of the current format.
   */
  def flatten: Format

  /**
   * Adjusts the formatting of the content by applying an additional level of indentation.
   * This method increases the hierarchical structure or nesting level used during output formatting.
   *
   * @return A new `Format` instance with the updated indentation level applied.
   */
  def indent: Format

  /**
   * Represents the number of indentation levels used in the current formatting context.
   * This value determines the hierarchical structure applied during the formatting process.
   */
  val indents: Int

  /**
   * Formats the name based on the given options and state of rendering.
   *
   * @param open   An optional Boolean value that specifies whether the formatting should
   *               consider an open state.
   *               A `Some(true)` might indicate open, `Some(false)`
   *               a closed state, and `None` an undefined state.
   * @param stateR The current rendering state, represented as a `StateR` instance, containing
   *               details such as the name, attributes, and level of rendering invocation.
   * @tparam T The type parameter with an implicit `ClassTag`, typically used to provide
   *           runtime type information.
   * @return A `String` representing the formatted name based on the input parameters.
   */
  def formatName[T: ClassTag](open: Option[Boolean], stateR: StateR): String

  /**
   * Retrieves the default delimiter used for separating elements in formatted content.
   *
   * @return A `String` representing the default delimiter, typically a comma followed by a space (", ").
   */
  def delimiter: String = ", "

  /**
   * Determines the sequence format based on the provided open state.
   *
   * @param open An optional Boolean value that specifies whether the sequence
   *             should consider an open state.
   *             `Some(true)` might indicate open,
   *             `Some(false)` a closed state, and `None` an undefined state.
   * @return A String representing the sequence format based on the input state.
   */
  def sequencer(open: Option[Boolean]): String

  /**
   * Retrieves the newline character or sequence used by the current format.
   * This is typically used to control line breaks in formatted output.
   *
   * @return A `String` representing the newline character or sequence.
   */
  def newline: String
}

/**
 * The `BaseFormat` abstract class provides a foundational implementation of common formatting operations,
 * extending the `Format` trait.
 * It includes methods and definitions for handling indentation, newline formatting,
 * and rendering class names within a specific rendering state.
 *
 * @param indents The number of indent levels to be used in formatting, affecting the structure of the formatted output.
 */
abstract class BaseFormat(indents: Int) extends Format {
  /**
   * Represents the name associated with the `BaseFormat` class or its subclasses.
   * It is expected to provide an identity or label that can be utilized in formatting operations
   * or for rendering purposes within the implementation.
   */
  val name: String

  /**
   * Returns this Format unaffected..
   *
   * @return The current `Format` instance, representing the flat version of the format.
   */
  def flatten: Format = this

  /**
   * Generates a newline character followed by a string of spaces based on the current indent level.
   * The indentation is determined by the number of `indents` multiplied by a predefined tab unit.
   *
   * @return A formatted string consisting of a newline character followed by the appropriate level of indentation.
   */
  def newline: String = "\n" + (tab * indents)

  private lazy val tab = "  "
}

/**
 * The `FormatXML` case class provides a specialized implementation for formatting content
 * in XML-like structures.
 * It inherits from `BaseFormat` and offers additional functionality
 * for handling XML-specific formatting operations, including indentation, flattening,
 * delimiters, and opening/closing tag formatting.
 *
 * @param indents The number of indentation levels to be applied in formatting.
 * @param flat    A boolean flag indicating whether the format should be flattened,
 *                eliminating structural indentation.
 */
case class FormatXML(indents: Int, flat: Boolean = false) extends BaseFormat(indents) {

  /**
   * Represents the name of the format associated with this case class.
   * This value is used to identify the formatting type, specifically "FormatXML".
   */
  val name: String = "FormatXML"

  /**
   * Transforms the current `FormatXML` instance into a flat format by setting the `flat` flag to `true`.
   * This eliminates hierarchical indentation, resulting in a single-line layout.
   *
   * @return A new `Format` instance with the `flat` property set to `true`.
   */
  override def flatten: Format = copy(flat = true)

  /**
   * Increases the indentation level by 1, creating a new `Format` instance with the updated indentation level.
   *
   * @return A new `Format` instance with the `indents` value incremented by 1.
   */
  def indent: Format = copy(indents = indents + 1)

  /**
   * Provides the delimiter string used for formatting in the `FormatXML` case class.
   * The delimiter defines the space between elements in the formatted XML output.
   *
   * @return A string representing the delimiter, which is a single space.
   */
  override def delimiter: String = " "

  /**
   * Formats the name based on the given rendering state and the optional opening or closing tag context.
   *
   * @param open   An optional Boolean indicating the context of formatting:
   *               Some(true) for an opening tag,
   *               Some(false) for a closing tag,
   *               or None for an inline separator.
   * @param stateR The rendering state that may include an optional name or other formatting context.
   * @tparam T The type whose class name may be retrieved if no name is provided in the rendering state.
   * @return A formatted string representing the name or tag, appropriately formatted based on the given state and context.
   */
  def formatName[T: ClassTag](open: Option[Boolean], stateR: StateR): String = {
    val name = stateR.getClassName
    open match {
      case Some(true) => (if (indents > 0) newline else "") + s"<$name"
      case Some(false) => (if (flat) "" else newline) + s"</$name>"
      case None => ">"
    }
  }

  /**
   * Determines the appropriate string separator based on the provided optional boolean value.
   *
   * @param open An optional Boolean value:
   *             Some(true) or Some(false) returns an empty string,
   *             None returns a newline character ("\n").
   * @return A string representing the separator for formatting, either "" or "\n".
   */
  def sequencer(open: Option[Boolean]): String = open match {
    case Some(_) => ""
    case _ => "\n"
  }
}

/**
 * Companion object for the `FormatXML` class.
 *
 * Provides factory methods to create instances of `FormatXML` with specified or default indentation levels.
 */
object FormatXML {
  /**
   * Creates a new `FormatXML` instance with a specified number of indentation levels.
   *
   * @param indents The number of indentation levels to apply for the `FormatXML` instance.
   * @return A new `FormatXML` instance configured with the specified indentation level.
   */
  def apply(indents: Int): FormatXML = new FormatXML(indents)

  /**
   * Creates a new `FormatXML` instance with the default indentation level of 0.
   *
   * @return A new `FormatXML` instance configured with the default indentation level.
   */
  def apply(): FormatXML = apply(0)
}

/**
 * `FormatText` is a case class that extends the functionality of `BaseFormat` for
 * text-based formatting purposes.
 * It provides mechanisms for handling hierarchical
 * indentation, formatting class names within a rendering state, and generating
 * sequences or enclosed structures based on an open/close state.
 *
 * @param indents The number of initial indentation levels to apply.
 */
case class FormatText(indents: Int) extends BaseFormat(indents) {
  /**
   * The name of the format, specifically associated with this formatter.
   * `name` represents the type of the formatter and is initialized as "FormatText".
   */
  val name: String = "FormatText"

  /**
   * Increases the indentation level by one.
   *
   * @return A new `Format` instance with the updated indentation level.
   */
  def indent: Format = copy(indents = indents + 1)

  /**
   * Formats the name of a class or type based on the provided `StateR` instance and an optional open/close state.
   *
   * @param open   An optional boolean indicating whether to open (`true`), close (`false`), or perform no action (`None`).
   * @param stateR The current rendering state (`StateR`), which may include the name or other attributes used for formatting.
   * @tparam T The type whose class name is being formatted, used in conjunction with `StateR`.
   * @return A formatted string representing the class name, including any open or close indicators based on the `open` parameter.
   */
  def formatName[T: ClassTag](open: Option[Boolean], stateR: StateR): String =
    open match {
      case Some(true) => s"${stateR.getClassName}{"
      case Some(false) => "}"
      case None => ""
  }

  /**
   * Generates a sequence delimiter based on the provided optional boolean value.
   *
   * @param open An optional boolean indicating the type of delimiter:
   *             `Some(true)` returns an opening bracket "[",
   *             `Some(false)` returns a closing bracket "]",
   *             and `None` returns a newline character.
   * @return A string representing the appropriate delimiter based on the input.
   */
  def sequencer(open: Option[Boolean]): String = open match {
    case Some(true) => "["
    case Some(false) => "]"
    case None => "\n"
  }
}

// TESTME
case class FormatIndented(indents: Int) extends BaseFormat(indents) {
  val name: String = "FormatIndented"

  def indent: Format = copy(indents = indents + 1)

  def formatName[T: ClassTag](open: Option[Boolean], stateR: StateR): String = open match {
    case Some(true) => "{"
    case Some(false) => "}"
    case None => ""
  }

  def sequencer(open: Option[Boolean]): String = open match {
    case Some(true) => "["
    case Some(false) => "]"
    case None => ", "
  }
}
