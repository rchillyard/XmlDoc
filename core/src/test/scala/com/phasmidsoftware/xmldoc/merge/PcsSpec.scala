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

  behavior of "Pcs.relations, text-only leaves"

  it should "carry a leaf's text into its own Content relation, not as a separate Pcs sibling" in {
    val root = GenericElement("Placemark", Nil, Seq(
      GenericElement("name", Nil, Seq(GenericText("Simple placemark"))),
      GenericElement("description", Nil, Seq(GenericText("Attached to the ground.")))
    ))
    val rs = Pcs.relations(root)
    rs.content should contain allOf(
      Content("/0", "name", Nil, Some("Simple placemark")),
      Content("/1", "description", Nil, Some("Attached to the ground."))
    )
    // the structural chain is exactly as if the leaves were childless - no extra relation for the text itself
    rs.pcs should contain allOf(Pcs("/0", ListStart, ListEnd), Pcs("/1", ListStart, ListEnd))
  }
}
