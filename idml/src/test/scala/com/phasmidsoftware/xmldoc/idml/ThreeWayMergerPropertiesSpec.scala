package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import scala.util.Random
import scala.util.control.NonFatal

/** Bounded legal recipes. Expected trees and the edit ledger are constructed without a merger.
  * Node counts include the root and three groups; operations are not PCS-edit counts.
  */
private object IdmlPropertyTrees {
  val sizes = Seq(8, 32, 96)
  val seeds = 0L until 8L
  case class Edit(kind: String, id: String, parent: Option[String] = None,
                  attribute: Option[(String, String)] = None)
  case class Sample(seed: Long, size: Int, name: String, base: GenericElement,
                    left: GenericElement, right: GenericElement, expected: GenericElement,
                    edits: Seq[Edit])
  def id(e: GenericElement): String = e.attributes.toMap.apply("Self")
  def node(id: String, kids: Seq[GenericElement] = Nil): GenericElement =
    GenericElement("Node", Seq("Self" -> id, "Value" -> "base", "Other" -> "base"), kids)
  def nodes(e: GenericElement): Seq[GenericElement] = e +: e.childElements.flatMap(nodes)
  def change(e: GenericElement, target: String)(f: GenericElement => GenericElement): GenericElement =
    if (id(e) == target) f(e) else e.copy(children = e.childElements.map(c => change(c, target)(f)))
  def attribute(e: GenericElement, target: String, key: String, value: String): GenericElement =
    change(e, target)(n => n.copy(attributes = n.attributes.filterNot(_._1 == key) :+ (key -> value)))
  def remove(e: GenericElement, target: String): GenericElement =
    e.copy(children = e.childElements.filterNot(c => id(c) == target).map(c => remove(c, target)))
  def append(e: GenericElement, parent: String, child: GenericElement): GenericElement =
    change(e, parent)(n => n.copy(children = n.children :+ child))
  def move(e: GenericElement, target: String, parent: String): GenericElement =
    append(remove(e, target), parent, nodes(e).find(n => id(n) == target).get)

  def samples(seed: Long, size: Int): Seq[Sample] = {
    require(sizes.contains(size))
    val random = new Random(seed * 1009 + size)
    val leaves = random.shuffle((0 until size - 4).map(i =>
      attribute(node(s"n$i"), s"n$i", "Value", s"v${random.nextInt(10000)}")))
    val groups = Seq("p", "q", "z").zipWithIndex.map { case (g, index) =>
      node(g, leaves.zipWithIndex.collect { case (n, i) if i % 3 == index => n })
    }
    val base = node("root", groups)
    val a = id(groups(0).childElements.last)
    val b = id(groups(1).childElements.head)
    val red = s"red-$seed"
    val blue = s"blue-$seed"
    def sample(name: String, l: GenericElement, r: GenericElement, e: GenericElement, edits: Edit*) =
      Sample(seed, size, name, base, l, r, e, edits)
    val updateA = attribute(base, a, "Value", red)
    val updateB = attribute(base, b, "Value", blue)
    val moved = move(base, a, "q")
    val insertedL = change(base, "p")(n => n.copy(children = node("insertL") +: n.children))
    val insertedR = append(base, "q", node("insertR"))
    val reordered = change(base, "p")(n => n.copy(children = n.children.reverse))
    Seq(
      sample("disjoint-content", updateA, updateB, attribute(updateA, b, "Value", blue),
        Edit("update", a, attribute = Some("Value" -> red)), Edit("update", b, attribute = Some("Value" -> blue))),
      sample("distinct-inserts", insertedL, insertedR, append(insertedL, "q", node("insertR")),
        Edit("insert", "insertL", Some("p")), Edit("insert", "insertR", Some("q"))),
      sample("delete-and-update", remove(base, a), updateB, attribute(remove(base, a), b, "Value", blue),
        Edit("delete", a), Edit("update", b, attribute = Some("Value" -> blue))),
      sample("move-and-update", moved, updateA, attribute(moved, a, "Value", red),
        Edit("move", a, Some("q")), Edit("update", a, attribute = Some("Value" -> red))),
      sample("same-new-parent", moved, moved, moved, Edit("move", a, Some("q"))),
      sample("reorder-and-update", reordered, updateB, attribute(reordered, b, "Value", blue),
        Edit("reorder", "p"), Edit("update", b, attribute = Some("Value" -> blue))),
      sample("same-update", updateA, updateA, updateA, Edit("update", a, attribute = Some("Value" -> red))))
  }

