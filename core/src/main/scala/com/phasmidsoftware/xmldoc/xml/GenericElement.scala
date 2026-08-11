package com.phasmidsoftware.xmldoc.xml

import com.phasmidsoftware.xmldoc.core.XmlException
import com.phasmidsoftware.xmldoc.render.{Format, Renderer}

import scala.util.{Failure, Success, Try}

/**
 * A fully generic representation of XML content: used for anything that doesn't have
 * (or doesn't need) a specialist type. `GenericElement` carries a tag, its attributes,
 * and its children — any of which may themselves be unmodeled elements, or text.
 *
 * The "mixing" of specialist and generic content happens one level up, in whatever
 * container type models a specific element (e.g. a Story's content might be
 * `Seq[Either[ParagraphStyleRange, GenericElement]]`) — GenericElement itself doesn't
 * know or care whether its parent is specialist or generic.
 *
 * `fromNode`/`toXmlString` are hand-written rather than built from the `Extractor`/`Renderer`
 * combinators (`extractor1`...`extractor7`, `renderer1`...`renderer7`): those all assume a
 * fixed, known number of fields, each mapped by name to one specific attribute or child —
 * a "capture whatever's left over" field, of unbounded/unnamed size, isn't something they can
 * express (the parallel is a fixed-arity method versus a varargs method). That said,
 * `GenericElement` still has ordinary `Extractor[GenericElement]`/`Renderer[GenericElement]`
 * instances below, delegating to `fromElem`/`toXmlString` — these let `GenericElement` be used
 * as a normal field type within an otherwise-specialist `extractorN`/`rendererN`, for a
 * specific, named field that's deliberately left generic (as opposed to modeling an unbounded
 * remainder, which these instances don't help with).
 *
 * NOTE: `scala.xml.Comment` and `scala.xml.EntityRef` nodes are not treated specially and
 * fall through to `GenericText` via their `.text` value, which loses their distinct node
 * type (a comment would round-trip as plain escaped text, not a comment) — this is judged
 * an acceptable gap because InDesign-generated IDML content does not appear to contain
 * either. `scala.xml.PCData` (CDATA sections) and `scala.xml.Text` are handled properly.
 */
sealed trait GenericContent

case class GenericElement(
  tag: String, // the qualified name as written, e.g. "idPkg:Story" — no namespace-URI decomposition
  attributes: Seq[(String, String)], // ordered, so round-tripping doesn't reshuffle them
  children: Seq[GenericContent]
) extends GenericContent {

  /**
   * The element children only, skipping any interleaved text/CDATA (e.g. whitespace
   * indentation between sibling elements).
   */
  def childElements: Seq[GenericElement] = children.collect { case e: GenericElement => e }
}

case class GenericText(text: String) extends GenericContent

case class GenericCData(text: String) extends GenericContent // preserves CDATA-ness on round-trip

object GenericElement {

  /**
   * Method to convert a `scala.xml.Node` into a `GenericContent`, recursively.
   *
   * NOTE on namespaces: `Elem.label` is only the local name (e.g. "Story" for "idPkg:Story"),
   * and an `xmlns:prefix="uri"` declaration is carried in `Elem.scope`, not in `.attributes` —
   * so both have to be handled specially to avoid silently losing the "idPkg:" prefix (used
   * throughout IDML's designmap.xml) or the namespace declaration itself. Whichever bindings
   * in `e.scope` are not already visible in `inheritedScope` are re-materialized here as
   * ordinary-looking `xmlns[:prefix]="uri"` attributes, so the output is self-contained.
   *
   * @param node           the node to convert (typically an `Elem`, but any `Node` is accepted).
   * @param inheritedScope the namespace bindings already in scope from an enclosing element;
   *                       defaults to none, for a top-level call.
   * @return a `GenericContent`: a `GenericElement` for an `Elem`, `GenericCData` for a CDATA
   *         section, otherwise `GenericText`.
   */
  def fromNode(node: scala.xml.Node, inheritedScope: scala.xml.NamespaceBinding = scala.xml.TopScope): GenericContent = node match {
    case e: scala.xml.Elem =>
      val tag = Option(e.prefix).fold(e.label)(p => s"$p:${e.label}")
      val nsAttrs = newBindings(e.scope, inheritedScope).map {
        case (prefix, uri) => (if (prefix == null) "xmlns" else s"xmlns:$prefix") -> uri
      }
      val attrs = nsAttrs ++ e.attributes.toSeq.map(m => m.key -> m.value.text)
      GenericElement(tag, attrs, e.child.map(fromNode(_, e.scope)))
    case pcdata: scala.xml.PCData => GenericCData(pcdata.text)
    case other => GenericText(other.text)
  }

