package com.phasmidsoftware.core

/**
 * Represents a point or vector in a three-dimensional Cartesian coordinate system.
 *
 * @param x The x-coordinate of the Cartesian point.
 * @param y The y-coordinate of the Cartesian point.
 * @param z The z-coordinate of the Cartesian point.
 */
case class Cartesian(x: Double, y: Double, z: Double) {
  /**
   * Computes the vector difference between this Cartesian point and another Cartesian point.
   *
   * @param c The Cartesian point to subtract from this point.
   * @return A new Cartesian object representing the vector difference.
   */
  infix def vector(c: Cartesian): Cartesian = {
    val f = diff(c)(_)
    Cartesian(f(_.x), f(_.y), f(_.z))
  }

  /**
   * Calculates the Euclidean distance between the current Cartesian point and another Cartesian point.
   *
   * @param c The Cartesian point to which the distance is to be calculated.
   * @return The Euclidean distance between the current Cartesian point and the specified point.
   */
  infix def distance(c: Cartesian): Double = {
    val f = sqr(c)(_)
    math.sqrt(f(_.x) + f(_.y) + f(_.z))
  }

  /**
   * Computes the dot product of the current Cartesian point with another Cartesian point.
   *
   * @param c The Cartesian point with which the dot product is to be computed.
   * @return The dot product of the two Cartesian points as a Double value.
   */
  def dotProduct(c: Cartesian): Double = {
    val f = mult(c)(_)
    f(_.x) + f(_.y) + f(_.z)
  }

  /**
   * Computes the square of the value obtained from the difference between the specified function
   * applied to the given Cartesian point and the current Cartesian point (aka the variance).
   *
   * @param c The Cartesian point to compare against the current Cartesian point.
   * @param f A function that operates on a Cartesian point and returns a double value,
   *          representing a specific property or dimension of the Cartesian point.
   * @return The squared result of the difference between the function `f` applied to the given
   *         Cartesian point and the current Cartesian point.
   */
  private def sqr(c: Cartesian)(f: Cartesian => Double): Double = sqr(diff(c)(f)) //sqr(f(this) - f(c))

  /**
   * Multiplies the result of applying a given function to the current Cartesian point
   * and another Cartesian point.
   *
   * @param c The Cartesian point whose function result will be multiplied with that of the current point.
   * @param f A function that takes a Cartesian point and returns a Double value, representing
   *          an operation or property of the Cartesian point.
   * @return The product of the results of applying the function `f` to the current Cartesian point
   *         and the specified Cartesian point.
   */
  private def mult(c: Cartesian)(f: Cartesian => Double): Double = f(this) * f(c)

  /**
   * Computes the difference between the value of the function `f` applied to the given Cartesian
   * point and the value of the same function applied to the current Cartesian point.
   *
   * @param c The Cartesian point to compare against the current Cartesian point.
   * @param f A function that takes a Cartesian point as input and returns a Double value,
   *          representing a specific property or calculation on the Cartesian point.
   * @return The difference between the result of applying the function `f` to the given Cartesian
   *         point and the result of applying it to the current Cartesian point.
   */
  def diff(c: Cartesian)(f: Cartesian => Double): Double = f(c) - f(this)

  /**
   * Computes the square of a given Double value.
   *
   * @param x The Double value to be squared.
   * @return The square of the provided Double value.
   */
  private def sqr(x: Double): Double = x * x
}
