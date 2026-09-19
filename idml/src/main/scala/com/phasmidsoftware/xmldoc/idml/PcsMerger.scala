package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement

/**
 * A genuine structural conflict: two sides impose incompatible requirements on the same relation
 * slot (Lindholm's Update/Update and Position/Position conflicts, section 6.3), and it isn't this
 * code's job to guess a winner. `slot` names what's in contention (e.g. `"content of u13d"`, or
 * `"successor after u1 under us"`); `baseValue`/`leftValue`/`rightValue` are each side's value
 * there, rendered as a string (`None` when a side has nothing there at all - e.g. a freshly
 * inserted node has no base value).
 */
case class StructuralConflict(slot: String, baseValue: Option[String], leftValue: Option[String], rightValue: Option[String])

/**
 * The result of a structural (`Pcs`-level) 3-way merge. `relations` is `Δ` after resolving
 * whatever could be resolved automatically - safe to reconstruct a merged tree from by walking it
 * from `rootLabel` (see `PcsTreeBuilder`). `conflicts` is everywhere that couldn't be resolved.
 * For a conflicting slot, `relations` keeps the base's own value if it had one (Lindholm's rule:
 * an edit always wins over the base, so a real conflict - two *edits* disagreeing - is the one
 * case with no automatic winner); a conflicting slot with no base value at all (e.g. both sides
 * independently insert different content at the same anchor) is simply absent from `relations`.
 * Either way, `relations` isn't guaranteed internally consistent when `conflicts` is non-empty -
 * per Lindholm, that's expected: `Δ` only becomes `Tm` once every conflict is resolved one way or
 * another (`PcsTreeBuilder.build` fails cleanly rather than silently reconstructing a bad tree
 * when it isn't).
 *
 * @param rootLabel the label of `base`/`left`/`right`'s shared root node (they're assumed to
 *                   already correspond, same as everywhere else in this package).
 */
case class StructuralMergeResult(relations: RelationSet, rootLabel: String, conflicts: Seq[StructuralConflict])

/**
 * A structural merger over `Pcs`/`Content` relations - the reconciliation half of Lindholm's
 * `merge` procedure ("A Three-way Merge for XML Documents", section 6: raw-merge-then-resolve-
 * contradictions), applied to whatever `PcsEditDetector` finds. `EditDetector`/`Merger` still only
 * reconcile attributes on already-matched nodes; this is what actually detects and merges a plain
 * reorder, which they cannot (see `PcsSpec`/`PcsEditDetectorSpec`).
 *
 * Checks all three of Lindholm's structural consistency rules: "unique successor" and "unique
 * predecessor" (a node's position *within* its current parent's child list), and "unique parent" -
 * a node claimed as a child by two genuinely different parents (e.g. Kaining's
 * `10-group-ungroup`), which the first two can't see: they only compare values sharing the same
 * `(parent, ...)` key, but a node moved to a different parent changes which *key* it appears
 * under at all, not the value at a shared one.
 */
object PcsMerger {

