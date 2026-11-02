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
}
