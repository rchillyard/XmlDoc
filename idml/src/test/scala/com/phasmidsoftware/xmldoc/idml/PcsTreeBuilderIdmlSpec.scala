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

  // Pcs.relations only ever captures element children (same scope limit as TreeMatcher/
  // EditDetector), so a rebuilt tree never has the whitespace GenericText nodes real files are
  // full of - this projects a real tree down to the same elements-only shape before comparing.
  private def stripText(e: GenericElement): GenericElement = GenericElement(e.tag, e.attributes, e.childElements.map(stripText))

  behavior of "PcsTreeBuilder.build, real HelloWorld2 files"

  it should "rebuild HelloWorld2D's real insertion end to end through PcsMerger" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    val d = spread("HelloWorld2D.idml", "Spreads/Spread_ud1.xml")
    val result = PcsMerger.merge(base, d, base)
    result.conflicts shouldBe Nil
    val rebuilt = PcsTreeBuilder.build(result).get
    rebuilt.childElements.flatMap(_.attributes.collectFirst { case ("Self", v) => v }) should contain("u141")
    rebuilt shouldBe stripText(d)
  }
}
