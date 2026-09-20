package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.{Failure, Success}

class PcsTreeBuilderSpec extends AnyFlatSpec with should.Matchers {

  private def spreadOf(self: String, children: GenericElement*): GenericElement =
    GenericElement("Spread", Seq("Self" -> self), children)

  private def rect(self: String, attrs: (String, String)*): GenericElement =
    GenericElement("Rectangle", ("Self" -> self) +: attrs, Nil)

  behavior of "PcsTreeBuilder.build, round-tripping a single tree through Pcs.relations"

  it should "reconstruct an untouched tree exactly, including a childless node's own Self" in {
    val tree = spreadOf("us", rect("u1", "Fill" -> "None"), rect("u2"))
    PcsTreeBuilder.build(Pcs.relations(tree), "us") shouldBe Success(tree)
  }

  it should "reconstruct a Self-less node using its NodePath label" in {
    val tree = GenericElement("Root", Nil, Seq(GenericElement("Group", Nil, Nil)))
    PcsTreeBuilder.build(Pcs.relations(tree), "/") shouldBe Success(tree)
  }

  it should "reconstruct a text-only leaf's text intact (found and fixed merging real KML)" in {
    val tree = GenericElement("Placemark", Nil, Seq(
      GenericElement("name", Nil, Seq(GenericText("Simple placemark"))),
      GenericElement("description", Nil, Seq(GenericText("Attached to the ground.")))
    ))
    PcsTreeBuilder.build(Pcs.relations(tree), "/") shouldBe Success(tree)
  }

  it should "reconstruct a text-only leaf as GenericCData when the original was uniformly CDATA" in {
    val tree = GenericElement("Root", Nil, Seq(GenericElement("script", Nil, Seq(GenericCData("alert(1)")))))
    PcsTreeBuilder.build(Pcs.relations(tree), "/") shouldBe Success(tree)
  }

  behavior of "PcsTreeBuilder.build, after a clean PcsMerger.merge (no conflicts)"

  it should "rebuild a one-sided reorder exactly as the modifying side arranged it" in {
    val base = spreadOf("us", rect("u1"), rect("u2"))
    val right = spreadOf("us", rect("u2"), rect("u1"))
    val result = PcsMerger.merge(base, base, right)
    result.conflicts shouldBe Nil
    PcsTreeBuilder.build(result) shouldBe Success(right)
  }

  it should "rebuild a one-sided insertion with the new node spliced in" in {
    val base = spreadOf("us", rect("u1"))
    val left = spreadOf("us", rect("u1"), rect("u2", "Fill" -> "Red"))
    val result = PcsMerger.merge(base, left, base)
    result.conflicts shouldBe Nil
    PcsTreeBuilder.build(result) shouldBe Success(left)
  }

  it should "rebuild both sides' independent, non-overlapping changes combined" in {
    val base = spreadOf("us", rect("u1", "Fill" -> "None"), rect("u2"))
    val left = spreadOf("us", rect("u1", "Fill" -> "Red"), rect("u2")) // left restyles u1
    val right = spreadOf("us", rect("u1", "Fill" -> "None"), rect("u2"), rect("u3")) // right adds u3
    val result = PcsMerger.merge(base, left, right)
    result.conflicts shouldBe Nil
    val expected = spreadOf("us", rect("u1", "Fill" -> "Red"), rect("u2"), rect("u3"))
    PcsTreeBuilder.build(result) shouldBe Success(expected)
  }

  behavior of "PcsTreeBuilder.build, an unresolved conflict"

  it should "refuse to build at all from a result with any unresolved conflict, move/move (Kaining's 05) included" in {
    val base = spreadOf("us", rect("u1"), rect("u2"), rect("u3"))
    val left = spreadOf("us", rect("u2"), rect("u3"), rect("u1")) // left moves u1 to the end
    val right = spreadOf("us", rect("u2"), rect("u1"), rect("u3")) // right moves u1 to the middle
    val result = PcsMerger.merge(base, left, right)
    result.conflicts should not be empty
    val Failure(t) = PcsTreeBuilder.build(result): @unchecked
    t.getMessage should include("refusing to build")
  }

  it should "refuse to build from an Insert/Insert content conflict too, even though its relations alone wouldn't cycle" in {
    val base = spreadOf("us")
    val left = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val right = spreadOf("us", rect("u1", "Fill" -> "Magenta"))
    val result = PcsMerger.merge(base, left, right)
    PcsTreeBuilder.build(result) shouldBe a[Failure[?]]
  }

  it should "still fail (via the lower-level relations/rootLabel overload) with a clear error when a referenced label has no content" in {
    val base = spreadOf("us")
    val left = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val right = spreadOf("us", rect("u1", "Fill" -> "Magenta"))
    val result = PcsMerger.merge(base, left, right)
    val Failure(t) = PcsTreeBuilder.build(result.relations, result.rootLabel): @unchecked
    t.getMessage should include("no content recorded for u1")
  }

  behavior of "PcsTreeBuilder.build, the unique-parent rule (Kaining's 10-group-ungroup)"

  it should "refuse to build when left ungroups a node right takes as its new home" in {
    val g1 = GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x")))
    val y = rect("y")
    val base = spreadOf("us", g1, y)
    val left = spreadOf("us", rect("x"), y) // left ungroups g1: x is promoted, g1 vanishes
    val right = spreadOf("us", GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x"), y))) // right moves y inside g1
    val result = PcsMerger.merge(base, left, right)
    result.conflicts.map(_.slot) should (contain("parent of x") and contain("parent of y"))
    PcsTreeBuilder.build(result) shouldBe a[Failure[?]]
  }
}
