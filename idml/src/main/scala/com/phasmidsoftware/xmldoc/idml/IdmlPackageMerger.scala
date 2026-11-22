package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.core.FP
import com.phasmidsoftware.xmldoc.merge.StructuralConflict
import com.phasmidsoftware.xmldoc.xml.GenericElement

import scala.util.Try

/**
 * One part's outcome from a whole-package 3-way merge - only ever reported for a part that needs
 * an actual change; a part left untouched by both sides needs no entry at all (`base`'s own copy,
 * already sitting in `left`'s file unmodified, is already correct).
 */
sealed trait PartOutcome {
  def src: String
}

/** `src` should exist with this content in the merged package - either a genuine edit, or a part
  * only one side added. */
case class PartContent(src: String, partType: String, content: GenericElement) extends PartOutcome

/** `src` should no longer exist in the merged package - both sides deleted it, or one side deleted
  * it and the other never touched it. */
case class PartRemoved(src: String) extends PartOutcome

/** A whole part-level conflict - `conflicts` is empty only for the "one side deleted, the other
  * genuinely edited" and "both sides added a different part at the same `src`" cases, where the
  * disagreement is about the part's very existence, not any specific slot within it. */
case class PartConflict(src: String, conflicts: Seq[StructuralConflict])

/**
 * The result of merging every part `IdmlPackage.parts` lists (Spreads, Stories, MasterSpreads,
 * Resources, ...) across `base`/`left`/`right` - deliberately *not* `designmap.xml` itself, which
 * `IdmlMergeDriver` handles separately (see its own doc): `designmap.xml` is a much richer element
 * than "a list of part references" (it has its own `Self`, and plenty of other `Self`-bearing
 * children of its own, e.g. `Language` resource definitions) - a real 3-way merge of *all* of that
 * is out of scope here; only the specific list of `idPkg:*` references needs to track what
 * `partOutcomes` decided.
 *
 * @param partOutcomes every part that needs to change - additions, edits, and removals.
 * @param conflicts    every part-level conflict found; if non-empty, `partOutcomes` should not be
 *                      applied (mirrors `PcsMerger`/`ThreeWayMerger`'s own "conflicts present means
 *                      don't trust the resolved side" contract).
 */
case class PackageMergeResult(partOutcomes: Seq[PartOutcome], conflicts: Seq[PartConflict])

/**
 * Merges every part of an IDML package - one `ThreeWayMerger.merge` call per part still present on
 * all three sides, plus the same present/absent case analysis `Merger`/`PcsMerger` already use for
 * a single node, just one level up: at the granularity of "does this whole part exist", not "does
 * this attribute/relation exist".
 */
object IdmlPackageMerger {

  /**
   * Merges `left` and `right`, both relative to `base`.
   *
   * @param base              the common ancestor package.
   * @param left              one independently modified version.
   * @param right             the other independently modified version.
   * @param ignoredAttributes forwarded to `ThreeWayMerger.merge` for each part - see
   *                          `EditDetector.defaultIgnoredAttributes`.
   * @return every part that needs to change, plus every part-level conflict found.
   */
  def merge(
    base: IdmlPackage,
    left: IdmlPackage,
    right: IdmlPackage,
    ignoredAttributes: Set[String] = EditDetector.defaultIgnoredAttributes
  ): Try[PackageMergeResult] = {
    val allParts = (base.parts ++ left.parts ++ right.parts).groupBy(_.src).map(_._2.head).toSeq.sortBy(_.src)
    def load(pkg: IdmlPackage): Try[IdmlReferenceValidator.View] =
      FP.sequence(pkg.parts.map(p => pkg.loadPart(p.src).map(p.src -> _))).flatMap { entries =>
        Try {
          require(entries.map(_._1).distinct.size == entries.size, "designmap.xml: duplicate src")
          val view = entries.toMap
          IdmlReferenceValidator.requireIdentities(view)
          view
        }
      }
    for {
      b <- load(base)
      l <- load(left)
      r <- load(right)
      results <- Try(allParts.map(p => reconcile(p, b.get(p.src), l.get(p.src), r.get(p.src), ignoredAttributes)))
      result <- Try {
        val outcomes = results.collect { case Right(o) => o }
        val conflicts = results.collect { case Left(c) => c } ++
          IdmlReferenceValidator.intentConflicts(b, l, r, ignoredAttributes)
        if (conflicts.nonEmpty) PackageMergeResult(outcomes, conflicts)
        else {
          val candidate = outcomes.foldLeft(l) {
            case (view, PartContent(src, _, content)) => view.updated(src, content)
            case (view, PartRemoved(src)) => view - src
          }
          IdmlReferenceValidator.requireIdentities(candidate)
          PackageMergeResult(outcomes, IdmlReferenceValidator.validate(candidate))
        }
      }
    } yield result
  }

  private def reconcile(
    part: PackagePart,
    base: Option[GenericElement],
    left: Option[GenericElement],
    right: Option[GenericElement],
    ignoredAttributes: Set[String]
  ): Either[PartConflict, PartOutcome] = {
    val src = part.src

    def content(e: GenericElement): PartOutcome = PartContent(src, part.partType, e)

    (base, left, right) match {
      case (Some(b), Some(l), Some(r)) =>
        ThreeWayMerger.merge(b, l, r, ignoredAttributes) match {
          case Right(merged) => Right(content(merged))
          case Left(conflicts) => Left(PartConflict(src, conflicts))
        }
      case (Some(b), None, Some(r)) => // left deleted it
        if (r == b) Right(PartRemoved(src)) else Left(deletedVsEdited(src, deletedIsLeft = true))
      case (Some(b), Some(l), None) => // right deleted it
        if (l == b) Right(PartRemoved(src)) else Left(deletedVsEdited(src, deletedIsLeft = false))
      case (Some(_), None, None) => Right(PartRemoved(src)) // both deleted it - agree
      case (None, Some(l), Some(r)) => // both sides added a part at this src, independently
        if (l == r) Right(content(l)) else Left(insertInsertConflict(src, l, r))
      case (None, Some(l), None) => Right(content(l)) // only left added it
      case (None, None, Some(r)) => Right(content(r)) // only right added it
      case (None, None, None) =>
        throw new IllegalStateException(s"IdmlPackageMerger.reconcile: unreachable - $src came from the union of all three packages' own parts")
    }
  }

  private def deletedVsEdited(src: String, deletedIsLeft: Boolean): PartConflict = {
    val (deleted, edited) = if (deletedIsLeft) (Some("(deleted)"), Some("(modified)")) else (Some("(modified)"), Some("(deleted)"))
    PartConflict(src, Seq(StructuralConflict(s"part $src", None, deleted, edited)))
  }

  private def insertInsertConflict(src: String, left: GenericElement, right: GenericElement): PartConflict =
    PartConflict(src, Seq(StructuralConflict(s"part $src", None, Some(left.toString), Some(right.toString))))
}
