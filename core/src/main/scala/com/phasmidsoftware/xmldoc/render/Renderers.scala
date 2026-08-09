package com.phasmidsoftware.xmldoc.render

import com.phasmidsoftware.xmldoc.core.{CDATA, Text, XmlException}
import com.phasmidsoftware.xmldoc.render.Renderer.{doNestedRender, doRenderSequence, renderAttribute, renderOuter}
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.unused
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * Trait which defines generic and standard renderers.
 *
 */
trait Renderers {

  /**
   * Method to create a renderer for a case class with no members, or a case object.
   *
   * @tparam R the type of `Renderer` to be returned (must be a `Product`).
   * @return a `Renderer[R]`.
   */
  def renderer0[R <: Product : ClassTag]: Renderer[R] = Renderer {
    (_: R, format, stateR) =>
      doNestedRender(format, stateR, "", "", "")
  }

  /**
   * Method to create a renderer for a Product (e.g., case class) with one member.
   *
   * @param ignored (unused) a function which takes a `P0` and yields an `R`
   *                (this is usually the `apply` method of a case class).
   * @tparam P0 the (`Renderer`) type of the (single) member of `Product` type `R`.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a `Renderer[R]`.
   */
  def renderer1[P0: Renderer, R <: Product : ClassTag](@unused ignored: P0 => R): Renderer[R] = Renderer {
    (r: R, format, stateR) =>
      r.productElement(0) match {
        case p0: P0 @unchecked =>
          for {
            wOuter <- renderOuter(r, p0, 0, format.indent)
            result <- doNestedRender(format, stateR, "", wOuter, r.productElementName(0))
          } yield result
      }
  }

  /**
   * Method to create a renderer for a `Product` (e.g., case class) with one member.
   *
   * @param ignored (unused) a function which takes a `P0` and yields an `R`
   *                (this is usually the `apply` method of a case class).
   * @tparam P0 the (`Renderer`) type of the (single) member of `Product` type `R`.
   * @tparam R  the type of `Renderer` to be returned (must be a `Product`).
   * @return a `Renderer[R]`.
   */
  def renderer1Special[P0: Renderer, R <: Product : ClassTag](@unused ignored: P0 => R, prefix: String): Renderer[R] = Renderer {
    (r: R, format, stateR) =>
      r.productElement(0) match {
        case p0: P0 @unchecked =>
          for {
            // XXX See renderer1 for better way to get a P0 parameter
            wOuter <- renderOuter(r, p0, 0, format.indent)
            result <- doNestedRender(format, stateR, "", wOuter, r.productElementName(0))
          } yield prefix + result
      }
  }

