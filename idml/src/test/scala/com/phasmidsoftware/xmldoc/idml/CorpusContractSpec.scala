package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters._

/** Versioned test data reader. It validates fixtures/oracles; it never invokes a merger.
  * MR09 can consume Document.oracle to map business assertions to implementation reports.
  */
private[idml] object CorpusContract {
  val profile = "strict-text-node-v1"
  val names: Seq[String] = Seq("01-single-add", "02-single-delete", "03-single-move",
    "04-both-add-diff-pos", "05-move-move-divergent", "06-delete-vs-modify",
    "06b-delete-thread-vs-content", "07-same-story-diff-para", "08-same-para-overlap",
    "08b-same-para-disjoint", "09-zorder", "10-group-ungroup", "10b-divergent-parent", "11-thread-insert",
    "12-next-divergent", "13-parentstory-vs-content")
  private val conflictNames = names.filter(n => Seq("05-", "06-", "06b-", "08-", "08b-", "10b-", "12-", "13-").exists(n.startsWith)).toSet
  private val packageNames = Set("base.idml", "left.idml", "right.idml")
  private val fields = Set("schemaVersion", "policyProfile", "provenance", "targets", "identity", "oracle",
    "migration", "case", "requirement", "expectedStatus", "acceptedEdits", "conflicts", "invariants", "artifacts", "note")

  case class Document(name: String, status: String, oracle: ujson.Value,
                      expectedXmlParts: Map[String, GenericElement])

  def digest(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 0xff}%02x").mkString

  def snapshot(root: Path): Map[String, String] = {
    val paths = Files.walk(root)
    try paths.iterator.asScala.filter(Files.isRegularFile(_)).map(p =>
      root.relativize(p).toString -> digest(Files.readAllBytes(p))).toMap
    finally paths.close()
  }

  private def keys(v: ujson.Value, expected: Set[String], context: String): Unit =
    require(v.obj.keySet.toSet == expected, s"$context: unexpected or missing keys")

  private def nonEmpty(v: ujson.Value): Unit = require(v.str.nonEmpty, "empty string")

  private def safeRelative(value: String): Path = {
    val p = Path.of(value)
    require(value.nonEmpty && !p.isAbsolute && !p.iterator.asScala.exists(x => Set("..", ".")(x.toString)) && !value.contains('\\'),
      s"unsafe relative path: $value")
    p
  }

  private def checkDigest(v: ujson.Value, bytes: Array[Byte]): Unit = {
    require(v("bytes").num == bytes.length.toDouble, "artifact byte count mismatch")
    require(v("sha256").str == digest(bytes), "artifact digest mismatch")
  }

  private def entries(path: Path): Map[String, Array[Byte]] = {
    val zip = new ZipFile(path.toFile)
    try {
      val all = zip.entries().asScala.toSeq
      require(all.map(_.getName).distinct.size == all.size, "duplicate ZIP entry")
      all.map { e =>
        val in = zip.getInputStream(e)
        try e.getName -> in.readAllBytes() finally in.close()
      }.toMap
    } finally zip.close()
  }

  def read(caseDir: Path, json: String): Document = {
    val d = ujson.read(json)
    require(d("schemaVersion") == ujson.Num(3), "unsupported schemaVersion; explicit migration from 1/2 to 3 required")
    require(d("policyProfile").str == profile, s"unknown or missing policyProfile; expected $profile")
    keys(d, fields, "document")
    val name = d("case").str
    require(names.contains(name) && caseDir.getFileName.toString == name, "unknown or mismatched case")
    nonEmpty(d("requirement"))
    val status = if (conflictNames(name)) "conflict" else "accept"
    require(d("expectedStatus").str == status, "case status disagrees with frozen policy")
    keys(d("provenance"), Set("layers", "reference", "construction", "oracle"), "provenance")
    val layers = d("provenance")("layers").arr.map(_.str)
    require(layers.nonEmpty && layers.forall(Set("paper", "project", "idml", "engineering")), "invalid provenance layers")
    nonEmpty(d("provenance")("reference"))
    require(d("provenance")("construction").str == "xml-derived", "unsupported construction source")
    require(d("provenance")("oracle").str == "reviewed-target-recipe-no-merger", "unsupported oracle provenance")
    require(d("targets").arr.nonEmpty, "missing structured targets")
    d("targets").arr.foreach { t =>
      keys(t, Set("part", "self"), "target")
      safeRelative(t("part").str)
      nonEmpty(t("self"))
    }
    keys(d("migration"), Set("fromSchemaVersion", "date", "reason"), "migration")
    require(d("migration")("fromSchemaVersion") == (if (name == "10b-divergent-parent") ujson.Null else ujson.Num(2)),
      "invalid migration source; 10b is new and existing cases migrate from schema 2")
    nonEmpty(d("migration")("date")); nonEmpty(d("migration")("reason"))
    keys(d("identity"), Set("strategy", "baseSelfByPart", "selflessMatches", "note"), "identity")
    require(d("identity")("strategy").str == "stable-Self-plus-explicit-paragraph-ordinal", "identity strategy")
    nonEmpty(d("identity")("note"))
    require(d("identity")("baseSelfByPart").obj.nonEmpty, "missing identities")
    require(d("identity")("selflessMatches").arr.size == 4, "missing paragraph matches")
    d("identity")("selflessMatches").arr.zipWithIndex.foreach { case (m, i) =>
      keys(m, Set("part", "selector", "baseOrdinal", "leftOrdinal", "rightOrdinal"), "selfless match")
      safeRelative(m("part").str); nonEmpty(m("selector"))
      Seq("baseOrdinal", "leftOrdinal", "rightOrdinal").foreach(k => require(m(k) == ujson.Num(i + 1), "paragraph identity mismatch"))
    }
    keys(d("invariants"), Set("commonBase", "unchangedSelfValuesStable", "reciprocalThreadReferencesRequired"), "invariants")
    require(d("invariants").obj.values.forall(_ == ujson.Bool(true)), "required invariant disabled")
    require(d("note") == ujson.Null || d("note").isInstanceOf[ujson.Str], "invalid note")
    d("acceptedEdits").arr.foreach { e =>
      keys(e, Set("side", "type", "target", "description"), "edit")
      require(Set("left", "right")(e("side").str), "unknown branch")
      Seq("type", "target", "description").foreach(k => nonEmpty(e(k)))
    }
    d("conflicts").arr.foreach { c =>
      keys(c, Set("type", "target", "left", "right", "resolution"), "legacy conflict")
      c.obj.values.foreach(nonEmpty)
    }
    require((d("conflicts").arr.nonEmpty) == (status == "conflict"), "conflict/status mismatch")
    require(status != "conflict" || d("acceptedEdits").arr.isEmpty, "conflict case cannot declare applied edits")
    keys(d("artifacts"), packageNames, "input artifacts")
    packageNames.foreach { n =>
      keys(d("artifacts")(n), Set("sha256", "bytes"), n)
      checkDigest(d("artifacts")(n), Files.readAllBytes(caseDir.resolve(n)))
    }
    val base = entries(caseDir.resolve("base.idml"))
    d("identity")("baseSelfByPart").obj.foreach { case (part, ids) =>
      val xml = scala.xml.XML.loadString(new String(base(part), java.nio.charset.StandardCharsets.UTF_8))
      val actual = (xml \\ "_").flatMap(n => n.attribute("Self").map(_.text)).sorted
      require(ids.arr.map(_.str).sorted.toSeq == actual, s"$part: base identity mismatch")
    }
    val oracle = d("oracle")
    keys(oracle, Set("kind", "assertions", "entries"), "oracle")
    val expectedXml = scala.collection.mutable.Map.empty[String, GenericElement]
    var required = packageNames ++ Set("expected.json", "README.md")
    if (status == "accept") {
      require(oracle("kind").str == "package" && oracle("assertions").arr.isEmpty, "success oracle mismatch")
      require(oracle("entries").obj.keySet.toSet == base.keySet, "incomplete package inventory")
      oracle("entries").obj.foreach { case (entry, record) =>
        safeRelative(entry)
        val source = record("source").str
        val bytes = if (source == "snapshot") {
          keys(record, Set("source", "sha256", "bytes", "path"), "snapshot")
          val relative = "expected/" + entry
          require(record("path").str == relative && entry.endsWith(".xml"), "invalid snapshot path")
          required += relative
          val data = Files.readAllBytes(caseDir.resolve(safeRelative(relative)))
          expectedXml(entry) = GenericElement.fromElem(scala.xml.XML.loadString(new String(data, java.nio.charset.StandardCharsets.UTF_8)))
          data
        } else {
          require(source == "base", "unknown entry source")
          keys(record, Set("source", "sha256", "bytes"), "base entry")
          base(entry)
        }
        checkDigest(record, bytes)
      }
      require(expectedXml.nonEmpty, "success case has no changed XML snapshot")
    } else {
      require(oracle("kind").str == "conflicts" && oracle("entries").obj.isEmpty, "conflict oracle mismatch")
      require(oracle("assertions").arr.nonEmpty, "missing exact conflict objects")
      oracle("assertions").arr.foreach { c =>
        keys(c, Set("level", "part", "target", "field", "base", "left", "right", "type", "relatedParts"), "conflict assertion")
        val domain = name.startsWith("06b-") || name.startsWith("13-")
        require(c("level").str == (if (domain) "domain" else "generic"), "wrong assertion level")
        Seq("target", "field", "type").foreach(k => nonEmpty(c(k)))
        require(base.contains(c("part").str), "unknown conflict part")
        c("relatedParts").arr.foreach(p => require(base.contains(p.str), "unknown related part"))
        require(c("left") != c("right"), "non-divergent conflict")
      }
    }
    if (name == "10b-divergent-parent") {
      require(oracle("assertions").arr.exists { c =>
        c("target") == ujson.Str("r3") && c("field") == ujson.Str("parent") &&
          c("base") == ujson.Str("g1") && c("left") == ujson.Str("ud1") && c("right") == ujson.Str("g2")
      }, "10b requires the genuine r3 parent conflict: g1/ud1/g2")
    }
    val paths = Files.walk(caseDir)
    try {
      val all = paths.iterator.asScala.toSeq
      require(!all.exists(Files.isSymbolicLink(_)), "symlink not permitted")
      val actual = all.filter(Files.isRegularFile(_)).map(p => caseDir.relativize(p).toString).toSet
      require(actual == required, s"case inventory mismatch: ${actual.diff(required)} / ${required.diff(actual)}")
    } finally paths.close()
    Document(name, status, oracle, expectedXml.toMap)
  }

  def load(root: Path): Seq[Document] = {
    val dirs = Files.list(root)
    try require(dirs.iterator.asScala.filter(Files.isDirectory(_)).map(_.getFileName.toString).toSet == names.toSet,
      "case directory inventory mismatch")
    finally dirs.close()
    require(Files.isRegularFile(root.resolve("README.md")), "missing corpus README")
    names.map { n =>
      val dir = root.resolve(n)
      read(dir, Files.readString(dir.resolve("expected.json")))
    }
  }
}

