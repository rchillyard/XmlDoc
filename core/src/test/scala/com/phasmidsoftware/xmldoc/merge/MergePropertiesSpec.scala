package com.phasmidsoftware.xmldoc.merge

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.phasmidsoftware.xmldoc.xml.GenericElement

class MergePropertiesSpec extends AnyFlatSpec with Matchers {
  import MergeTreeGenerator._
  private def included(a: RelationSet, b: RelationSet): Boolean = a.pcs.subsetOf(b.pcs) && a.content.subsetOf(b.content)
  private def union(a: RelationSet, b: RelationSet): RelationSet = RelationSet(a.pcs ++ b.pcs, a.content ++ b.content)
  private def bounds(s: Sample, delta: RelationSet): Unit = {
    val edits = union(PcsEditDetector.detectEdits(s.base, s.left), PcsEditDetector.detectEdits(s.base, s.right))
    val all = union(union(Pcs.relations(s.base), Pcs.relations(s.left)), Pcs.relations(s.right))
    assert(included(edits, delta), "E subset Delta: an edit was lost")
    assert(included(delta, all), "Delta subset T*: an invented relation appeared")
  }
  private def merged(b: GenericElement, l: GenericElement, r: GenericElement): StructuralMergeResult = {
    val result = PcsMerger.merge(b, l, r)
    MergeAssertions.assertConflictsEqual(result.conflicts, Nil)
    result
  }

