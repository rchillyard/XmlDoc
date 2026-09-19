package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

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

  behavior of "PcsEditDetector.detectEdits, real HelloWorld2 files"

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  it should "find the new Rectangle's Content among the edits from HelloWorld2 to HelloWorld2D" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    val d = spread("HelloWorld2D.idml", "Spreads/Spread_ud1.xml")
    val edits = PcsEditDetector.detectEdits(base, d)
    edits.content.map(_.label) should contain("u141") // the new Rectangle added in D, per TreeMatcherSpec
  }
}
