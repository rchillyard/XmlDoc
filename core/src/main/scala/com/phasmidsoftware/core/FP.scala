/*
 * Copyright (c) 2019. Phasmid Software
 */

package com.phasmidsoftware.core

import java.net.URL
import scala.reflect.ClassTag
import scala.util.Using.Releasable
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try, Using}

/**
 * Various utilities for functional programming.
 */
object FP {

  /**
   * Sequence method to combine elements of Try.
   *
   * TESTME
   *
   * @param xys an Iterator of Try[X]
   * @tparam X the underlying type
   * @return a Try of Iterator[X]
   */
  def sequence[X](xys: Iterator[Try[X]]): Try[Iterator[X]] = sequence(xys.to(List)).map(_.iterator)

  /**
   * Method to transform a Seq of Try[X] into a Try of Seq[X].
   *
   * @param xys a Seq of Try[X].
   * @tparam X the underlying type.
   * @return a Try of Seq[X].
   */
  def sequence[X](xys: Iterable[Try[X]]): Try[Seq[X]] = xys.foldLeft(Try(Seq[X]())) {
    (xsy, xy) => for (xs <- xsy; x <- xy) yield xs :+ x
  }

  /**
   * Method to transform a `Seq[Option[X]]` into an `Option[Seq[X]]`.
   * NOTE this is pessimistic.
   *
   * @param xos a `Seq[Option[X]]`].
   * @tparam X the underlying type.
   * @return an `Option[Seq[X]]` which is defined only if all elements of the input were defined.
   */
  def sequence[X](xos: Iterable[Option[X]]): Option[Seq[X]] = xos.foldLeft(Option(Seq[X]())) {
    (xso, xo) => for (xs <- xso; x <- xo) yield xs :+ x
  }

  /**
   * Method to transform a Seq of Try[X] into a Try of Seq[X].
   *
   * Non-fatal failures are eliminated from consideration, although each one invokes the function f.
   * Fatal failures are retained so that the result will be a Failure.
   *
   * @param f   a function Throwable => Unit which will process each non-fatal failure.
   * @param xys a Seq of Try[X].
   * @tparam X the underlying type.
   * @return a Try of Seq[X].
   */
  def sequenceForgiving[X](f: Throwable => Unit)(xys: Iterable[Try[X]]): Try[Seq[X]] = {
    val (successes, failures) = xys partition (_.isSuccess)
    val fatalFailures: Iterable[Try[X]] = failures.collect {
      case Failure(x) if !NonFatal(x) => Failure(x)
    }
    val nonFatalFailures: Iterable[Try[X]] = failures.collect {
      case Failure(x) if NonFatal(x) => Failure(x)
    }
    nonFatalFailures foreach (xy => xy.recover { case x => f(x) })
    sequence(fatalFailures ++ successes)
  }

  /**
   * Sequence method to combine elements of Try.
   *
   * @param xys       an Iterable of Try[X].
   * @param pfFailure a partial function of type Throwable => Try of Option[X].
   * @tparam X the underlying type.
   * @return a Try of Seq[X].
   *         NOTE: that the output collection type will be Seq, regardless of the input type
   */
  def sequenceForgivingWith[X](xys: Iterable[Try[X]])(pfFailure: PartialFunction[Throwable, Try[Option[X]]]): Try[Seq[X]] =
    sequenceForgivingTransform[X](xys)(x => Success(Some(x)), pfFailure)

  /**
   * Sequence method to combine elements of Try.
   *
   * TESTME
   *
   * @param xys       an Iterable of Try[X].
   * @param fSuccess  a function of type X => Try of Option[X].
   * @param pfFailure a partial function of type Throwable => Try of Option[X].
   * @tparam X the underlying type.
   * @return a Try of Seq[X].
   *         NOTE: that the output collection type will be Seq, regardless of the input type
   */
  def sequenceForgivingTransform[X](xys: Iterable[Try[X]])(fSuccess: X => Try[Option[X]], pfFailure: PartialFunction[Throwable, Try[Option[X]]]): Try[Seq[X]] = {
    val xosy: Try[Seq[Option[X]]] = sequence(for (xy <- xys) yield xy.transform[Option[X]](fSuccess, pfFailure))
    for (xos <- xosy) yield xos.filter(_.isDefined).map(_.get)
  }

  /**
   * Method to partition an  method to combine elements of Try as an Iterator.
   *
   * TESTME
   *
   * @param xys an Iterator of Try[X].
   * @tparam X the underlying type.
   * @return a tuple of two iterators of Try[X], the first one being successes, the second one being failures.
   */
  def partition[X](xys: Iterator[Try[X]]): (Iterator[Try[X]], Iterator[Try[X]]) = xys.partition(_.isSuccess)

  /**
   * Method to partition an  method to combine elements of Try.
   *
   * TESTME
   *
   * @param xys a Seq of Try[X].
   * @tparam X the underlying type.
   * @return a tuple of two Seqs of Try[X], the first one being successes, the second one being failures.
   */
  def partition[X](xys: Iterable[Try[X]]): (Iterable[Try[X]], Iterable[Try[X]]) = xys.partition(_.isSuccess)

  /**
   * Method to yield a URL for a given resourceForClass in the classpath for C.
   *
   * TESTME
   *
   * @param resourceName the name of the resourceForClass.
   * @tparam C a class of the package containing the resourceForClass.
   * @return a Try[URL].
   */
  def resource[C: ClassTag](resourceName: String): Try[URL] = resourceForClass(resourceName, implicitly[ClassTag[C]].runtimeClass)

