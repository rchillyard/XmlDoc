package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.core.{FP, XmlException}
import com.phasmidsoftware.xmldoc.xml.{GenericElement, Zip}

import java.io.File
import scala.util.{Failure, Try}

/**
 * A reference to a package part declared in designmap.xml via an `idPkg:*` element, e.g.
 * `<idPkg:Story src="Stories/Story_ue6.xml"/>`.
 *
 * @param partType the local part type, e.g. "Story", "Spread", "MasterSpread", "Graphic".
 * @param src      the path of the referenced entry within the zip, e.g. "Stories/Story_ue6.xml".
 */
case class PackagePart(partType: String, src: String)

/**
 * An open IDML package: a zip file plus its designmap.xml manifest, parsed only as far as
 * discovering the other parts it references — everything is still fully generic at this
 * stage (`GenericElement`), since no specialist types exist yet.
 *
 * @param file      the underlying .idml file.
 * @param designmap the parsed content of designmap.xml.
 */
class IdmlPackage private(file: File, val designmap: GenericElement) {

  /**
   * The package parts referenced from designmap.xml, i.e. its direct `idPkg:*` children.
   * Every such reference observed in practice is a childless element with only a `src`
   * attribute (e.g. `<idPkg:Spread src="Spreads/Spread_ud1.xml"/>`).
   */
  lazy val parts: Seq[PackagePart] =
    designmap.children.collect {
      case e: GenericElement if e.tag.startsWith("idPkg:") =>
        e.attributes.collectFirst { case ("src", src) => PackagePart(e.tag.stripPrefix("idPkg:"), src) }
    }.flatten

  /**
   * Method to load and generically parse a specific part, by its `src` path as it appears in
   * `parts` (e.g. "Stories/Story_ue6.xml").
   *
   * @param src the path of the entry within the zip.
   * @return a Try of GenericElement.
   */
  def loadPart(src: String): Try[GenericElement] =
    Zip.readEntry(file, src).map(text => GenericElement.fromElem(scala.xml.XML.loadString(text)))

  /**
   * Method to load and generically parse every part of a given type (e.g. "Story").
   *
   * @param partType the part type to filter on, e.g. "Story", "Spread".
   * @return a Try of Seq[GenericElement], one per matching part, in designmap order.
   */
  def loadParts(partType: String): Try[Seq[GenericElement]] =
    FP.sequence(parts.filter(_.partType == partType).map(p => loadPart(p.src)))

  /**
   * Method to load a Story part and extract its Self identity and the rest of its content.
   *
   * @param src the path of the Story entry within the zip (e.g. "Stories/Story_ue6.xml").
   * @return a Try of Story.
   */
  def loadStory(src: String): Try[Story] =
    loadPart(src).flatMap {
      wrapper =>
        wrapper.childElements.find(_.tag == "Story") match {
          case Some(e) => Story.fromGenericElement(e)
          case None => Failure(XmlException(s"IdmlPackage.loadStory: no Story child found in $src"))
        }
    }

  /**
   * Method to load every Story part referenced from designmap.xml.
   *
   * @return a Try of Seq[Story], in designmap order.
   */
  def loadStories: Try[Seq[Story]] =
    FP.sequence(parts.filter(_.partType == "Story").map(p => loadStory(p.src)))
}

object IdmlPackage {

  /**
   * Method to open an IDML package and parse just its designmap.xml manifest.
   *
   * @param file the .idml file.
   * @return a Try of IdmlPackage.
   */
  def open(file: File): Try[IdmlPackage] =
    Zip.readEntry(file, "designmap.xml").map(text => new IdmlPackage(file, GenericElement.fromElem(scala.xml.XML.loadString(text))))
}
