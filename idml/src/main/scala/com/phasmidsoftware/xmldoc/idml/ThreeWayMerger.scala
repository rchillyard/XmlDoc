package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.{ContentMatcher, Sibling, SiblingNode, Content, NodePath, NodeRef, Pcs, PcsMerger, PcsTreeBuilder, RelationSet, StructuralConflict}
import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericElement, GenericText}

import scala.util.{Failure, Success}

/**
 * The real, end-to-end 3-way merge, combining `Merger`'s attribute-level reconciliation with
 * `PcsMerger`'s structural one - each catches conflicts, and resolves edits, that the other
 * cannot:
 *
 *   - `Merger` decides a matched node's own tag/attributes, attribute by attribute - so two sides
 *     changing *different* attributes of the same node (the running `HelloWorld2A`/`HelloWorld2B`
 *     example, `MergerSpec`) combine cleanly. It has no notion of position at all, so it is blind
 *     to reordering and reparenting.
 *   - `PcsMerger` decides a node's position and parent - so a plain reorder or a Kaining
 *     `10b-divergent-parent` reparenting conflict, both invisible to `Merger`, are handled correctly
 *     (`PcsMergerSpec`). Its own `Content` relation, though, only ever sees a node's whole
 *     tag+attributes as one indivisible blob - by itself, it would (wrongly) report the
 *     `HelloWorld2A`/`HelloWorld2B` case above as a conflict, since it can't tell "different
 *     attributes changed" from "the same attribute changed differently".
 *
 * This reconciles the two: structure (which nodes exist, in what order, under what parent) comes
 * entirely from `PcsMerger`/`PcsTreeBuilder`; a matched node's own tag/attributes come from
 * `Merger` wherever `Merger` has an opinion (i.e. the node has a `Self`) - `PcsMerger`'s own
 * (coarser) content resolution only fills in the rest: `Self`-less nodes (`Properties`, the
 * `PathGeometry` chain, ...), which `Merger` never sees at all.
 */
object ThreeWayMerger {

  /**
   * Merges `left` and `right`, both relative to `base`.
   *
   * @param base              the common ancestor.
   * @param left              one independently modified version.
   * @param right             the other independently modified version.
   * @param ignoredAttributes forwarded to both `Merger.merge` and `PcsMerger.merge` - see
   *                          `EditDetector.defaultIgnoredAttributes`. Passed to both, not just one,
   *                          since a node whose *only* difference is an ignored attribute needs to
   *                          look unchanged from *both* mergers' point of view - otherwise the one
   *                          that still sees a difference reports a conflict `ThreeWayMerger`'s own
   *                          dedup logic (below) can't catch, since the other merger never had any
   *                          opinion on that node at all to defer to.
   * @return `Right` the merged tree, or `Left` every conflict found (attribute-level and
   *         structural together, in one uniform shape) if there was at least one.
   */
  def merge(
    base: GenericElement,
    left: GenericElement,
    right: GenericElement,
    ignoredAttributes: Set[String] = EditDetector.defaultIgnoredAttributes
  ): Either[Seq[StructuralConflict], GenericElement] = {
    val attrOutcomes = Merger.merge(base, left, right, ignoredAttributes)
    val attrSelves = attrOutcomes.flatMap(selfOf).toSet
    val attrConflictReports = attrOutcomes.collect { case c: Conflict => renderAttrConflict(c) }

    val baseRelations = matchedRelations(base, Pcs.label)
    val leftRelations = matchedRelations(left, matchedLabels(base, left, ignoredAttributes))
    val rightRelations = matchedRelations(right, matchedLabels(base, right, ignoredAttributes))
    val structResult = PcsMerger.mergeRelations(baseRelations, leftRelations, rightRelations,
      Pcs.label(NodeRef(NodePath.root, base)), ignoredAttributes)
    // Merger already handles content-level conflicts for anything with a real Self, finer-grained
    // than PcsMerger's own whole-blob comparison (see the class doc) - keep PcsMerger's content
    // conflicts for anonymous nodes, which Merger never sees at all. Anonymous identities are
    // scoped to their matched base location or their inserted Self-bearing ancestor.
    val structConflictReports = structResult.conflicts.filterNot { c =>
      c.slot.startsWith("content of ") && attrSelves.contains(c.slot.stripPrefix("content of "))
    }

    val allConflicts = attrConflictReports ++ structConflictReports
    if (allConflicts.nonEmpty) Left(allConflicts)
    else {
      val mergedContent = (structResult.relations.content.map(c => c.label -> c).toMap ++ resolveAttrContent(base, attrOutcomes)).values.toSet
      val deletedEdits = deletedSubtreeConflicts(baseRelations, leftRelations, rightRelations, structResult.relations, structResult.rootLabel, ignoredAttributes)
      if (deletedEdits.nonEmpty) Left(deletedEdits)
      else PcsTreeBuilder.build(RelationSet(structResult.relations.pcs, mergedContent), structResult.rootLabel) match {
        case Success(tree) => Right(tree)
        case Failure(t) => Left(Seq(StructuralConflict("internal error reconstructing the merged tree", None, None, Some(t.getMessage))))
      }
    }
  }