  for (seed <- selectedSeeds; size <- selectedSizes; sample <- samples(seed, size)) {
    it should s"preserve ${sample.name}, identities, bounds and symmetry seed=$seed nodes=$size" in {
      nodes(sample.base).size shouldBe size
      verify(sample) { s =>
        val normal = merged(s.base, s.left, s.right)
        val swapped = merged(s.base, s.right, s.left)
        bounds(s, normal.relations)
        normal.relations shouldBe swapped.relations
        val tree = PcsTreeBuilder.build(normal).get
        MergeAssertions.assertXmlEquals(tree, s.expected)
        MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(swapped).get, s.expected)
        verifyLedger(s, tree)
        for (changed <- Seq(s.left, s.right).distinct;
             (b, l, r, expected) <- Seq((s.base, s.base, s.base, s.base),
               (s.base, changed, s.base, changed), (s.base, s.base, changed, changed), (s.base, changed, changed, changed))) {
          MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(merged(b, l, r)).get, expected)
        }
      }
    }
    if (sample.name == "disjoint-content") it should s"exchange divergent content and new-parent conflicts seed=$seed nodes=$size recipe=${sample.name}" in {
      // Only one conflict pair per generated base, rather than repeating identical inputs per recipe.
      val target = nodes(sample.base).find(_.children.isEmpty).get
      val key = id(target)
      val old = target.attributes.toMap.apply("Value")
      val left = attribute(sample.base, key, "Value", "LEFT")
      val right = attribute(sample.base, key, "Value", "RIGHT")
      verify(sample.copy(name = "content-conflict", left = left, right = right, edits = Seq(Edit("update", key)))) { c =>
      val content = PcsMerger.merge(c.base, c.left, c.right)
      val reverse = PcsMerger.merge(c.base, c.right, c.left)
      def render(n: GenericElement) = "<" + n.tag + n.attributes.map { case (k, v) => s" $k=\"$v\"" }.mkString + ">"
      MergeAssertions.assertConflictsEqual(content.conflicts, Seq(StructuralConflict(s"content of $key",
        Some(render(target)), Some(render(nodes(left).find(n => id(n) == key).get)),
        Some(render(nodes(right).find(n => id(n) == key).get)))))
      MergeAssertions.assertConflictsEqual(reverse.conflicts, content.conflicts, swapActualSides = true)
      content.conflicts.head.baseValue.get should include (s"Value=\"$old\"")
      content.conflicts.head.leftValue.get should include ("Value=\"LEFT\"")
      content.conflicts.head.rightValue.get should include ("Value=\"RIGHT\"")
      PcsTreeBuilder.build(content).isFailure shouldBe true
      }
      val parent = "p" // first leaf in the generated traversal is under p
      val l = move(sample.base, key, "q")
      val r = move(sample.base, key, "z")
      verify(sample.copy(name = "parent-conflict", left = l, right = r, edits = Seq(Edit("move", key)))) { c =>
      val result = PcsMerger.merge(c.base, c.left, c.right)
      MergeAssertions.assertConflictsEqual(result.conflicts,
        Seq(StructuralConflict(s"parent of $key", Some(parent), Some("q"), Some("z"))))
      MergeAssertions.assertConflictsEqual(PcsMerger.merge(c.base, c.right, c.left).conflicts, result.conflicts, swapActualSides = true)
      PcsTreeBuilder.build(result).isFailure shouldBe true
      }
    }
  }

  it should "reject dropped edits, left-biased outputs and invented relations in the property oracle" in {
    val s = samples(0, 8).find(_.name == "disjoint-content").get
    intercept[AssertionError](verifyLedger(s, s.base))
    intercept[AssertionError](verifyLedger(s, s.left))
    intercept[org.scalatest.exceptions.TestFailedException](bounds(s, Pcs.relations(s.base)))
    val real = merged(s.base, s.left, s.right).relations
    val invented = real.copy(pcs = real.pcs + Pcs("invented", ListStart, ListEnd))
    intercept[org.scalatest.exceptions.TestFailedException](bounds(s, invented))
  }

  it should "record generated operation counts separately from detected relation edits" in {
    for (size <- selectedSizes) {
      val cases = selectedSeeds.flatMap(seed => samples(seed, size))
      val e = cases.map { s =>
        val edits = union(PcsEditDetector.detectEdits(s.base, s.left), PcsEditDetector.detectEdits(s.base, s.right))
        edits.pcs.size + edits.content.size
      }
      info(s"nodes=$size seeds=${selectedSeeds.size} recipes=${cases.size} operations=${cases.map(_.edits.size).sum} E-range=${e.min}..${e.max}")
    }
  }

  it should "preserve ignored-field policy without treating source combinations as raw atomic content" in {
    def item(value: String, stamp: String) = GenericElement("Item", Seq("Self" -> "x", "Value" -> value,
      "LinkImportTime" -> stamp, "StyleUniqueId" -> stamp), Nil)
    val ignored = Set("LinkImportTime", "StyleUniqueId")
    val base = item("old", "base")
    val left = item("new", "left")
    val right = item("old", "right")
    for ((l, r) <- Seq(left -> right, right -> left)) {
      val result = PcsMerger.merge(base, l, r, ignored)
      MergeAssertions.assertConflictsEqual(result.conflicts, Nil)
      MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(result).get, item("new", "base"))
    }
    val empty = node("root")
    val first = node("root", Seq(item("new", "left")))
    val second = node("root", Seq(item("new", "z")))
    for ((l, r) <- Seq(first -> second, second -> first)) {
      val result = PcsMerger.merge(empty, l, r, ignored)
      MergeAssertions.assertConflictsEqual(result.conflicts, Nil)
      MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(result).get, first)
    }
    for ((smaller, larger) <- Seq(("a&", "a\""), ("\uE000", "\uD800\uDC00"))) {
      val a = node("root", Seq(item("new", smaller)))
      val b = node("root", Seq(item("new", larger)))
      for ((l, r) <- Seq(a -> b, b -> a)) {
        val result = PcsMerger.merge(empty, l, r, ignored)
        MergeAssertions.assertConflictsEqual(result.conflicts, Nil)
        MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(result).get, a)
      }
    }
    val reorderedAttributes = left.copy(attributes = left.attributes.reverse)
    val same = PcsMerger.merge(base, left, reorderedAttributes, ignored)
    MergeAssertions.assertConflictsEqual(same.conflicts, Nil)
    MergeAssertions.assertXmlEquals(PcsTreeBuilder.build(same).get, item("new", "base"))
  }

  it should "emit a replay seed and a locally reduced three-way XML counterexample" in {
    val s = samples(3, 32).find(_.name == "disjoint-content").get
    val failure = intercept[AssertionError] {
      verify(s)(candidate => verifyLedger(candidate, candidate.base))
    }
    failure.getMessage should include ("seed=3; size=32")
    val line = failure.getMessage.split("\n").find(_.startsWith("locally minimized base=")).get
    val reduced = GenericElement.fromElem(scala.xml.XML.loadString(line.stripPrefix("locally minimized base=")))
    nodes(reduced).size should be < 32
    failure.getMessage should include ("left=<")
    failure.getMessage should include ("right=<")
  }
}