class CorpusContractSpec extends AnyFlatSpec with Matchers {
  private val root = Path.of(getClass.getResource("three-way-corpus/README.md").toURI).getParent
  private val name = "08b-same-para-disjoint"
  private val dir = root.resolve(name)
  private def document = ujson.read(Files.readString(dir.resolve("expected.json")))

  "Corpus contract" should "load all 16 cases and snapshots without modifying fixture bytes" in {
    val before = CorpusContract.snapshot(root)
    val documents = CorpusContract.load(root)
    documents.map(_.name).toSet.shouldBe(CorpusContract.names.toSet)
    documents.size.shouldBe(16)
    documents.count(_.status == "accept").shouldBe(8)
    documents.count(_.status == "conflict").shouldBe(8)
    documents.filter(_.status == "accept").foreach(d => assert(d.expectedXmlParts.nonEmpty))
    CorpusContract.snapshot(root).shouldBe(before)
  }

  it should "give 08b exactly one strict text-node conflict with precise branch text" in {
    val d = CorpusContract.read(dir, document.toString)
    d.status.shouldBe("conflict")
    val c = d.oracle("assertions")(0)
    c("base").str.shouldBe("The quick brown fox jumps over the lazy dog.")
    c("left").str.shouldBe("The swift brown fox jumps over the lazy dog.")
    c("right").str.shouldBe("The quick brown fox jumps over the sleepy dog.")
  }

