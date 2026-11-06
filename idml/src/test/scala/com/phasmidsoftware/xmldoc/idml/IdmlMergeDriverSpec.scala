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
    // Text is an independent PCS node, so the conflict reports the exact character-data payload
    // rather than rendering its parent <Content> element around it.
    slots.map(_.leftValue) should contain(Some("Hello World2A!"))
    slots.map(_.rightValue) should contain(Some("Hello World2B!"))

    Files.readAllBytes(ours.toPath) shouldBe oursBytesBefore // untouched - byte for byte
  }

  behavior of "IdmlMergeDriver.merge, a whole part removed"

  it should "delete the part and patch designmap.xml's idPkg reference, leaving everything else untouched" in {
    val base = resourceFile("HelloWorld2.idml")
    val ours = tempCopyOf("HelloWorld2.idml") // ours: unchanged from base
    val theirs = tempCopyOf("HelloWorld2.idml")
    removePart(theirs, "Stories/Story_u128.xml", "Story") // theirs: deletes a whole Story

    // A deleted Story must not leave its frame's ParentStory dangling.
    val spreadSrc = "Spreads/Spread_ud1.xml"
    val spread = IdmlPackage.open(theirs).get.loadPart(spreadSrc).get
    def removeFrame(e: GenericElement): GenericElement = e.copy(children = e.children.filterNot {
      case n: GenericElement => n.attributes.contains("Self" -> "u13a")
      case _ => false
    }.map { case n: GenericElement => removeFrame(n); case n => n })
    Zip.writeEntry(theirs, spreadSrc, GenericElement.toXmlString(removeFrame(spread))).get

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

class IdmlMergeDriverAtomicSpec extends AnyFlatSpec with should.Matchers {
  import scala.jdk.CollectionConverters.*
  import PackageTestSupport.*
  import scala.util.{Failure, Try}
  import java.nio.file.{AtomicMoveNotSupportedException, Path}
  import java.io.IOException
  private val unchanged = Map("s" -> "base", "t" -> "other")
  private val changed = Map("s" -> "edited", "t" -> "other", "u" -> "added")

  Seq("part", "manifest", "staged-validation", "replace", "atomic-unsupported").foreach { stage =>
    it should s"keep every input unchanged and clean temporary files on $stage failure" in withDirectory { dir =>
      val b = write(dir.resolve("base.idml"), simple(unchanged))
      val l = write(dir.resolve("ours.idml"), simple(unchanged))
      val r = write(dir.resolve("theirs.idml"), simple(changed))
      val before = Seq(b, l, r).map(f => Files.readAllBytes(f.toPath))
      var replacements = 0
      val writes = new IdmlMergeDriver.Writes {
        override def part(f: File, p: PartOutcome): Try[Unit] =
          super.part(f, p).flatMap { _ => if (stage == "part") Failure(new IOException("injected part failure")) else scala.util.Success(()) }
        override def manifest(f: File, a: Seq[PartContent], d: Seq[PartRemoved]): Try[Unit] =
          super.manifest(f, a, d).flatMap { _ =>
            if (stage == "manifest") Failure(new IOException("injected manifest failure"))
            else if (stage == "staged-validation") Zip.writeEntry(f, "Stories/u.xml", "<broken")
            else scala.util.Success(())
          }
        override def replace(staged: Path, target: Path): Try[Unit] = {
          replacements += 1
          staged.getParent shouldBe target.getParent
          if (stage == "atomic-unsupported") Failure(new AtomicMoveNotSupportedException(staged.toString, target.toString, "injected"))
          else Failure(new IOException("injected replace failure"))
        }
      }
      val result = IdmlMergeDriver.mergeWithWrites(b, l, r, EditDetector.defaultIgnoredAttributes, writes)
      result.isFailure shouldBe true
      if (Set("replace", "atomic-unsupported")(stage)) replacements shouldBe 1 else replacements shouldBe 0
      Seq(b, l, r).zip(before).foreach { case (f, bytes) => Files.readAllBytes(f.toPath) shouldBe bytes }
      val files = Files.list(dir)
      try files.count() shouldBe 3L finally files.close()
    }
  }

  Seq("unreadable", "bad-zip", "bad-xml", "bad-designmap-patch").foreach { stage =>
    it should s"retain ours and clean staging on real $stage errors" in withDirectory { dir =>
      val b = write(dir.resolve("base.idml"), simple(unchanged))
      val l = write(dir.resolve("ours.idml"), simple(unchanged))
      val r = write(dir.resolve("theirs.idml"), simple(changed))
      stage match {
        case "unreadable" => Files.delete(r.toPath)
        case "bad-zip" => Files.write(r.toPath, Array[Byte](1, 2, 3))
        case "bad-xml" => Zip.writeEntry(r, "Stories/s.xml", "<broken").get
        // Valid XML, unsupported closing syntax: fail safely after writing staged parts.
        case "bad-designmap-patch" =>
          val dm = Zip.readEntry(l, "designmap.xml").get.replace("</Document>", "</Document >")
          Zip.writeEntry(l, "designmap.xml", dm).get
      }
      val before = Files.readAllBytes(l.toPath)
      IdmlMergeDriver.merge(b, l, r).isFailure shouldBe true
      Files.readAllBytes(l.toPath) shouldBe before
      val files = Files.list(dir)
      try files.iterator().asScala.exists(_.getFileName.toString.startsWith(".idml-merge-")) shouldBe false finally files.close()
    }
  }

  it should "preserve untouched XML bytes including PIs, attribute order and ignored metadata" in withDirectory { dir =>
    val b = write(dir.resolve("base.idml"), simple(unchanged))
    val original = simple(unchanged).updated("Stories/t.xml",
      "<?keep untouched='yes'?><Story StyleUniqueId='ours' Self='t'><Content>other</Content></Story>".getBytes(java.nio.charset.StandardCharsets.UTF_8))
    val l = write(dir.resolve("ours.idml"), original)
    val r = write(dir.resolve("theirs.idml"), simple(unchanged.updated("s", "edited")))
    IdmlMergeDriver.merge(b, l, r).get shouldBe empty
    entries(l)("Stories/t.xml") shouldBe original("Stories/t.xml")
    entries(l)("designmap.xml") shouldBe original("designmap.xml")
  }

  it should "merge independent targets concurrently without sharing staging or write hooks" in withDirectory { dir =>
    import scala.concurrent.{Await, ExecutionContext, Future}
    import scala.concurrent.duration.*
    given ExecutionContext = ExecutionContext.global
    val b = write(dir.resolve("base.idml"), simple(unchanged))
    val r = write(dir.resolve("theirs.idml"), simple(changed))
    val targets = (1 to 4).map(i => write(dir.resolve(s"ours-$i.idml"), simple(unchanged)))
    Await.result(Future.sequence(targets.map(f => Future(IdmlMergeDriver.merge(b, f, r).get))), 30.seconds).foreach(_ shouldBe empty)
    targets.foreach(f => IdmlMergeAssertions.assertXmlPartsEqual(xmlParts(f), xmlParts(r)))
    val files = Files.list(dir)
    try files.count() shouldBe 6L finally files.close()
  }

  it should "return CLI exit codes 0, 1 and 2 in real subprocesses" in withDirectory { dir =>
    import scala.sys.process.*
    val b = write(dir.resolve("base.idml"), simple(unchanged))
    val l = write(dir.resolve("ours.idml"), simple(unchanged))
    val r = write(dir.resolve("theirs.idml"), simple(changed))
    val conflict = write(dir.resolve("conflict.idml"), simple(unchanged.updated("s", "different")))
    // sbt's layered classloader does not expose a complete java.class.path.
    def urls(loader: ClassLoader): Seq[String] = if (loader == null) Nil else {
      val current = loader match {
        case u: java.net.URLClassLoader => u.getURLs.toSeq.filter(_.getProtocol == "file").map(u => new File(u.toURI).getPath)
        case _ => Nil
      }
      current ++ urls(loader.getParent)
    }
    val cp = (urls(getClass.getClassLoader) ++ System.getProperty("java.class.path").split(File.pathSeparator)).distinct.mkString(File.pathSeparator)
    val javaCommand = new File(System.getProperty("java.home"), "bin/java").getPath
    def exit(args: Seq[String]): Int = Process(Seq(javaCommand, "-cp", cp, "com.phasmidsoftware.xmldoc.idml.IdmlMergeDriver") ++ args).!(ProcessLogger(_ => (), _ => ()))
    exit(Seq(b.getPath, l.getPath, r.getPath)) shouldBe 0
    val before = Files.readAllBytes(l.toPath)
    exit(Seq(b.getPath, l.getPath, conflict.getPath)) shouldBe 1
    Files.readAllBytes(l.toPath) shouldBe before
    exit(Nil) shouldBe 2
    val badXml = write(dir.resolve("bad-xml.idml"), simple(unchanged).updated("Stories/s.xml", "<bad".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
    exit(Seq(b.getPath, l.getPath, badXml.getPath)) shouldBe 2
    // Reach an actual patch failure after staged part writes, through the CLI.
    val patchFailure = write(dir.resolve("patch-failure.idml"), simple(unchanged).updated("designmap.xml",
      new String(simple(unchanged)("designmap.xml"), java.nio.charset.StandardCharsets.UTF_8).replace("</Document>", "</Document >").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
    val patchBefore = Files.readAllBytes(patchFailure.toPath)
    exit(Seq(b.getPath, patchFailure.getPath, r.getPath)) shouldBe 2
    Files.readAllBytes(patchFailure.toPath) shouldBe patchBefore
    exit(Seq(b.getPath, l.getPath, dir.resolve("missing.idml").toString)) shouldBe 2
    Files.readAllBytes(l.toPath) shouldBe before
  }
}
