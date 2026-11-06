package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.*
import com.phasmidsoftware.xmldoc.xml.{GenericElement, GenericText}
import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Random

/** Explicit runMain only: no ScalaTest registration and no long-running default tests. */
object MergeBenchmark {
  case class Input(base: GenericElement, left: GenericElement, right: GenericElement, expected: GenericElement,
                   operations: Map[String, Int], rejected: Int)
  def node(id: String, children: Seq[GenericElement] = Nil, text: Int = 0): GenericElement =
    GenericElement("Node", Seq("Self" -> id, "Value" -> "base"),
      if (text > 0 && children.isEmpty) Seq(GenericText("x" * text)) else children)
  def id(e: GenericElement): String = e.attributes.toMap.apply("Self")
  def generate(size: Int, budget: Int, seed: Long, text: Int): Input = {
    val count = (size - 1) / 4
    require(budget <= count)
    val groups = (0 until count).map(i => node(s"g$i", Seq("a", "b", "c").map(k => node(s"$k$i", text = text))))
    val padding = (0 until size - 1 - count * 4).map(i => node(s"padding$i", text = text))
    val base = node("root", groups ++ padding)
    var left = base; var right = base; var expected = base
    val random = new Random(seed)
    var used = Set.empty[Int]; var rejected = 0
    var distribution = Map("insert" -> 0, "delete" -> 0, "move" -> 0, "update" -> 0)
    while (used.size < budget) {
      val index = random.nextInt(count)
      val kind = Seq("insert", "delete", "move", "update")(random.nextInt(4))
      if (used(index)) rejected += 1
      else {
        used += index; distribution = distribution.updated(kind, distribution(kind) + 1)
        def edit(tree: GenericElement) = tree.copy(children = tree.childElements.map { group =>
          if (id(group) != s"g$index") group else {
            val kids = group.childElements
            val changed = kind match {
              case "insert" => kids.head +: node(s"new$index", text = text) +: kids.tail
              case "delete" => Seq(kids.head, kids.last)
              case "move" => kids.tail :+ kids.head
              case "update" => kids.head.copy(attributes = Seq("Self" -> s"a$index", "Value" -> s"edit-$seed")) +: kids.tail
            }
            group.copy(children = changed)
          }
        })
        if (random.nextBoolean()) left = edit(left) else right = edit(right)
        expected = edit(expected)
      }
    }
    Input(base, left, right, expected, distribution, rejected)
  }
  def milliseconds[A](f: => A): (A, Double) = {
    val start = System.nanoTime(); val result = f
    result -> ((System.nanoTime() - start) / 1e6)
  }
  def heap: Long = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getUsed
  def writePackage(file: Path, tree: GenericElement): Unit = {
    val out = new ZipOutputStream(Files.newOutputStream(file))
    try {
      Seq("mimetype" -> "application/vnd.adobe.indesign-idml-package",
        "designmap.xml" -> "<Document xmlns:idPkg='urn:test'><idPkg:Story src='Stories/generated.xml'/></Document>",
        "Stories/generated.xml" -> GenericElement.toXmlString(tree)).foreach { case (name, value) =>
        out.putNextEntry(new ZipEntry(name)); out.write(value.getBytes(UTF_8)); out.closeEntry()
      }
    } finally out.close()
  }
  def measure(input: Input, directory: Path): ujson.Obj = {
    val source = Seq(input.base, input.left, input.right).map(GenericElement.toXmlString)
    val memoryPools = ManagementFactory.getMemoryPoolMXBeans.asScala.filter(_.isValid)
    memoryPools.foreach(_.resetPeakUsage())
    val peak = new java.util.concurrent.atomic.AtomicLong(heap)
    val sampler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    sampler.scheduleAtFixedRate(() => peak.accumulateAndGet(heap, (a, b) => math.max(a, b)): Unit,
      0, 1, java.util.concurrent.TimeUnit.MILLISECONDS): Unit
    val start = System.nanoTime()
    try {
      val (parsed, parseMs) = milliseconds(source.map(s => GenericElement.fromElem(scala.xml.XML.loadString(s))))
      val (relations, matchMs) = milliseconds {
        // Generated inputs have a Self on every element. Validate the same matching boundary
        // as the public path and materialize relations before the pure merge interval.
        TreeMatcher.matchBySelf(parsed(0), parsed(1)); TreeMatcher.matchBySelf(parsed(0), parsed(2))
        parsed.map(Pcs.relations(_))
      }
      val (merged, mergeMs) = milliseconds {
        val result = PcsMerger.mergeRelations(relations(0), relations(1), relations(2), "root", Set.empty)
        require(result.conflicts.isEmpty, s"generated conflict: ${result.conflicts}")
        PcsTreeBuilder.build(result).get
      }
      IdmlMergeAssertions.assertXmlEquals(merged, input.expected)
      val file = directory.resolve("candidate.idml")
      val (_, writeMs) = milliseconds(writePackage(file, merged))
      // Reopen all XML and validate complete independent target outside pure merge timing.
      val pkg = IdmlPackage.open(file.toFile).get
      IdmlMergeAssertions.assertXmlEquals(pkg.loadPart("Stories/generated.xml").get, input.expected)
      val totalMs = (System.nanoTime() - start) / 1e6
      val l = PcsEditDetector.detectEdits(relations(0), relations(1), Set.empty)
      val r = PcsEditDetector.detectEdits(relations(0), relations(2), Set.empty)
      val e = (l.pcs ++ r.pcs).size + (l.content ++ r.content).size
      ujson.Obj("parseMs" -> parseMs, "matchMs" -> matchMs, "mergeMs" -> mergeMs,
        "writeMs" -> writeMs, "totalMs" -> totalMs, "E" -> e,
        "sampledHeapPeakMiB" -> (peak.get.toDouble / 1048576),
        "poolHeapPeakMiB" -> (memoryPools.filter(_.getType == java.lang.management.MemoryType.HEAP).map(_.getPeakUsage.getUsed).sum.toDouble / 1048576),
        "correct" -> true)
    } finally { sampler.shutdownNow(): Unit; sampler.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS): Unit }
  }
  def main(args: Array[String]): Unit = {
    require(args.length == 2 && Set("calibration", "formal")(args(1)), "output.json calibration|formal")
    val formal = args(1) == "formal"
    val warmups = if (formal) 3 else 1
    val repeats = if (formal) 5 else 2
    val gatesPath = Path.of("idml/docs/evidence/mr13-gates.json")
    val gates = if (formal) ujson.read(Files.readString(gatesPath)) else ujson.Null
    if (formal) require(gates("frozen").bool, "gates must be frozen before formal measurements")
    val output = Path.of(args(0)).toAbsolutePath
    Files.createDirectories(output.getParent)
    val directory = Files.createTempDirectory("idml-benchmark-")
    try {
      val matrix = for (size <- Seq(32, 128, 512); seed <- Seq(13L, 37L, 73L);
        budget <- (if (size == 32) Seq(0, 4) else Seq(0, 4, 16))) yield (size, budget, seed, 0)
      val runs = (matrix :+ (32, 4, 13L, 65536)).map { case (size, budget, seed, text) =>
        val input = generate(size, budget, seed, text)
        for (_ <- 0 until warmups) measure(input, directory)
        val rows = (0 until repeats).map(_ => measure(input, directory))
        ujson.Obj("nodes" -> size, "budget" -> budget, "seed" -> seed.toDouble, "textBytesPerLeaf" -> text,
          "operations" -> ujson.Obj.from(input.operations.map { case (k, v) => k -> ujson.Num(v) }), "rejections" -> input.rejected,
          "measurements" -> ujson.Arr.from(rows))
      }
      val doc = ujson.Obj("schemaVersion" -> 1, "gateStatus" -> (if (formal) "unvalidated" else "calibration-only"), "mode" -> args(1), "warmups" -> warmups, "repetitions" -> repeats,
        "sourceCommit" -> sys.env.getOrElse("MERGE_BENCHMARK_COMMIT", "uncommitted"),
        "sourceSha256" -> CorpusContract.digest(Files.readAllBytes(Path.of("idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/MergeBenchmark.scala"))),
        "gateSha256" -> (if (formal) CorpusContract.digest(Files.readAllBytes(gatesPath)) else "not-formal"),
        "environment" -> ujson.Obj("java" -> System.getProperty("java.runtime.version"), "vm" -> System.getProperty("java.vm.name"),
          "os" -> System.getProperty("os.name"), "arch" -> System.getProperty("os.arch"),
          "processors" -> Runtime.getRuntime.availableProcessors(), "maxHeapMiB" -> (Runtime.getRuntime.maxMemory().toDouble / 1048576),
          "jvmArguments" -> ujson.Arr.from(ManagementFactory.getRuntimeMXBean.getInputArguments.asScala)),
        "runs" -> ujson.Arr.from(runs))
      Files.writeString(output, ujson.write(doc, indent = 2))
      if (formal) {
        val calibrationPath = Path.of("idml/docs/evidence/mr13-calibration.json")
        require(CorpusContract.digest(Files.readAllBytes(calibrationPath)) == gates("calibrationSha256").str, "calibration changed after gate freeze")
        val calibration = ujson.read(Files.readString(calibrationPath))
        for (field <- Seq("java", "vm", "os", "arch", "processors", "maxHeapMiB"))
          require(doc("environment")(field) == calibration("environment")(field), s"calibration environment mismatch: $field")
        require(doc("sourceSha256") == calibration("sourceSha256"), "benchmark source changed after calibration")
        val failures = runs.flatMap { run => run("measurements").arr.flatMap { row =>
          Seq("totalMs" -> "maxTotalMs", "mergeMs" -> "maxMergeMs", "sampledHeapPeakMiB" -> "maxHeapMiB", "poolHeapPeakMiB" -> "maxHeapMiB").collect {
            case (metric, gate) if row(metric).num > gates(gate).num => s"${run("nodes")}/${run("budget")}: $metric=${row(metric)} > ${gates(gate)}"
          }
        }}
        def median(values: Seq[Double]): Double = {
          val sorted = values.sorted
          (sorted((sorted.size - 1) / 2) + sorted(sorted.size / 2)) / 2
        }
        val ratios = runs.zip(calibration("runs").arr).flatMap { case (run, previous) =>
          require(Seq("nodes", "budget", "seed", "textBytesPerLeaf").forall(k => run(k) == previous(k)), "matrix mismatch")
          val ratio = median(run("measurements").arr.toSeq.map(_("mergeMs").num)) /
            math.max(1.0, median(previous("measurements").arr.toSeq.map(_("mergeMs").num)))
          if (ratio > gates("maxMedianMergeRatio").num) Seq(s"median ratio=$ratio exceeds frozen gate") else Nil
        }
        require((failures ++ ratios).isEmpty, (failures ++ ratios).mkString("formal gate failed: ", "; ", ""))
      }
      if (formal) { doc("gateStatus") = "passed"; Files.writeString(output, ujson.write(doc, indent = 2)): Unit }
      println(s"Wrote ${runs.size} scenarios to $output; all independent targets verified")
    } finally {
      Files.deleteIfExists(directory.resolve("candidate.idml")); Files.delete(directory)
    }
  }
}
