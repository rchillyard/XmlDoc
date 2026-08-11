package com.phasmidsoftware.xmldoc.xml

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import scala.util.{Failure, Success}

class ZipSpec extends AnyFlatSpec with should.Matchers {

  private val idmlFile = new File(getClass.getResource("HelloWorld.idml").toURI)

  // NOTE: writeEntry mutates its file argument, so tests that write must run against a
  // scratch copy of idmlFile, never idmlFile itself (which is a shared test resource).
  private def scratchCopy(): File = {
    val temp = File.createTempFile("HelloWorld", ".idml")
    temp.deleteOnExit()
    Files.copy(idmlFile.toPath, temp.toPath, StandardCopyOption.REPLACE_EXISTING)
    temp
  }

  behavior of "Zip"

  it should "list entries" in {
    Zip.entries(idmlFile) match {
      case Success(names) =>
        names should contain("mimetype")
        names should contain("designmap.xml")
        names should contain("META-INF/container.xml")
        names should contain("Spreads/Spread_ud1.xml")
        names should contain("Stories/Story_ue6.xml")
      case Failure(x) => fail(x)
    }
  }

  it should "read the mimetype entry" in {
    Zip.readEntry(idmlFile, "mimetype") shouldBe Success("application/vnd.adobe.indesign-idml-package")
  }

  it should "read designmap.xml as XML-parseable text" in {
    Zip.readEntry(idmlFile, "designmap.xml") match {
      case Success(content) =>
        content should include("<Document")
        noException should be thrownBy scala.xml.XML.loadString(content)
      case Failure(x) => fail(x)
    }
  }

  it should "fail to read a non-existent entry" in {
    Zip.readEntry(idmlFile, "NoSuchEntry.xml") should matchPattern { case Failure(_) => }
  }

  it should "overwrite an existing entry" in {
    val file = scratchCopy()
    Zip.writeEntry(file, "designmap.xml", "<Document/>") shouldBe Success(())
    Zip.readEntry(file, "designmap.xml") shouldBe Success("<Document/>")
  }

  it should "write a new entry that didn't exist before" in {
    val file = scratchCopy()
    Zip.writeEntry(file, "NewEntry.txt", "hello") shouldBe Success(())
    Zip.readEntry(file, "NewEntry.txt") shouldBe Success("hello")
    Zip.entries(file).get should contain("NewEntry.txt")
  }

  it should "leave other entries untouched when writing one entry" in {
    val file = scratchCopy()
    Zip.writeEntry(file, "designmap.xml", "<Document/>")
    Zip.readEntry(file, "mimetype") shouldBe Success("application/vnd.adobe.indesign-idml-package")
  }
}
