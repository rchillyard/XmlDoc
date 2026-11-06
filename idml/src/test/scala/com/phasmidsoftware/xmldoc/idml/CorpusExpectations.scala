package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.{NodePath, NodeRef, Pcs, StructuralConflict}
import com.phasmidsoftware.xmldoc.xml.GenericElement
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters._

/** Adapts reviewed schema-3 business assertions to the existing generic report vocabulary.
  * Never derives expected results from a merge execution.
  */
private[idml] object CorpusExpectations {
  def entries(path: Path): Map[String, Array[Byte]] = {
    val zip = new ZipFile(path.toFile)
    try zip.entries().asScala.map { e =>
      val in = zip.getInputStream(e)
      try e.getName -> in.readAllBytes() finally in.close()
    }.toMap finally zip.close()
  }
  def xml(bytes: Array[Byte]): GenericElement =
    GenericElement.fromElem(scala.xml.XML.loadString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)))

  def expectedParts(dir: Path, doc: CorpusContract.Document): Map[String, GenericElement] = {
    val base = entries(dir.resolve("base.idml"))
    val pkg = IdmlPackage.open(dir.resolve("base.idml").toFile).get
    pkg.parts.map { p =>
      p.src -> doc.expectedXmlParts.getOrElse(p.src, xml(base(p.src)))
    }.toMap
  }

  /** Candidate logical package: overlay part outcomes onto ours. No driver writes here;
    * ZIP serialization, designmap patching and failure atomicity are MR12 responsibilities.
    */
  def assertPackage(result: PackageMergeResult, ours: Path, dir: Path, doc: CorpusContract.Document): Unit = {
    val original = entries(ours)
    val removed = result.partOutcomes.collect { case PartRemoved(src) => src }.toSet
    val changed = result.partOutcomes.collect { case p: PartContent => p.src -> p.content }.toMap
    val actualNames = (original.keySet -- removed) ++ changed.keySet
    val records = doc.oracle("entries").obj
    require(actualNames == records.keySet.toSet, "candidate package inventory differs from oracle")
    val baseBytes = entries(dir.resolve("base.idml"))
    records.foreach { case (entry, record) =>
      val expected = if (record("source").str == "snapshot") Files.readAllBytes(dir.resolve(record("path").str)) else baseBytes(entry)
      if (changed.contains(entry)) IdmlMergeAssertions.assertXmlEquals(changed(entry), xml(expected))
      else {
        require(java.util.Arrays.equals(original(entry), expected), s"untouched entry differs: $entry")
      }
    }
  }

  def conflicts(doc: CorpusContract.Document, base: IdmlPackage): Seq[PartConflict] = {
    def optional(v: ujson.Value): Option[String] = if (v == ujson.Null) None else Some(v.str)
    val reports = doc.oracle("assertions").arr.filter(_("level").str == "generic").flatMap { a =>
      val part = a("part").str
      val target = a("target").str
      val b = a("base"); val l = a("left"); val r = a("right")
      val cs = a("field").str match {
        case "parent" => Seq(StructuralConflict(s"parent of $target", optional(b), optional(l), optional(r)))
        case "NextTextFrame" => Seq(StructuralConflict(s"NextTextFrame of $target", optional(b), optional(l), optional(r)))
        case "existence/FillColor" =>
          require(b("exists").bool && !l("exists").bool && r("exists").bool)
          Seq(StructuralConflict(s"(deleted vs updated) of $target", None, Some("(deleted)"),
            Some(s"FillColor=${r("FillColor").str}")))
        case "text" =>
          def refs(e: GenericElement, path: NodePath): Seq[NodeRef] =
            NodeRef(path, e) +: e.childElements.zipWithIndex.flatMap { case (c, i) => refs(c, path.child(i)) }
          val candidates = refs(base.loadPart(part).get, NodePath.root).filter(ref =>
            ref.element.tag == "Content" && Pcs.leafText(ref.element).contains(b.str))
          require(candidates.size == 1, s"ambiguous oracle text target $target")
          Seq(StructuralConflict(s"content of ${Pcs.label(candidates.head)}::text[0]", optional(b), optional(l), optional(r)))
        case "page-item-sibling-context" =>
          // The frozen context omits Page and indentation. The actual chain has
          // stable before-element whitespace identities and a final trailing node.
          // The first page item follows Page ud6, not the parent's ListStart.
          val parent = "ud1"
          def value(v: ujson.Value, index: Int): Option[String] =
            Some(if (index == 0) {
              if (v(index) == ujson.Null) "ud6" else v(index).str
            } else {
              if (v(index) == ujson.Null) "ud1::trailing" else s"ud1::before[${v(index).str}]"
            })
          Seq(StructuralConflict(s"predecessor before ud1::before[$target] under $parent", value(b, 0), value(l, 0), value(r, 0)),
            StructuralConflict(s"successor after $target under $parent", value(b, 1), value(l, 1), value(r, 1)))
        case field => throw new IllegalArgumentException(s"unsupported generic oracle: $field")
      }
      cs.map(part -> _)
    }
    reports.groupMap(_._1)(_._2).toSeq.sortBy(_._1).map { case (src, cs) => PartConflict(src, cs.toSeq) }
  }
}
