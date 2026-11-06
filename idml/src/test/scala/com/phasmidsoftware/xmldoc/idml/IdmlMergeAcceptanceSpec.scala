package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.StructuralConflict
import java.nio.file.{Files, Path}
import java.io.IOException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Failure

/** Few full-pipeline checks; the 32-order matrix stays in the corpus suites. */
class IdmlMergeAcceptanceSpec extends AnyFlatSpec with Matchers {
  import PackageTestSupport.*
  private val corpus = Path.of(getClass.getResource("three-way-corpus/README.md").toURI).getParent
  private val realRoot = Path.of(getClass.getResource("real-world-corpus/manifest.json").toURI).getParent
  private val manifest = ujson.read(Files.readString(realRoot.resolve("manifest.json")))
  private def prepare(name: String, directory: Path): (Path, Path, Path) = {
    val dir = corpus.resolve(name)
    Seq("base", "left", "right").foreach(side => Files.copy(dir.resolve(s"$side.idml"), directory.resolve(s"$side.idml")))
    (directory.resolve("base.idml"), directory.resolve("left.idml"), directory.resolve("right.idml"))
  }

  it should "preserve the independently declared complete case-10 package through atomic write and reopen" in withDirectory { dir =>
    val (base, ours, theirs) = prepare("10-group-ungroup", dir)
    val before = CorpusContract.snapshot(dir)
    val contract = CorpusContract.read(corpus.resolve("10-group-ungroup"), Files.readString(corpus.resolve("10-group-ungroup/expected.json")))
    IdmlMergeDriver.merge(base.toFile, ours.toFile, theirs.toFile).get shouldBe empty
    val actual = entries(ours.toFile)
    actual.keySet shouldBe contract.oracle("entries").obj.keySet.toSet
    val baseEntries = entries(base.toFile)
    contract.oracle("entries").obj.foreach { case (name, record) =>
      val expected = if (record("source").str == "snapshot") Files.readAllBytes(corpus.resolve("10-group-ungroup").resolve(record("path").str)) else baseEntries(name)
      if (name.endsWith(".xml")) IdmlMergeAssertions.assertXmlEquals(xml(actual(name)), xml(expected))
      else actual(name) shouldBe expected
    }
    IdmlReferenceValidator.validate(xmlParts(ours.toFile)) shouldBe empty
    val after = CorpusContract.snapshot(dir)
    after("base.idml") shouldBe before("base.idml")
    after("right.idml") shouldBe before("right.idml")
    after.keySet shouldBe before.keySet
  }

  for (name <- Seq("10b-divergent-parent", "06b-delete-thread-vs-content")) {
    it should s"preserve all inputs and exact generic/domain conflicts for $name" in withDirectory { dir =>
      val (base, ours, theirs) = prepare(name, dir)
      val before = CorpusContract.snapshot(dir)
      val doc = CorpusContract.read(corpus.resolve(name), Files.readString(corpus.resolve(name).resolve("expected.json")))
      val actual = IdmlMergeDriver.merge(base.toFile, ours.toFile, theirs.toFile).get
      IdmlMergeAssertions.assertPartConflictsEqual(actual, CorpusExpectations.conflicts(doc, IdmlPackage.open(base.toFile).get))
      CorpusContract.snapshot(dir) shouldBe before
    }
  }

  it should "preserve the original package and remove staging on an injected final replacement failure" in withDirectory { dir =>
    val (base, ours, theirs) = prepare("10-group-ungroup", dir)
    val before = CorpusContract.snapshot(dir)
    val writes = new IdmlMergeDriver.Writes {
      override def replace(staged: Path, target: Path) = Failure(new IOException("MR14 injected replacement failure"))
    }
    val result = IdmlMergeDriver.mergeWithWrites(base.toFile, ours.toFile, theirs.toFile, EditDetector.defaultIgnoredAttributes, writes)
    result.failed.get.getMessage should include ("MR14 injected")
    CorpusContract.snapshot(dir) shouldBe before
  }

  it should "reject corrupt input without modifying ours or leaving staging" in withDirectory { dir =>
    val (base, ours, theirs) = prepare("10-group-ungroup", dir)
    Files.writeString(base, "not a ZIP")
    val before = CorpusContract.snapshot(dir)
    IdmlMergeDriver.merge(base.toFile, ours.toFile, theirs.toFile).isFailure shouldBe true
    CorpusContract.snapshot(dir) shouldBe before
  }

  it should "validate a fixed real-world inventory while reporting absent application evidence explicitly" in {
    manifest("schemaVersion").num shouldBe 1
    manifest("minimumCases").num shouldBe 4
    manifest("profile").str shouldBe CorpusContract.profile
    manifest("cases").arr.map(_("name").str).toSet shouldBe Set("real-01-text-move", "real-02-ungroup-style", "real-03-thread-delete-edit", "real-04-thread-assets")
    if (manifest("cases").arr.exists(_("status").str != "ready")) {
      manifest("releaseReady").bool shouldBe false
      info("REAL APPLICATION ACCEPTANCE BLOCKED: user has not supplied authorized triples or InDesign records; no real group counted as executed")
    }
  }

  // Once actual records arrive, their independent oracles are exercised rather than merely loaded.
  for (record <- manifest("cases").arr if record("status").str == "ready"; swapped <- Seq(false, true)) {
    it should s"verify actual exported ${record("name").str}, swapped=$swapped" in withDirectory { dir =>
      val source = realRoot.resolve(record("name").str)
      val base = source.resolve("base.idml")
      val left = source.resolve(if (swapped) "right.idml" else "left.idml")
      val right = source.resolve(if (swapped) "left.idml" else "right.idml")
      record("artifacts").obj.foreach { case (path, hash) =>
        val artifact = source.resolve(path).normalize()
        require(!Path.of(path).isAbsolute && artifact.startsWith(source) && artifact.toRealPath().startsWith(source.toRealPath()), "unsafe real-world artifact")
        CorpusContract.digest(Files.readAllBytes(artifact)) shouldBe hash.str
      }
      val ours = Files.copy(left, dir.resolve("merged.idml"))
      val before = Files.readAllBytes(ours)
      val reports = IdmlMergeDriver.merge(base.toFile, ours.toFile, right.toFile).get
      if (record("expectedStatus").str == "accept") {
        reports shouldBe empty
        val actual = entries(ours.toFile); val expected = entries(source.resolve("expected.idml").toFile)
        actual.keySet shouldBe expected.keySet
        actual.foreach { case (name, bytes) =>
          if (name.endsWith(".xml")) IdmlMergeAssertions.assertXmlEquals(xml(bytes), xml(expected(name)), EditDetector.defaultIgnoredAttributes)
          else bytes shouldBe expected(name)
        }
        IdmlReferenceValidator.validate(xmlParts(ours.toFile)) shouldBe empty
      } else {
        def opt(v: ujson.Value) = if (v == ujson.Null) None else Some(v.str)
        val expected = ujson.read(Files.readString(source.resolve("expected-conflicts.json"))).arr.map { p =>
          PartConflict(p("src").str, p("conflicts").arr.toSeq.map(c => StructuralConflict(c("slot").str,
            opt(c("baseValue")), opt(c("leftValue")), opt(c("rightValue")))))
        }.toSeq
        IdmlMergeAssertions.assertPartConflictsEqual(reports, expected, swapActualSides = swapped)
        Files.readAllBytes(ours) shouldBe before
      }
    }
  }
}