  /** Every operation must survive in the reachable output, including exact identity cardinality. */
  def verifyLedger(s: Sample, actual: GenericElement): Unit = {
    val all = nodes(actual)
    val ids = all.map(id)
    assert(ids.distinct.size == ids.size, "duplicate identity")
    s.edits.foreach { edit =>
      val found = all.filter(n => id(n) == edit.id)
      if (edit.kind == "delete") assert(found.isEmpty, s"lost deletion ${edit.id}")
      else {
        assert(found.size == 1, s"missing or duplicated edit ${edit.id}")
        edit.attribute.foreach { case (k, v) => assert(found.head.attributes.toMap.get(k).contains(v), s"lost $k of ${edit.id}") }
        edit.parent.foreach { p => assert(all.find(n => id(n) == p).exists(_.childElements.exists(n => id(n) == edit.id)), s"lost parent of ${edit.id}") }
        if (edit.kind == "reorder") {
          val expected = nodes(s.expected).find(n => id(n) == edit.id).get.childElements.map(id)
          assert(found.head.childElements.map(id) == expected, s"lost sibling order of ${edit.id}")
        }
      }
    }
  }

  def selectedSeeds: Seq[Long] = sys.props.get("merge.seed").map(v => Seq(v.toLong)).getOrElse(seeds)
  def selectedSizes: Seq[Int] = sys.props.get("merge.size").map(v => Seq(v.toInt)).getOrElse(sizes)

  /** On failure, greedily remove untouched leaves while preserving that same failed check.
    * This is a locally minimal reproducer under untouched-leaf removal, not a general XML minimizer.
    */
  def verify(s: Sample)(check: Sample => Unit): Unit = try check(s) catch {
    case NonFatal(error) =>
      var small = s
      val protectedIds = s.edits.map(_.id).toSet
      var reduced = true
      while (reduced) {
        reduced = false
        nodes(small.base).filter(n => n.children.isEmpty && !protectedIds(id(n)) && !Set("root", "p", "q", "z")(id(n))).foreach { n =>
          val key = id(n)
          val candidate = small.copy(base = remove(small.base, key), left = remove(small.left, key),
            right = remove(small.right, key), expected = remove(small.expected, key))
          try check(candidate) catch {
            case NonFatal(e) if e.getClass == error.getClass => small = candidate; reduced = true
            case NonFatal(_) => ()
          }
        }
      }
      def xml(e: GenericElement): String = GenericElement.toXmlString(e)
      throw new AssertionError(s"${s.name}; seed=${s.seed}; size=${s.size}; replay with -Dmerge.seed=${s.seed} -Dmerge.size=${s.size}\n" +
        s"locally minimized base=${xml(small.base)}\nleft=${xml(small.left)}\nright=${xml(small.right)}", error)
  }
}

