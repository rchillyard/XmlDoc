package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement

/**
 * The outcome of combining `left`'s and `right`'s edits (each relative to a common `base`) for one
 * node or one attribute.
 */
sealed trait MergeOutcome

/**
 * A node inserted by (at least) one side - safe to apply.
 */
case class MergedInsert(node: NodeRef) extends MergeOutcome

/**
 * A node to be removed from the merged result - deleted by one or both sides. If one side deleted
 * a node the other side updated, deletion wins (Lindholm's default behavior for what he calls the
 * "Delete/Edit" case - it's an *optional* conflict category, checked separately from the core
 * merge, and not implemented here).
 */
case class MergedDelete(node: NodeRef) extends MergeOutcome

/**
 * One attribute change that's safe to apply - made by only one side, or made by both sides to the
 * same new value.
 */
case class MergedAttributeChange(self: String, key: String, baseValue: Option[String], newValue: Option[String]) extends MergeOutcome

/**
 * A genuine conflict: both sides changed the same thing differently, and it isn't this code's job
 * to guess which one wins.
 */
case class Conflict(self: String, key: String, baseValue: Option[String], leftValue: Option[String], rightValue: Option[String]) extends MergeOutcome

/**
 * Combines two independent sets of edits (`left` and `right`, both relative to the same `base`)
 * into one merge result, per `MERGE.md`'s design direction. Built directly on `EditDetector`:
 * nodes edited by only one side are trivially safe to apply; nodes edited by both sides are
 * reconciled attribute-by-attribute, so (per the running `HelloWorld2A`/`HelloWorld2B` example)
 * one side moving an object and the other restyling it combine cleanly, while two sides changing
 * the *same* attribute differently is reported as a `Conflict`, not silently resolved either way.
 *
 * Deliberately narrow, matching `EditDetector`'s own scope: only attribute-level content is
 * reconciled, not child order/position (Lindholm's node-context/guard machinery, not yet built).
 */
object Merger {

  /**
   * Merges `left` and `right`, both relative to `base`.
   *
   * @param base  the common ancestor.
   * @param left  one independently modified version.
   * @param right the other independently modified version.
   * @return every insertion, deletion, safe attribute change, and conflict found.
   */
  def merge(base: GenericElement, left: GenericElement, right: GenericElement): Seq[MergeOutcome] = {
    val leftBySelf = bySelf(EditDetector.detectEdits(base, left))
    val rightBySelf = bySelf(EditDetector.detectEdits(base, right))
    (leftBySelf.keySet ++ rightBySelf.keySet).toSeq.sorted.flatMap { self =>
      (leftBySelf.get(self), rightBySelf.get(self)) match {
        case (Some(e), None) => toOutcomes(self, e)
        case (None, Some(e)) => toOutcomes(self, e)
        case (Some(_: Deleted), Some(r: Deleted)) => Seq(MergedDelete(r.node))
        case (Some(u: Updated), Some(_: Deleted)) => Seq(MergedDelete(u.base))
        case (Some(_: Deleted), Some(u: Updated)) => Seq(MergedDelete(u.base))
        case (Some(l: Updated), Some(r: Updated)) => reconcileUpdates(self, l, r)
        case (Some(l: Inserted), Some(r: Inserted)) => reconcileInserts(self, l, r)
        // Insert+Delete or Insert+Update for the same Self can't happen: Inserted implies absent
        // from base, while Deleted/Updated both imply present in (and matched against) base.
        case _ => Nil
      }
    }
  }

  private def bySelf(edits: Seq[Edit]): Map[String, Edit] = edits.flatMap(e => e.self.map(_ -> e)).toMap

  private def toOutcomes(self: String, edit: Edit): Seq[MergeOutcome] = edit match {
    case Inserted(node) => Seq(MergedInsert(node))
    case Deleted(node) => Seq(MergedDelete(node))
    case Updated(_, _, changes) => changes.map { case (key, baseValue, newValue) => MergedAttributeChange(self, key, baseValue, newValue) }
  }

  private def reconcileUpdates(self: String, left: Updated, right: Updated): Seq[MergeOutcome] = {
    val leftByKey = left.changedAttributes.map(c => c._1 -> c).toMap
    val rightByKey = right.changedAttributes.map(c => c._1 -> c).toMap
    (leftByKey.keySet ++ rightByKey.keySet).toSeq.sorted.map { key =>
      (leftByKey.get(key), rightByKey.get(key)) match {
        case (Some((_, baseValue, leftValue)), Some((_, _, rightValue))) if leftValue == rightValue =>
          MergedAttributeChange(self, key, baseValue, leftValue)
        case (Some((_, baseValue, leftValue)), Some((_, _, rightValue))) =>
          Conflict(self, key, baseValue, leftValue, rightValue)
        case (Some((_, baseValue, leftValue)), None) =>
          MergedAttributeChange(self, key, baseValue, leftValue)
        case (None, Some((_, baseValue, rightValue))) =>
          MergedAttributeChange(self, key, baseValue, rightValue)
        case (None, None) =>
          throw new IllegalStateException(s"Merger.reconcileUpdates: unreachable - key $key came from the union of both sides' own keys")
      }
    }
  }

  private def reconcileInserts(self: String, left: Inserted, right: Inserted): Seq[MergeOutcome] = {
    // Both sides independently created a node with the same Self - genuinely rare (independent
    // insertions were confirmed empirically not to collide), but handled defensively rather than
    // just picking one side silently.
    val leftAttrs = left.node.element.attributes.toMap
    val rightAttrs = right.node.element.attributes.toMap
    if (leftAttrs == rightAttrs) Seq(MergedInsert(left.node))
    else Seq(Conflict(self, "(whole inserted node)", None, Some(left.node.element.toString), Some(right.node.element.toString)))
  }
}
