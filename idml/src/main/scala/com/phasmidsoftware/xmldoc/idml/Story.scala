package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.core.XmlException
import com.phasmidsoftware.xmldoc.xml.{GenericContent, GenericElement}

import scala.util.{Failure, Success, Try}

/**
 * The first specialist type in the mixed parsing system described for `idml`: models just the
 * `Self` identity of an IDML `<Story>` element, leaving everything else (style ranges, content,
 * preferences) generic for now.
 *
 * `Self` is InDesign's own stable identifier for an object across saves, and is the central
 * open question for the eventual three-way merge work: whether it survives independent edits
 * to the same base document. Starting the specialist-type system here, rather than with
 * something structurally richer, means the first real specialist type directly exercises the
 * thing that actually matters.
 *
 * @param self       the Self identity attribute, e.g. "ue6".
 * @param attributes the element's other attributes (Self removed, so there's only one place
 *                   that holds it).
 * @param children   the element's children, still fully generic.
 */
case class Story(self: String, attributes: Seq[(String, String)], children: Seq[GenericContent])

object Story {

  /**
   * Method to extract a `Story` from a `GenericElement`, provided it is tagged "Story" and has
   * a `Self` attribute.
   *
   * @param e the element to convert.
   * @return a Try of Story.
   */
  def fromGenericElement(e: GenericElement): Try[Story] =
    if (e.tag != "Story") Failure(XmlException(s"Story.fromGenericElement: not a Story element: ${e.tag}"))
    else e.attributes.collectFirst { case ("Self", self) => self } match {
      case Some(self) => Success(Story(self, e.attributes.filterNot(_._1 == "Self"), e.children))
      case None => Failure(XmlException("Story.fromGenericElement: no Self attribute"))
    }

  /**
   * Method to convert a `Story` back into a `GenericElement`, e.g. for rendering.
   *
   * @param story the Story to convert.
   * @return a GenericElement tagged "Story", with Self restored as its first attribute
   *         (matching where InDesign itself always places it).
   */
  def toGenericElement(story: Story): GenericElement =
    GenericElement("Story", ("Self", story.self) +: story.attributes, story.children)
}
