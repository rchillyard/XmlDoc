package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement

/**
 * A detected edit between a base tree and one modified version of it, per `MERGE.md`'s "edit
 * detection" phase (Lindholm's terminology: the set of edits `E` is what's actually fed to the
 * merge/reconciliation step, not the whole tree). There's no "unchanged" case - a matched node
 * whose content didn't change simply produces no `Edit` at all.
 */
sealed trait Edit {
  /**
   * The `Self` this edit is about (the inserted/deleted/updated node's own identity).
   */
  def self: Option[String]
}

/**
 * A node present in the modified tree with no match in the base - i.e. newly created.
 */
case class Inserted(node: NodeRef) extends Edit {
  def self: Option[String] = node.self
}

/**
 * A node present in the base tree with no match in the modified tree - i.e. removed.
 */
case class Deleted(node: NodeRef) extends Edit {
  def self: Option[String] = node.self
}

/**
 * A matched node (same `Self` in both trees) whose own attributes differ.
 *
 * @param base           the node as it was in the base tree.
 * @param modified       the node as it is in the modified tree.
 * @param changedAttributes one entry per attribute key that differs, as
 *                          `(key, valueInBase, valueInModified)` - `None` means the attribute was
 *                          absent on that side (i.e. it was added or removed, not just changed).
 */
case class Updated(base: NodeRef, modified: NodeRef, changedAttributes: Seq[(String, Option[String], Option[String])]) extends Edit {
  def self: Option[String] = base.self
}

/**
 * Detects edits between a base tree and one modified version, by combining `TreeMatcher`'s
 * matching with a per-node attribute diff. Structural changes (insertions/deletions, at whatever
 * depth they occur) fall out of `TreeMatcher`'s own whole-tree walk for free; this only adds
 * content-level change detection for the nodes that *do* still correspond.
 *
 * Deliberately narrow for now: only a matched node's own attributes are compared, not its
 * children's order (a "move" among already-matched siblings) - that's `MERGE.md`'s node-context/
 * guard machinery, not yet built.
 */
object EditDetector {

  /**
   * Detects the edits that turn `base` into `modified`.
   *
   * @param base     the base tree.
   * @param modified one modified version of it.
   * @return every insertion, deletion, and content update found - omitting anything unchanged.
   */
  def detectEdits(base: GenericElement, modified: GenericElement): Seq[Edit] = {
    val m = TreeMatcher.matchBySelf(base, modified)
    val inserted = m.onlyInRight.map(Inserted.apply)
    val deleted = m.onlyInLeft.map(Deleted.apply)
    val updated = m.matched.flatMap { case (baseRef, modRef) => diffAttributes(baseRef, modRef).map(Updated(baseRef, modRef, _)) }
    inserted ++ deleted ++ updated
  }

  private def diffAttributes(base: NodeRef, modified: NodeRef): Option[Seq[(String, Option[String], Option[String])]] = {
    val baseAttrs = base.element.attributes.toMap
    val modAttrs = modified.element.attributes.toMap
    val changes = (baseAttrs.keySet ++ modAttrs.keySet).toSeq.sorted.flatMap { key =>
      val (bv, mv) = (baseAttrs.get(key), modAttrs.get(key))
      if (bv != mv) Some((key, bv, mv)) else None
    }
    if (changes.isEmpty) None else Some(changes)
  }
}