  /**
   * Method to create a renderer for a `Product` (e.g., case class) with two members.
   *
   * @param construct a function `(P0, P1) => R` (this is usually the `apply` method of a case class).
   * @tparam P0 the (`Renderer`) type of the first member of `Product` type `R`.
   * @tparam P1 the (`Renderer`) type of the second member of `Product` type `R`.
   * @tparam R  the underlying type of the `Renderer` to be returned (must be a `Product`).
   * @return a `Renderer[R]`.
   */
  def renderer2[P0: Renderer, P1: Renderer, R <: Product : ClassTag](construct: (P0, P1) => R): Renderer[R] = Renderer {
    (r: R, format, stateR) =>
      ((r.productElement(0), r.productElement(1)): @unchecked) match {
        case (p0: P0 @unchecked, p1: P1 @unchecked) =>
          val constructorInner: P0 => R = construct(_, p1)
          for {wInner <- renderer1(constructorInner).render(constructorInner(p0), format, stateR.recurse)
               wOuter <- renderOuter(r, p1, 1, format.indent)
               result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(1))
               } yield result
      }
  }

  /**
   * Method to create a renderer for a `Product` (e.g., case class) with three members.
   *
   * @param construct a function `(P0, P1, P2) => R` (this is usually the `apply` method of a case class).
   * @tparam P0 the (`Renderer`) type of the first member of `Product` type `R`.
   * @tparam P1 the (`Renderer`) type of the second member of `Product` type `R`.
   * @tparam P2 the (`Renderer`) type of the third member of `Product` type `R`.
   * @tparam R  the underlying type of `Renderer` to be returned (must be a `Product`).
   * @return a `Renderer[R]`.
   */
  def renderer3[P0: Renderer, P1: Renderer, P2: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2) => R): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      ((r.productElement(0), r.productElement(1), r.productElement(2)): @unchecked) match {
        case (p0: P0 @unchecked, p1: P1 @unchecked, p2: P2 @unchecked) =>
          val constructorInner: (P0, P1) => R = construct(_, _, p2)
          for {
            wInner <- renderer2(constructorInner).render(constructorInner(p0, p1), format, stateR.recurse)
            wOuter <- renderOuter(r, p2, 2, format.indent)
            result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(2))
          } yield result
      }
    }
  }

  /**
   * Method to create a renderer for a `Product` (e.g., case class) with four members.
   *
   * @param construct a function `(P0, P1, P2, P3) => R` (this is usually the `apply` method of a case class).
   * @tparam P0 the (`Renderer`) type of the first member of `Product` type `R`.
   * @tparam P1 the (`Renderer`) type of the second member of `Product` type `R`.
   * @tparam P2 the (`Renderer`) type of the third member of `Product` type `R`.
   * @tparam P3 the (`Renderer`) type of the fourth member of `Product` type `R`.
   * @tparam R  the underlying type of the `Renderer` to be returned (must be a `Product`).
   * @return `Renderer[R]`.
   */
  def renderer4[P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3) => R): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      ((r.productElement(0), r.productElement(1), r.productElement(2), r.productElement(3)): @unchecked) match {
        case (p0: P0 @unchecked, p1: P1 @unchecked, p2: P2 @unchecked, p3: P3 @unchecked) =>
          val constructorInner: (P0, P1, P2) => R = construct(_, _, _, p3)
          for {wInner <- renderer3(constructorInner).render(constructorInner(p0, p1, p2), format, stateR.recurse)
               wOuter <- renderOuter(r, p3, 3, format.indent)
               result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(3))
               } yield result
      }
    }
  }

  /**
   * Provides a renderer for a product type with five elements. Uses the provided constructor
   * to render the product type by handling recursive states and formatting.
   *
   * @param construct A function that takes five parameters of types `P0`, `P1`, `P2`, `P3`, `P4`
   *                  and constructs an instance of the product type `R`.
   * @tparam P0 The type of the first element in the product type, which must have an implicit `Renderer` instance provided.
   * @tparam P1 The type of the second element in the product type, which must have an implicit `Renderer` instance provided.
   * @tparam P2 The type of the third element in the product type, which must have an implicit `Renderer` instance provided.
   * @tparam P3 The type of the fourth element in the product type, which must have an implicit `Renderer` instance provided.
   * @tparam P4 The type of the fifth element in the product type, which must have an implicit `Renderer` instance provided.
   * @tparam R  The product type being rendered. It must extend `Product` and have an implicit `ClassTag`.
   * @return A `Renderer` instance for the product type `R`.
   */
  def renderer5[P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, P4: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => R): Renderer[R] = Renderer {
    (r: R, format, stateR) =>
      ((r.productElement(0), r.productElement(1), r.productElement(2), r.productElement(3), r.productElement(4)): @unchecked) match {
        case (p0: P0 @unchecked, p1: P1 @unchecked, p2: P2 @unchecked, p3: P3 @unchecked, p4: P4 @unchecked) =>
          val constructorInner: (P0, P1, P2, P3) => R = construct(_, _, _, _, p4)
          for {wInner <- renderer4(constructorInner).render(constructorInner(p0, p1, p2, p3), format, stateR.recurse)
               wOuter <- renderOuter(r, p4, 4, format.indent)
               result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(4))
               } yield result
      }
  }

  /**
   * Creates a `Renderer` for a product type `R` with six type parameters.
   *
   * @param construct A function that takes six parameters of types `P0`, `P1`, `P2`, `P3`, `P4`, `P5` and
   *                  constructs an instance of type `R`.
   * @tparam P0 The type of the first parameter to the constructor.
   * @tparam P1 The type of the second parameter to the constructor.
   * @tparam P2 The type of the third parameter to the constructor.
   * @tparam P3 The type of the fourth parameter to the constructor.
   * @tparam P4 The type of the fifth parameter to the constructor.
   * @tparam P5 The type of the sixth parameter to the constructor.
   * @tparam R  The resulting product type that can be constructed by the `construct` function.
   * @return A `Renderer` for the product type `R` that renders objects of this type based on its six components and their respective renderers.
   */
  def renderer6[P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, P4: Renderer, P5: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => R): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      ((r.productElement(0), r.productElement(1), r.productElement(2), r.productElement(3), r.productElement(4), r.productElement(5)): @unchecked) match {
        case (p0: P0 @unchecked, p1: P1 @unchecked, p2: P2 @unchecked, p3: P3 @unchecked, p4: P4 @unchecked, p5: P5 @unchecked) =>
          val constructorInner: (P0, P1, P2, P3, P4) => R = construct(_, _, _, _, _, p5)
          for {wInner <- renderer5(constructorInner).render(constructorInner(p0, p1, p2, p3, p4), format, stateR.recurse)
               wOuter <- renderOuter(r, p5, 5, format.indent)
               result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(5))
               } yield result
      }
    }
  }

  /**
   * Creates a Renderer instance for a product type with seven elements, where all elements have associated Renderers.
   *
   * @param construct A function that takes seven parameters of types P0, P1, P2, P3, P4, P5, and P6, and constructs
   *                  an instance of type R.
   * @tparam P0 The type of the first element, which must have a Renderer instance available.
   * @tparam P1 The type of the second element, which must have a Renderer instance available.
   * @tparam P2 The type of the third element, which must have a Renderer instance available.
   * @tparam P3 The type of the fourth element, which must have a Renderer instance available.
   * @tparam P4 The type of the fifth element, which must have a Renderer instance available.
   * @tparam P5 The type of the sixth element, which must have a Renderer instance available.
   * @tparam P6 The type of the seventh element, which must have a Renderer instance available.
   * @tparam R  The resulting product type, constrained to be a subclass of Product and require a ClassTag.
   * @return A Renderer instance that can render objects of type R, utilizing the provided construct function
   *         and the Renderers for each corresponding parameter type.
   */
  def renderer7[P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, P4: Renderer, P5: Renderer, P6: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5, P6) => R): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      ((r.productElement(0), r.productElement(1), r.productElement(2), r.productElement(3), r.productElement(4), r.productElement(5), r.productElement(6)): @unchecked) match {
        case (p0: P0 @unchecked, p1: P1 @unchecked, p2: P2 @unchecked, p3: P3 @unchecked, p4: P4 @unchecked, p5: P5 @unchecked, p6: P6 @unchecked) =>
          val constructorInner: (P0, P1, P2, P3, P4, P5) => R = construct(_, _, _, _, _, _, p6)
          for {wInner <- renderer6(constructorInner).render(constructorInner(p0, p1, p2, p3, p4, p5), format, stateR.recurse)
               wOuter <- renderOuter(r, p6, 6, format.indent)
               result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(5))
               } yield result
      }
    }
  }

  /**
   * Alternative method to create a renderer for a Product (e.g., case class) with one member but also an auxiliary object in a second parameter set.
   *
   * CONSIDER rename each of theses renderXSuper methods to renderXAux.
   *
   * @param construct a function which takes a P0 and yields a function of B => R
   *                  (this is usually the apply method of a case class that has a second parameter set with one parameter of type B).
   * @tparam B the (Renderer) type of the auxiliary object of type R.
   * @tparam R the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer0Super[B: Renderer, R <: Product : ClassTag](construct: B => R)(lens: R => B): Renderer[R] = Renderer {
    (r: R, format, stateR) =>
      for {
        wInner <- Renderer.render(lens(r), format, stateR.recurse)
        result <- doNestedRender(format, stateR, wInner, "", r.productElementName(0))
      } yield result
  }

  /**
   * Alternative method to create a renderer for a Product (e.g., case class) with one member but also an auxiliary object in a second parameter set.
   *
   * @param construct a function which takes a P0 and yields a function of B => R
   *                  (this is usually the apply method of a case class that has a second parameter set with one parameter of type B).
   * @tparam B  the (Renderer) type of the auxiliary object of type R.
   * @tparam P0 the (Renderer) type of the (single) member of Product type R.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer1Super[B: Renderer, P0: Renderer, R <: Product : ClassTag](construct: P0 => B => R)(lens: R => B): Renderer[R] = Renderer {
    (r: R, format, stateR) =>
      val b = lens(r)
      val constructOuter: P0 => R = construct(_)(b)
      for {
        // CONSIDER not invoking recurse here.
        wInner <- Renderer.render(b, format, stateR.recurse)
        wOuter <- renderer1(constructOuter).render(r, format, stateR.recurse)
        result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(0))
      } yield result
  }

  /**
   * Method to create a renderer for a Product (e.g., case class) with two members but also an auxiliary object in a second parameter set.
   *
   * @param construct a function (P0, P1) => R (this is usually the apply method of a case class).
   * @tparam B  the (Renderer) type of the auxiliary object of type R.
   * @tparam P0 the (Renderer) type of the first member of Product type R.
   * @tparam P1 the (Renderer) type of the second member of Product type R.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer2Super[B: Renderer, P0: Renderer, P1: Renderer, R <: Product : ClassTag](construct: (P0, P1) => B => R)(lens: R => B): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      val b = lens(r)
      val constructOuter: (P0, P1) => R = construct(_, _)(b)
      for {wInner <- Renderer.render(b, format, stateR.recurse)
           wOuter <- renderer2(constructOuter).render(r, format, stateR.recurse)
           result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(0))
           } yield result
    }
  }

  /**
   * Method to create a renderer for a Product (e.g., case class) with three members but also an auxiliary object in a second parameter set.
   *
   * @param construct a function (P0, P1, P2) => R (this is usually the apply method of a case class).
   * @tparam B  the (Renderer) type of the auxiliary object of type R.
   * @tparam P0 the (Renderer) type of the first member of Product type R.
   * @tparam P1 the (Renderer) type of the second member of Product type R.
   * @tparam P2 the (Renderer) type of the third member of Product type R.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer3Super[B: Renderer, P0: Renderer, P1: Renderer, P2: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2) => B => R)(lens: R => B): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      val b = lens(r)
      val constructOuter: (P0, P1, P2) => R = construct(_, _, _)(b)
      for {wInner <- Renderer.render(b, format, stateR.recurse)
           wOuter <- renderer3(constructOuter).render(r, format, stateR.recurse)
           result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(0))
           } yield result
    }
  }

  /**
   * Method to create a renderer for a Product (e.g., case class) with four members but also an auxiliary object in a second parameter set.
   *
   * @param construct a function (P0, P1, P2, P3) => R (this is usually the apply method of a case class).
   * @tparam B  the (Renderer) type of the auxiliary object of type R.
   * @tparam P0 the (Renderer) type of the first member of Product type R.
   * @tparam P1 the (Renderer) type of the second member of Product type R.
   * @tparam P2 the (Renderer) type of the third member of Product type R.
   * @tparam P3 the (Renderer) type of the fourth member of Product type R.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer4Super[B: Renderer, P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3) => B => R)(lens: R => B): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      val b = lens(r)
      val constructOuter: (P0, P1, P2, P3) => R = construct(_, _, _, _)(b)
      for {
        wInner <- Renderer.render(b, format, stateR.recurse)
        wOuter <- renderer4(constructOuter).render(r, format, stateR.recurse)
        result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(0))
      } yield result
    }
  }

  /**
   * Method to create a renderer for a Product (e.g., case class) with five members but also an auxiliary object in a second parameter set.
   *
   * @param construct a function (P0, P1, P2, P3, P4) => R (this is usually the apply method of a case class).
   * @tparam B  the (Renderer) type of the auxiliary object of type R.
   * @tparam P0 the (Renderer) type of the first member of Product type R.
   * @tparam P1 the (Renderer) type of the second member of Product type R.
   * @tparam P2 the (Renderer) type of the third member of Product type R.
   * @tparam P3 the (Renderer) type of the fourth member of Product type R.
   * @tparam P4 the (Renderer) type of the fifth member of Product type R.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer5Super[B: Renderer, P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, P4: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => B => R)(lens: R => B): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      val b = lens(r)
      val constructOuter: (P0, P1, P2, P3, P4) => R = construct(_, _, _, _, _)(b)
      for {
        wInner <- Renderer.render(b, format, stateR.recurse)
        wOuter <- renderer5(constructOuter).render(r, format, stateR.recurse)
        result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(0))
      } yield result
    }
  }

  /**
   * Method to create a renderer for a Product (e.g., case class) with six members but also an auxiliary object in a second parameter set.
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5) => R (this is usually the apply method of a case class).
   * @tparam B  the (Renderer) type of the auxiliary object of type R.
   * @tparam P0 the (Renderer) type of the first member of Product type R.
   * @tparam P1 the (Renderer) type of the second member of Product type R.
   * @tparam P2 the (Renderer) type of the third member of Product type R.
   * @tparam P3 the (Renderer) type of the fourth member of Product type R.
   * @tparam P4 the (Renderer) type of the fifth member of Product type R.
   * @tparam P5 the (Renderer) type of the sixth member of Product type R.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer6Super[B: Renderer, P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, P4: Renderer, P5: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => B => R)(lens: R => B): Renderer[R] = Renderer[R] {
    (r: R, format, stateR) => {
      val b = lens(r)
      val constructOuter: (P0, P1, P2, P3, P4, P5) => R = construct(_, _, _, _, _, _)(b)
      for {
        wInner <- Renderer.render(b, format, stateR.recurse)
        wOuter <- renderer6(constructOuter).render(r, format, stateR.recurse)
        result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(0))
      } yield result
    }
  }

  /**
   * Method to create a renderer for a Product (e.g., case class) with seven members but also an auxiliary object in a second parameter set.
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5, P6) => R (this is usually the apply method of a case class).
   * @tparam B  the (Renderer) type of the auxiliary object of type R.
   * @tparam P0 the (Renderer) type of the first member of Product type R.
   * @tparam P1 the (Renderer) type of the second member of Product type R.
   * @tparam P2 the (Renderer) type of the third member of Product type R.
   * @tparam P3 the (Renderer) type of the fourth member of Product type R.
   * @tparam P4 the (Renderer) type of the fifth member of Product type R.
   * @tparam P5 the (Renderer) type of the sixth member of Product type R.
   * @tparam P6 the (Renderer) type of the seventh member of Product type R.
   * @tparam R  the type of Renderer to be returned (must be a Product).
   * @return a Renderer[R].
   */
  def renderer7Super[B: Renderer, P0: Renderer, P1: Renderer, P2: Renderer, P3: Renderer, P4: Renderer, P5: Renderer, P6: Renderer, R <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5, P6) => B => R)(lens: R => B): Renderer[R] = Renderer {
    (r: R, format, stateR) => {
      val b = lens(r)
      val constructOuter: (P0, P1, P2, P3, P4, P5, P6) => R = construct(_, _, _, _, _, _, _)(b)
      for {
        wInner <- Renderer.render(b, format, stateR.recurse)
        wOuter <- renderer7(constructOuter).render(r, format, stateR.recurse)
        result <- doNestedRender(format, stateR, wInner, wOuter, r.productElementName(0))
      } yield result
    }
  }

  /**
   * Creates a Renderer instance for an Option container type.
   *
   * This method lifts the functionality of an existing (implicit) Renderer for a type `R`
   * to handle `Option[R]`, allowing rendering of optional values by delegating
   * to the Renderer of the underlying type `R`.
   *
   * @return A Renderer for the Option wrapper of type R
   * @tparam R the underlying type, which must provide evidence of Renderer[R].
   */
  def optionRenderer[R: Renderer]: Renderer[Option[R]] = implicitly[Renderer[R]].lift

  /**
   * Method to yield a Renderer[T] such that the rendering can be performed according to the renderables for one subtype of T
   * (R0).
   *
   * @tparam T  the super-type and the underlying type of the result.
   * @tparam R0 one subtype of T.
   * @return a Renderer[T].
   */
  def rendererSuper1[T: ClassTag, R0 <: T : Renderer : ClassTag]: Renderer[T] = Renderer {
    (t: T, format, stateR) =>
      t match {
        case r: R0 =>
          for {
            result <- Renderer.render(r, format, stateR)
          } yield result
        case _ =>
          Failure(XmlException(s"rendererSuper1: object of type ${t.getClass} is not a subtype for ${implicitly[ClassTag[T]]}\n" +
            s"Are you sure that, in the appropriate rendererSuperN definition, you've included all possible subtypes?" +
            s" (compare with the corresponding (multi) extractor definition"))
      }
  }

  /**
   * Method to yield a Renderer[T] such that the rendering can be performed according to the renderables for two subtypes of T
   * (R0 or R1).
   *
   * @tparam T  the super-type and the underlying type of the result.
   * @tparam R0 one subtype of T.
   * @tparam R1 another subtype of T.
   * @return a Renderer[T].
   */
  def rendererSuper2[T: ClassTag, R0 <: T : Renderer : ClassTag, R1 <: T : Renderer : ClassTag]: Renderer[T] = Renderer {
    (t: T, format, stateR) =>
      t match {
        case r: R0 =>
          for {
            result <- Renderer.render(r, format, stateR)
          } yield result
        case _ => rendererSuper1[T, R1].render(t, format, stateR)
      }
  }

  /**
   * Method to yield a Renderer[T] such that the rendering can be performed according to the renderables for three subtypes of T
   * (R0, R1, or R2).
   *
   * @tparam T  the super-type and the underlying type of the result.
   * @tparam R0 one subtype of T.
   * @tparam R1 another subtype of T.
   * @tparam R2 another subtype of T.
   * @return a Renderer[T].
   */
  def rendererSuper3[T: ClassTag, R0 <: T : Renderer : ClassTag, R1 <: T : Renderer : ClassTag, R2 <: T : Renderer : ClassTag]: Renderer[T] = Renderer {
    (t: T, format, stateR) =>
      t match {
        case r: R0 =>
          for {
            result <- Renderer.render(r, format, stateR)
          } yield result
        case _ => rendererSuper2[T, R1, R2].render(t, format, stateR)
      }
  }

  /**
   * Method to yield a Renderer[T] such that the rendering can be performed according to the renderables for four subtypes of T
   * (R0, R1, R2, or R3).
   *
   * @tparam T  the super-type and the underlying type of the result.
   * @tparam R0 one subtype of T.
   * @tparam R1 another subtype of T.
   * @tparam R2 another subtype of T.
   * @tparam R3 another subtype of T.
   * @return a Renderer[T].
   */
  def rendererSuper4[T: ClassTag, R0 <: T : Renderer : ClassTag, R1 <: T : Renderer : ClassTag, R2 <: T : Renderer : ClassTag, R3 <: T : Renderer : ClassTag]: Renderer[T] = Renderer {
    (t: T, format, stateR) =>
      t match {
        case r: R0 =>
          for {
            result <- Renderer.render(r, format, stateR)
          } yield result
        case _ => rendererSuper3[T, R1, R2, R3].render(t, format, stateR)
      }
  }

  /**
   * Method to yield a Renderer[T] such that the rendering can be performed according to the renderables for five subtypes of T
   * (R0, R1, R2, R3, or R4).
   *
   * @tparam T  the super-type and the underlying type of the result.
   * @tparam R0 one subtype of T.
   * @tparam R1 another subtype of T.
   * @tparam R2 another subtype of T.
   * @tparam R3 another subtype of T.
   * @tparam R4 another subtype of T.
   * @return a Renderer[T].
   */
  def rendererSuper5[T: ClassTag, R0 <: T : Renderer : ClassTag, R1 <: T : Renderer : ClassTag, R2 <: T : Renderer : ClassTag, R3 <: T : Renderer : ClassTag, R4 <: T : Renderer : ClassTag]: Renderer[T] = Renderer {
    (t: T, format, stateR) =>
      t match {
        case r: R0 =>
          for {
            result <- Renderer.render(r, format, stateR)
          } yield result
        case _ => rendererSuper4[T, R1, R2, R3, R4].render(t, format, stateR)
      }
  }

  /**
   * Method to yield a Renderer[T] such that the rendering can be performed according to the renderables for six subtypes of T
   * (R0, R1, R2, R3, R4, or R5).
   *
   * @tparam T  the super-type and the underlying type of the result.
   * @tparam R0 one subtype of T.
   * @tparam R1 another subtype of T.
   * @tparam R2 another subtype of T.
   * @tparam R3 another subtype of T.
   * @tparam R4 another subtype of T.
   * @tparam R5 another subtype of T.
   * @return a Renderer[T].
   */
  def rendererSuper6[T: ClassTag, R0 <: T : Renderer : ClassTag, R1 <: T : Renderer : ClassTag, R2 <: T : Renderer : ClassTag, R3 <: T : Renderer : ClassTag, R4 <: T : Renderer : ClassTag, R5 <: T : Renderer : ClassTag]: Renderer[T] = Renderer { (t: T, format, stateR) =>
    t match {
      case r: R0 =>
        for {
          result <- Renderer.render(r, format, stateR)
        } yield result
      case _ => rendererSuper5[T, R1, R2, R3, R4, R5].render(t, format, stateR)
    }
  }

  /**
   * Method to return a Renderer of Seq[R].
   *
   * @tparam R the underlying element type.
   * @return a Renderer of Seq[R].
   */
  def sequenceRenderer[R: Renderer]: Renderer[Seq[R]] = Renderer {
    (rs, format, _) => Renderer.doRenderSequence(rs, format, None)
  }

  /**
   * Method to return a Renderer of Seq[R] with a pre-defined format.
   *
   * NOTE This is required for allowing a format to take precedence over the format parameter passed into the render method.
   *
   * @param formatFunc a function which, given the current state of indents, yields the required format to pass into doRenderSequence.
   * @tparam R the underlying type to be rendered.
   * @return a Renderer of Seq[R].
   */
  def sequenceRendererFormatted[R: Renderer : ClassTag](formatFunc: Int => Format): Renderer[Seq[R]] = Renderer {
    (rs, format, stateR) =>
      doRenderSequence(rs, formatFunc(format.indents), stateR.maybeName)
  }
}

