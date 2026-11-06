package com.phasmidsoftware.xmldoc.idml

import java.nio.file.{Files, Path, StandardCopyOption}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

class ThreeWayCorpusSpec extends AnyFlatSpec with Matchers {
  private val root = Path.of(getClass.getResource("three-way-corpus/README.md").toURI).getParent
  private val documents = CorpusContract.load(root)
  require(documents.size == 16 && documents.count(_.status == "accept") == 8 && documents.count(_.status == "conflict") == 8)

  for (doc <- documents; swapped <- Seq(false, true)) {
    val domain = doc.oracle("assertions").arr.exists(_("level").str == "domain")
    val scope = if (domain) "generic + integrated MR11/MR12 domain acceptance" else "generic acceptance"
    it should s"${doc.name} ${if (swapped) "swapped" else "normal"} [$scope]" in {
      val dir = root.resolve(doc.name)
      val temp = Files.createTempDirectory(s"corpus-${doc.name}-")
      val fixtureBefore = CorpusContract.snapshot(dir)
      try {
        for (name <- Seq("base", "left", "right"))
          Files.copy(dir.resolve(s"$name.idml"), temp.resolve(s"$name.idml"), StandardCopyOption.REPLACE_EXISTING)
        val before = CorpusContract.snapshot(temp)
        val base = IdmlPackage.open(temp.resolve("base.idml").toFile).get
        val left = IdmlPackage.open(temp.resolve(if (swapped) "right.idml" else "left.idml").toFile).get
        val right = IdmlPackage.open(temp.resolve(if (swapped) "left.idml" else "right.idml").toFile).get
        val result = IdmlPackageMerger.merge(base, left, right).get
        IdmlMergeAssertions.assertPartConflictsEqual(result.conflicts, CorpusExpectations.conflicts(doc, base), swapActualSides = swapped)
        doc.status match {
          case "accept" =>
            val parts = result.partOutcomes.collect { case p: PartContent => p.src -> p.content }.toMap
            result.partOutcomes.collect { case p: PartRemoved => p.src } shouldBe empty
            IdmlMergeAssertions.assertXmlPartsEqual(parts, CorpusExpectations.expectedParts(dir, doc))
            CorpusExpectations.assertPackage(result, temp.resolve(if (swapped) "right.idml" else "left.idml"), dir, doc)
          case _ if domain =>
            info(s"${doc.name}: domain oracle validated through the integrated MR11 package entry point")
            result.partOutcomes should not be empty: Unit
          case _ => ()
        }
        CorpusContract.snapshot(temp) shouldBe before
        CorpusContract.snapshot(dir) shouldBe fixtureBefore
      } finally {
        val paths = Files.walk(temp)
        try paths.iterator.asScala.toSeq.sortBy(_.getNameCount).reverse.foreach(Files.delete)
        finally paths.close()
      }
    }
  }

  it should "reject a missing case directory rather than discover an empty successful suite" in {
    val temp = Files.createTempDirectory("missing-corpus-case-")
    try {
      Files.copy(root.resolve("README.md"), temp.resolve("README.md"))
      intercept[IllegalArgumentException](CorpusContract.load(temp))
    } finally {
      Files.deleteIfExists(temp.resolve("README.md"))
      Files.delete(temp)
    }
  }

  it should "reject a damaged expected document and an extra reported conflict" in {
    intercept[Exception](CorpusContract.read(root.resolve(documents.head.name), "{"))
    val base = IdmlPackage.open(root.resolve("10b-divergent-parent/base.idml").toFile).get
    val doc = documents.find(_.name == "10b-divergent-parent").get
    val expected = CorpusExpectations.conflicts(doc, base)
    val extra = expected.head.copy(conflicts = expected.head.conflicts :+
      com.phasmidsoftware.xmldoc.merge.StructuralConflict("unexpected slot", None, Some("left"), Some("right")))
    intercept[org.scalatest.exceptions.TestFailedException] {
      IdmlMergeAssertions.assertPartConflictsEqual(Seq(extra), expected)
    }
  }
}
