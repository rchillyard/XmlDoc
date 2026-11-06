package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.{GenericElement, Zip}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.File
import java.nio.file.{Files, StandardCopyOption}

class IdmlPackageMergerSpec extends AnyFlatSpec with Matchers {
  private def file(name: String, side: String) = new File(getClass.getResource(s"three-way-corpus/$name/$side.idml").toURI)
  private def pkg(name: String, side: String) = IdmlPackage.open(file(name, side)).get
  private def view(p: IdmlPackage) = p.parts.map(x => x.src -> p.loadPart(x.src).get).toMap
  private def merge(name: String, l: String = "left", r: String = "right") =
    IdmlPackageMerger.merge(pkg(name, "base"), pkg(name, l), pkg(name, r)).get

  "Package merge" should "detect 06b and 13 intent conflicts even though layout references are legal" in {
    Seq("06b-delete-thread-vs-content", "13-parentstory-vs-content").foreach { name =>
      IdmlReferenceValidator.validate(view(pkg(name, "left"))) shouldBe empty
      val normal = merge(name)
      normal.conflicts.flatMap(_.conflicts).exists(_.slot.contains("attribute=layout")) shouldBe true
      IdmlMergeAssertions.assertPartConflictsEqual(merge(name, "right", "left").conflicts, normal.conflicts, swapActualSides = true)
      merge(name, "left", "base").conflicts shouldBe empty
      merge(name, "base", "right").conflicts shouldBe empty
    }
  }

  it should "produce the complete valid insertion candidate in either branch order" in {
    val name = "11-thread-insert"
    Seq(("left", "right"), ("right", "left")).foreach { case (l, r) =>
      val result = merge(name, l, r)
      result.conflicts shouldBe empty
      val candidate = result.partOutcomes.foldLeft(view(pkg(name, l))) {
        case (v, PartContent(src, _, e)) => v.updated(src, e)
        case (v, PartRemoved(src)) => v - src
      }
      IdmlReferenceValidator.validate(candidate) shouldBe empty
      IdmlMergeAssertions.assertXmlPartsEqual(candidate, view(pkg(name, "left")), EditDetector.defaultIgnoredAttributes)
    }
  }

  it should "report 12's attribute disagreement independently of legal branch topology" in {
    val name = "12-next-divergent"
    val result = merge(name)
    result.conflicts.flatMap(_.conflicts).exists(_.slot.contains("NextTextFrame")) shouldBe true
    IdmlReferenceValidator.validate(view(pkg(name, "left"))) shouldBe empty
    IdmlReferenceValidator.validate(view(pkg(name, "right"))) shouldBe empty
    IdmlMergeAssertions.assertPartConflictsEqual(merge(name, "right", "left").conflicts, result.conflicts, swapActualSides = true)
  }

  it should "leave ours unchanged on domain conflicts and identity or missing-part failures" in {
    Seq("06b-delete-thread-vs-content", "13-parentstory-vs-content").foreach { name =>
      val ours = File.createTempFile("mr11-domain-", ".idml")
      try {
        Files.copy(file(name, "left").toPath, ours.toPath, StandardCopyOption.REPLACE_EXISTING)
        val before = Files.readAllBytes(ours.toPath)
        IdmlMergeDriver.merge(file(name, "base"), ours, file(name, "right")).get should not be empty
        Files.readAllBytes(ours.toPath) shouldBe before
      } finally ours.delete()
    }
    Seq("duplicate", "missing").foreach { mode =>
      val ours = File.createTempFile("mr11-invalid-", ".idml")
      val name = "11-thread-insert"
      try {
        Files.copy(file(name, "base").toPath, ours.toPath, StandardCopyOption.REPLACE_EXISTING)
        val p = IdmlPackage.open(ours).get
        val part = p.parts.find(_.partType == "Story").get
        if (mode == "missing") Zip.deleteEntry(ours, part.src).get
        else {
          val root = p.loadPart(part.src).get
          val duplicate = root.copy(children = root.children ++ root.childElements.filter(_.tag == "Story"))
          Zip.writeEntry(ours, part.src, GenericElement.toXmlString(duplicate)).get
        }
        val before = Files.readAllBytes(ours.toPath)
        val result = IdmlMergeDriver.merge(file(name, "base"), ours, file(name, "base"))
        result.isFailure shouldBe true
        result.failed.get.getMessage should include(part.src)
        Files.readAllBytes(ours.toPath) shouldBe before
      } finally ours.delete()
    }
  }

  it should "reject a dangling candidate created from independently valid inputs before driver writes" in {
    val name = "11-thread-insert"
    val originals = Seq("base", "base", "base").map { side =>
      val tmp = File.createTempFile("mr11-candidate-", ".idml")
      Files.copy(file(name, side).toPath, tmp.toPath, StandardCopyOption.REPLACE_EXISTING)
      tmp
    }
    try {
      val Seq(b, l, r) = originals: @unchecked
      // Left removes an unused Story; right adds a new frame referring to it.
      val lp = IdmlPackage.open(l).get
      val storySrc = view(lp).find { case (_, e) => e.childElements.exists(x => x.tag == "Story" && x.attributes.contains("Self" -> "story2")) }.get._1
      // Remove all story2 frames on both base and left, making Story2 initially unused.
      def removeFrames(f: File): Unit = {
        val p = IdmlPackage.open(f).get
        p.parts.filter(_.partType == "Spread").foreach { part =>
          def prune(e: GenericElement): GenericElement = e.copy(children = e.children.filterNot {
            case n: GenericElement => n.tag == "TextFrame" && n.attributes.contains("ParentStory" -> "story2")
            case _ => false
          }.map { case n: GenericElement => prune(n); case n => n })
          Zip.writeEntry(f, part.src, GenericElement.toXmlString(prune(p.loadPart(part.src).get))).get
        }
      }
      originals.foreach(removeFrames)
      val dm = Zip.readEntry(l, "designmap.xml").get
      val patched = dm.linesIterator.filterNot(_.contains(storySrc)).mkString("\n")
      Zip.writeEntry(l, "designmap.xml", patched).get
      Zip.deleteEntry(l, storySrc).get
      val rp = IdmlPackage.open(r).get
      val spread = rp.parts.find(_.partType == "Spread").get.src
      def insert(e: GenericElement): GenericElement = if (e.tag == "Spread") e.copy(children = e.children :+
        GenericElement("TextFrame", Seq("Self" -> "newFrame", "ParentStory" -> "story2", "NextTextFrame" -> "n", "PreviousTextFrame" -> "n"), Nil))
        else e.copy(children = e.children.map { case n: GenericElement => insert(n); case n => n })
      Zip.writeEntry(r, spread, GenericElement.toXmlString(insert(rp.loadPart(spread).get))).get
      originals.foreach(f => IdmlReferenceValidator.validate(view(IdmlPackage.open(f).get)) shouldBe empty)
      val before = Files.readAllBytes(l.toPath)
      val conflicts = IdmlMergeDriver.merge(b, l, r).get
      conflicts.flatMap(_.conflicts).exists(_.slot.contains("target=story2: missing Story")) shouldBe true
      Files.readAllBytes(l.toPath) shouldBe before
    } finally originals.foreach(_.delete())
  }
}
