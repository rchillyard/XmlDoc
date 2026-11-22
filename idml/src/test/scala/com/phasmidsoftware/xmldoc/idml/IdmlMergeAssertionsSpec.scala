package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.StructuralConflict
import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.exceptions.TestFailedException

class IdmlMergeAssertionsSpec extends AnyFlatSpec with Matchers {
  import IdmlMergeAssertions._
  private val sample = GenericElement("p", Seq("Self" -> "p1", "xmlns" -> "urn:p"),
    Seq(GenericText(" hello "), GenericElement("b", Nil, Nil), GenericCData("!")))
  private val c = StructuralConflict("Fill of p1", Some("None"), Some("Red"), Some("Blue"))

  "XML assertions" should "ignore attribute order without changing the input" in {
    assertXmlEquals(sample.copy(attributes = sample.attributes.reverse), sample)
    sample.attributes.head shouldBe ("Self" -> "p1")
  }

  it should "reject reordered, extra and missing children" in {
    for (children <- Seq(sample.children.reverse, sample.children :+ GenericText("extra"), sample.children.dropRight(1))) {
      intercept[TestFailedException](assertXmlEquals(sample.copy(children = children), sample))
    }
  }

  it should "preserve whitespace, node kinds, qualified names, namespaces and references" in {
    val variants = Seq(
      sample.copy(children = GenericText("hello ") +: sample.children.tail),
      sample.copy(children = sample.children.dropRight(1) :+ GenericText("!")),
      sample.copy(tag = "x:p"),
      sample.copy(attributes = Seq("Self" -> "p1", "xmlns" -> "urn:other")),
      sample.copy(attributes = sample.attributes :+ ("ParentStory" -> "s2")),
      sample.copy(attributes = sample.attributes.map { case (k, v) => k -> (if (k == "Self") "p2" else v) }))
    variants.foreach(v => intercept[TestFailedException](assertXmlEquals(v, sample)))
  }

  it should "ignore only explicitly selected attributes at every depth" in {
    val left = sample.copy(children = Seq(sample.copy(attributes = sample.attributes :+ ("LinkImportTime" -> "1"))))
    val right = sample.copy(children = Seq(sample.copy(attributes = sample.attributes :+ ("LinkImportTime" -> "2"))))
    intercept[TestFailedException](assertXmlEquals(left, right))
    assertXmlEquals(left, right, Set("LinkImportTime"))
    intercept[TestFailedException](assertXmlEquals(sample.copy(tag = "q"), sample, Set("LinkImportTime")))
  }

  it should "report the first differing path with actual and expected values" in {
    val err = intercept[TestFailedException](assertXmlEquals(
      sample.copy(children = GenericText("changed") +: sample.children.tail), sample))
    err.message.get should include ("/root/child[0]/text")
    err.message.get should include ("actual=changed")
    err.message.get should include ("expected= hello ")
  }

  "Conflict assertions" should "ignore report order and explicitly reverse only branch values" in {
    val other = c.copy(slot = "parent of p1")
    assertConflictsEqual(Seq(other, c), Seq(c, other))
    val reversed = c.copy(leftValue = c.rightValue, rightValue = c.leftValue)
    assertConflictsEqual(Seq(reversed), Seq(c), swapActualSides = true)
    normalizeConflicts(Seq(reversed), swapSides = true).head.baseValue shouldBe c.baseValue
    intercept[TestFailedException](assertConflictsEqual(Seq(reversed), Seq(c)))
  }

  it should "reject missing, extra, duplicate and changed conflict data" in {
    val variants = Seq(Seq.empty, Seq(c, c), Seq(c.copy(slot = "other")),
      Seq(c.copy(baseValue = None)), Seq(c.copy(leftValue = Some(""))), Seq(c.copy(rightValue = None)))
    variants.foreach(v => intercept[TestFailedException](assertConflictsEqual(v, Seq(c))))
    intercept[TestFailedException](assertConflictsEqual(Seq(c.copy(baseValue = Some(""))), Seq(c.copy(baseValue = None))))
  }

  "Part assertions" should "preserve part paths, empty reports and duplicate reports" in {
    val p = PartConflict("Stories/s.xml", Seq(c))
    val q = PartConflict("Spreads/p.xml", Nil)
    assertPartConflictsEqual(Seq(q, p), Seq(p, q))
    assertPartConflictsEqual(Seq(p.copy(conflicts = Seq(c.copy(leftValue = c.rightValue, rightValue = c.leftValue)))),
      Seq(p), swapActualSides = true)
    for (v <- Seq(Seq(p), Seq(q, p, p), Seq(q, p.copy(src = "Stories/other.xml")),
                  Seq(q, p.copy(conflicts = Nil)), Seq(q, p.copy(conflicts = Seq(c.copy(baseValue = None)))))) {
      intercept[TestFailedException](assertPartConflictsEqual(v, Seq(p, q)))
    }
  }

  it should "compare complete XML inventories and locate XML errors by part" in {
    val parts = Map("Stories/s.xml" -> sample)
    assertXmlPartsEqual(parts, parts)
    intercept[TestFailedException](assertXmlPartsEqual(Map.empty, parts))
    intercept[TestFailedException](assertXmlPartsEqual(parts + ("Spreads/x.xml" -> sample), parts))
    val err = intercept[TestFailedException](assertXmlPartsEqual(Map("Stories/s.xml" -> sample.copy(tag = "other")), parts))
    err.message.get should include ("Stories/s.xml: /root/name")
    val dated = Map("Stories/s.xml" -> sample.copy(attributes = sample.attributes :+ ("StyleUniqueId" -> "new")))
    intercept[TestFailedException](assertXmlPartsEqual(dated, parts))
    assertXmlPartsEqual(dated, parts, Set("StyleUniqueId"))
  }

}
