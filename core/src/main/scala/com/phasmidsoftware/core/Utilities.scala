package com.phasmidsoftware.core

import cats.effect.IO

import scala.collection.mutable
import scala.util.matching.Regex
import scala.xml.{Elem, Node, NodeSeq}

/**
 * Object containing various utility methods for sequence filtering, XML parsing,
 * and rendering XML nodes as strings.
 * CONSIDER splitting this object among FP and XML.
 */
object Utilities {

  /**
   * Method to filter a sequence of object according to a value and a lens function.
   *
   * CONSIDER is there a method in the standard library which does this?
   *
   * @param lens the function which will extract an A from a T.
   * @param a    the value that must be matched.
   * @param ts   the sequence to be filtered.
   * @tparam T the underlying type of the input and output sequences.
   * @tparam A the type of the value to be matched.
   * @return the filtered version of the sequence.
   */
  def lensFilter[T, A](lens: T => A)(a: A)(ts: Seq[T]): Seq[T] = ts filter (t => lens(t) == a)

  /**
   * The purpose of this method is to allow a String to be parsed as an XML entity, WITHOUT replace " by &quot;
   *
   * @param w the XML string to be parsed.
   * @return an XML element.
   */
  def parseUnparsed(w: String): Elem = {
    val unparsed = scala.xml.Unparsed(w) // NOTE: unparsed really is used (ignore warning).
    <xml>$unparsed</xml>
  }

  /**
   * Renders a detailed representation of an XML node, including its attributes, descendants,
   * and optionally its children in a deep or shallow manner.
   *
   * @param node the XML node to be rendered.
   * @param deep a boolean indicating if children should be rendered recursively. Defaults to false.
   * @return a string containing the rendered representation of the XML node.
   */
  def renderNode(node: Node, deep: Boolean = false): String = {
    // CONSIDER use SmartBuffer
    val result = new mutable.StringBuilder("node: ")
    result.append(s"label=${node.label}, ")
    result.append(s"length=${node.length}, ")
    result.append(s"descendants=${node.descendant.size}, ")
    result.append(s"attributes=${node.attributes.mkString}, ")
    val children = node.child map (if (deep) renderNode(_, deep) else renderNodeBrief)
    result.append(s"children=${children.mkString("{", ",", "}")}")
    result.toString()
  }

  /**
   * Renders a sequence of XML nodes into a single formatted string.
   *
   * @param nodes the sequence of XML nodes to be rendered.
   * @return a string representation of the rendered XML nodes.
   */
  def renderNodes(nodes: NodeSeq): String = (for (node <- nodes) yield renderNode(node)).mkString("{", ",", "}")

  /**
   * Prints the rendered representation of an XML node using the provided function.
   *
   * @param node the XML node to be rendered and displayed.
   * @param f    the function used to output the rendered node as a string. Defaults to `println`.
   * @return an `IO[Unit]` representing the execution of the display action.
   */
  def show(node: Node)(f: String => Unit = println): IO[Unit] = IO(f(renderNode(node)))

  /**
   * Renders a brief representation of an XML node by returning its label.
   *
   * @param node the XML node to be rendered.
   * @return the label of the XML node as a string.
   */
  private def renderNodeBrief(node: Node): String = node.label
}


/**
 * A class that extends the standard Regex functionality by allowing a transformation
 * to be applied to the sequence of matched groups.
 * This is achieved by providing a
 * function that maps a list of matched groups to a new transformed list.
 *
 * @constructor Creates a MappableRegex with a given regular expression pattern and a transformation function.
 * @param r The regular expression pattern as a string.
 * @param f A function that takes a list of matched groups and transforms it into another list.
 */
class MappableRegex(r: String, f: List[String] => List[String]) extends Regex(r) {
  /**
   * Extracts matched groups from the provided character sequence and applies a transformation
   * function to the list of matched groups.
   *
   * @param s The character sequence to match against the regular expression.
   * @return An Option containing a transformed list of matched groups if the input matches
   *         the regular expression, or None if there is no match.
   */
  override def unapplySeq(s: CharSequence): Option[List[String]] =
    super.unapplySeq(s) map f
}

/**
 * A specialized regular expression class that extends `MappableRegex` to provide
 * functionality where the first character of each matched group is converted to
 * lowercase, while the rest of the characters remain unchanged.
 *
 * @constructor Creates a `LowerCaseInitialRegex` instance with a specified regular expression
 *              pattern to apply the lowercase transformation to matched groups.
 * @param r The regular expression pattern as a string.
 */
class LowerCaseInitialRegex(r: String) extends MappableRegex(r, ws => ws map { w => s"${w.head.toLower}${w.tail}" })
