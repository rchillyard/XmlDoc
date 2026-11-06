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

  it should "round-trip mixed text, elements and CDATA in their exact original order" in {
    val tree = GenericElement("p", Nil, Seq(
      GenericText("Hello "),
      GenericElement("b", Nil, Seq(GenericText("world"))),
      GenericCData("<raw>"),
      GenericText("!")
    ))
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

  behavior of "PcsTreeBuilder.build, from an inconsistent relation set"

  private val root = Content.element("r", "R", Nil)

  // The Pcs relations of a parent whose children are the given labels, in order.
  private def chain(parent: String, labels: String*): Set[Pcs] = {
    val siblings = ListStart +: labels.map(SiblingNode(_)) :+ ListEnd
    siblings.zip(siblings.tail).map((p, s) => Pcs(parent, p, s)).toSet
  }

  private def failureMessage(relations: RelationSet, rootLabel: String = "r"): String =
    PcsTreeBuilder.build(relations, rootLabel) match {
      case Failure(x) => x.getMessage
      case Success(e) => fail(s"expected a failure, but built $e")
    }

  it should "fail when a parent has no first child recorded" in {
    failureMessage(RelationSet(Set.empty, Set(root))) should include("no successor recorded after ⊣ under r")
  }

  it should "fail when a child chain stops short" in {
    failureMessage(RelationSet(Set(Pcs("r", ListStart, SiblingNode("a"))), Set(root, Content.element("a", "A", Nil)))) should include("no successor recorded after a under r")
  }

  it should "fail when a child chain has list-start as a successor" in {
    failureMessage(RelationSet(Set(Pcs("r", ListStart, ListStart)), Set(root))) should include("list-start")
  }

  it should "fail when a child chain is cyclic" in {
    val pcs = Set(Pcs("r", ListStart, SiblingNode("a")), Pcs("r", SiblingNode("a"), SiblingNode("a")))
    failureMessage(RelationSet(pcs, Set(root, Content.element("a", "A", Nil)))) should include("cyclic child chain")
  }

  it should "fail when a node is its own ancestor" in {
    failureMessage(RelationSet(chain("r", "a") ++ chain("a", "r"), Set(root, Content.element("a", "A", Nil)))) should include("its own ancestor")
  }

  it should "fail when a child has no content" in {
    failureMessage(RelationSet(chain("r", "a"), Set(root))) should include("no content recorded for a")
  }

  it should "fail when a text node has children, or no text" in {
    failureMessage(RelationSet(chain("r", "t") ++ chain("t", "x"), Set(root, Content.text("t", "hi")))) should include("text node t cannot have children")
    failureMessage(RelationSet(chain("r", "t") ++ chain("t"), Set(root, Content("t", "", Nil, None, kind = TextNode)))) should include("text node t has no text payload")
  }

  it should "fail when a CDATA node has children, or no text" in {
    failureMessage(RelationSet(chain("r", "c") ++ chain("c", "x"), Set(root, Content.cdata("c", "a<b")))) should include("CDATA node c cannot have children")
    failureMessage(RelationSet(chain("r", "c") ++ chain("c"), Set(root, Content("c", "", Nil, None, kind = CDataNode)))) should include("CDATA node c has no text payload")
  }

  it should "fail when the root is not an element" in {
    failureMessage(RelationSet(chain("t"), Set(Content.text("t", "hi"))), "t") should include("root t is not an element")
  }

  it should "build an element's legacy text (kept on its Content) as its last child" in {
    PcsTreeBuilder.build(RelationSet(chain("r"), Set(Content("r", "R", Nil, Some("hi")))), "r") shouldBe Success(GenericElement("R", Nil, Seq(GenericText("hi"))))
    PcsTreeBuilder.build(RelationSet(chain("r"), Set(Content("r", "R", Nil, Some("a<b"), textIsCData = true))), "r") shouldBe Success(GenericElement("R", Nil, Seq(GenericCData("a<b"))))
  }
}
