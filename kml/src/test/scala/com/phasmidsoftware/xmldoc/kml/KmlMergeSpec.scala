package com.phasmidsoftware.xmldoc.kml

import com.phasmidsoftware.xmldoc.merge.{ContentMatcher, Pcs, PcsMerger, PcsTreeBuilder}
import com.phasmidsoftware.xmldoc.xml.{GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.xml.XML

/**
 * The first test of the generic merge layer (`Pcs`/`PcsEditDetector`/`PcsMerger`/`PcsTreeBuilder`,
 * `core`'s `com.phasmidsoftware.xmldoc.merge`) against a document type it wasn't originally built
 * for. `kml` has no `GenericElement`-based tree of its own (see `KML.scala`'s specialist
 * `Extractor`/`Renderer` case classes) - `GenericElement.fromElem` bridges straight from raw
 * `scala.xml` parsing, bypassing that model entirely, exactly as `IdmlPackage` does for `idml`.
 *
 * Trying this surfaced two real limitations `idml`'s IDML documents happen to hide:
 *
 *   - **Text-node blindness (found and fixed 2026-09-20)**: `Pcs.relations` originally captured
 *     only element children, never `GenericText` - a minor footnote for `idml`, which puts
 *     essentially everything of interest in attributes, but KML's actual content (`<name>`,
 *     `<description>`, `<coordinates>`) lives almost entirely in element-wrapped *text*, so a
 *     document merged through `PcsTreeBuilder` came back with its element skeleton intact but that
 *     text gone completely. The first fix folded text-only leaves into their parent `Content`;
 *     the complete fix now gives text and CDATA their own `Content` relation and place in the PCS
 *     child chain, preserving mixed content and node kind through detect/merge/rebuild.
 *   - **No stable per-node identity, unlike IDML's `Self` (found and fixed 2026-09-20)**: KML
 *     content elements (`Placemark`, `Folder`, `Document`) have no equivalent - real fixtures only
 *     ever populate `id` on `Style`/`StyleMap` (confirmed by inspection), never on content.
 *     `Pcs.label`'s `NodePath` fallback ties identity to raw position, so `PcsMerger.merge`
 *     (plain `merge`, still) can silently lose an edit made to a node whose position shifted
 *     underneath it. `PcsMerger.mergeByContent` fixes the demonstrated case, via `ContentMatcher`
 *     (content/structural matching, no identifier needed - see its own doc for the algorithm and
 *     its real, narrower-than-3dm scope limits).
 */
class KmlMergeSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Pcs.relations, a real KML file"

  it should "decompose the real Placemarks folder from KML_Samples.kml without crashing" in {
    val url = getClass.getResource("/KML_Samples.kml")
    val root = GenericElement.fromElem(XML.loadFile(url.getFile))
    val placemarksFolder = findFolder(root, "Placemarks").get
    val rs = Pcs.relations(placemarksFolder)
    rs.content.count(_.tag == "Placemark") shouldBe 3
  }

  behavior of "PcsTreeBuilder.build, text-node blindness (fixed)"

  it should "round-trip a Placemark's real name/description text intact" in {
    val base = folderOf(placemark("Simple placemark", "Attached to the ground."))
    val result = PcsMerger.merge(base, base, base) // no edits at all - isolates the round-trip itself
    result.conflicts shouldBe Nil
    val merged = PcsTreeBuilder.build(result).get
    val rebuiltPlacemark = merged.childElements.head
    name(rebuiltPlacemark) shouldBe "Simple placemark"
    description(rebuiltPlacemark) shouldBe "Attached to the ground."
  }

  behavior of "PcsMerger.merge, synthetic (but real-shaped) KML trees with no identity attribute at all"

  it should "cleanly combine disjoint edits to different Placemarks' real description text, purely from position" in {
    val base = folderOf(
      placemark("Simple placemark", "Attached to the ground."),
      placemark("Floating placemark", "Floats above the ground."),
      placemark("Extruded placemark", "Tethered to the ground.")
    )
    val left = folderOf(
      placemark("Simple placemark", "Right at ground level now."), // left restyles the first
      placemark("Floating placemark", "Floats above the ground."),
      placemark("Extruded placemark", "Tethered to the ground.")
    )
    val right = folderOf(
      placemark("Simple placemark", "Attached to the ground."),
      placemark("Floating placemark", "Floats above the ground."),
      placemark("Extruded placemark", "Tethered by a customizable tail.") // right restyles the third
    )
    val result = PcsMerger.merge(base, left, right)
    result.conflicts shouldBe Nil
    val merged = PcsTreeBuilder.build(result).get
    description(merged.childElements(0)) shouldBe "Right at ground level now."
    description(merged.childElements(2)) shouldBe "Tethered by a customizable tail."
  }

  // The real cost of having no stable identity: left deletes the first Placemark (A), shifting B
  // and C one position earlier; right, independently, only edits C's description - a change that
  // doesn't overlap with left's edit in any *conceptual* sense. But `Pcs.label` only knows
  // positions, not "which Placemark is which" - so from its point of view, position "/0" changed
  // from A's content to B's, and position "/1" changed from B's to C's *original* content, both
  // registered as ordinary (non-conflicting) edits `left` alone made; right's edit at position "/2"
  // has nowhere to land, since nothing in the merged chain points there any more once left's
  // deletion is applied. The result: no conflict is ever reported, but right's real edit is
  // silently dropped from the merged tree - worse than a false conflict, a false *negative*. This
  // isn't a bug in `PcsMerger`; it's the predicted consequence of feeding Lindholm's algorithm a
  // weak (purely positional) matching relation. Independent of the text-node fix above: this would
  // happen identically for attribute-only content too.
  it should "silently lose an edit made to a node whose position shifted underneath it - plain merge, not mergeByContent" in {
    val base = folderOf(placemark("A", "descA"), placemark("B", "descB"), placemark("C", "descC"))
    val left = folderOf(placemark("B", "descB"), placemark("C", "descC")) // left deletes A
    val right = folderOf(placemark("A", "descA"), placemark("B", "descB"), placemark("C", "descC-updated")) // right only edits C
    val result = PcsMerger.merge(base, left, right)
    result.conflicts shouldBe Nil // no conflict reported...
    val merged = PcsTreeBuilder.build(result).get
    merged.childElements should have size 2
    // ...yet right's real edit to C is nowhere in the merged result: position "/1" ends up with
    // B's successor's *original*, untouched description, not right's update.
    description(merged.childElements(1)) shouldBe "descC"
    merged.childElements.map(description) should not contain "descC-updated"
  }

  behavior of "PcsMerger.mergeByContent, the fix"

  it should "keep the edit the plain positional merge above silently drops" in {
    val base = folderOf(placemark("A", "descA"), placemark("B", "descB"), placemark("C", "descC"))
    val left = folderOf(placemark("B", "descB"), placemark("C", "descC")) // left deletes A
    val right = folderOf(placemark("A", "descA"), placemark("B", "descB"), placemark("C", "descC-updated")) // right only edits C
    val result = PcsMerger.mergeByContent(base, left, right)
    result.conflicts shouldBe Nil
    val merged = PcsTreeBuilder.build(result).get
    merged.childElements should have size 2
    // B, untouched by either side, survives unmatched-by-content into the positional fallback too
    description(merged.childElements(0)) shouldBe "descB"
    // C keeps its own identity across the shift, so right's real edit to it is not lost
    description(merged.childElements(1)) shouldBe "descC-updated"
  }

  it should "still correctly detect a genuine conflict, not just paper over every disagreement" in {
    val base = folderOf(placemark("A", "descA"))
    val left = folderOf(placemark("A", "descA-left")) // left restyles the only Placemark's description
    val right = folderOf(placemark("A", "descA-right")) // right restyles it differently
    val result = PcsMerger.mergeByContent(base, left, right)
    // the Placemark itself (label "/0") matches cleanly either way - it has no attributes of its
    // own, so left's and right's edits are indistinguishable at that level; the real conflict is on
    // the <description> child's independent text-node label ("/0/1::text[0]"), under the element
    // matched by ContentMatcher's recursion into (A, A-left)/(A, A-right).
    val contentConflicts = result.conflicts.filter(_.slot == "content of /0/1::text[0]")
    contentConflicts should have size 1
    contentConflicts.head.leftValue.get should include("descA-left")
    contentConflicts.head.rightValue.get should include("descA-right")
  }

  behavior of "PcsMerger.mergeByContent, a full real-document 3-way merge"

  // The real files, not a hand-built excerpt: KML_Samples.kml is the common ancestor, _A rewrites
  // every http:// href to https://, retouches a couple of style attributes (LineStyle width,
  // PolyStyle color), bumps an altitude, fixes a typo inside a CDATA description, and reflows some
  // description text onto single lines - all disjoint, scattered edits across the whole document,
  // none of them touching Placemark identity. _B instead deletes one whole Placemark ("Building 42")
  // and trims two coordinate tuples out of two different <coordinates> text blobs elsewhere. Neither
  // side's edits overlap the other's, but A's edits land at positions B's deletion shifts - exactly
  // the scenario `mergeByContent`'s `ContentMatcher` exists for (see the synthetic test above). This
  // is the real end-to-end case the rest of this spec was building toward.
  it should "cleanly combine A's https/style/CDATA edits with B's Placemark and coordinates deletions" in {
    val base = loadKml("/KML_Samples.kml")
    val a = loadKml("/KML_Samples_A.kml")
    val b = loadKml("/KML_Samples_B.kml")
    val result = PcsMerger.mergeByContent(base, a, b)
    result.conflicts shouldBe Nil
    val merged = GenericElement.toXmlString(PcsTreeBuilder.build(result).get)

    // B's deletions survive
    merged should not include "Building 42"
    merged should not include "-112.3331289492913,36.14780840132443,100"
    merged should not include "-112.3358353861232,36.1489624162954,100"

    // A's scattered edits survive, all of them
    merged should not include "http://maps.google.com"
    merged should not include "http://developers.google.com"
    merged should not include "http://earth.google.com"
    merged should include("https://maps.google.com/mapfiles/kml/pal4/icon28.png")
    merged should include("<width>3</width>")
    merged should include("7fff01ff")
    merged should include("<altitude>100</altitude>")
    merged should include("the line follows a simple straight-line path")

    // the KML namespace URI is not an href A touched, so it's still plain http
    merged should include("http://www.opengis.net/kml/2.2")
  }

  private def loadKml(resourceName: String): GenericElement =
    GenericElement.fromElem(XML.loadFile(getClass.getResource(resourceName).getFile))

  private def placemark(name: String, description: String): GenericElement =
    GenericElement("Placemark", Nil, Seq(
      GenericElement("name", Nil, Seq(GenericText(name))),
      GenericElement("description", Nil, Seq(GenericText(description)))
    ))

  private def folderOf(placemarks: GenericElement*): GenericElement = GenericElement("Folder", Nil, placemarks)

  private def childText(e: GenericElement, tag: String): Option[String] =
    e.childElements.find(_.tag == tag).flatMap(_.children.collectFirst { case GenericText(t) => t })

  private def name(placemark: GenericElement): String = childText(placemark, "name").get

  private def description(placemark: GenericElement): String = childText(placemark, "description").get

  private def findFolder(e: GenericElement, name: String): Option[GenericElement] =
    if (e.tag == "Folder" && childText(e, "name").contains(name)) Some(e)
    else e.childElements.view.flatMap(findFolder(_, name)).headOption
}