class ThreeWayMergerPropertiesSpec extends org.scalatest.flatspec.AnyFlatSpec
  with org.scalatest.matchers.should.Matchers {
  import IdmlPropertyTrees._
  import com.phasmidsoftware.xmldoc.merge.StructuralConflict
  import com.phasmidsoftware.xmldoc.xml.GenericText
  private def merged(b: GenericElement, l: GenericElement, r: GenericElement): GenericElement =
    ThreeWayMerger.merge(b, l, r).fold(cs => fail(s"unexpected conflicts: $cs"), identity)
  private def sources(s: Sample, tree: GenericElement): Unit = {
    nodes(tree).foreach { n =>
      val originals = Seq(s.base, s.left, s.right).flatMap(nodes).filter(x => id(x) == id(n))
      n.attributes.foreach { case (key, value) =>
        assert(originals.exists(_.attributes.toMap.get(key).contains(value)), s"invented $key of ${id(n)}")
      }
    }
  }
  for (seed <- selectedSeeds; size <- selectedSizes; sample <- samples(seed, size)) {
    it should s"retain reachable ${sample.name} edits, identities and symmetry seed=$seed nodes=$size" in {
      nodes(sample.base).size shouldBe size
      verify(sample) { s =>
        val normal = merged(s.base, s.left, s.right)
        val swapped = merged(s.base, s.right, s.left)
        IdmlMergeAssertions.assertXmlEquals(normal, s.expected)
        IdmlMergeAssertions.assertXmlEquals(swapped, s.expected)
        verifyLedger(s, normal)
        sources(s, normal)
        for (changed <- Seq(s.left, s.right).distinct;
             (l, r, expected) <- Seq((s.base, s.base, s.base), (changed, s.base, changed),
               (s.base, changed, changed), (changed, changed, changed)))
          IdmlMergeAssertions.assertXmlEquals(merged(s.base, l, r), expected)
      }
    }
    if (sample.name == "disjoint-content") {
      it should s"combine attribute sources and exchange intentional conflicts seed=$seed nodes=$size" in {
        val s = sample
        val key = id(nodes(s.base).find(_.children.isEmpty).get)
        val left = attribute(s.base, key, "Value", "left")
        val right = attribute(s.base, key, "Other", "right")
        val expected = attribute(left, key, "Other", "right")
        verify(s.copy(name = "disjoint-attributes", left = left, right = right, expected = expected,
          edits = Seq(Edit("update", key, attribute = Some("Value" -> "left")),
            Edit("update", key, attribute = Some("Other" -> "right"))))) { c =>
          for ((l, r) <- Seq(c.left -> c.right, c.right -> c.left)) {
            val tree = merged(c.base, l, r)
            IdmlMergeAssertions.assertXmlEquals(tree, c.expected)
            sources(c, tree)
            verifyLedger(c, tree)
          }
        }
        val divergent = attribute(s.base, key, "Value", "right")
        val old = nodes(s.base).find(n => id(n) == key).get.attributes.toMap.apply("Value")
        val expectedConflicts = Seq(StructuralConflict(s"Value of $key", Some(old), Some("left"), Some("right")))
        verify(s.copy(name = "attribute-conflict", left = left, right = divergent, edits = Seq(Edit("update", key)))) { c =>
        val actual = ThreeWayMerger.merge(c.base, c.left, c.right).swap.toOption.get
        IdmlMergeAssertions.assertConflictsEqual(actual, expectedConflicts)
        IdmlMergeAssertions.assertConflictsEqual(ThreeWayMerger.merge(c.base, c.right, c.left).swap.toOption.get,
          expectedConflicts, swapActualSides = true)
        }
        val l = move(s.base, key, "q")
        val r = move(s.base, key, "z")
        val parents = Seq(StructuralConflict(s"parent of $key", Some("p"), Some("q"), Some("z")))
        verify(s.copy(name = "parent-conflict", left = l, right = r, edits = Seq(Edit("move", key)))) { c =>
        IdmlMergeAssertions.assertConflictsEqual(ThreeWayMerger.merge(c.base, c.left, c.right).swap.toOption.get, parents)
        IdmlMergeAssertions.assertConflictsEqual(ThreeWayMerger.merge(c.base, c.right, c.left).swap.toOption.get, parents, swapActualSides = true)
        }
      }
    }
  }

  it should "preserve anonymous B and edited C after deletion shifts absolute paths" in {
    def story(values: String*) = GenericElement("Story", Seq("Self" -> "s"), values.map(v =>
      GenericElement("Paragraph", Nil, Seq(GenericText(v)))))
    val base = story("A", "B", "C")
    for ((l, r) <- Seq(story("B", "C") -> story("A", "B", "C′"), story("A", "B", "C′") -> story("B", "C")))
      IdmlMergeAssertions.assertXmlEquals(merged(base, l, r), story("B", "C′"))
  }

  it should "retain reparented edits but reject edits in a truly unreachable deleted region" in {
    def group(child: GenericElement) = GenericElement("Group", Seq("Self" -> "g"), Seq(child))
    val child = node("x")
    val updated = attribute(child, "x", "Value", "new")
    val base = node("root", Seq(group(child)))
    val moved = node("root", Seq(child))
    val edited = node("root", Seq(group(updated)))
    for ((l, r) <- Seq(moved -> edited, edited -> moved))
      IdmlMergeAssertions.assertXmlEquals(merged(base, l, r), node("root", Seq(updated)))
    val text = GenericElement("Content", Seq("Self" -> "text"), Seq(GenericText("old")))
    def nested(n: GenericElement) = node("root", Seq(group(node("deep", Seq(n)))))
    val original = nested(text)
    val changed = nested(text.copy(children = Seq(GenericText("new"))))
    val deleted = node("root")
    val reports = ThreeWayMerger.merge(original, deleted, changed).swap.toOption.get
    reports.map(_.slot) shouldBe Seq("deleted subtree edit of text::text[0]")
    reports.head.baseValue.get should include ("Some(old)")
    reports.head.leftValue shouldBe None
    reports.head.rightValue.get should include ("Some(new)")
    IdmlMergeAssertions.assertConflictsEqual(ThreeWayMerger.merge(original, changed, deleted).swap.toOption.get,
      reports, swapActualSides = true)
    IdmlMergeAssertions.assertXmlEquals(merged(original, deleted, original), deleted)
  }

  it should "retain base ignored fields and source fields on new insertions" in {
    def stamped(value: String, stamp: String) = GenericElement("Item",
      Seq("Self" -> "x", "Value" -> value, "LinkImportTime" -> stamp, "StyleUniqueId" -> stamp), Nil)
    val base = node("root", Seq(stamped("old", "base")))
    val left = node("root", Seq(stamped("new", "left")))
    val right = node("root", Seq(stamped("old", "right")))
    val expected = node("root", Seq(stamped("new", "base")))
    IdmlMergeAssertions.assertXmlEquals(merged(base, left, right), expected)
    IdmlMergeAssertions.assertXmlEquals(merged(base, right, left), expected)
    val empty = node("root")
    IdmlMergeAssertions.assertXmlEquals(merged(empty, left, empty), left)
    IdmlMergeAssertions.assertXmlEquals(merged(empty, empty, left), left)
    // Same new identity, equal significant content: retain the lexically smaller
    // complete attribute set, rather than mixing fields from different sources.
    val other = node("root", Seq(stamped("new", "z")))
    IdmlMergeAssertions.assertXmlEquals(merged(empty, left, other), left)
    IdmlMergeAssertions.assertXmlEquals(merged(empty, other, left), left)
      for ((smaller, larger) <- Seq(("a&", "a\""), ("\uE000", "\uD800\uDC00"))) {
      val a = node("root", Seq(stamped("new", smaller)))
      val b = node("root", Seq(stamped("new", larger)))
      for ((l, r) <- Seq(a -> b, b -> a))
        IdmlMergeAssertions.assertXmlEquals(merged(empty, l, r), a)
    }
  }

  it should "reject biased and invented attribute values in the source and ledger oracles" in {
    val s = samples(0, 8).find(_.name == "disjoint-content").get
    intercept[AssertionError](verifyLedger(s, s.left))
    val key = s.edits.head.id
    intercept[org.scalatest.exceptions.TestFailedException](sources(s, attribute(s.expected, key, "Value", "invented")))
  }
}
