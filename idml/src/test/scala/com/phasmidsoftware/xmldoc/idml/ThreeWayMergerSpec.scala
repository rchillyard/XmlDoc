package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

class ThreeWayMergerSpec extends AnyFlatSpec with should.Matchers {

  private def spreadOf(self: String, children: GenericElement*): GenericElement =
    GenericElement("Spread", Seq("Self" -> self), children)

  private def rect(self: String, attrs: (String, String)*): GenericElement =
    GenericElement("Rectangle", ("Self" -> self) +: attrs, Nil)

  private def expectMerged(result: Either[Seq[StructuralConflict], GenericElement]): GenericElement =
    result.getOrElse(fail(s"expected a successful merge, got conflicts: $result"))

  private def expectConflicts(result: Either[Seq[StructuralConflict], GenericElement]): Seq[StructuralConflict] =
    result.swap.getOrElse(fail(s"expected conflicts, got a successful merge: $result"))

  behavior of "ThreeWayMerger.merge, the reason both mergers are wired together"

  it should "cleanly combine disjoint attribute changes to the same node - PcsMerger alone would (wrongly) conflict on the whole content blob" in {
    val base = rect("u1", "Fill" -> "None", "Stroke" -> "None")
    val left = rect("u1", "Fill" -> "Red", "Stroke" -> "None") // left changed Fill only
    val right = rect("u1", "Fill" -> "None", "Stroke" -> "Black") // right changed Stroke only
    PcsMerger.merge(base, left, right).conflicts should not be empty // the blindspot this class exists to fix
    val merged = expectMerged(ThreeWayMerger.merge(base, left, right))
    merged.attributes.toMap should contain allOf("Fill" -> "Red", "Stroke" -> "Black")
  }

  behavior of "ThreeWayMerger.merge, single-sided changes"

  it should "cleanly apply a one-sided structural reorder" in {
    val base = spreadOf("us", rect("u1"), rect("u2"))
    val right = spreadOf("us", rect("u2"), rect("u1"))
    expectMerged(ThreeWayMerger.merge(base, base, right)) shouldBe right
  }

  it should "cleanly apply a one-sided insertion, with the new node's own attributes intact" in {
    val base = spreadOf("us", rect("u1"))
    val left = spreadOf("us", rect("u1"), rect("u2", "Fill" -> "Red"))
    expectMerged(ThreeWayMerger.merge(base, left, base)) shouldBe left
  }

  behavior of "ThreeWayMerger.merge, conflicts from Merger's own attribute-level checks"

  it should "report a Delete/Update conflict (Kaining's 06) even though nothing about it is structural" in {
    val base = spreadOf("us", rect("u1", "Fill" -> "None"))
    val left = spreadOf("us") // left deletes u1
    val right = spreadOf("us", rect("u1", "Fill" -> "Red")) // right just updates it
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.map(_.slot) should contain("(deleted vs updated) of u1")
  }

  behavior of "ThreeWayMerger.merge, conflicts from PcsMerger's own structural checks"

  it should "report both directions of a move/move divergence (Kaining's 05)" in {
    val base = spreadOf("us", rect("u1"), rect("u2"), rect("u3"))
    val left = spreadOf("us", rect("u2"), rect("u3"), rect("u1"))
    val right = spreadOf("us", rect("u2"), rect("u1"), rect("u3"))
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.map(_.slot).toSet shouldBe Set("successor after u1 under us", "predecessor before u1 under us")
  }

  it should "report a unique-parent conflict (Kaining's 10-group-ungroup)" in {
    val g1 = GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x")))
    val y = rect("y")
    val base = spreadOf("us", g1, y)
    val left = spreadOf("us", rect("x"), y) // left ungroups g1
    val right = spreadOf("us", GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x"), y))) // right moves y into g1
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.map(_.slot).toSet shouldBe Set("parent of x", "parent of y")
  }

  behavior of "ThreeWayMerger.merge, deduplicating an Insert/Insert collision Merger and PcsMerger both detect"

  it should "report exactly one conflict, not two, for the same node" in {
    val base = spreadOf("us")
    val left = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val right = spreadOf("us", rect("u1", "Fill" -> "Magenta"))
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts should have size 1
    conflicts.head.slot should include("u1")
  }

  behavior of "ThreeWayMerger.merge, real IDML files"

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  // The whole Spread doesn't merge cleanly end to end: A and B also independently touched an
  // unrelated Link's LinkImportTime (InDesign re-stamps that on export, regardless of user
  // action) - a real instance of the paper's own §7 caveat ("document metadata often changes
  // inconsistently on both sides"), not a bug here. Scoped down to the u13d subtree the test is
  // actually about, the move-plus-restyle combination is exactly as clean as MergerSpec/
  // PcsMergerSpec's own separate checks already showed.
  it should "merge HelloWorld2A's move and HelloWorld2B's restyle of u13d into one real tree (base C)" in {
    def u13dOf(spreadEl: GenericElement): GenericElement = spreadEl.childElements.find(_.attributes.toMap.get("Self").contains("u13d")).get
    val base = u13dOf(spread("HelloWorld2C.idml", "Spreads/Spread_ue6.xml"))
    val left = u13dOf(spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml"))
    val right = u13dOf(spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml"))
    val merged = expectMerged(ThreeWayMerger.merge(base, left, right))
    merged.attributes.toMap.get("ItemTransform") shouldBe left.attributes.toMap.get("ItemTransform") // A's move
    merged.attributes.toMap.get("FillColor") shouldBe right.attributes.toMap.get("FillColor") // B's restyle
  }

  it should "report the real, unrelated LinkImportTime conflict when merging the whole Spread (not a bug - see above)" in {
    val base = spread("HelloWorld2C.idml", "Spreads/Spread_ue6.xml")
    val left = spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml")
    val right = spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml")
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.map(_.slot) should contain("LinkImportTime of u10d")
  }

  it should "surface the real Insert/Insert collision (u11c) as a single conflict, from Mergeable-Left/-Right" in {
    val base = spread("Mergeable.idml", "Spreads/Spread_ud1.xml")
    val left = spread("Mergeable-Left.idml", "Spreads/Spread_ud1.xml")
    val right = spread("Mergeable-Right.idml", "Spreads/Spread_ud1.xml")
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.count(_.slot.contains("u11c")) shouldBe 1
  }
}
