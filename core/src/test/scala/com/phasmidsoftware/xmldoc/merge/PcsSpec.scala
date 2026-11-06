package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class PcsSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Pcs.relations, synthetic trees"

  it should "give a childless node just one pcs chain link, from ListStart straight to ListEnd" in {
    val root = GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)
    val rs = Pcs.relations(root)
    rs.pcs shouldBe Set(Pcs("u1", ListStart, ListEnd))
    rs.content shouldBe Set(Content("u1", "Rectangle", Seq("Self" -> "u1")))
  }

  it should "chain two children from ListStart to ListEnd via their Self labels" in {
    val root = GenericElement("Root", Nil, Seq(
      GenericElement("Rectangle", Seq("Self" -> "u1"), Nil),
      GenericElement("TextFrame", Seq("Self" -> "u2"), Nil)
    ))
    val rs = Pcs.relations(root)
    rs.pcs should contain allOf(
      Pcs("/", ListStart, SiblingNode("u1")),
      Pcs("/", SiblingNode("u1"), SiblingNode("u2")),
      Pcs("/", SiblingNode("u2"), ListEnd)
    )
  }

  it should "fall back to a NodePath label for a child with no Self, distinct from any Self label" in {
    val root = GenericElement("Root", Nil, Seq(GenericElement("Group", Nil, Nil))) // no Self
    val rs = Pcs.relations(root)
    rs.pcs should contain(Pcs("/", ListStart, SiblingNode("/0")))
    rs.content should contain(Content("/0", "Group", Nil))
  }

  behavior of "Pcs.leafText"

  it should "capture a text-only leaf's text" in {
    Pcs.leafText(GenericElement("name", Nil, Seq(GenericText("Simple placemark")))) shouldBe Some("Simple placemark")
  }

  it should "concatenate more than one text/CDATA child, in order" in {
    val e = GenericElement("mixed-text", Nil, Seq(GenericText("Hello, "), GenericCData("world"), GenericText("!")))
    Pcs.leafText(e) shouldBe Some("Hello, world!")
  }

  it should "return None for a childless element (nothing to capture)" in {
    Pcs.leafText(GenericElement("Point", Nil, Nil)) shouldBe None
  }

  it should "return None for a container with element children, even if some incidental text sits alongside them" in {
    val e = GenericElement("Folder", Nil, Seq(GenericText("\n  "), GenericElement("Placemark", Nil, Nil), GenericText("\n")))
    Pcs.leafText(e) shouldBe None
  }

  behavior of "Pcs.leafIsCData"

  it should "be true for a leaf whose only child is CDATA" in {
    Pcs.leafIsCData(GenericElement("script", Nil, Seq(GenericCData("alert(1)")))) shouldBe true
  }

  it should "be false for a leaf whose only child is plain text" in {
    Pcs.leafIsCData(GenericElement("name", Nil, Seq(GenericText("Simple placemark")))) shouldBe false
  }

  it should "be false for a genuine mix of text and CDATA, even though leafText still concatenates both" in {
    Pcs.leafIsCData(GenericElement("mixed", Nil, Seq(GenericText("a"), GenericCData("b")))) shouldBe false
  }

  it should "be false for a childless element (nothing to be CDATA)" in {
    Pcs.leafIsCData(GenericElement("Point", Nil, Nil)) shouldBe false
  }

  behavior of "Pcs.relations, character-data nodes"

  it should "represent a leaf's text as an independent Content relation and Pcs child" in {
    val root = GenericElement("Placemark", Nil, Seq(
      GenericElement("name", Nil, Seq(GenericText("Simple placemark"))),
      GenericElement("description", Nil, Seq(GenericText("Attached to the ground.")))
    ))
    val rs = Pcs.relations(root)
    rs.content should contain allOf(
      Content.element("/0", "name", Nil),
      Content.text("/0::text[0]", "Simple placemark"),
      Content.element("/1", "description", Nil),
      Content.text("/1::text[0]", "Attached to the ground.")
    )
    rs.pcs should contain allOf(
      Pcs("/0", ListStart, SiblingNode("/0::text[0]")),
      Pcs("/0", SiblingNode("/0::text[0]"), ListEnd),
      Pcs("/0::text[0]", ListStart, ListEnd)
    )
  }

  it should "put text, elements and trailing text in one ordered Pcs chain" in {
    val root = GenericElement("p", Nil, Seq(
      GenericText("Hello "),
      GenericElement("b", Seq("Self" -> "bold"), Seq(GenericText("world"))),
      GenericText("!")
    ))
    val rs = Pcs.relations(root)

    rs.pcs should contain allOf(
      Pcs("/", ListStart, SiblingNode("/::text[0]")),
      Pcs("/", SiblingNode("/::text[0]"), SiblingNode("bold")),
      Pcs("/", SiblingNode("bold"), SiblingNode("/::text[1]")),
      Pcs("/", SiblingNode("/::text[1]"), ListEnd)
    )
    rs.content should contain allOf(
      Content.text("/::text[0]", "Hello "),
      Content.element("bold", "b", Seq("Self" -> "bold")),
      Content.text("/::text[1]", "!")
    )
  }

  // The project stores the root's own content and child chain, without the paper's
  // artificial parent above it. Equivalence is limited to a fixed, known root label.
  private def node(id: String, kids: GenericElement*): GenericElement =
    GenericElement("N", Seq("Self" -> id), kids)
  private def c(id: String): Content = Content.element(id, "N", Seq("Self" -> id))
  private def leaf(id: String): Pcs = Pcs(id, ListStart, ListEnd)
  private def edge(p: String, a: String, b: String): Pcs = Pcs(p, SiblingNode(a), SiblingNode(b))
  private def start(p: String, a: String): Pcs = Pcs(p, ListStart, SiblingNode(a))
  private def end(p: String, a: String): Pcs = Pcs(p, SiblingNode(a), ListEnd)

  it should "encode complete relation sets for all list boundaries and structural states" in {
    val cases = Seq(
      ("empty", node("r"), Set(leaf("r")), Set(c("r"))),
      ("single", node("r", node("a")), Set(start("r", "a"), end("r", "a"), leaf("a")), Set(c("r"), c("a"))),
      ("two children", node("r", node("a"), node("b")),
        Set(start("r", "a"), edge("r", "a", "b"), end("r", "b"), leaf("a"), leaf("b")), Set(c("r"), c("a"), c("b"))),
      ("before middle delete", node("r", node("a"), node("b"), node("c")),
        Set(start("r", "a"), edge("r", "a", "b"), edge("r", "b", "c"), end("r", "c"), leaf("a"), leaf("b"), leaf("c")),
        Set(c("r"), c("a"), c("b"), c("c"))),
      ("before cross parent", node("r", node("p", node("a")), node("q")),
        Set(start("r", "p"), edge("r", "p", "q"), end("r", "q"), start("p", "a"), end("p", "a"), leaf("a"), leaf("q")),
        Set(c("r"), c("p"), c("q"), c("a"))),
      ("head insert", node("r", node("x"), node("a"), node("b")),
        Set(start("r", "x"), edge("r", "x", "a"), edge("r", "a", "b"), end("r", "b"), leaf("x"), leaf("a"), leaf("b")),
        Set(c("r"), c("x"), c("a"), c("b"))),
      ("tail insert", node("r", node("a"), node("b"), node("x")),
        Set(start("r", "a"), edge("r", "a", "b"), edge("r", "b", "x"), end("r", "x"), leaf("a"), leaf("b"), leaf("x")),
        Set(c("r"), c("a"), c("b"), c("x"))),
      ("middle delete", node("r", node("a"), node("c")),
        Set(start("r", "a"), edge("r", "a", "c"), end("r", "c"), leaf("a"), leaf("c")), Set(c("r"), c("a"), c("c"))),
      ("reorder", node("r", node("b"), node("a")),
        Set(start("r", "b"), edge("r", "b", "a"), end("r", "a"), leaf("a"), leaf("b")), Set(c("r"), c("a"), c("b"))),
      ("cross parent", node("r", node("p"), node("q", node("a"))),
        Set(start("r", "p"), edge("r", "p", "q"), end("r", "q"), leaf("p"), start("q", "a"), end("q", "a"), leaf("a")),
        Set(c("r"), c("p"), c("q"), c("a"))))
    cases.foreach { case (name, tree, pcs, content) =>
      withClue(name) { Pcs.relations(tree) shouldBe RelationSet(pcs, content) }
    }
  }

  it should "encode the complete mixed text and CDATA chain including every leaf boundary" in {
    val tree = GenericElement("p", Nil, Seq(GenericText(" before "), node("a"), GenericCData("after")))
    Pcs.relations(tree) shouldBe RelationSet(
      Set(start("/", "/::text[0]"), edge("/", "/::text[0]", "a"), edge("/", "a", "/::text[1]"),
        end("/", "/::text[1]"), leaf("/::text[0]"), leaf("a"), leaf("/::text[1]")),
      Set(Content.element("/", "p", Nil), Content.text("/::text[0]", " before "), c("a"), Content.cdata("/::text[1]", "after")))
  }

}
