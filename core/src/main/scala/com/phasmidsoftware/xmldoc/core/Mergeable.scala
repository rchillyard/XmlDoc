package com.phasmidsoftware.xmldoc.core

/**
 * Hierarchical trait to satisfy merging.
 *
 * CONSIDER making it a typeclass instead.
 *
 * @tparam T the underlying type.
 */
trait Mergeable[T] {

  /**
   * Merge this mergeable object with <code>t</code>.
   *
   * @param t the object to be merged with this.
   * @return the merged value of T.
   */
  infix def merge(t: T, mergeName: Boolean = true): Option[T]
}

/**
 * Companion object for Mergeable.
 */
object Mergeable {

  /**
   * Method to merge a Sequence into a Sequence of one.
   *
   * NOTE the merge method assumes that it is associative with respect to T.
   *
   * @param ts   the sequence to be merged.
   * @param fail the value of T used for a merge failure.
   * @tparam T the underlying type (extends Mergeable).
   * @return Seq[T] with only one element.
   */
  def mergeSequence[T <: Mergeable[T]](ts: Seq[T])(fail: => T): Seq[T] =
    Seq(ts reduce ((t1, t2) => (t1 merge t2).getOrElse(fail)))

  /**
   * Method to merge two Optional values of the same type.
   *
   * @param ao the first optional value.
   * @param bo the second optional value.
   * @param f  a method to merge two elements of the underlying type T.
   * @tparam T the underlying type.
   * @return an Option[T].
   */
  def mergeOptions[T](ao: Option[T], bo: Option[T])(f: (T, T) => Option[T]): Option[T] = (ao, bo) match {
    case (Some(a), Some(b)) => f(a, b)
    case (None, _) => bo
    case (_, None) => ao
  }

  /**
   * Method to merge two Optional values of the same type.
   *
   * NOTE: In this method, there is no proper merging of two defined values: we arbitrarily select the first.
   *
   * @param ao the first optional value.
   * @param bo the second optional value.
   * @tparam T the underlying type.
   * @return an Option[T].
   */
  def mergeOptionsBiased[T](ao: Option[T], bo: Option[T]): Option[T] = mergeOptions(ao, bo)((t1, _) => Some(t1))

  /**
   * Method to merge two optional Strings.
   *
   * @param ao the first optional String.
   * @param bo the second optional String.
   * @param f  a function to combine two Strings and yield an Option[String].
   * @return an Option[String].
   */
  def mergeStrings(ao: Option[String], bo: Option[String])(f: (String, String) => Option[String]): Option[String] = mergeOptions(ao, bo)(f)

  /**
   * Method to concatenate two optional Strings with a delimiter between them.
   *
   * @param ao        the first optional String.
   * @param bo        the second optional String.
   * @param delimiter a String to be placed between the two given Strings.
   * @return an Option[String].
   */
  def mergeStringsDelimited(ao: Option[String], bo: Option[String])(delimiter: String): Option[String] = mergeOptions(ao, bo)((a, b) => Some(s"$a$delimiter$b"))

  /**
   * Method to join two ordered sequences of elements at whichever end of `second` attaches more
   * closely to the end of `first`, per the given `distance` function - without ever reversing (or
   * reordering) `first`: its own orientation is authoritative, and it always comes first in the
   * result. Only `second` may be reversed, if that yields a closer join.
   *
   * This is deliberately generic (not tied to geometry, or to any particular element type): the
   * only thing it needs to know about `A` is how far apart two instances of it are, if that's even
   * defined (e.g. Issue #54 uses `Coordinate.distance`, but any "ordered sequence of things with a
   * distance/compatibility notion between them" can reuse this).
   *
   * @param first    the first sequence: appended as-is, never reversed, never reordered.
   * @param second   the second sequence: appended as-is, or reversed - whichever end attaches more
   *                 closely to `first`'s last element. If `distance` is undefined for both
   *                 candidate joins (or either sequence is empty), `second` is appended as-is.
   * @param distance a function yielding the distance between two elements, if defined.
   * @tparam A the element type.
   * @return the concatenation of `first` and (possibly-reversed) `second`.
   */
  def joinOrdered[A](first: Seq[A], second: Seq[A])(distance: (A, A) => Option[Double]): Seq[A] =
    (first.lastOption, second.headOption, second.lastOption) match {
      case (Some(a), Some(b0), Some(b1)) =>
        (distance(a, b0), distance(a, b1)) match {
          case (Some(asIs), Some(reversed)) if reversed < asIs => first ++ second.reverse
          case _ => first ++ second
        }
      case _ => first ++ second
    }
}
