package com.phasmidsoftware.xmldoc.kml

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import scala.util._

class KMLEditorSpec extends AnyFlatSpec with should.Matchers {

  behavior of "KMLEditor"

  val placemark = "Placemark"

  it should "parse" in {
    val result: IO[KMLEditor] = KMLEditor.parse(Success("kml/src/main/resources/com/phasmidsoftware/xmldoc/kml/sampleEdits.txt"))
    val editor = result.unsafeRunSync()
    editor.edits.take(2) shouldBe Seq(
      KmlEdit(KmlEdit.JOIN, 2, Element(placemark, "Salem & Lowell RR (#1)"), Some(Element(placemark, "Salem & Lowell RR (#2)"))),
      KmlEdit(KmlEdit.DELETE, 1, Element(placemark, "Salem & Lowell RR (#2)"), None)
    )
  }

}
