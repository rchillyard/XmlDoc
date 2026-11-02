package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.core.XmlException

import scala.util.{Failure, Try}

/**
 * Whether a `PageItem`'s dimension (or the margin on either side of it) stays fixed relative to
 * its parent, or is free to vary, under InDesign's object-based layout rules ("Liquid Layout").
 *
 * Corresponds exactly to InDesign's own `DimensionsConstraints` enum (ExtendScript), which has
 * only these two values - confirmed against the InDesign scripting API reference, since this
 * isn't otherwise documented anywhere in the IDML files themselves.
 */
enum DimensionConstraint {
  case FixedDimension, FlexibleDimension
}

/**
 * The value of a `HorizontalLayoutConstraints` or `VerticalLayoutConstraints` attribute on any
 * IDML `PageItem` (`TextFrame`, `Rectangle`, `Oval`, ... - confirmed against real content: both
 * element types in `HelloWorld2.idml` carry this attribute with an identical shape, matching
 * InDesign's own `PageItem.horizontalLayoutConstraints`/`verticalLayoutConstraints` properties,
 * which the ExtendScript API documents as applying to `PageItem` generally, not to any one
 * subtype).
 *
 * The three values are, in order: the constraint on the margin/space before the item (left, for
 * horizontal; top, for vertical), on the item's own dimension (width or height), and on the
 * margin/space after it (right or bottom).
 *
 * @param before    the constraint on the space before the item (left margin / top margin).
 * @param dimension the constraint on the item's own dimension (width / height).
 * @param after     the constraint on the space after the item (right margin / bottom margin).
 */
case class LayoutConstraints(before: DimensionConstraint, dimension: DimensionConstraint, after: DimensionConstraint)

object LayoutConstraints {

  /**
   * Parses a `HorizontalLayoutConstraints`/`VerticalLayoutConstraints` attribute value, e.g.
   * "FlexibleDimension FixedDimension FlexibleDimension".
   *
   * @param w the attribute value text.
   * @return a `Try[LayoutConstraints]`, a `Failure` if `w` isn't exactly three
   *         space-separated `DimensionConstraint` names.
   */
  def fromString(w: String): Try[LayoutConstraints] = w.trim.split("\\s+") match {
    case Array(before, dimension, after) =>
      Try(LayoutConstraints(DimensionConstraint.valueOf(before), DimensionConstraint.valueOf(dimension), DimensionConstraint.valueOf(after)))
    case _ => Failure(XmlException(s"""LayoutConstraints.fromString: expected exactly three space-separated values, got: "$w""""))
  }

  /**
   * The inverse of `fromString`: renders a `LayoutConstraints` back into its attribute-value form.
   *
   * @param lc the `LayoutConstraints` to render.
   * @return the attribute value text, e.g. "FlexibleDimension FixedDimension FlexibleDimension".
   */
  def asString(lc: LayoutConstraints): String = s"${lc.before} ${lc.dimension} ${lc.after}"
}
