package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.jdk.CollectionConverters.*

private[idml] object PackageTestSupport {
  def entries(file: File): Map[String, Array[Byte]] = {
    val z = new ZipFile(file)
    try z.entries().asScala.filterNot(_.isDirectory).map { e =>
      val in = z.getInputStream(e)
      try e.getName -> in.readAllBytes() finally in.close()
    }.toMap finally z.close()
  }
  def xml(bytes: Array[Byte]): GenericElement = GenericElement.fromElem(scala.xml.XML.loadString(new String(bytes, UTF_8)))
  def xmlParts(file: File): Map[String, GenericElement] = {
    val pkg = IdmlPackage.open(file).get
    pkg.parts.map(p => p.src -> pkg.loadPart(p.src).get).toMap
  }
  def withDirectory(run: Path => Unit): Unit = {
    val dir = Files.createTempDirectory("mr12-")
    try run(dir) finally {
      val stream = Files.walk(dir)
      try stream.iterator().asScala.toSeq.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }
  def write(path: Path, entries: Map[String, Array[Byte]]): File = {
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try entries.toSeq.sortBy(_._1).foreach { case (name, bytes) =>
      out.putNextEntry(new ZipEntry(name)); out.write(bytes); out.closeEntry()
    } finally out.close()
    path.toFile
  }
  def story(id: String, text: String): Array[Byte] =
    s"<Story Self='$id'><Content>$text</Content></Story>".getBytes(UTF_8)
  def simple(parts: Map[String, String] = Map("s" -> "base", "t" -> "other")): Map[String, Array[Byte]] = {
    val references = parts.keys.toSeq.sorted.map(id => s"<idPkg:Story src='Stories/$id.xml'/>").mkString
    Map("designmap.xml" -> s"<?aid preserve='yes'?><Document xmlns:idPkg='urn:test' Self='doc'><Other keep='yes'/>$references</Document>".getBytes(UTF_8),
      "mimetype" -> "application/vnd.adobe.indesign-idml-package".getBytes(UTF_8),
      "Links/image.bin" -> Array[Byte](0, 1, -1, 42)) ++ parts.map { case (id, text) => s"Stories/$id.xml" -> story(id, text) }
  }
}

/** MR12 executes every schema-3 package/driver expectation; generic relation detail belongs to MR09. */
class ThreeWayPackageCorpusSpec extends AnyFlatSpec with Matchers {
  import PackageTestSupport.*
  private val root = Path.of(getClass.getResource("three-way-corpus/README.md").toURI).getParent
  private val documents = CorpusContract.load(root)
  documents.foreach { document =>
    Seq(false, true).foreach { swapped =>
      it should s"honour ${document.name} at package and driver layers (swapped=$swapped)" in withDirectory { dir =>
        val caseDir = root.resolve(document.name)
        val b = caseDir.resolve("base.idml").toFile
        val l = caseDir.resolve(if (swapped) "right.idml" else "left.idml").toFile
        val r = caseDir.resolve(if (swapped) "left.idml" else "right.idml").toFile
        val inputsBefore = Seq(b, l, r).map(f => CorpusContract.digest(Files.readAllBytes(f.toPath)))
        val result = IdmlPackageMerger.merge(IdmlPackage.open(b).get, IdmlPackage.open(l).get, IdmlPackage.open(r).get).get
        val ours = Files.copy(l.toPath, dir.resolve("ours.idml")).toFile
        val before = Files.readAllBytes(ours.toPath)
        val conflicts = IdmlMergeDriver.merge(b, ours, r).get
        IdmlMergeAssertions.assertPartConflictsEqual(conflicts, result.conflicts)
        if (document.status == "conflict") {
          conflicts should not be empty
          Files.readAllBytes(ours.toPath) shouldBe before
          document.oracle("assertions").arr.filter(_("level").str == "domain").foreach { assertion =>
            val Seq(frame, story) = assertion("target").str.split("/").toSeq: @unchecked
            val expected = com.phasmidsoftware.xmldoc.merge.StructuralConflict(
              s"Self=$frame attribute=layout target=$story body=${assertion("relatedParts")(0).str}/Story[$story]/*",
              Some(s"frame=$frame Story=$story"),
              Some(if (swapped) s"Story=$story body edited" else s"frame=$frame layout changed"),
              Some(if (swapped) s"frame=$frame layout changed" else s"Story=$story body edited"))
            conflicts should contain(PartConflict(assertion("part").str, Seq(expected)))
          }
        } else {
          conflicts shouldBe empty
          val actual = entries(ours)
          val base = entries(b)
          actual.keySet shouldBe document.oracle("entries").obj.keySet.toSet
          document.oracle("entries").obj.foreach { case (name, record) =>
            val expected = if (record("source").str == "snapshot") Files.readAllBytes(caseDir.resolve(record("path").str)) else base(name)
            if (name.endsWith(".xml")) IdmlMergeAssertions.assertXmlEquals(xml(actual(name)), xml(expected), EditDetector.defaultIgnoredAttributes)
            else actual(name) shouldBe expected
          }
          val candidate = result.partOutcomes.foldLeft(xmlParts(l)) {
            case (v, PartContent(src, _, content)) => v.updated(src, content)
            case (v, PartRemoved(src)) => v - src
          }
          IdmlMergeAssertions.assertXmlPartsEqual(xmlParts(ours), candidate)
          IdmlReferenceValidator.validate(xmlParts(ours)) shouldBe empty
          entries(ours)("designmap.xml") shouldBe entries(l)("designmap.xml")
        }
        Seq(b, l, r).map(f => CorpusContract.digest(Files.readAllBytes(f.toPath))) shouldBe inputsBefore
        val files = Files.list(dir)
        try files.iterator().asScala.map(_.getFileName.toString).toSet shouldBe Set("ours.idml") finally files.close()
      }
    }
  }
}