/**
 * Object containing various `Renderer` instances and utility methods for rendering objects
 * into string representations with optional attributes and specific formatting rules.
 */
object Renderers {

  val logger: Logger = LoggerFactory.getLogger(Renderers.getClass)

  /**
   * Provides a `Renderer` instance for rendering objects of type `T` with an optional name.
   * This renderer delegates to the `renderAny` method to generate the string representation,
   * incorporating the provided name if available.
   *
   * @tparam T the type of the object to be rendered.
   * @return a `Renderer[T]` that renders objects of type `T` with an optional name.
   */
  private def rendererAnyWithName[T]: Renderer[T] = {
    (t, _, stateR) => renderAny(t, stateR.maybeName)
  }

  /**
   * Renders any given object into a string representation, optionally incorporating a name as part of the rendering.
   * The rendering is performed using the `renderAttribute` method, which formats the output as either `name="value"`
   * or simply `value` if no name is provided.
   *
   * @param t         the object to be rendered, converted to its string representation using `toString`
   * @param maybeName an optional string representing the name to be prefixed in the rendered output
   * @return a `Try[String]` containing the rendered representation or an exception if rendering fails
   */
  def renderAny(t: Any, maybeName: Option[String]): Try[String] = renderAttribute(t.toString, maybeName)

  /**
   * Provides a `Renderer` instance for rendering objects of type `T`.
   * This method converts the input object into a string representation using its `toString` method.
   *
   * @tparam T the type of the object to render.
   * @return a `Renderer[T]` that processes the object and renders its string representation.
   */
  def enumObjectRenderer[T]: Renderer[T] = {
    (t: T, _: Format, _: StateR) => renderAny(t, None)
  }

