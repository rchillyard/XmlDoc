package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.GenericElement

/**
 * The location of a node within a `GenericElement` tree: the sequence of child-element indices
 * from the root (empty means the root itself). Kept alongside a matched/unmatched node (see
 * `NodeRef`) so a future diff/merge phase can navigate back to it without re-walking the tree.
 */
case class NodePath(indices: Vector[Int]) {
  def child(i: Int): NodePath = NodePath(indices :+ i)

  override def toString: String = if (indices.isEmpty) "/" else indices.mkString("/", "/", "")
}

object NodePath {
  val root: NodePath = NodePath(Vector.empty)
}

/**
 * A node found while walking a tree, together with its location.
 *
 * @param path    the node's location within its own tree.
 * @param element the node itself.
 */
case class NodeRef(path: NodePath, element: GenericElement) {
  /**
   * The node's own `Self` attribute value, if it has one.
   */
  def self: Option[String] = element.attributes.collectFirst { case ("Self", v) => v }
}

/**
 * The result of matching two trees: which nodes correspond (`matched`), and which appear in only
 * one side (`onlyInLeft`/`onlyInRight` - candidate deletions/insertions, from the left tree's
 * point of view).
 */
case class Matching(matched: Seq[(NodeRef, NodeRef)], onlyInLeft: Seq[NodeRef], onlyInRight: Seq[NodeRef])

/**
 * First matching prototype for `idml`'s eventual 3-way merge (see `MERGE.md`): matches two
 * `GenericElement` trees purely by their literal `Self` attribute value.
 *
 * This deliberately does not attempt any content/structural heuristic matching (the "3dm-style"
 * fallback `MERGE.md` describes for un-identified content) - it only reports what `Self` alone
 * can tell us. That's a real scope limit: nodes with no `Self` at all (most obviously, story text)
 * never appear in the result, in either the matched or unmatched sets. Duplicate Self values
 * within either input tree fail at this matcher boundary with both paths; the same identity
 * appearing once in each version is valid.
 */
object TreeMatcher {

  private def collectBySelf(e: GenericElement, path: NodePath = NodePath.root): Seq[(String, NodeRef)] = {
    val ref = NodeRef(path, e)
    val here = ref.self.map(s => s -> ref).toSeq
    val children = e.childElements.zipWithIndex.flatMap { case (child, i) => collectBySelf(child, path.child(i)) }
    here ++ children
  }

  private def index(tree: GenericElement, side: String): Map[String, NodeRef] = {
    val entries = collectBySelf(tree)
    entries.groupBy(_._1).toSeq.sortBy(_._1).foreach { case (self, refs) =>
      require(refs.size == 1,
        s"Duplicate Self '$self' in $side tree at ${refs.map(_._2.path).mkString(", ")}")
    }
    entries.toMap
  }

  /**
   * Matches two trees by their nodes' `Self` attribute values.
   *
   * @param left  the first tree (e.g. the base, in a 3-way merge).
   * @param right the second tree (e.g. one of the two modified versions).
   * @return a `Matching` of the two trees.
   */
  def matchBySelf(left: GenericElement, right: GenericElement): Matching = {
    val leftBySelf = index(left, "left")
    val rightBySelf = index(right, "right")
    val matched = (leftBySelf.keySet intersect rightBySelf.keySet).toSeq.sorted.map(s => leftBySelf(s) -> rightBySelf(s))
    val onlyInLeft = (leftBySelf.keySet diff rightBySelf.keySet).toSeq.sorted.map(leftBySelf)
    val onlyInRight = (rightBySelf.keySet diff leftBySelf.keySet).toSeq.sorted.map(rightBySelf)
    Matching(matched, onlyInLeft, onlyInRight)
  }
}