  /**
   * Method to yield a Try[URL] for a resource name and a given class.
   *
   * @param resourceName the name of the resource.
   * @param clazz        the class, relative to which, the resource can be found (defaults to the caller's class).
   * @return a Try[URL]
   */
  def resourceForClass(resourceName: String, clazz: Class[?] = getClass): Try[URL] = Option(clazz.getResource(resourceName)) match {
    case Some(u) => Success(u)
    case None => Failure(FPException(s"$resourceName is not a valid resource for $clazz"))
  }

  /**
   * Method to determine if the String w was found at a valid index (i).
   *
   * @param w the String (ignored unless there's an exception).
   * @param i the index found.
   * @return Success(i) if all well, else Failure(exception).
   */
  def indexFound(w: String, i: Int): Try[Int] = i match {
    case x if x >= 0 => Success(x)
    case _ => Failure(FPException(s"Header column '$w' not found"))
  }

  /**
   * Method to transform a `Try[X]` into an `Option[X]`.
   * But, unlike "toOption," a Failure can be logged.
   *
   * @param f  a function to process any Exception (typically a logging function).
   * @param xy the input `Try[X]`.
   * @tparam X the underlying type.
   * @return an `Option[X]`.
   */
  def tryToOption[X](f: Throwable => Unit)(xy: Try[X]): Option[X] = xy match {
    case Success(x) => Some(x)
    case Failure(NonFatal(x)) => f(x); None
    case Failure(x) => throw x
  }

  /**
   * Converts an `Option[X]` to a `Try[X]`.
   * If the `Option` is `Some`, it returns a `Success` containing the value.
   * If the `Option` is `None`, it returns a `Failure` containing the provided or default `Throwable`.
   *
   * @param xo        the input `Option[X]` to convert.
   * @param throwable the `Throwable` to use if the input is `None`. Defaults to `NoSuchElementException`.
   * @tparam X the underlying type of the `Option` and `Try`.
   * @return a `Try[X]` representing either the success or failure of the conversion.
   */
  def optionToTry[X](xo: Option[X], throwable: Throwable = new NoSuchElementException("optionToTry: None")): Try[X] = xo match {
    case Some(x) => Success(x)
    case None => Failure(throwable)
  }

  /**
   * Attempts to evaluate the provided expression and wraps the result in a `Success` if it is non-null.
   * If the value is `null`, a `Failure` wrapping a `NoSuchElementException` with the provided message is returned.
   *
   * @param x   a lazily-evaluated expression of type `X`.
   * @param msg a message to include in the exception if the result is `null`.
   * @tparam X the type of the expression's result.
   * @return a `Try[X]` containing `Success(x)` if `x` is non-null, or `Failure` if `x` is `null`.
   */
  def tryNotNull[X](x: => X)(msg: String): Try[X] = Option(x) match {
    case Some(x) => Success(x)
    case None => Failure(new NoSuchElementException(s"tryNotNull: null: $msg"))
  }

  /** Uncurrying for functions of arity 6.
   *
   */
  def uncurried[T1, T2, T3, T4, T5, T6, R](f: T1 => T2 => T3 => T4 => T5 => T6 => R): (T1, T2, T3, T4, T5, T6) => R = {
    (x1, x2, x3, x4, x5, x6) => f(x1)(x2)(x3)(x4)(x5)(x6)
  }

  /**
   * Applies a transformation function to the successful value of a Try if the value satisfies a predicate.
   * If the predicate fails, it returns a Failure with an exception indicating the reason.
   *
   * TESTME
   *
   * @param p         a predicate function to test the successful value of the input Try.
   * @param predicate a descriptive string used in the exception message if the predicate fails. Default is "predicate".
   * @param f         a transformation function applied to the value if the predicate passes.
   * @tparam X the input type of the Try.
   * @tparam Y the output type after applying the transformation function.
   * @return a function that transforms a Try[X] to a Try[Y] based on the predicate and transformation function.
   */
  def mapTryGuarded[X, Y](p: X => Boolean, predicate: String = "predicate")(f: X => Y): Try[X] => Try[Y] = {
    case Success(x) if p(x) => Success(f(x))
    case Success(x) => Failure(FPException(s"mapTryGuarded: $predicate failed for $x"))
    case Failure(x) => Failure(x)
  }
}

/**
 * The `TryUsing` object provides utility methods to manage resources safely
 * and effectively using Scala's `Using` and `Try`.
 * It encapsulates resource management in a functional way, ensuring proper release of resources.
 * The methods in this object extend the functionality of `Using.apply`
 * by offering flattening operations over nested `Try`.
 */
object TryUsing {
  /**
   * This method is to `Using.apply` as `flatMap` is to `map`.
   *
   * @param resource a resource which is used by f and will be managed via `Using.apply`
   * @param f        a function of R => Try[A].
   * @tparam R the resource type.
   * @tparam A the underlying type of the result.
   * @return a Try[A]
   */
  def apply[R: Releasable, A](resource: => R)(f: R => Try[A]): Try[A] = Using(resource)(f).flatten

  /**
   * This method is similar to `apply(r)` but it takes a `Try[R]` as its parameter.
   * The definition of `f` is the same as in the other apply, however.
   *
   * TESTME
   *
   * @param ry a Try[R] which is passed into f and will be managed via `Using.apply`
   * @param f  a function of R => Try[A].
   * @tparam R the resource type.
   * @tparam A the underlying type of the result.
   * @return a Try[A]
   */
  def apply[R: Releasable, A](ry: Try[R])(f: R => Try[A]): Try[A] = for (r <- ry; a <- apply(r)(f)) yield a
}

/**
 * This class represents an exception related to functional programming operations.
 *
 * @param msg A message describing the exception.
 * @param eo  An optional underlying cause for the exception, as a Throwable.
 */
case class FPException(msg: String, eo: Option[Throwable] = None) extends Exception(msg, eo.orNull)
