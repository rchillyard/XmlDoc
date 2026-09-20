package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.{GenericElement, Zip}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import java.nio.file.{Files, StandardCopyOption}

/**
 * Exercises `IdmlMergeDriver` against real `.idml` files - real zip I/O throughout, not just the
 * in-memory `GenericElement` merging `ThreeWayMerger`'s own specs already cover. `ours` is mutated
 * in place (exactly git's `%A` contract), so every test works on a fresh temp copy, never the real
 * fixture file.
 */
class IdmlMergeDriverSpec extends AnyFlatSpec with should.Matchers with BeforeAndAfterEach {

  private var tempFiles: List[File] = Nil

  override def afterEach(): Unit = {
    tempFiles.foreach(_.delete())
    tempFiles = Nil
  }

  private def resourceFile(name: String): File = new File(getClass.getResource(name).toURI)

  // A fresh, independently-mutable temp copy of a real fixture - the file `IdmlMergeDriver.merge`
  // is allowed to write into, since a resource-file copy can't be (it's on the test classpath).
  private def tempCopyOf(resourceName: String): File = {
    val temp = File.createTempFile("idml-merge-driver-spec-", ".idml")
    tempFiles ::= temp
    Files.copy(resourceFile(resourceName).toPath, temp.toPath, StandardCopyOption.REPLACE_EXISTING)
    temp
  }

  private def spreadOf(pkg: IdmlPackage, src: String): GenericElement =
    pkg.loadPart(src).get.childElements.find(_.tag == "Spread").get

  behavior of "IdmlMergeDriver.merge, a clean one-sided insertion"

  it should "apply theirs' new Rectangle into ours, in place, with no conflicts" in {
    val base = resourceFile("HelloWorld2.idml")
    val ours = tempCopyOf("HelloWorld2.idml") // ours: unchanged from base
    val theirs = resourceFile("HelloWorld2D.idml") // theirs: added Rectangle u141

    IdmlMergeDriver.merge(base, ours, theirs).get shouldBe Nil

    val merged = IdmlPackage.open(ours).get
    val spread = spreadOf(merged, "Spreads/Spread_ud1.xml")
    spread.childElements.flatMap(_.attributes.collectFirst { case ("Self", v) => v }) should contain("u141")
  }

  behavior of "IdmlMergeDriver.merge, a real conflict"

  // A whole-*package* merge of this real trio surfaces a genuine conflict that every prior test in
  // this codebase, scoped to one Spread, never could have: Story_ued.xml's own text content
  // ("Hello World!" in the base) was independently changed to two different strings by A and B -
  // a real Update/Update, correctly left blocking the merge. It also (before
  // EditDetector.defaultIgnoredAttributes grew a second entry, found via this same test) surfaced
  // a *second* volatile, auto-regenerated attribute beyond LinkImportTime: Resources/Styles.xml's
  // StyleUniqueId, a UUID InDesign re-stamps on every export - now ignored by default too, so it no
  // longer masks the one real conflict here under a pile of false ones.
  it should "report the real Story-text conflict and leave ours untouched" in {
    val base = resourceFile("HelloWorld2C.idml")
    val ours = tempCopyOf("HelloWorld2A.idml")
    val oursBytesBefore = Files.readAllBytes(ours.toPath)
    val theirs = resourceFile("HelloWorld2B.idml")

    val conflicts = IdmlMergeDriver.merge(base, ours, theirs).get
    conflicts.map(_.src) shouldBe Seq("Stories/Story_ued.xml")
    val slots = conflicts.flatMap(_.conflicts)
    slots.map(_.leftValue) should contain(Some("<Content>Hello World2A!</Content>"))
    slots.map(_.rightValue) should contain(Some("<Content>Hello World2B!</Content>"))

    Files.readAllBytes(ours.toPath) shouldBe oursBytesBefore // untouched - byte for byte
  }

  behavior of "IdmlMergeDriver.merge, a whole part removed"

  it should "delete the part and patch designmap.xml's idPkg reference, leaving everything else untouched" in {
    val base = resourceFile("HelloWorld2.idml")
    val ours = tempCopyOf("HelloWorld2.idml") // ours: unchanged from base
    val theirs = tempCopyOf("HelloWorld2.idml")
    removePart(theirs, "Stories/Story_u128.xml", "Story") // theirs: deletes a whole Story

    IdmlMergeDriver.merge(base, ours, theirs).get shouldBe Nil

    val merged = IdmlPackage.open(ours).get
    merged.parts.map(_.src) should not contain "Stories/Story_u128.xml"
    merged.parts.map(_.src) should contain allOf ("Stories/Story_u110.xml", "Stories/Story_ue6.xml", "Spreads/Spread_ud1.xml")
    merged.loadPart("Stories/Story_u110.xml") shouldBe a[scala.util.Success[?]] // everything else still readable
  }

  // Simulates "someone deleted a whole part and updated designmap.xml by hand" directly with the
  // generic Zip primitives (already independently trusted/tested) - deliberately not reusing
  // IdmlMergeDriver's own designmap-patching logic, so this fixture setup can't accidentally hide
  // a bug that logic might have.
  private def removePart(file: File, src: String, partType: String): Unit = {
    Zip.deleteEntry(file, src).get
    val designmap = Zip.readEntry(file, "designmap.xml").get
    val line = designmap.linesWithSeparators.find(l => l.contains(s"""<idPkg:$partType""") && l.contains(s"""src="$src"""")).get
    Zip.writeEntry(file, "designmap.xml", designmap.replace(line, "")).get
  }
}