  /**
   * Provides a `Renderer` instance for rendering enum-like attributes.
   * This renderer converts the input type `T` into
   * a string representation and processes it as an attribute using the rendering state information.
   *
   * @tparam T the type parameter for the object being rendered, representing an enum-like type.
   * @return a `Renderer[T]` instance for rendering enum attributes.
   */
  def enumAttributeRenderer[T]: Renderer[T] = {
    (t: T, _: Format, stateR: StateR) => renderAny(t, stateR.maybeName)
  }

  /**
   * Implicit `Renderer` instance for the `CharSequence` type.
   *
   * This renderer is responsible for converting a `CharSequence` into its string representation,
   * based on the given format and rendering state. It handles two specific cases:
   *
   * 1. If the input `CharSequence` is of type `CDATA` and the format is `_XML`, it invokes `toXML`
   * to generate the appropriate XML representation.
   * 2. For all other `CharSequence` inputs, it uses the `scrub` method to sanitize the input and
   * then calls `renderAttribute` to produce the final string representation.
   *
   * Note: Additional handling may be needed to convert all other `CharSequence` inputs into `CDATA` if required.
   */
  implicit val charSequenceRenderer: Renderer[CharSequence] = Renderer[CharSequence] {
    (x, format, stateR) =>
      (x, format) match {
        case (c: CDATA, _XML) => c.toXML
        // CONSIDER turning all other CharSequence into CDATA if appropriate.
        case _ => renderAttribute(scrub(x), stateR.maybeName)
      }
  } ^^ "charSequenceRenderer"

