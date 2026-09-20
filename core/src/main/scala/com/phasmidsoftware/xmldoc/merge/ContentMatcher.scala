package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.GenericElement

/**
 * Matches `base` against `modified` using pure content/structural similarity - no identifier
 * needed, unlike `TreeMatcher.matchBySelf`. Loosely modeled on the real 3dm tool's own matcher
 * (see `MERGE.md`, "the actual 3dm implementation's matcher"): exact whole-subtree content
 * equality first (a node - and everything below it, byte for byte - is almost certainly the same
 * node even if it moved or its siblings changed), then positional pairing for whatever's left among
 * an already-matched parent's remaining children.
 *
 * Deliberately simpler than 3dm's own algorithm: no fuzzy content similarity (q-gram distance) for
 * the positional fallback - it just pairs whatever's left, in order - and no real "copy resolution"
 * for duplicate content (see `matchByExactContent`). It also never looks *across* an unmatched
 * parent: a node moved to a genuinely different parent shows up as a plain delete-from-the-old-
 * parent plus insert-into-the-new-one, not a recognized move - a real, known scope limit, not a bug.
 *
 * This exists to fix a real, confirmed problem (`MERGE.md`, "no stable per-node identity" /
 * "silently lose an edit..."): for a document type with nothing like IDML's `Self`, `Pcs.label`'s
 * `NodePath` fallback ties identity to raw position, so a deletion that shifts later siblings can
 * make an unrelated, independent edit to one of them silently vanish from a 3-way merge. Matching by
 * content first, and only falling back to position for the *unmatched remainder*, fixes exactly
 * that: an edited-but-not-moved sibling keeps its base identity across the edit, instead of
 * inheriting whichever base identity now happens to sit at its shifted position.
 */
object ContentMatcher {

  /**
   * Matches every level of `base` against `modified`, recursively, starting from their (assumed
   * corresponding) roots.
   *
   * @param base     the base tree.
   * @param modified one modified version of it.
   * @return every matched pair, plus whatever's only in one side.
   */
  def matchTrees(base: GenericElement, modified: GenericElement): Matching = {
    val rootBase = NodeRef(NodePath.root, base)
    val rootModified = NodeRef(NodePath.root, modified)
    val (matched, onlyInBase, onlyInModified) = matchChildren(rootBase, rootModified)
    Matching((rootBase, rootModified) +: matched, onlyInBase, onlyInModified)
  }

  /**
   * A label function for `modified`'s nodes, for use with `Pcs.relations`/`PcsEditDetector`/
   * `PcsMerger`'s optional label parameters: whichever `base` node `matchTrees` matched a node to
   * gets that base node's own label (`Pcs.label`) - the same identity `Self` would have given it,
   * had it one; anything with no match falls back to its own path, prefixed with `+` so it can never
   * collide with a real base path (which never starts with `+`).
   */
  def matchedLabel(base: GenericElement, modified: GenericElement): NodeRef => String = {
    val byPath = matchTrees(base, modified).matched.map { case (b, m) => m.path -> Pcs.label(b) }.toMap
    ref => byPath.getOrElse(ref.path, s"+${ref.path}")
  }

  private def matchChildren(baseParent: NodeRef, modifiedParent: NodeRef): (Seq[(NodeRef, NodeRef)], Seq[NodeRef], Seq[NodeRef]) = {
    val baseKids = childrenOf(baseParent)
    val modifiedKids = childrenOf(modifiedParent)
    val (exactPairs, baseRemainder1, modifiedRemainder1) = matchByExactContent(baseKids, modifiedKids)
    val (positionalPairs, baseRemainder2, modifiedRemainder2) = matchByPosition(baseRemainder1, modifiedRemainder1)
    val here = exactPairs ++ positionalPairs
    val below = here.map { case (b, m) => matchChildren(b, m) }
    (here ++ below.flatMap(_._1), baseRemainder2 ++ below.flatMap(_._2), modifiedRemainder2 ++ below.flatMap(_._3))
  }

  private def childrenOf(ref: NodeRef): Seq[NodeRef] = ref.element.childElements.zipWithIndex.map { case (c, i) => NodeRef(ref.path.child(i), c) }

  // Whole-subtree equality (GenericElement's own structural equality, recursively) - greedy,
  // document-order pairing within each content-identical group. Two distinct base nodes with
  // identical content but only one modified counterpart will have the earlier one matched and the
  // later one fall through to the positional pass below - a real simplification of 3dm's own "copy
  // resolution" phase, not a full solution to duplicate content.
  private def matchByExactContent(baseKids: Seq[NodeRef], modifiedKids: Seq[NodeRef]): (Seq[(NodeRef, NodeRef)], Seq[NodeRef], Seq[NodeRef]) = {
    val initialByContent = modifiedKids.groupBy(_.element)
    val (pairs, _) = baseKids.foldLeft((Vector.empty[(NodeRef, NodeRef)], initialByContent)) { case ((acc, byContent), b) =>
      byContent.get(b.element).filter(_.nonEmpty) match {
        case Some(m +: rest) => (acc :+ (b -> m), byContent.updated(b.element, rest))
        case _ => (acc, byContent)
      }
    }
    val matchedBasePaths = pairs.map(_._1.path).toSet
    val matchedModifiedPaths = pairs.map(_._2.path).toSet
    (pairs, baseKids.filterNot(b => matchedBasePaths(b.path)), modifiedKids.filterNot(m => matchedModifiedPaths(m.path)))
  }

  // Whatever's left after exact matching, paired up strictly by relative order - the simplest
  // possible stand-in for 3dm's own fuzzy-content fallback (q-gram distance): correct when exactly
  // one candidate remains on each side (the concrete case this was built for), weaker when several
  // do and more than one of them also changed.
  private def matchByPosition(baseRemainder: Seq[NodeRef], modifiedRemainder: Seq[NodeRef]): (Seq[(NodeRef, NodeRef)], Seq[NodeRef], Seq[NodeRef]) = {
    val n = math.min(baseRemainder.size, modifiedRemainder.size)
    (baseRemainder.take(n).zip(modifiedRemainder.take(n)), baseRemainder.drop(n), modifiedRemainder.drop(n))
  }
}