  /** Match anonymous children within corresponding identified parents. Self anchors are
    * indexed globally, so moving one never changes the identities of its anonymous descendants.
    * Unidentified cross-parent moves remain outside ContentMatcher's supported model.
    */
  private def matchedLabels(base: GenericElement, modified: GenericElement, ignored: Set[String]): NodeRef => String = {
    def refs(e: GenericElement, path: NodePath = NodePath.root): Seq[NodeRef] =
      NodeRef(path, e) +: e.childElements.zipWithIndex.flatMap { case (child, i) => refs(child, path.child(i)) }
    val bs = refs(base)
    val ms = refs(modified)
    val baseBySelf = bs.flatMap(r => r.self.map(_ -> r)).toMap
    val labels = scala.collection.mutable.Map.empty[NodePath, String]
    def matchAnonymous(b: NodeRef, m: NodeRef): Unit = {
      val bk = b.element.childElements.zipWithIndex.filterNot(_._1.attributes.exists(_._1 == "Self"))
      val mk = m.element.childElements.zipWithIndex.filterNot(_._1.attributes.exists(_._1 == "Self"))
      // Whole subtrees provide exact anchors before the positional fallback.
      val matched = ContentMatcher.matchTrees(
        GenericElement("MatchingRoot", Nil, bk.map(_._1)), GenericElement("MatchingRoot", Nil, mk.map(_._1)), ignored)
      matched.matched.filter(_._1.path.indices.size == 1).foreach { case (br, mr) =>
        val (be, bi) = bk(br.path.indices.head)
        val (me, mi) = mk(mr.path.indices.head)
        val actualB = NodeRef(b.path.child(bi), be)
        val actualM = NodeRef(m.path.child(mi), me)
        labels(actualM.path) = Pcs.label(actualB)
        matchAnonymous(actualB, actualM)
      }
    }
    ms.foreach { m =>
      m.self.flatMap(baseBySelf.get).foreach(b => matchAnonymous(b, m))
    }
    if (base.attributes.forall(_._1 != "Self") && modified.attributes.forall(_._1 != "Self")) {
      labels(NodePath.root) = Pcs.label(NodeRef(NodePath.root, base))
      matchAnonymous(NodeRef(NodePath.root, base), NodeRef(NodePath.root, modified))
    }
    // Scope inserted anonymous descendants to their nearest identified ancestor. Independent
    // insertions at the same raw path must never share the same anonymous node identities.
    val modifiedByPath = ms.map(r => r.path -> r).toMap
    def insertedLabel(ref: NodeRef): String = {
      val ancestors = (0 to ref.path.indices.size).reverse.map(n => NodePath(ref.path.indices.take(n)))
      ancestors.flatMap(p => modifiedByPath.get(p).flatMap(r => r.self.map(id => p -> id))).headOption match {
        case Some((path, id)) => s"+$id::${ref.path.indices.drop(path.indices.size).mkString("/")}"
        case None => s"+${ref.path}"
      }
    }
    ref => ref.self.getOrElse(labels.getOrElse(ref.path, insertedLabel(ref)))
  }

  /** Character data is anchored to the following matched element (or the list end),
    * instead of a text ordinal that shifts after an independent sibling insertion.
    * Consecutive text/CDATA nodes keep their order and distinct identities. */
  private def matchedRelations(tree: GenericElement, labelOf: NodeRef => String): RelationSet = {
    val textLabels = scala.collection.mutable.Map.empty[String, String]
    def walk(ref: NodeRef): Unit = {
      val parent = labelOf(ref)
      var elementOrdinal = 0
      val children = ref.element.children.map {
        case e: GenericElement =>
          val child = NodeRef(ref.path.child(elementOrdinal), e)
          elementOrdinal += 1
          Left(child)
        case c => Right(c)
      }
      var ordinal = 0
      val perAnchor = scala.collection.mutable.Map.empty[String, Int]
      children.zipWithIndex.foreach {
        case (Left(child), _) => walk(child)
        case (Right(_: GenericText | _: GenericCData), i) =>
          val anchor = children.drop(i + 1).collectFirst { case Left(next) => s"before[${labelOf(next)}]" }.getOrElse("end")
          val occurrence = perAnchor.getOrElse(anchor, 0)
          perAnchor(anchor) = occurrence + 1
          // Text-only parents retain the established public text-slot vocabulary.
          if (children.exists(_.isLeft))
            textLabels(s"$parent::text[$ordinal]") = s"$parent::$anchor::text[$occurrence]"
          ordinal += 1
        case _ => ()
      }
    }
    walk(NodeRef(NodePath.root, tree))
    def rename(label: String): String = textLabels.getOrElse(label, label)
    def sibling(s: Sibling): Sibling = s match {
      case SiblingNode(label) => SiblingNode(rename(label))
      case marker => marker
    }
    val relations = Pcs.relations(tree, labelOf)
    RelationSet(relations.pcs.map(p => Pcs(rename(p.parent), sibling(p.predecessor), sibling(p.successor))),
      relations.content.map(c => c.copy(label = rename(c.label))))
  }

