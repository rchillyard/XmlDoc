package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.{GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class PcsMergerSpec extends AnyFlatSpec with should.Matchers {

  private def spreadOf(self: String, children: GenericElement*): GenericElement =
    GenericElement("Spread", Seq("Self" -> self), children)

  private def rect(self: String, attrs: (String, String)*): GenericElement =
    GenericElement("Rectangle", ("Self" -> self) +: attrs, Nil)

  private def leaf(tag: String, text: String): GenericElement = GenericElement(tag, Nil, Seq(GenericText(text)))

  behavior of "PcsMerger.merge, single-sided structural changes"

  it should "cleanly apply a one-sided reorder (Kaining's 03-single-move), with no conflicts" in {
    val base = spreadOf("us", rect("u1"), rect("u2"))
    val right = spreadOf("us", rect("u2"), rect("u1")) // right swaps them; left leaves it alone
    val result = PcsMerger.merge(base, base, right)
    result.conflicts shouldBe Nil
    result.relations.pcs should contain allOf(
      Pcs("us", ListStart, SiblingNode("u2")),
      Pcs("us", SiblingNode("u2"), SiblingNode("u1")),
      Pcs("us", SiblingNode("u1"), ListEnd)
    )
  }

  it should "cleanly apply a one-sided insertion, splicing the new node into the chain" in {
    val base = spreadOf("us", rect("u1"))
    val left = spreadOf("us", rect("u1"), rect("u2", "Fill" -> "Red"))
    val result = PcsMerger.merge(base, left, base)
    result.conflicts shouldBe Nil
    result.relations.pcs should contain allOf(Pcs("us", SiblingNode("u1"), SiblingNode("u2")), Pcs("us", SiblingNode("u2"), ListEnd))
    result.relations.content should contain(Content("u2", "Rectangle", Seq("Self" -> "u2", "Fill" -> "Red")))
  }

  it should "accept both sides' inserts at different anchors, in a deterministic combined order (Kaining's 04-both-add-diff-pos)" in {
    val base = spreadOf("us", rect("r1"), rect("r2"))
    val left = spreadOf("us", rect("r1"), rect("rL"), rect("r2")) // left inserts rL after r1
    val right = spreadOf("us", rect("r1"), rect("r2"), rect("rR")) // right inserts rR after r2
    val result = PcsMerger.merge(base, left, right)
    result.conflicts shouldBe Nil
    result.relations.pcs should contain allOf(
      Pcs("us", ListStart, SiblingNode("r1")),
      Pcs("us", SiblingNode("r1"), SiblingNode("rL")),
      Pcs("us", SiblingNode("rL"), SiblingNode("r2")),
      Pcs("us", SiblingNode("r2"), SiblingNode("rR")),
      Pcs("us", SiblingNode("rR"), ListEnd)
    )
  }

  it should "accept a z-order swap described from both ends as the same move, not a conflict (Kaining's 09-zorder)" in {
    val base = spreadOf("us", rect("r1"), rect("r2"))
    val left = spreadOf("us", rect("r2"), rect("r1")) // "move r1 forward, after r2"
    val right = spreadOf("us", rect("r2"), rect("r1")) // "move r2 backward, before r1" - same result
    val result = PcsMerger.merge(base, left, right)
    result.conflicts shouldBe Nil
    result.relations.pcs should contain allOf(Pcs("us", ListStart, SiblingNode("r2")), Pcs("us", SiblingNode("r2"), SiblingNode("r1")), Pcs("us", SiblingNode("r1"), ListEnd))
  }

  behavior of "PcsMerger.merge, move/move divergence (Kaining's 05-move-move-divergent)"

  it should "report a conflict (from both the predecessor and successor views) when both sides move the same node incompatibly" in {
    val base = spreadOf("us", rect("u1"), rect("u2"), rect("u3"))
    val left = spreadOf("us", rect("u2"), rect("u3"), rect("u1")) // left moves u1 to the end
    val right = spreadOf("us", rect("u2"), rect("u1"), rect("u3")) // right moves u1 to the middle
    val result = PcsMerger.merge(base, left, right)
    result.conflicts.map(_.slot).toSet shouldBe Set("successor after u1 under us", "predecessor before u1 under us")
    val bySlot = result.conflicts.map(c => c.slot -> c).toMap
    bySlot("successor after u1 under us") shouldBe StructuralConflict("successor after u1 under us", Some("u2"), Some("⊢"), Some("u3"))
    bySlot("predecessor before u1 under us") shouldBe StructuralConflict("predecessor before u1 under us", Some("⊣"), Some("u3"), Some("u2"))
  }

  behavior of "PcsMerger.merge, Insert/Insert content collisions"

  it should "report a content conflict when both sides independently insert a node with the same Self but different content" in {
    val base = spreadOf("us")
    val left = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val right = spreadOf("us", rect("u1", "Fill" -> "Magenta"))
    val result = PcsMerger.merge(base, left, right)
    val contentConflicts = result.conflicts.filter(_.slot == "content of u1")
    contentConflicts should have size 1
    contentConflicts.head.baseValue shouldBe None
    contentConflicts.head.leftValue.get should include("Fill=\"Yellow\"")
    contentConflicts.head.rightValue.get should include("Fill=\"Magenta\"")
    // both sides agree on *where* it goes, so that part isn't a conflict
    result.relations.pcs should contain(Pcs("us", ListStart, SiblingNode("u1")))
  }

  it should "not conflict when both sides independently insert identical content at the same Self" in {
    val base = spreadOf("us")
    val left = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val right = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val result = PcsMerger.merge(base, left, right)
    result.conflicts shouldBe Nil
    result.relations.content should contain(Content("u1", "Rectangle", Seq("Self" -> "u1", "Fill" -> "Yellow")))
  }

  behavior of "PcsMerger.merge, the unique-parent rule (Kaining's 10-group-ungroup)"

  it should "report a parent conflict for every node whose new parent the two sides disagree about" in {
    val g1 = GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x")))
    val y = rect("y")
    val base = spreadOf("us", g1, y)
    val left = spreadOf("us", rect("x"), y) // left ungroups g1: x is promoted to a direct child of us, g1 vanishes
    val right = spreadOf("us", GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x"), y))) // right moves y inside g1
    val result = PcsMerger.merge(base, left, right)
    val bySlot = result.conflicts.map(c => c.slot -> c).toMap
    bySlot("parent of x") shouldBe StructuralConflict("parent of x", Some("g1"), Some("us"), Some("g1"))
    bySlot("parent of y") shouldBe StructuralConflict("parent of y", Some("us"), Some("us"), Some("g1"))
  }

  it should "not confuse a single-sided move to a new parent (no disagreement) with a conflict" in {
    val g1 = GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x")))
    val base = spreadOf("us", g1)
    val right = spreadOf("us", rect("x")) // right ungroups g1; left leaves it alone
    val result = PcsMerger.merge(base, base, right)
    result.conflicts shouldBe Nil
  }

  behavior of "PcsMerger.merge, text-only leaves (found merging real KML, which has no Self at all)"

  it should "cleanly combine one side's text edit with the other's untouched copy" in {
    val base = spreadOf("us", leaf("name", "Simple placemark"))
    val left = spreadOf("us", leaf("name", "Renamed placemark"))
    val result = PcsMerger.merge(base, left, base)
    result.conflicts shouldBe Nil
    result.relations.content should contain(Content("/0", "name", Nil, Some("Renamed placemark")))
  }

  it should "report a conflict when both sides change the same leaf's text differently" in {
    val base = spreadOf("us", leaf("name", "Simple placemark"))
    val left = spreadOf("us", leaf("name", "Left's name"))
    val right = spreadOf("us", leaf("name", "Right's name"))
    val result = PcsMerger.merge(base, left, right)
    val contentConflicts = result.conflicts.filter(_.slot == "content of /0")
    contentConflicts should have size 1
    contentConflicts.head.leftValue.get should include("Left's name")
    contentConflicts.head.rightValue.get should include("Right's name")
  }

  behavior of "PcsMerger.mergeByContent, weak/no-identity documents (found and fixed merging real KML)"

  it should "keep an edit that plain merge would silently lose when a deletion shifts positions" in {
    val base = spreadOf("us", leaf("name", "A"), leaf("name", "B"), leaf("name", "C"))
    val left = spreadOf("us", leaf("name", "B"), leaf("name", "C")) // left deletes A
    val right = spreadOf("us", leaf("name", "A"), leaf("name", "B"), leaf("name", "C-updated")) // right only edits C

    val plain = PcsMerger.merge(base, left, right)
    plain.conflicts shouldBe Nil
    PcsTreeBuilder.build(plain).get.childElements.flatMap(_.children).collect { case GenericText(t) => t } should not contain "C-updated"

    val byContent = PcsMerger.mergeByContent(base, left, right)
    byContent.conflicts shouldBe Nil
    val rebuilt = PcsTreeBuilder.build(byContent).get
    rebuilt.childElements.flatMap(_.children).collect { case GenericText(t) => t } should contain allOf("B", "C-updated")
  }
}
