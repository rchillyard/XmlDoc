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

  it should "report two genuinely different new parents for the same node (10b)" in {
    val base = spreadOf("us", group("g1", rect("x")), group("g2"))
    val left = spreadOf("us", rect("x"), group("g2"))
    val right = spreadOf("us", group("g1"), group("g2", rect("x")))
    val expected = Seq(StructuralConflict("parent of x", Some("g1"), Some("us"), Some("g2")))
    for ((l, r, swap) <- Seq((left, right, false), (right, left, true))) {
      val result = PcsMerger.merge(base, l, r)
      MergeAssertions.assertConflictsEqual(result.conflicts, expected, swapActualSides = swap)
      PcsTreeBuilder.build(result).isFailure shouldBe true
    }
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
    result.relations.content should contain(Content.text("/0::text[0]", "Renamed placemark"))
  }

  it should "report a conflict when both sides change the same leaf's text differently" in {
    val base = spreadOf("us", leaf("name", "Simple placemark"))
    val left = spreadOf("us", leaf("name", "Left's name"))
    val right = spreadOf("us", leaf("name", "Right's name"))
    val result = PcsMerger.merge(base, left, right)
    val contentConflicts = result.conflicts.filter(_.slot == "content of /0::text[0]")
    contentConflicts should have size 1
    contentConflicts.head.leftValue.get should include("Left's name")
    contentConflicts.head.rightValue.get should include("Right's name")
  }

  it should "merge a mixed-content text edit independently from a parent attribute edit" in {
    def paragraph(style: String, greeting: String): GenericElement =
      GenericElement("p", Seq("Self" -> "p1", "style" -> style), Seq(
        GenericText(greeting),
        GenericElement("b", Seq("Self" -> "b1"), Seq(GenericText("world"))),
        GenericText("!")
      ))

    val base = paragraph("plain", "Hello ")
    val left = paragraph("plain", "Hi ")
    val right = paragraph("loud", "Hello ")
    val result = PcsMerger.merge(base, left, right)

    result.conflicts shouldBe Nil
    PcsTreeBuilder.build(result) shouldBe scala.util.Success(paragraph("loud", "Hi "))
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

  behavior of "MR06 compatible edits with complete results"

  private def group(id: String, children: GenericElement*): GenericElement =
    GenericElement("Group", Seq("Self" -> id), children)

  private def assertCompatible(base: GenericElement, left: GenericElement, right: GenericElement,
                               expected: GenericElement): Unit = {
    for ((l, r) <- Seq(left -> right, right -> left)) {
      val result = PcsMerger.merge(base, l, r)
      MergeAssertions.assertConflictsEqual(result.conflicts, Nil)
      result.rootLabel shouldBe "us"
      result.relations shouldBe Pcs.relations(expected)
      MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(result).get, expected)
    }
  }

  it should "combine a move of b past c with b's FillColor update exactly once" in {
    val a = rect("a", "Keep" -> "A")
    val b = rect("b", "FillColor" -> "None")
    val c = rect("c", "Keep" -> "C")
    val red = rect("b", "FillColor" -> "Red")
    assertCompatible(spreadOf("us", a, b, c), spreadOf("us", a, c, b),
      spreadOf("us", a, red, c), spreadOf("us", a, c, red))
  }

  it should "combine a parent's cross-parent move with its children's reorder" in {
    val a = rect("a")
    val b = rect("b")
    val g = group("g", a, b)
    val reordered = group("g", b, a)
    assertCompatible(spreadOf("us", group("p", g), group("q")),
      spreadOf("us", group("p"), group("q", g)),
      spreadOf("us", group("p", reordered), group("q")),
      spreadOf("us", group("p"), group("q", reordered)))
  }

  it should "close a nonempty source gap and preserve the exact target anchor during a parent move" in {
    val x = rect("x")
    val y = rect("y")
    val z = rect("z")
    val g = group("g", rect("a"), rect("b"))
    val reordered = group("g", rect("b"), rect("a"))
    val base = spreadOf("us", group("p", x, g, y), group("q", z))
    val moved = spreadOf("us", group("p", x, y), group("q", z, g))
    val edited = spreadOf("us", group("p", x, reordered, y), group("q", z))
    assertCompatible(base, moved, edited,
      spreadOf("us", group("p", x, y), group("q", z, reordered)))
    assertCompatible(base, moved, moved, moved)
  }

  it should "combine inserts in distinct compatible gaps without losing surrounding nodes" in {
    val a = rect("a")
    val b = rect("b")
    val x = rect("x", "FillColor" -> "Blue")
    val y = rect("y", "FillColor" -> "Green")
    assertCompatible(spreadOf("us", a, b), spreadOf("us", x, a, b),
      spreadOf("us", a, b, y), spreadOf("us", x, a, b, y))
  }

  it should "coalesce identical updates and identical structural changes" in {
    val a = rect("a")
    val b = rect("b", "FillColor" -> "None")
    val base = spreadOf("us", a, b)
    val changed = spreadOf("us", a, rect("b", "FillColor" -> "Red"))
    assertCompatible(base, changed, changed, changed)
    val reordered = spreadOf("us", b, a)
    assertCompatible(base, reordered, reordered, reordered)
    val inserted = spreadOf("us", a, b, rect("x"))
    assertCompatible(base, inserted, inserted, inserted)
  }

  it should "keep whole content atomic when different attributes of one node change" in {
    val base = spreadOf("us", rect("b", "FillColor" -> "None", "StrokeColor" -> "None"))
    val left = spreadOf("us", rect("b", "FillColor" -> "Red", "StrokeColor" -> "None"))
    val right = spreadOf("us", rect("b", "FillColor" -> "None", "StrokeColor" -> "Black"))
    val expected = Seq(StructuralConflict("content of b",
      Some("<Rectangle Self=\"b\" FillColor=\"None\" StrokeColor=\"None\">"),
      Some("<Rectangle Self=\"b\" FillColor=\"Red\" StrokeColor=\"None\">"),
      Some("<Rectangle Self=\"b\" FillColor=\"None\" StrokeColor=\"Black\">")))
    for ((l, r, swap) <- Seq((left, right, false), (right, left, true))) {
      val result = PcsMerger.merge(base, l, r)
      MergeAssertions.assertConflictsEqual(result.conflicts, expected, swapActualSides = swap)
      result.relations shouldBe Pcs.relations(base)
    }
  }


  private def ordered(ids: String): GenericElement = spreadOf("us", ids.map(c => rect(c.toString))*)

  it should "report exactly b's successor and c's predecessor guard conflicts" in {
    val base = ordered("abcd")
    val left = ordered("bacd")
    val right = ordered("abdc")
    val expected = Seq(
      StructuralConflict("successor after b under us", Some("c"), Some("a"), Some("d")),
      StructuralConflict("predecessor before c under us", Some("b"), Some("a"), Some("d")))
    for ((l, r, swap) <- Seq((left, right, false), (right, left, true))) {
      val result = PcsMerger.merge(base, l, r)
      MergeAssertions.assertConflictsEqual(result.conflicts, expected, swapActualSides = swap)
      PcsTreeBuilder.build(result).isFailure shouldBe true
    }
  }

  it should "merge isolated reorder contexts into the complete baced tree" in {
    assertCompatible(ordered("abcde"), ordered("bacde"), ordered("abced"), ordered("baced"))
  }

  it should "report both slot conflicts for distinct inserts in the same gap" in {
    val base = ordered("ab")
    val left = ordered("axb")
    val right = ordered("ayb")
    val expected = Seq(
      StructuralConflict("successor after a under us", Some("b"), Some("x"), Some("y")),
      StructuralConflict("predecessor before b under us", Some("a"), Some("x"), Some("y")))
    for ((l, r, swap) <- Seq((left, right, false), (right, left, true))) {
      val result = PcsMerger.merge(base, l, r)
      MergeAssertions.assertConflictsEqual(result.conflicts, expected, swapActualSides = swap)
      PcsTreeBuilder.build(result).isFailure shouldBe true
    }
  }

  it should "preserve an ungrouped child's independent content update (case 10)" in {
    val base = spreadOf("us", group("g", rect("x", "ItemTransform" -> "old")))
    val left = spreadOf("us", rect("x", "ItemTransform" -> "old"))
    val right = spreadOf("us", group("g", rect("x", "ItemTransform" -> "new")))
    for ((l, r) <- Seq(left -> right, right -> left)) {
      val result = PcsMerger.merge(base, l, r)
      MergeAssertions.assertConflictsEqual(result.conflicts, Nil)
      MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(result).get,
        spreadOf("us", rect("x", "ItemTransform" -> "new")))
    }
  }

}