  implicit val stringRenderer: Renderer[String] = rendererAnyWithName ^^ "stringRenderer"

  implicit val rendererOptionString: Renderer[Option[String]] = new Renderers {}.optionRenderer[String] ^^ "rendererOptionString"

  implicit val rendererSequenceString: Renderer[Seq[String]] = new Renderers {}.sequenceRenderer[String] ^^ "rendererSequenceString"

  implicit val intRenderer: Renderer[Int] = rendererAnyWithName ^^ "intRenderer"


  implicit val byteRenderer: Renderer[Byte] = Renderer[Byte] {
    (t, _, stateR) => Success(f"$t%02x")


  } ^^ "byteRenderer"

  implicit val rendererOptionInt: Renderer[Option[Int]] = new Renderers {}.optionRenderer[Int]// ^^ "rendererOptionInt"

  implicit val booleanRenderer: Renderer[Boolean] = Renderer[Boolean] {
    (t, _, stateR) => renderAttribute(if (t) "1" else "0", stateR.maybeName)
  } ^^ "booleanRenderer"

  implicit val rendererOptionBoolean: Renderer[Option[Boolean]] = new Renderers {}.optionRenderer[Boolean]// ^^ "rendererOptionBoolean"

  implicit val doubleRenderer: Renderer[Double] = Renderer[Double] {
    (t, _, stateR) =>
      val sb = new StringBuilder(t.toString)
      while (sb.endsWith("0")) sb.setLength(sb.length() - 1)
      if (sb.endsWith(".")) sb.setLength(sb.length() - 1)
      renderAttribute(sb.toString, stateR.maybeName)
  } ^^ "doubleRenderer"
  implicit val rendererOptionDouble: Renderer[Option[Double]] = new Renderers {}.optionRenderer[Double]

  implicit val longRenderer: Renderer[Long] = rendererAnyWithName ^^ "longRenderer"

  /**
   * Sanitizes the input character sequence by replacing certain characters with their HTML-safe representations.
   * Specifically, it replaces '<' with "&lt;" and '&' with "&amp;".
   *
   * @param x the input character sequence to be sanitized
   * @return a sanitized string with unsafe characters replaced
   */
  private def scrub(x: CharSequence): String =
    x.toString.replaceAll("<", "&lt;").replaceAll("&", "&amp;")

  private val renderers = new Renderers {}
  implicit val rendererText: Renderer[Text] = renderers.renderer1(Text.apply)
  implicit val rendererOptionText: Renderer[Option[Text]] = renderers.optionRenderer[Text]
}
