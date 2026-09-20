package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.GenericElement

/**
 * Detects structural/content edits between a base tree and one modified version, using
 * `Pcs.relations` rather than `TreeMatcher`/`EditDetector`'s Self-based matching plus per-node
 * attribute diff. Lindholm's own formula for this ("A Three-way Merge for XML Documents",
 * section 6): the edit set `E = T* - T*0` - whatever relation appears in the modified tree's
 * relation set but not the base's is, by definition, an edit; nothing else needs deciding, since
 * `Pcs.relations` has already reduced both trees to the same uniform vocabulary of relations.
 *
 * This is genuinely simpler than `EditDetector`, precisely because it doesn't try to *classify*
 * what happened (insert/delete/update/move) - it just reports the raw relations `PcsMerger` needs
 * to reconcile across left and right. In particular, unlike `EditDetector`, a plain reorder of
 * already-matched siblings shows up here (as new `Pcs` relations), which `PcsSpec` already
 * demonstrates `EditDetector` misses entirely.
 */
object PcsEditDetector {

  /**
   * Detects the relation-level edits that turn `base` into `modified`.
   *
   * @param base              the base tree.
   * @param modified          one modified version of it.
   * @param ignoredAttributes attribute names to leave out of content comparisons entirely (e.g. an
   *                          auto-stamped timestamp a caller knows changes on every export
   *                          regardless of real edits) - empty by default, so nothing is ignored
   *                          unless a caller opts in. A node whose *only* difference from `base` is
   *                          one of these attributes is reported as unchanged, not as an edit -
   *                          same as if that attribute didn't exist at all - though the attribute
   *                          itself is still carried along unchanged wherever a real edit to that
   *                          node *is* reported, so it's never lost from the resulting `Content`.
   * @param modifiedLabel     how to label `modified`'s nodes - forwarded to `Pcs.relations`; `base`
   *                          always labels itself the ordinary way (`Pcs.label`), since it's the
   *                          reference frame everything else is expressed relative to.
   * @return every `Pcs`/`Content` relation present in `modified` but not in `base`.
   */
  def detectEdits(base: GenericElement, modified: GenericElement, ignoredAttributes: Set[String] = Set.empty, modifiedLabel: NodeRef => String = Pcs.label): RelationSet = {
    val t0 = Pcs.relations(base)
    val tPrime = Pcs.relations(modified, modifiedLabel)
    val baseContentByLabel = t0.content.map(c => c.label -> c).toMap
    val contentEdits = tPrime.content.filterNot(c => baseContentByLabel.get(c.label).exists(sameContent(_, c, ignoredAttributes)))
    RelationSet(tPrime.pcs -- t0.pcs, contentEdits)
  }

  private def sameContent(a: Content, b: Content, ignoredAttributes: Set[String]): Boolean =
    a.label == b.label && a.tag == b.tag && a.text == b.text &&
      a.attributes.filterNot(kv => ignoredAttributes(kv._1)).toMap == b.attributes.filterNot(kv => ignoredAttributes(kv._1)).toMap
}
