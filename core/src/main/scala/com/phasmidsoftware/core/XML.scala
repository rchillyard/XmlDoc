package com.phasmidsoftware.core

import com.phasmidsoftware.xml.{Extractor, Extractors}

import scala.collection.mutable
import scala.util.{Success, Try}
import scala.xml.{Atom, Node}

/**
 * The `XML` object does nothing other than give a name to this module.
 */
object XML

/**
 * Represents a text element that wraps a CharSequence and provides functionality for
 * merging text and matching content.
 *
 * @constructor Creates a new Text instance.
 * @param $ the wrapped CharSequence representing the text content.
 */
case class Text($: CharSequence) extends Mergeable[Text] {
  /**
   * Method to merge this and another Text element.
   *
   * @param t the object to be merged with this.
   * @return the merged value of T.
   */
  infix def merge(t: Text, mergeName: Boolean = true): Option[Text] = ($, t.$) match {
    case (c1: CDATA, c2: CDATA) => c1 merge c2 map (Text(_))
    case _ => Some(Text($.toString + " " + t.$.toString))
  }

  /**
   * Method to determine if this Text matches the given name.
   *
   * @param name the name to match
   * @return true if the this matches name.
   */
  def matches(name: String): Boolean = $ match {
    case c: CDATA => c.content == name
    case "" => false
    case x: CharSequence => x.toString == name
  }

  override def equals(obj: Any): Boolean = obj match {
    case text: Text => $ == text.$
    case _ => false
  }
}

/**
 * Companion object for the `Text` case class. Provides extractor utilities for `Text` and `Option[Text]`.
 */
object Text extends Extractors {

  /**
   * Text extractor.
   */
  implicit val extractor: Extractor[Text] = extractor10(apply) ^^ "text extractor"

  /**
   * Optional text extractor.
   */
  implicit val extractorOptionalText: Extractor[Option[Text]] = extractor.lift

}

/**
 * Object TagProperties provides functionality to manage and query a collection
 * of tags that must match a specified condition.
 *
 * This object allows adding tags to an internal collection and checking
 * whether a given tag exists within that collection.
 */
object TagProperties {
  /**
   * Adds a specified tag to the internal collection of tags that must match.
   *
   * @param tag the tag to be added to the must-match list
   * @return Unit
   */
  def addMustMatch(tag: String): Unit = mustMatchList += tag

  /**
   * Checks if the specified tag exists in the collection of tags that must match.
   *
   * @param tag the tag to check for a match in the must-match list
   * @return true if the tag exists in the must-match list, false otherwise
   */
  def mustMatch(tag: String): Boolean = mustMatchList.contains(tag)

  private val mustMatchList = mutable.Set[String]()
}

/**
 * Case class to represent a CDATA node.
 *
 * @param content the payload of the CDATA node (will contain <, >, & characters).
 * @param pre     the prefix (probably a newline).
 * @param post    the postfix (probably a newline).
 */
case class CDATA(content: String, pre: String, post: String) extends CharSequence with Mergeable[CDATA] {
  /**
   * Converts the CDATA node with its content and surrounding context into an XML-compatible string.
   *
   * @return a Try containing the resulting XML string, or a failure if the operation could not be completed.
   */
  def toXML: Try[String] = Success(s"""$pre<![CDATA[$content]]>$post""")

  /**
   * Computes the length of the content string of this CDATA node.
   *
   * @return the number of characters in the content string.
   */
  def length(): Int = content.length()

  /**
   * Returns the character at the specified index in the content string of this CDATA node.
   *
   * @param index the index of the character to be returned, starting from 0.
   * @return the character at the specified index in the content string.
   */
  def charAt(index: Int): Char = content.charAt(index)

  /**
   * Returns a subsequence of the content string of this CDATA node, starting from the specified
   * start index and ending at the specified end index (exclusive).
   *
   * @param start the starting index of the subsequence, inclusive.
   * @param end   the ending index of the subsequence, exclusive.
   * @return a CharSequence that is a subsequence of the content string.
   */
  def subSequence(start: Int, end: Int): CharSequence = content.subSequence(start, end)

  override def toString: String = s"$pre$content$post"

  /**
   * Merge this mergeable object with <code>t</code>.
   *
   * @param t the object to be merged with this.
   * @return the merged value of T.
   */
  infix def merge(t: CDATA, mergeName: Boolean = true): Option[CDATA] = Some(CDATA(content + separator(post, t.pre) + t.content, pre, t.post))

  /**
   * Concatenates two strings and replaces newline characters with "<br>".
   *
   * @param a the first string to be concatenated.
   * @param b the second string to be concatenated.
   * @return a new string resulting from the concatenation of a and b, with all newline characters replaced by "<br>".
   */
  private def separator(a: String, b: String): String = (a + b).replaceAll("\n", "<br>")
}

/**
 * Companion object to CDATA.
 */
object CDATA {

  /**
   * Constructs a new CDATA instance with the provided content and trimmed prefix and postfix strings.
   *
   * @param x    the string content of the CDATA node.
   * @param pre  the prefix string, typically surrounding whitespace or newlines.
   * @param post the postfix string, typically surrounding whitespace or newlines.
   * @return a new instance of the CDATA case class with trimmed prefix and postfix.
   */
  def apply(x: String, pre: String, post: String): CDATA = new CDATA(x, trimSpace(pre), trimSpace(post))

