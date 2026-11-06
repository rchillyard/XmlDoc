package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.GenericElement
import scala.util.Random
import scala.util.control.NonFatal

/** Bounded legal recipes. Expected trees and the edit ledger are constructed without a merger.
  * Node counts include the root and three groups; operations are not PCS-edit counts.
  */
object MergeTreeGenerator {
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
