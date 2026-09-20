package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.{PcsMerger, PcsTreeBuilder}
import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericElement, GenericText}
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

  // Pcs.relations keeps a text-only leaf's text (Pcs.leafText) but never the whitespace
  // GenericText nodes real files are full of between element children - this projects a real tree
  // down to that same shape (leaf text kept, inter-element whitespace dropped) before comparing.
  private def normalize(e: GenericElement): GenericElement =
    if (e.childElements.isEmpty) {
      val text = e.children.collect { case GenericText(t) => t; case GenericCData(t) => t }.mkString
      GenericElement(e.tag, e.attributes, if (text.isEmpty) Nil else Seq(GenericText(text)))
    } else GenericElement(e.tag, e.attributes, e.childElements.map(normalize))

  behavior of "PcsTreeBuilder.build, real HelloWorld2 files"

  it should "rebuild HelloWorld2D's real insertion end to end through PcsMerger" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    val d = spread("HelloWorld2D.idml", "Spreads/Spread_ud1.xml")
    val result = PcsMerger.merge(base, d, base)
    result.conflicts shouldBe Nil
    val rebuilt = PcsTreeBuilder.build(result).get
    rebuilt.childElements.flatMap(_.attributes.collectFirst { case ("Self", v) => v }) should contain("u141")
    rebuilt shouldBe normalize(d)
  }
}
