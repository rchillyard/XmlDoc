package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.StructuralConflict
import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericContent, GenericElement, GenericText}
import org.scalatest.Assertion
import org.scalatest.Assertions.{assert, fail}

/** Test-only comparison for the MR01 qualified-name model, not XML canonicalization.
  * No attributes are ignored unless the caller explicitly supplies their names.
  */
object IdmlMergeAssertions {
  def xmlDifference(actual: GenericContent, expected: GenericContent,
                    ignoredAttributes: Set[String] = Set.empty): Option[String] = {
    def difference(a: GenericContent, e: GenericContent, path: String): Option[String] = {
      def mismatch(field: String, av: Any, ev: Any): Option[String] =
        if (av == ev) None else Some(s"$path/$field: actual=$av; expected=$ev")
      (a, e) match {
        case (ae: GenericElement, ee: GenericElement) =>
          val aa = ae.attributes.filterNot(x => ignoredAttributes(x._1)).toSet
          val ea = ee.attributes.filterNot(x => ignoredAttributes(x._1)).toSet
          mismatch("name", ae.tag, ee.tag)
            .orElse(mismatch("attributes", aa.toSeq.sorted, ea.toSeq.sorted))
            .orElse {
              val shared = ae.children.zip(ee.children).zipWithIndex.iterator
                .map { case ((ac, ec), i) => difference(ac, ec, s"$path/child[$i]") }
                .collectFirst { case Some(message) => message }
              shared.orElse(mismatch("child-count", ae.children.size, ee.children.size))
            }
        case (GenericText(at), GenericText(et)) => mismatch("text", at, et)
        case (GenericCData(at), GenericCData(et)) => mismatch("cdata", at, et)
        case _ => Some(s"$path/node-kind: actual=$a; expected=$e")
      }
    }
    difference(actual, expected, "/root")
  }

  def assertXmlEquals(actual: GenericContent, expected: GenericContent,
                      ignoredAttributes: Set[String] = Set.empty): Assertion =
    xmlDifference(actual, expected, ignoredAttributes) match {
      case Some(message) => fail(message)
      case None => assert(true)
    }

  /** Sorting preserves multiplicity, None versus empty, base values and full slots. */
  def normalizeConflicts(conflicts: Seq[StructuralConflict],
                         swapSides: Boolean = false): Seq[StructuralConflict] =
    conflicts.map { c =>
      if (swapSides) c.copy(leftValue = c.rightValue, rightValue = c.leftValue) else c
    }.sortBy(c => (c.slot, c.baseValue, c.leftValue, c.rightValue))

  def assertConflictsEqual(actual: Seq[StructuralConflict], expected: Seq[StructuralConflict],
                           swapActualSides: Boolean = false): Assertion =
    assert(normalizeConflicts(actual, swapActualSides) == normalizeConflicts(expected),
      s"conflicts: actual=${normalizeConflicts(actual, swapActualSides)}; expected=${normalizeConflicts(expected)}")

  def normalizePartConflicts(conflicts: Seq[PartConflict],
                             swapSides: Boolean = false): Seq[PartConflict] =
    conflicts.map(c => c.copy(conflicts = normalizeConflicts(c.conflicts, swapSides)))
      .sortBy(c => (c.src, c.conflicts.toString))

  def assertPartConflictsEqual(actual: Seq[PartConflict], expected: Seq[PartConflict],
                               swapActualSides: Boolean = false): Assertion =
    assert(normalizePartConflicts(actual, swapActualSides) == normalizePartConflicts(expected),
      s"part conflicts: actual=${normalizePartConflicts(actual, swapActualSides)}; expected=${normalizePartConflicts(expected)}")

  /** Compare the complete declared XML part inventory, including additions/removals.
    * Binary entry preservation belongs to driver tests, not this XML helper.
    */
  def assertXmlPartsEqual(actual: Map[String, GenericElement], expected: Map[String, GenericElement],
                          ignoredAttributes: Set[String] = Set.empty): Assertion = {
    assert(actual.keySet == expected.keySet,
      s"parts: actual=${actual.keys.toSeq.sorted}; expected=${expected.keys.toSeq.sorted}")
    actual.keys.toSeq.sorted.foreach { src =>
      xmlDifference(actual(src), expected(src), ignoredAttributes).foreach(message => fail(s"$src: $message"))
    }
    assert(true)
  }

}
