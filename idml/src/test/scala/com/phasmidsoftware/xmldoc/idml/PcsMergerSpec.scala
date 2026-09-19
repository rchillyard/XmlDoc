package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

class PcsMergerSpec extends AnyFlatSpec with should.Matchers {

  private def spreadOf(self: String, children: GenericElement*): GenericElement =
    GenericElement("Spread", Seq("Self" -> self), children)

  private def rect(self: String, attrs: (String, String)*): GenericElement =
    GenericElement("Rectangle", ("Self" -> self) +: attrs, Nil)

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

  behavior of "PcsMerger.merge, real Mergeable files (the same trio MergerSpec uses)"

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  it should "surface the same real Insert/Insert collision (u11c) that Merger's attribute-level check finds" in {
    val base = spread("Mergeable.idml", "Spreads/Spread_ud1.xml")
    val left = spread("Mergeable-Left.idml", "Spreads/Spread_ud1.xml")
    val right = spread("Mergeable-Right.idml", "Spreads/Spread_ud1.xml")
    val result = PcsMerger.merge(base, left, right)
    result.conflicts.map(_.slot) should contain("content of u11c")
  }
}
