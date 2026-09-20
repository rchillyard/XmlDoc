package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.{Content, ListStart, Pcs, SiblingNode}
import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

/**
 * `Pcs` itself is generic (see `core`'s own `PcsSpec`) - these are the tests that need either a
 * real `.idml` fixture or the idml-specific `EditDetector`, so only this module can run them.
 */
class PcsIdmlSpec extends AnyFlatSpec with should.Matchers {

  it should "detect a reorder as a change in Pcs relations, even though the two Self-identified nodes are otherwise untouched" in {
    val base = GenericElement("Spread", Seq("Self" -> "us"), Seq(
      GenericElement("Rectangle", Seq("Self" -> "u1"), Nil),
      GenericElement("TextFrame", Seq("Self" -> "u2"), Nil)
    ))
    val reordered = GenericElement("Spread", Seq("Self" -> "us"), Seq(
      GenericElement("TextFrame", Seq("Self" -> "u2"), Nil),
      GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)
    ))
    // EditDetector, being attribute-only, is blind to the swap: no node's own attributes changed.
    EditDetector.detectEdits(base, reordered) shouldBe Nil
    // Pcs sees it: the successor of u1 was u2, now it's ListEnd (and symmetrically for u2).
    val (baseRs, reorderedRs) = (Pcs.relations(base), Pcs.relations(reordered))
    baseRs.pcs should contain(Pcs("us", SiblingNode("u1"), SiblingNode("u2")))
    reorderedRs.pcs should not contain Pcs("us", SiblingNode("u1"), SiblingNode("u2"))
    (reorderedRs.pcs -- baseRs.pcs) should contain allOf(
      Pcs("us", ListStart, SiblingNode("u2")),
      Pcs("us", SiblingNode("u2"), SiblingNode("u1"))
    )
    baseRs.content shouldBe reorderedRs.content // content itself is identical either way
  }

  behavior of "Pcs.relations, real HelloWorld2 file"

  it should "decompose a real Spread without crashing, and place a known Self both as content and as a sibling" in {
    val spread = IdmlPackage.open(new File(getClass.getResource("HelloWorld2.idml").toURI)).get
      .loadPart("Spreads/Spread_ud1.xml").get.childElements.find(_.tag == "Spread").get
    val rs = Pcs.relations(spread)
    rs.content.map(_.label) should contain("u13a") // a real page item's Self, from TreeMatcherIdmlSpec
    rs.pcs.collect { case Pcs(_, SiblingNode("u13a"), _) => () } should not be empty
  }
}
