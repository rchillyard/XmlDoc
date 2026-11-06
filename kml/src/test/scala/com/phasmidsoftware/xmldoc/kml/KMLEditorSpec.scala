package com.phasmidsoftware.xmldoc.kml

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.args.Args
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import java.nio.file.{Files, Paths}
import scala.io.Source
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

  // A copy of sample.kml in a new temporary directory (the editor writes its output next to its input).
  private def scratchKml(): File = {
    val file = new File(Files.createTempDirectory("KMLEditorSpec").toFile, "sample.kml")
    Files.copy(Paths.get("kml/src/main/resources/com/phasmidsoftware/xmldoc/kml/sample.kml"), file.toPath)
    file
  }

  private val edits = "kml/src/main/resources/com/phasmidsoftware/xmldoc/kml/sampleEdits.txt"

  it should "edit a KML file, writing the result next to it (as for KMLDoc's command line)" in {
    val in = scratchKml()
    KMLEditor.processKML(Args.parse(Array(in.getPath, edits))).unsafeRunSync()
    val out = new File(in.getParentFile, "sample_out.kml")
    out.exists() shouldBe true
    val text = Using.resource(Source.fromFile(out))(_.mkString)
    // NOTE: #2 has been joined to #1 (whose name now has both), and then deleted.
    text should include("<name><![CDATA[Salem & Lowell RR (#1)Salem & Lowell RR (#2)]]></name>")
    text should not include "<name><![CDATA[Salem & Lowell RR (#2)]]></name>"
    // NOTE: the output is itself KML.
    KMLCompanion.loadKML(Success(out.getPath)).unsafeRunSync().size shouldBe 1
  }

  it should "leave a KML file unchanged (apart from its format) when there are no edits" in {
    val in = scratchKml()
    KMLEditor(Nil).process(Success(in.getPath)).unsafeRunSync()
    val original = KMLCompanion.loadKML(Success(in.getPath)).unsafeRunSync()
    KMLCompanion.loadKML(Success(new File(in.getParentFile, "sample_out.kml").getPath)).unsafeRunSync() shouldBe original
  }

  it should "fail for a file which isn't .kml" in {
    the[Exception] thrownBy KMLEditor(Nil).process(Success("sample.txt")).unsafeRunSync() should have message "Syntax error: filename does not end with .kml"
  }

  it should "fail without both a KML file and an edits file" in {
    KMLEditor.processKML(Args.parse(Array("sample.kml"))).attempt.unsafeRunSync() shouldBe a[Left[?, ?]]
  }
}
