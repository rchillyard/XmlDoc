package com.phasmidsoftware.xmldoc.idml

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
 * what happened (insert/delete/update/move) - it just reports the raw relations an eventual
 * structural `Merger` (not yet built - see `MERGE.md`) would need to reconcile across left and
 * right. In particular, unlike `EditDetector`, a plain reorder of already-matched siblings shows
 * up here (as new `Pcs` relations), which `PcsSpec` already demonstrates `EditDetector` misses
 * entirely.
 */
object PcsEditDetector {

  /**
   * Detects the relation-level edits that turn `base` into `modified`.
   *
   * @param base     the base tree.
   * @param modified one modified version of it.
   * @return every `Pcs`/`Content` relation present in `modified` but not in `base`.
   */
  def detectEdits(base: GenericElement, modified: GenericElement): RelationSet = {
    val t0 = Pcs.relations(base)
    val tPrime = Pcs.relations(modified)
    RelationSet(tPrime.pcs -- t0.pcs, tPrime.content -- t0.content)
  }
}
