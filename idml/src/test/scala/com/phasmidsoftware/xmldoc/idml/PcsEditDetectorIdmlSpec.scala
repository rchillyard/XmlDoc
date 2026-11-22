package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.PcsEditDetector
import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

/**
 * `PcsEditDetector` itself is generic (see `core`'s own `PcsEditDetectorSpec`) - this is the test
 * that needs a real `.idml` fixture, which only this module can load.
 */
class PcsEditDetectorIdmlSpec extends AnyFlatSpec with should.Matchers {

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  behavior of "PcsEditDetector.detectEdits, real HelloWorld2 files"

  it should "find the new Rectangle's Content among the edits from HelloWorld2 to HelloWorld2D" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    val d = spread("HelloWorld2D.idml", "Spreads/Spread_ud1.xml")
    val edits = PcsEditDetector.detectEdits(base, d)
    edits.content.map(_.label) should contain("u141") // the new Rectangle added in D, per TreeMatcherIdmlSpec
  }

  it should "produce no relation edits for an unchanged real Spread" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    PcsEditDetector.detectEdits(base, base, EditDetector.defaultIgnoredAttributes) shouldBe
      com.phasmidsoftware.xmldoc.merge.RelationSet(Set.empty, Set.empty)
  }

  it should "ignore only the frozen volatile fields while retaining full edited content" in {
    import com.phasmidsoftware.xmldoc.merge.{Content, RelationSet}
    val ignored = EditDetector.defaultIgnoredAttributes
    ignored shouldBe Set("LinkImportTime", "StyleUniqueId")
    def rectangle(stamp: String, fill: String) = GenericElement("Rectangle", Seq(
      "Self" -> "a", "LinkImportTime" -> stamp, "StyleUniqueId" -> stamp, "FillColor" -> fill), Nil)
    def spreadWith(item: GenericElement) = GenericElement("Spread", Seq("Self" -> "s"), Seq(item))
    val base = spreadWith(rectangle("1", "None"))
    val volatile = spreadWith(rectangle("2", "None"))
    PcsEditDetector.detectEdits(base, volatile, ignored) shouldBe RelationSet(Set.empty, Set.empty)
    PcsEditDetector.detectEdits(base, volatile) shouldBe RelationSet(Set.empty,
      Set(Content.element("a", "Rectangle", rectangle("2", "None").attributes)))
    val edited = rectangle("2", "Red")
    PcsEditDetector.detectEdits(base, spreadWith(edited), ignored) shouldBe RelationSet(Set.empty,
      Set(Content.element("a", "Rectangle", edited.attributes)))
    // Comparison normalization must not remove the ignored fields from the emitted relation.
    PcsEditDetector.detectEdits(base, base, ignored) shouldBe RelationSet(Set.empty, Set.empty)
  }

  it should "report the full text edit while preserving IDML wrapper identities" in {
    import com.phasmidsoftware.xmldoc.merge.{Content, RelationSet}
    import com.phasmidsoftware.xmldoc.xml.GenericText
    def story(value: String) = GenericElement("Story", Seq("Self" -> "s"), Seq(
      GenericElement("ParagraphStyleRange", Nil, Seq(GenericElement("Content", Nil, Seq(GenericText(value)))))))
    PcsEditDetector.detectEdits(story(" A "), story(" B "), EditDetector.defaultIgnoredAttributes) shouldBe
      RelationSet(Set.empty, Set(Content.text("/0/0::text[0]", " B ")))
  }

}
