package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import scala.util.{Failure, Success}

class PcsTreeBuilderSpec extends AnyFlatSpec with should.Matchers {

  private def spreadOf(self: String, children: GenericElement*): GenericElement =
    GenericElement("Spread", Seq("Self" -> self), children)

  private def rect(self: String, attrs: (String, String)*): GenericElement =
    GenericElement("Rectangle", ("Self" -> self) +: attrs, Nil)

  behavior of "PcsTreeBuilder.build, round-tripping a single tree through Pcs.relations"

  it should "reconstruct an untouched tree exactly, including a childless node's own Self" in {
    val tree = spreadOf("us", rect("u1", "Fill" -> "None"), rect("u2"))
    PcsTreeBuilder.build(Pcs.relations(tree), "us") shouldBe Success(tree)
  }

  it should "reconstruct a Self-less node using its NodePath label" in {
    val tree = GenericElement("Root", Nil, Seq(GenericElement("Group", Nil, Nil)))
    PcsTreeBuilder.build(Pcs.relations(tree), "/") shouldBe Success(tree)
  }

  behavior of "PcsTreeBuilder.build, after a clean PcsMerger.merge (no conflicts)"

  it should "rebuild a one-sided reorder exactly as the modifying side arranged it" in {
    val base = spreadOf("us", rect("u1"), rect("u2"))
    val right = spreadOf("us", rect("u2"), rect("u1"))
    val result = PcsMerger.merge(base, base, right)
    result.conflicts shouldBe Nil
    PcsTreeBuilder.build(result) shouldBe Success(right)
  }

  it should "rebuild a one-sided insertion with the new node spliced in" in {
    val base = spreadOf("us", rect("u1"))
    val left = spreadOf("us", rect("u1"), rect("u2", "Fill" -> "Red"))
    val result = PcsMerger.merge(base, left, base)
    result.conflicts shouldBe Nil
    PcsTreeBuilder.build(result) shouldBe Success(left)
  }

  it should "rebuild both sides' independent, non-overlapping changes combined" in {
    val base = spreadOf("us", rect("u1", "Fill" -> "None"), rect("u2"))
    val left = spreadOf("us", rect("u1", "Fill" -> "Red"), rect("u2")) // left restyles u1
    val right = spreadOf("us", rect("u1", "Fill" -> "None"), rect("u2"), rect("u3")) // right adds u3
    val result = PcsMerger.merge(base, left, right)
    result.conflicts shouldBe Nil
    val expected = spreadOf("us", rect("u1", "Fill" -> "Red"), rect("u2"), rect("u3"))
    PcsTreeBuilder.build(result) shouldBe Success(expected)
  }

  behavior of "PcsTreeBuilder.build, an unresolved conflict"

  it should "fail rather than silently reconstruct a bad tree from a move/move conflict (Kaining's 05)" in {
    val base = spreadOf("us", rect("u1"), rect("u2"), rect("u3"))
    val left = spreadOf("us", rect("u2"), rect("u3"), rect("u1")) // left moves u1 to the end
    val right = spreadOf("us", rect("u2"), rect("u1"), rect("u3")) // right moves u1 to the middle
    val result = PcsMerger.merge(base, left, right)
    result.conflicts should not be empty
    PcsTreeBuilder.build(result) shouldBe a[Failure[?]]
  }

  it should "fail with a clear error when a referenced label has no content (an Insert/Insert content conflict)" in {
    val base = spreadOf("us")
    val left = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val right = spreadOf("us", rect("u1", "Fill" -> "Magenta"))
    val result = PcsMerger.merge(base, left, right)
    val Failure(t) = PcsTreeBuilder.build(result): @unchecked
    t.getMessage should include("no content recorded for u1")
  }

  behavior of "PcsTreeBuilder.build, real HelloWorld2 files"

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  // Pcs.relations only ever captures element children (same scope limit as TreeMatcher/
  // EditDetector), so a rebuilt tree never has the whitespace GenericText nodes real files are
  // full of - this projects a real tree down to the same elements-only shape before comparing.
  private def stripText(e: GenericElement): GenericElement = GenericElement(e.tag, e.attributes, e.childElements.map(stripText))

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