  // Optional paper §6.3 check is strict at this public entry point. Walk the candidate
  // relation graph, not the original ancestry: an edited child moved out of a deleted
  // group remains reachable and must keep its edit.
  private def deletedSubtreeConflicts(b: RelationSet, l: RelationSet, r: RelationSet,
                                     candidate: RelationSet, root: String, ignored: Set[String]): Seq[StructuralConflict] = {
    import com.phasmidsoftware.xmldoc.merge.{SiblingNode, ListStart, ListEnd}
    val successors = candidate.pcs.map(p => (p.parent, p.predecessor) -> p.successor).toMap
    val reachable = scala.collection.mutable.Set.empty[String]
    def visit(label: String): Unit = if (reachable.add(label)) {
      var next = successors.get(label -> ListStart)
      val seen = scala.collection.mutable.Set.empty[String]
      while (next.exists(_ != ListEnd)) next match {
        case Some(SiblingNode(child)) if seen.add(child) =>
          visit(child)
          next = successors.get(label -> SiblingNode(child))
        case _ => next = None
      }
    }
    visit(root)
    def state(rs: RelationSet, label: String): Option[String] = rs.content.find(_.label == label).map { content =>
      val normalized = content.copy(attributes = content.attributes.filterNot(x => ignored(x._1)).sortBy(_._1))
      val links = rs.pcs.filter(_.parent == label).toSeq.map(_.toString).sorted.mkString("; ")
      s"$normalized | $links"
    }
    b.content.toSeq.sortBy(_.label).filterNot(c => reachable(c.label)).flatMap { c =>
      val bv = state(b, c.label)
      val lv = state(l, c.label)
      val rv = state(r, c.label)
      if ((lv.isEmpty && rv.nonEmpty && rv != bv) || (rv.isEmpty && lv.nonEmpty && lv != bv))
        Some(StructuralConflict(s"deleted subtree edit of ${c.label}", bv, lv, rv))
      else None
    }
  }

  private def selfOf(o: MergeOutcome): Option[String] = o match {
    case MergedInsert(node) => node.self
    case MergedDelete(node) => node.self
    case MergedAttributeChange(self, _, _, _) => Some(self)
    case Conflict(self, _, _, _, _) => Some(self)
  }

  private def renderAttrConflict(c: Conflict): StructuralConflict = StructuralConflict(s"${c.key} of ${c.self}", c.baseValue, c.leftValue, c.rightValue)

  // Only the labels Merger actually has an opinion about (Inserted/Updated with a Self) - anything
  // else must fall through to PcsMerger's own content resolution untouched, or a genuinely new
  // insertion's structural-only content (an insert Merger and PcsMerger both independently detect,
  // agreeing on the same attributes) would get masked by a stale seed from `base`.
  private def resolveAttrContent(base: GenericElement, attrOutcomes: Seq[MergeOutcome]): Map[String, Content] = {
    val touched = attrOutcomes.flatMap(selfOf).toSet
    val seed = Pcs.relations(base).content.map(c => c.label -> c).toMap.view.filterKeys(touched).toMap
    attrOutcomes.foldLeft(seed) { (acc, outcome) =>
      outcome match {
        case MergedInsert(node) =>
          val self = node.self.get // Inserted always has a Self - EditDetector only ever diffs Self-bearing nodes
          acc.updated(self, Content.element(self, node.element.tag, node.element.attributes))
        case MergedDelete(node) =>
          acc.removed(node.self.get)
        case MergedAttributeChange(self, key, _, newValue) =>
          val current = acc.getOrElse(self, Content(self, "", Nil))
          acc.updated(self, current.copy(attributes = applyAttributeChange(current.attributes, key, newValue)))
        case _: Conflict =>
          acc // unreachable - `merge` only calls this once `attrConflictReports` is already empty
      }
    }
  }

  // Changes (or adds, or removes) one attribute in place, preserving the position of every other
  // attribute - so a merged node's attributes round-trip in close to their original order, not
  // reshuffled by whichever key happened to change.
  private def applyAttributeChange(attrs: Seq[(String, String)], key: String, newValue: Option[String]): Seq[(String, String)] = {
    val i = attrs.indexWhere(_._1 == key)
    (newValue, i) match {
      case (Some(v), idx) if idx >= 0 => attrs.updated(idx, key -> v)
      case (Some(v), _) => attrs :+ (key -> v)
      case (None, idx) if idx >= 0 => attrs.patch(idx, Nil, 1)
      case (None, _) => attrs
    }
  }
}
