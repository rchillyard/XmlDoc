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
      } finally ours.delete(): Unit
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
      } finally ours.delete(): Unit
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

class IdmlPackageIntegritySpec extends AnyFlatSpec with Matchers {
  import PackageTestSupport.*
  private val baseParts = Map("s" -> "base", "t" -> "other")
  private val scenarios = Seq(
    ("one added part", baseParts + ("u" -> "added"), baseParts, Some(baseParts + ("u" -> "added"))),
    ("one removed part", baseParts - "t", baseParts, Some(baseParts - "t")),
    ("both removed part", baseParts - "t", baseParts - "t", Some(baseParts - "t")),
    ("same src divergent additions", baseParts + ("u" -> "left"), baseParts + ("u" -> "right"), None),
    ("delete versus modify", baseParts - "t", baseParts.updated("t", "edited"), None),
    ("independent part edits", baseParts.updated("s", "left"), baseParts.updated("t", "right"), Some(Map("s" -> "left", "t" -> "right"))))

  scenarios.foreach { case (name, leftParts, rightParts, expectedParts) =>
    Seq(false, true).foreach { swapped =>
      it should s"preserve package inventory and assets for $name (swapped=$swapped)" in withDirectory { dir =>
        val b = write(dir.resolve("base.idml"), simple(baseParts))
        val l = write(dir.resolve("ours.idml"), simple(if (swapped) rightParts else leftParts))
        val r = write(dir.resolve("theirs.idml"), simple(if (swapped) leftParts else rightParts))
        val before = Seq(b, l, r).map(f => Files.readAllBytes(f.toPath))
        val result = IdmlPackageMerger.merge(IdmlPackage.open(b).get, IdmlPackage.open(l).get, IdmlPackage.open(r).get).get
        val conflicts = IdmlMergeDriver.merge(b, l, r).get
        IdmlMergeAssertions.assertPartConflictsEqual(conflicts, result.conflicts)
        expectedParts match {
          case None =>
            conflicts should not be empty
            Files.readAllBytes(l.toPath) shouldBe before(1)
            val src = if (name.startsWith("same src")) "Stories/u.xml" else "Stories/t.xml"
            conflicts.map(_.src) shouldBe Seq(src)
          case Some(parts) =>
            conflicts shouldBe empty
            val expected = simple(parts)
            val actual = entries(l)
            actual.keySet shouldBe expected.keySet
            val expectedXml = parts.map { case (id, text) => s"Stories/$id.xml" -> xml(story(id, text)) }
            IdmlMergeAssertions.assertXmlPartsEqual(xmlParts(l), expectedXml)
            IdmlReferenceValidator.validate(xmlParts(l)) shouldBe empty
            actual.filterNot(_._1.endsWith(".xml")).foreach { case (n, data) => data shouldBe expected(n) }
            val originalMap = new String(entries(write(dir.resolve("original.idml"), simple(if (swapped) rightParts else leftParts)))("designmap.xml"), java.nio.charset.StandardCharsets.UTF_8)
            val finalMap = new String(actual("designmap.xml"), java.nio.charset.StandardCharsets.UTF_8)
            if ((if (swapped) rightParts else leftParts).keySet == parts.keySet) finalMap shouldBe originalMap: Unit
            finalMap should include("<?aid preserve='yes'?>")
            finalMap should include("<Other keep='yes'/>")
            // Compare all non-reference text verbatim, allowing only inserted reference whitespace.
            def withoutReferences(s: String) = s.replaceAll("<idPkg:Story[^>]*/>", "").replace("\t", "").replace("\n", "")
            withoutReferences(finalMap) shouldBe withoutReferences(originalMap)
            val manifest = IdmlPackage.open(l).get.parts.map(_.src).toSet
            manifest shouldBe expectedXml.keySet
        }
        Files.readAllBytes(b.toPath) shouldBe before(0)
        Files.readAllBytes(r.toPath) shouldBe before(2): Unit
      }
    }
  }
}