  it should "reject historical or unknown schemas and missing or unknown profiles" in {
    for (version <- Seq(ujson.Num(1), ujson.Num(2), ujson.Num(99), ujson.Str("3"))) {
      val d = document; d("schemaVersion") = version
      intercept[IllegalArgumentException](CorpusContract.read(dir, d.toString))
    }
    for (profile <- Seq("token-aware", "")) {
      val d = document; d("policyProfile") = profile
      intercept[IllegalArgumentException](CorpusContract.read(dir, d.toString))
    }
    val missing = document; missing.obj.remove("policyProfile")
    intercept[Exception](CorpusContract.read(dir, missing.toString))
  }

  it should "reject malformed JSON, hash errors, missing conflict values and false success" in {
    intercept[Exception](CorpusContract.read(dir, "{"))
    val hash = document; hash("artifacts")("base.idml")("sha256") = "0" * 64
    intercept[IllegalArgumentException](CorpusContract.read(dir, hash.toString))
    val missing = document; missing("oracle")("assertions")(0).obj.remove("base")
    intercept[IllegalArgumentException](CorpusContract.read(dir, missing.toString))
    val status = document; status("expectedStatus") = "accept"
    intercept[IllegalArgumentException](CorpusContract.read(dir, status.toString))
    val shape = document; shape("invariants")("commonBase") = false
    intercept[IllegalArgumentException](CorpusContract.read(dir, shape.toString))
  }
  private val spreadPart = "Spreads/Spread_ud1.xml"
  private def identified(tree: GenericElement): Map[String, (Option[String], GenericElement)] = {
    def walk(node: GenericElement, parent: Option[String]): Seq[(String, (Option[String], GenericElement))] = {
      val self = node.attributes.toMap.get("Self")
      self.toSeq.map(id => id -> (parent, node)) ++ node.childElements.flatMap(child => walk(child, self.orElse(parent)))
    }
    walk(tree, None).toMap
  }