  /**
   * As `fromNode`, but for callers that know (as `scala.xml.XML.loadString` callers always do,
   * since it returns `Elem` specifically, not the more general `Node`) that the result must be
   * a `GenericElement`, avoiding a cast at every call site.
   *
   * @param elem the element to convert.
   * @return a `GenericElement`.
   */
  def fromElem(elem: scala.xml.Elem, inheritedScope: scala.xml.NamespaceBinding = scala.xml.TopScope): GenericElement =
    (fromNode(elem, inheritedScope): @unchecked) match {
      case e: GenericElement => e
    }

  // Bindings present in `scope` but not yet in `stopAt` (its enclosing scope), i.e. the ones
  // this specific element introduces. Relies on scala.xml reusing the same NamespaceBinding
  // instance for inherited (unchanged) scope, so recursion terminates via reference equality.
  private def newBindings(scope: scala.xml.NamespaceBinding, stopAt: scala.xml.NamespaceBinding): Seq[(String, String)] =
    if (scope == null || (scope eq stopAt)) Nil
    else (scope.prefix, scope.uri) +: newBindings(scope.parent, stopAt)

  /**
   * Method to render a `GenericContent` back into an XML string.
   *
   * NOTE: this is not guaranteed to be byte-identical to whatever text originally produced
   * the equivalent `GenericContent` (e.g. attribute-quoting style and entity-encoding choices
   * are normalized), but it is semantically equivalent and round-trips losslessly through
   * `fromNode` again (i.e. `fromNode(parse(toXmlString(g))) == g`).
   *
   * @param content the content to render.
   * @return an XML string.
   */
  def toXmlString(content: GenericContent): String = content match {
    case GenericText(text) => scala.xml.Utility.escape(text)
    case GenericCData(text) => s"<![CDATA[${escapeCData(text)}]]>"
    case GenericElement(tag, attrs, Seq()) =>
      s"<$tag${renderAttrs(attrs)}/>"
    case GenericElement(tag, attrs, children) =>
      s"<$tag${renderAttrs(attrs)}>${children.map(toXmlString).mkString}</$tag>"
  }

  private def renderAttrs(attrs: Seq[(String, String)]): String =
    attrs.map { case (k, v) => s""" $k="${escapeAttrValue(v)}"""" }.mkString

  // XML 1.0 attribute-value normalization replaces any *literal* tab/newline/CR character in
  // an attribute value with a plain space when parsed - only a character reference (e.g. &#9;)
  // survives intact. scala.xml.Utility.escape doesn't touch these, so without this, a real tab
  // (as seen in designmap.xml's EndnoteSeparatorText="&#x9;") would silently become a space.
  private def escapeAttrValue(v: String): String =
    scala.xml.Utility.escape(v).replace("\t", "&#9;").replace("\n", "&#10;").replace("\r", "&#13;")

  // A CDATA section can't contain its own terminator literally, so any occurrence of "]]>"
  // must be split across adjacent CDATA sections: "]]" + "]]><![CDATA[" + ">" reassembles to
  // the same content once all sections are concatenated back together.
  private def escapeCData(text: String): String = text.replace("]]>", "]]]]><![CDATA[>")

  /**
   * An `Extractor[GenericElement]`, for use as an ordinary field type within an
   * otherwise-specialist `extractorN`, wherever a specific, named field is deliberately left
   * generic. Fails if the node isn't an element (e.g. it's a bare text node).
   */
  implicit val extractor: Extractor[GenericElement] = Extractor {
    case e: scala.xml.Elem => Success(fromElem(e))
    case other => Failure(XmlException(s"GenericElement extractor: expected an element, got: $other"))
  } ^^ "genericElementExtractor"

  /**
   * A `Renderer[GenericElement]`, the counterpart to `extractor`, delegating to `toXmlString`.
   */
  implicit val renderer: Renderer[GenericElement] = Renderer[GenericElement] {
    (t, _, _) => Success(toXmlString(t))
  } ^^ "genericElementRenderer"
}
