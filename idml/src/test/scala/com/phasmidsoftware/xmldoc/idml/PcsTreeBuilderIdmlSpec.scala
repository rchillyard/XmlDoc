package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.{PcsMerger, PcsTreeBuilder}
import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

/**
 * `PcsTreeBuilder` itself is generic (see `core`'s own `PcsTreeBuilderSpec`) - this is the test
 * that needs a real `.idml` fixture, which only this module can load.
 */
class PcsTreeBuilderIdmlSpec extends AnyFlatSpec with should.Matchers {

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  behavior of "PcsTreeBuilder.build, real HelloWorld2 files"

  it should "rebuild HelloWorld2D's real insertion end to end through PcsMerger" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    val d = spread("HelloWorld2D.idml", "Spreads/Spread_ud1.xml")
    val result = PcsMerger.merge(base, d, base)
    result.conflicts shouldBe Nil
    val rebuilt = PcsTreeBuilder.build(result).get
    rebuilt.childElements.flatMap(_.attributes.collectFirst { case ("Self", v) => v }) should contain("u141")
    rebuilt shouldBe d
  }

  it should "round-trip a complete IDML style wrapper with mixed and adjacent character data" in {
    import com.phasmidsoftware.xmldoc.merge.Pcs
    import com.phasmidsoftware.xmldoc.xml.{GenericText, GenericCData}
    import scala.util.Success
    val story = GenericElement("Story", Seq("Self" -> "s"), Seq(
      GenericElement("ParagraphStyleRange", Seq("AppliedParagraphStyle" -> "Body"), Seq(
        GenericText(" "), GenericElement("CharacterStyleRange", Nil, Seq(
          GenericElement("Content", Nil, Seq(GenericText("a"), GenericText("b"), GenericCData("<c>"))))),
        GenericElement("Br", Nil, Nil), GenericText("\n")))))
    PcsTreeBuilder.build(Pcs.relations(story), "s") shouldBe Success(story)
  }

}