  it should "declare case 10 successful with the complete independent structural and attribute target" in {
    val caseDir = root.resolve("10-group-ungroup")
    val json = Files.readString(caseDir.resolve("expected.json"))
    val d = CorpusContract.read(caseDir, json)
    val expected = d.expectedXmlParts(spreadPart)
    val nodes = identified(expected)
    d.status.shouldBe("accept")
    assert(!nodes.contains("g1"))
    nodes("r3")._1.shouldBe(Some("ud1"))
    nodes("r4")._1.shouldBe(Some("ud1"))
    nodes("r3")._2.attributes.toMap.apply("ItemTransform").shouldBe("1 0 0 1 380 140")
    val order = nodes("ud1")._2.childElements.flatMap(_.attributes.toMap.get("Self"))
    order.shouldBe(Seq("ud6", "r1", "r2", "r3", "r4", "tf1", "tf2", "tfA", "tfB"))
    val doc = ujson.read(json)
    doc("acceptedEdits").arr.map(_("side").str).toSet.shouldBe(Set("left", "right"))
    assert(doc("conflicts").arr.isEmpty && d.oracle("assertions").arr.isEmpty)
    val old = ujson.read(json); old("expectedStatus") = "conflict"
    intercept[IllegalArgumentException](CorpusContract.read(caseDir, old.toString))
  }

  it should "require the actual two new parent edits in 10b without limiting other relation conflicts" in {
    val caseDir = root.resolve("10b-divergent-parent")
    val json = Files.readString(caseDir.resolve("expected.json"))
    val d = CorpusContract.read(caseDir, json)
    d.status.shouldBe("conflict")
    val actualParents = Seq("base", "left", "right").map { branch =>
      val tree = IdmlPackage.open(caseDir.resolve(s"$branch.idml").toFile).get.loadPart(spreadPart).get
      val nodes = identified(tree)
      assert(nodes.contains("g2") && nodes.contains("r6"))
      nodes("r3")._1
    }
    actualParents.shouldBe(Seq(Some("g1"), Some("ud1"), Some("g2")))
    val assertion = d.oracle("assertions").arr.find(c => c("target").str == "r3" && c("field").str == "parent").get
    Seq("base", "left", "right").map(assertion(_).str).shouldBe(Seq("g1", "ud1", "g2"))
    for (field <- Seq("base", "left", "right")) {
      val wrong = ujson.read(json); wrong("oracle")("assertions")(0)(field) = "wrong-parent"
      intercept[IllegalArgumentException](CorpusContract.read(caseDir, wrong.toString))
    }
    val missing = ujson.read(json); missing("oracle")("assertions") = ujson.Arr()
    intercept[IllegalArgumentException](CorpusContract.read(caseDir, missing.toString))
  }

}
