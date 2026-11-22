package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.{GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class PcsEditDetectorSpec extends AnyFlatSpec with should.Matchers {

  behavior of "PcsEditDetector.detectEdits, synthetic trees"

  it should "report no edits at all when nothing changed" in {
    val base = GenericElement("Spread", Seq("Self" -> "us"), Seq(GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)))
    PcsEditDetector.detectEdits(base, base) shouldBe RelationSet(Set.empty, Set.empty)
  }

  it should "report only Pcs edits for a pure reorder - content is untouched" in {
    val base = GenericElement("Spread", Seq("Self" -> "us"), Seq(
      GenericElement("Rectangle", Seq("Self" -> "u1"), Nil),
      GenericElement("TextFrame", Seq("Self" -> "u2"), Nil)
    ))
    val reordered = GenericElement("Spread", Seq("Self" -> "us"), Seq(
      GenericElement("TextFrame", Seq("Self" -> "u2"), Nil),
      GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)
    ))
    val edits = PcsEditDetector.detectEdits(base, reordered)
    edits.content shouldBe Set.empty
    edits.pcs shouldBe Set(
      Pcs("us", ListStart, SiblingNode("u2")),
      Pcs("us", SiblingNode("u2"), SiblingNode("u1")),
      Pcs("us", SiblingNode("u1"), ListEnd)
    )
  }

  it should "report only a Content edit for a pure attribute change - structure is untouched" in {
    val base = GenericElement("Spread", Seq("Self" -> "us"), Seq(GenericElement("Rectangle", Seq("Self" -> "u1", "Fill" -> "None"), Nil)))
    val restyled = GenericElement("Spread", Seq("Self" -> "us"), Seq(GenericElement("Rectangle", Seq("Self" -> "u1", "Fill" -> "Red"), Nil)))
    val edits = PcsEditDetector.detectEdits(base, restyled)
    edits.pcs shouldBe Set.empty
    edits.content shouldBe Set(Content("u1", "Rectangle", Seq("Self" -> "u1", "Fill" -> "Red")))
  }

  it should "report both the new node's Content and the Pcs links that splice it in, for an insertion" in {
    val base = GenericElement("Spread", Seq("Self" -> "us"), Seq(GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)))
    val inserted = GenericElement("Spread", Seq("Self" -> "us"), Seq(
      GenericElement("Rectangle", Seq("Self" -> "u1"), Nil),
      GenericElement("TextFrame", Seq("Self" -> "u2"), Nil)
    ))
    val edits = PcsEditDetector.detectEdits(base, inserted)
    edits.content shouldBe Set(Content("u2", "TextFrame", Seq("Self" -> "u2")))
    edits.pcs shouldBe Set(
      Pcs("us", SiblingNode("u1"), SiblingNode("u2")),
      Pcs("us", SiblingNode("u2"), ListEnd),
      Pcs("u2", ListStart, ListEnd) // the new (childless) node's own chain link
    )
  }

  it should "report a Content edit for the independent text node whose text changed, structure untouched" in {
    val base = GenericElement("Placemark", Nil, Seq(GenericElement("name", Nil, Seq(GenericText("Simple placemark")))))
    val renamed = GenericElement("Placemark", Nil, Seq(GenericElement("name", Nil, Seq(GenericText("Renamed placemark")))))
    val edits = PcsEditDetector.detectEdits(base, renamed)
    edits.pcs shouldBe Set.empty
    edits.content shouldBe Set(Content.text("/0::text[0]", "Renamed placemark"))
  }

  private def node(id: String, kids: GenericElement*): GenericElement = GenericElement("N", Seq("Self" -> id), kids)
  private def c(id: String): Content = Content.element(id, "N", Seq("Self" -> id))
  private def leaf(id: String): Pcs = Pcs(id, ListStart, ListEnd)
  private def edge(p: String, a: String, b: String): Pcs = Pcs(p, SiblingNode(a), SiblingNode(b))
  private def start(p: String, a: String): Pcs = Pcs(p, ListStart, SiblingNode(a))
  private def end(p: String, a: String): Pcs = Pcs(p, SiblingNode(a), ListEnd)

  it should "detect complete state differences at head, middle, tail and empty boundaries" in {
    val ab = node("r", node("a"), node("b"))
    val cases = Seq(
      (ab, node("r", node("x"), node("a"), node("b")),
        RelationSet(Set(start("r", "x"), edge("r", "x", "a"), leaf("x")), Set(c("x")))),
      (ab, node("r", node("a"), node("x"), node("b")),
        RelationSet(Set(edge("r", "a", "x"), edge("r", "x", "b"), leaf("x")), Set(c("x")))),
      (ab, node("r", node("a"), node("b"), node("x")),
        RelationSet(Set(edge("r", "b", "x"), end("r", "x"), leaf("x")), Set(c("x")))),
      (node("r", node("a"), node("x"), node("b")), ab,
        RelationSet(Set(edge("r", "a", "b")), Set.empty)),
      (ab, node("r", node("b")), RelationSet(Set(start("r", "b")), Set.empty)),
      (ab, node("r", node("a")), RelationSet(Set(end("r", "a")), Set.empty)),
      (node("r", node("a")), node("r"), RelationSet(Set(leaf("r")), Set.empty)),
      (node("r"), node("r", node("a")), RelationSet(Set(start("r", "a"), end("r", "a"), leaf("a")), Set(c("a")))))
    cases.foreach { case (base, modified, expected) =>
      PcsEditDetector.detectEdits(base, modified) shouldBe expected
      val t0 = Pcs.relations(base)
      val t = Pcs.relations(modified)
      expected shouldBe RelationSet(t.pcs -- t0.pcs, t.content -- t0.content)
      PcsEditDetector.detectEdits(modified, modified) shouldBe RelationSet(Set.empty, Set.empty)
    }
  }

  it should "close the source context and splice the target for a cross-parent move" in {
    val base = node("r", node("p", node("a")), node("q"))
    val moved = node("r", node("p"), node("q", node("a")))
    PcsEditDetector.detectEdits(base, moved) shouldBe RelationSet(
      Set(leaf("p"), start("q", "a"), end("q", "a")), Set.empty)
    PcsEditDetector.detectEdits(moved, base) shouldBe RelationSet(
      Set(start("p", "a"), end("p", "a"), leaf("q")), Set.empty)
  }

  it should "use the supplied matching instead of hiding shifted paths behind equal Self values" in {
    def paragraph(text: String) = GenericElement("P", Nil, Seq(GenericText(text)))
    val a = paragraph("A")
    val b = paragraph("B")
    val base = GenericElement("Story", Seq("Self" -> "s"), Seq(a, b))
    val modified = GenericElement("Story", Seq("Self" -> "s"), Seq(b))
    val label = ContentMatcher.matchedLabel(base, modified)
    val expected = RelationSet(Set(start("s", "/1")), Set.empty)
    PcsEditDetector.detectEdits(base, modified, modifiedLabel = label) shouldBe expected
    val raw = PcsEditDetector.detectEdits(base, modified)
    raw shouldBe RelationSet(Set(end("s", "/0")), Set(Content.text("/0::text[0]", "B")))
    raw should not be expected
    // Explicitly changing the mapping of a Self-bearing child must also be observable.
    val identified = node("r", node("a"))
    val alternate: NodeRef => String = ref => if (ref.self.contains("a")) "other" else Pcs.label(ref)
    PcsEditDetector.detectEdits(identified, identified, modifiedLabel = alternate) shouldBe
      RelationSet(Set(start("r", "other"), end("r", "other"), leaf("other")),
        Set(Content.element("other", "N", Seq("Self" -> "a"))))
  }

}