  /**
   * Merges `left` and `right`, both relative to `base`.
   *
   * @param base  the common ancestor.
   * @param left  one independently modified version.
   * @param right the other independently modified version.
   * @return the resolved relation set, plus every conflict found.
   */
  def merge(base: GenericElement, left: GenericElement, right: GenericElement): StructuralMergeResult = {
    val baseRelations = Pcs.relations(base)
    val leftEdits = PcsEditDetector.detectEdits(base, left)
    val rightEdits = PcsEditDetector.detectEdits(base, right)

    val baseContent = baseRelations.content.map(c => c.label -> c).toMap
    val (contentEdits, contentConflicts) = reconcile(
      baseContent,
      leftEdits.content.map(c => c.label -> c).toMap,
      rightEdits.content.map(c => c.label -> c).toMap
    )
    val mergedContent = (baseContent ++ contentEdits).values.toSet
    val contentReports = contentConflicts.map { case (label, b, l, r) =>
      StructuralConflict(s"content of $label", b.map(render), l.map(render), r.map(render))
    }

    val basePred = byPredecessor(baseRelations.pcs)
    val (predEdits, predConflicts) = reconcile(basePred, byPredecessor(leftEdits.pcs), byPredecessor(rightEdits.pcs))
    val mergedPcs = (basePred ++ predEdits).map { case ((parent, predecessor), successor) => Pcs(parent, predecessor, successor) }.toSet
    val predReports = predConflicts.map { case ((parent, predecessor), b, l, r) =>
      StructuralConflict(s"successor after ${render(predecessor)} under $parent", b.map(render), l.map(render), r.map(render))
    }

    // Only used to catch the contradiction shape "unique successor" (above) can't see: two
    // different predecessors both claiming the same successor. Its own resolved values aren't
    // needed - a consistent set of (parent, predecessor) -> successor links is already enough to
    // reconstruct the whole child list, following successors from ListStart to ListEnd.
    val baseSucc = bySuccessor(baseRelations.pcs)
    val (_, succConflicts) = reconcile(baseSucc, bySuccessor(leftEdits.pcs), bySuccessor(rightEdits.pcs))
    val succReports = succConflicts.map { case ((parent, successor), b, l, r) =>
      StructuralConflict(s"predecessor before ${render(successor)} under $parent", b.map(render), l.map(render), r.map(render))
    }

    // Like bySuccessor above, only used for conflict detection: it can't tell us anything safe to
    // apply (a label's "resolved" parent here says nothing about *where* under that parent it
    // goes - the predecessor/successor maps above already own that), only that two sides disagree
    // about which parent a node belongs to at all.
    val baseParent = parentOf(baseRelations.pcs)
    val (_, parentConflicts) = reconcile(baseParent, parentOf(leftEdits.pcs), parentOf(rightEdits.pcs))
    val parentReports = parentConflicts.map { case (label, b, l, r) => StructuralConflict(s"parent of $label", b, l, r) }

    val rootLabel = Pcs.label(NodeRef(NodePath.root, base))
    StructuralMergeResult(RelationSet(mergedPcs, mergedContent), rootLabel, contentReports ++ predReports ++ succReports ++ parentReports)
  }

  private def byPredecessor(pcs: Set[Pcs]): Map[(String, Sibling), Sibling] = pcs.map(p => (p.parent, p.predecessor) -> p.successor).toMap

  private def bySuccessor(pcs: Set[Pcs]): Map[(String, Sibling), Sibling] = pcs.map(p => (p.parent, p.successor) -> p.predecessor).toMap

  // Every real (non-boundary) label's parent, per this relation set - assumes a well-formed tree
  // (each label has exactly one parent within one tree's own relations); if that's ever violated,
  // same as TreeMatcher's Self-uniqueness assumption, whichever relation is visited last silently
  // wins the slot.
  private def parentOf(pcs: Set[Pcs]): Map[String, String] =
    pcs.flatMap(p => Seq(p.predecessor, p.successor).collect { case SiblingNode(label) => label -> p.parent }).toMap

  /**
   * Reconciles `left`'s and `right`'s edits (both relative to `base`) for one map of relation
   * "slots" to values: a slot only one side touched is resolved to that side's value; a slot both
   * sides touched identically is resolved to that value; a slot both sides touched differently is
   * a conflict, not resolved (the base's own value, if any, is left in place for the caller to
   * fall back on). A slot neither side touched needs no entry at all - the caller already has
   * `base`'s own map for that.
   */
  private def reconcile[K, V](base: Map[K, V], left: Map[K, V], right: Map[K, V]): (Map[K, V], Seq[(K, Option[V], Option[V], Option[V])]) = {
    val touched = left.keySet ++ right.keySet
    val resolved = touched.flatMap { k =>
      (left.get(k), right.get(k)) match {
        case (Some(l), Some(r)) if l == r => Some(k -> l)
        case (Some(l), None) => Some(k -> l)
        case (None, Some(r)) => Some(k -> r)
        case _ => None
      }
    }.toMap
    val conflicts = touched.toSeq.flatMap { k =>
      (left.get(k), right.get(k)) match {
        case (Some(l), Some(r)) if l != r => Some((k, base.get(k), Some(l), Some(r)))
        case _ => None
      }
    }
    (resolved, conflicts)
  }

  private def render(s: Sibling): String = s match {
    case SiblingNode(label) => label
    case ListStart => "⊣"
    case ListEnd => "⊢"
  }

  private def render(c: Content): String = {
    val attrs = if (c.attributes.isEmpty) "" else c.attributes.map { case (k, v) => s"""$k="$v"""" }.mkString(" ", " ", "")
    s"<${c.tag}$attrs>"
  }
}
