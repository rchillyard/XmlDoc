package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.PcsMerger
import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

/**
 * `PcsMerger` itself is generic (see `core`'s own `PcsMergerSpec`) - this is the test that needs
 * real `.idml` fixtures, which only this module can load.
 */
class PcsMergerIdmlSpec extends AnyFlatSpec with should.Matchers {

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  behavior of "PcsMerger.merge, real Mergeable files (the same trio MergerSpec uses)"

  it should "surface the same real Insert/Insert collision (u11c) that Merger's attribute-level check finds" in {
    val base = spread("Mergeable.idml", "Spreads/Spread_ud1.xml")
    val left = spread("Mergeable-Left.idml", "Spreads/Spread_ud1.xml")
    val right = spread("Mergeable-Right.idml", "Spreads/Spread_ud1.xml")
    val result = PcsMerger.merge(base, left, right)
    result.conflicts.map(_.slot) should contain("content of u11c")
  }
}