  /**
   * Constructs a new CDATA instance with the provided content and default empty prefix and postfix strings.
   *
   * @param x the string content of the CDATA node.
   * @return a new instance of the CDATA case class with default empty prefix and postfix.
   */
  def apply(x: String): CDATA = apply(x, "", "")

  /**
   * Constructs a new CDATA instance with the provided content and default prefix and postfix strings
   * set to newlines ("\n").
   *
   * @param x the string content of the CDATA node.
   * @return a new instance of the CDATA case class with default prefix and postfix strings set to newlines.
   */
  def wrapped(x: String): CDATA = apply(x, "\n", "\n")

  /**
   * Extracts a CDATA instance from a given XML `Node`, if the node contains a matching CDATA structure.
   *
   * @param node the XML node to be analyzed for CDATA content.
   * @return an `Option` containing the CDATA instance if the node matches the CDATA pattern; otherwise, `None`.
   */
  def unapply(node: Node): Option[CDATA] = node.child match {
      case Seq(pre: Node, PCData(x), post: Node) =>
        Some(CDATA(x, pre.text, post.text))
      case Seq(PCData(x)) =>
        Some(CDATA(x))
      case _ =>
      None
    }

  /**
   * Trims trailing spaces from the given string using a `StringBuilder`.
   *
   * @param w the input string to be trimmed of trailing spaces
   * @return the trimmed string with no trailing spaces
   */
  private def trimSpace(w: String): String = {
    val sb = new StringBuilder(w)
    SmartBuffer.trimStringBuilder(sb)
    sb.toString()
  }
}

/**
 * This class borrowed from Scala XML library as we rely on it but it is no longer used by Scala 3.
 *
 * This class (which is not used by all XML parsers, but always used by the
 * XHTML one) represents parseable character data, which appeared as CDATA
 * sections in the input and is to be preserved as CDATA section in the output.
 *
 * @author Burak Emir
 */
// Note: used by the Scala compiler (before Scala 3).
class PCData(data: String) extends Atom[String](data) {

  /**
   * Returns text, with some characters escaped according to the XML
   * specification.
   *
   * @param sb the input string buffer associated to some XML element
   * @return the input string buffer with the formatted CDATA section
   */
  override def buildString(sb: StringBuilder): StringBuilder = {
    val dataStr: String = data.replaceAll("]]>", "]]]]><![CDATA[>")
    sb.append(s"<![CDATA[$dataStr]]>")
  }
}

/**
 * This singleton object contains the `apply`and `unapply` methods for
 * convenient construction and deconstruction.
 * Taken from scala.xml.PCData object.
 *
 * @author Burak Emir
 *
 *         Note: used by the Scala compiler (before Scala 3).
 */
object PCData {
  def apply(data: String): PCData = new PCData(data)

  // NOTE that this pattern never succeeds if there is a newline in the text.
  private val pcdataPattern = """(.*[<>&]+.*)/s""".r

  def unapply(node: Node): Option[String] =
    node.text match {
      case pcdataPattern(data) =>
        Some(data)
      case x: String if x.contains('<') | x.contains('>') | x.contains('&') =>
        Some(x)
      case _ => None
    }
}

/**
 * A base exception class for handling XML-related errors.
 *
 * This class serves as a parent exception for all XML-specific exceptions, allowing
 * developers to catch and manage errors pertaining to XML processing.
 * It extends the standard `Exception` class in Scala to provide additional context
 * for XML-related errors through custom messages and causes.
 *
 * @param message A detailed error message describing the cause of the exception.
 * @param cause   The underlying throwable that triggered this exception, if available.
 */
class XmlBaseException(message: String, cause: Throwable) extends Exception(message, cause)

/**
 * CONSIDER renaming as ExtractorException.
 *
 * @param message the message.
 * @param cause   the cause (or null).
 */
case class XmlException(message: String, cause: Throwable) extends XmlBaseException(message, cause)

/**
 * Companion object for the XmlException case class.
 * Provides a utility method to create an instance of XmlException.
 *
 * The apply method creates an XmlException with the specified message,
 * and optionally allows the cause to be set to null by default.
 */
object XmlException {
  /**
   * Creates an instance of XmlException with the specified message and a null cause.
   *
   * @param message the error message describing the exception.
   * @return an instance of XmlException with the given message and a null cause.
   */
  def apply(message: String): XmlException = apply(message, null)
}

/**
 * Represents an exception thrown when a required field is missing during an XML-related operation.
 *
 * This exception indicates that an expected field was absent, typically during parsing
 * or validation of XML content.
 * It provides additional context regarding the missing field
 * and the reason for its absence through custom error messages.
 *
 * @param message A detailed error message describing the missing field.
 * @param reason  A string explaining the reason for the field's absence.
 * @param cause   The underlying throwable that caused this exception, if available.
 */
case class MissingFieldException(message: String, reason: String, cause: Throwable) extends XmlBaseException(s"$message ($reason)", cause)