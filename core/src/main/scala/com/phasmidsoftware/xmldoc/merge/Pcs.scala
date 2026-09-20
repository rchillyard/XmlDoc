package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericElement, GenericText}

/**
 * One end of a `Pcs` chain: the two children-list boundary markers from Lindholm's paper ("A
 * Three-way Merge for XML Documents", DocEng '04, section 2.3) - `⊣` (nothing precedes the first
 * child) and `⊢` (nothing follows the last) - plus an ordinary node, identified by its label (see
 * `Pcs.label`). The markers can't be edited, so every real child is guaranteed both a predecessor
 * and a successor, even at either end of the list.
 */
sealed trait Sibling

case class SiblingNode(label: String) extends Sibling

case object ListStart extends Sibling // ⊣

case object ListEnd extends Sibling // ⊢

/**
 * The parent-child-successor relation `pcs(parent, predecessor, successor)`: `parent` is the
 * parent of both `predecessor` and `successor`, and `successor` immediately follows `predecessor`
 * in `parent`'s child list. A whole child list is a chain of these, linked end to end from
 * `ListStart` to `ListEnd`; `parent` is identified by its own label, same as any other node.
 */
case class Pcs(parent: String, predecessor: Sibling, successor: Sibling)

/**
 * The content relation `c(label, tag, attributes, text)`: the node identified by `label` has this
 * tag, these attributes, and (for a text-only leaf - see `Pcs.leafText`) this text. Attribute-by-
 * attribute detail is `EditDetector`'s job, not this relation's - `Content` only needs to say "this
 * node's content is/isn't identical to that one's", which comparing the whole tuple already gives
 * for free.
 *
 * `textIsCData` records whether `text` should come back as a `GenericCData` (`PcsTreeBuilder`) -
 * true only when *every* text/CDATA child was itself CDATA (`Pcs.leafText`'s own doc); a genuine
 * mix of plain text and CDATA in the same leaf (rare) just comes back as plain `GenericText`, same
 * kind of acceptable-for-now gap `GenericElement.fromNode` already documents for comments and
 * entity references.
 */
case class Content(label: String, tag: String, attributes: Seq[(String, String)], text: Option[String] = None, textIsCData: Boolean = false)

/**
 * A tree, fully decomposed into `Pcs` and `Content` relations - Lindholm's representation of a
 * tree as a set (section 2.3), which is what let his merge algorithm be defined as set operations
 * (union, difference) rather than a tree-editing procedure. This is the foundation for an eventual
 * `MERGE.md`-style merge that (unlike today's attribute-only `EditDetector`/`Merger`) can also
 * detect and reconcile structural edits: moves and reordering (Kaining's `03-single-move`,
 * `05-move-move-divergent`, `09-zorder` cases).
 */
case class RelationSet(pcs: Set[Pcs], content: Set[Content])

object Pcs {

  /**
   * The node's identity label for relation purposes: its `Self` when it has one - stable across
   * independently edited copies of the same document, which is exactly what a label needs to be
   * for relations from different trees to ever compare equal - otherwise its `NodePath`, which is
   * only stable within one parse of one tree (any edit that shifts a sibling's index changes it).
   * Real IDML page items (`Rectangle`, `TextFrame`, `Group`, `Page`, `Spread`, `Story`, ...) always
   * carry `Self`; it's specifically wrapper elements (`Properties`, the `PathGeometry` chain,
   * `ParagraphStyleRange`/`CharacterStyleRange`/`Content`, and similar) that don't - the same gap
   * `TreeMatcher`/`EditDetector` already have, since neither looks at `Self`-less nodes at all.
   * A path-labelled node will only ever match itself, within the same tree, never a node from
   * another tree - it can't wrongly match an unrelated node, but nor can it usefully participate
   * in a merge; that's a known limitation, not a defect, until story-text-level merging is built.
   */
  def label(ref: NodeRef): String = ref.self.getOrElse(ref.path.toString)

  /**
   * An element's own text, when it's a text-only leaf - `childElements.isEmpty` (no element
   * children at all) and at least one `GenericText`/`GenericCData` child (concatenated, in order,
   * if there's more than one - e.g. either side of an entity reference). `None` for anything else,
   * including a structural container that happens to *also* mix in some text alongside real element
   * children (rare in both IDML and KML; a real remaining gap, not addressed here) - capturing that
   * unconditionally would mean two copies that only differ in incidental whitespace between element
   * children looking like a content change, which is worse than not capturing it at all.
   *
   * This is exactly the gap found empirically merging real KML: `<name>`/`<description>`/
   * `<coordinates>` are precisely this shape (one element, no element children, one text child) -
   * IDML barely has any of these (it puts everything in attributes), which is why this was invisible
   * until `kml` was tried.
   */
  def leafText(e: GenericElement): Option[String] =
    if (e.childElements.nonEmpty) None
    else e.children.collect { case GenericText(t) => t; case GenericCData(t) => t } match {
      case Nil => None
      case texts => Some(texts.mkString)
    }

  /**
   * Whether `leafText(e)` (if any) should come back as `GenericCData` rather than plain
   * `GenericText` - true only when `e` is a text-only leaf (same condition as `leafText`) *and*
   * every one of its text/CDATA children was itself `GenericCData`, so a single plain-text child
   * (or a genuine mix) is never wrongly promoted to CDATA.
   */
  def leafIsCData(e: GenericElement): Boolean =
    e.childElements.isEmpty && e.children.nonEmpty && e.children.forall {
      case _: GenericCData => true
      case _ => false
    }

  /**
   * Decomposes `root`'s whole subtree into `Pcs` and `Content` relations.
   *
   * @param root     the tree (or subtree) to decompose.
   * @param labelOf  how to label each node - `label` (`Self`-or-`NodePath`) by default; pass
   *                 `ContentMatcher.matchedLabel(base, root)` instead when `root` is one side of a
   *                 3-way merge against `base` and neither has a stable identity attribute at all
   *                 (see `ContentMatcher`'s own doc for why raw `NodePath` alone isn't safe there).
   * @return every relation needed to reconstruct `root`'s structure and content.
   */
  def relations(root: GenericElement, labelOf: NodeRef => String = label): RelationSet = {
    def go(ref: NodeRef): RelationSet = {
      val here = Content(labelOf(ref), ref.element.tag, ref.element.attributes, leafText(ref.element), leafIsCData(ref.element))
      val kids = ref.element.childElements.zipWithIndex.map { case (c, i) => NodeRef(ref.path.child(i), c) }
      val siblings: Seq[Sibling] = kids.map(k => SiblingNode(labelOf(k)))
      val chain = (ListStart +: siblings) zip (siblings :+ ListEnd)
      val pcsHere = chain.map { case (p, s) => Pcs(labelOf(ref), p, s) }.toSet
      val below = kids.map(go)
      RelationSet(pcsHere ++ below.flatMap(_.pcs), Set(here) ++ below.flatMap(_.content))
    }

    go(NodeRef(NodePath.root, root))
  }
}
