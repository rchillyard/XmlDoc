package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.TreeMatcher
import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

/**
 * `TreeMatcher` itself is generic (see `core`'s own `TreeMatcherSpec`) - these are the tests that
 * need real `.idml` fixtures, which only this module can load.
 */
class TreeMatcherIdmlSpec extends AnyFlatSpec with should.Matchers {

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  behavior of "TreeMatcher.matchBySelf, real HelloWorld2 files"

  it should "match the base's original page items against HelloWorld2D (a clean reopen plus one insertion)" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    val d = spread("HelloWorld2D.idml", "Spreads/Spread_ud1.xml")
    val m = TreeMatcher.matchBySelf(base, d)
    val matchedSelves = m.matched.map(_._1.self).flatten.toSet
    matchedSelves should contain allOf("uf8", "u10d", "u122", "u13a", "u13e")
    m.onlyInLeft shouldBe Nil // nothing was removed from the base
    m.onlyInRight.map(_.self) should contain(Some("u141")) // the new Rectangle added in D
  }

  it should "match every page item between HelloWorld2A and HelloWorld2B (independent edits to the same object)" in {
    val a = spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml")
    val b = spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml")
    val m = TreeMatcher.matchBySelf(a, b)
    val matchedSelves = m.matched.map(_._1.self).flatten.toSet
    matchedSelves should contain allOf("uff", "u102", "u124", "u13a", "u13d")
    // the moved-in-A/restyled-in-B Rectangle: same identity (matched), different content each side
    val (aRef, bRef) = m.matched.find(_._1.self.contains("u13d")).get
    val (aAttrs, bAttrs) = (aRef.element.attributes.toMap, bRef.element.attributes.toMap)
    aAttrs.get("ItemTransform") should not be bAttrs.get("ItemTransform") // A moved it, B didn't
    aAttrs.get("FillColor") shouldBe empty // A left fill/stroke alone
    bAttrs.get("FillColor") shouldBe defined // B restyled it
  }

  behavior of "TreeMatcher.matchBySelf, real Magazine files (Kaining's data)"

  it should "find substantial real-world matches between Magazine and Magazine-1 with no crash" in {
    val base = spread("Magazine.idml", "Spreads/Spread_uc6.xml")
    val v1 = spread("Magazine-1.idml", "Spreads/Spread_uc6.xml")
    val m = TreeMatcher.matchBySelf(base, v1)
    // 52 Self-bearing elements exist in the base's copy of this one spread alone
    m.matched.size should be > 10
  }
}
