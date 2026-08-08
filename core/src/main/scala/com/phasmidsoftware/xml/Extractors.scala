package com.phasmidsoftware.xml

import com.phasmidsoftware.core.FP.{optionToTry, sequence, tryNotNull}
import com.phasmidsoftware.core.{FP, Reflection, XmlException}
import com.phasmidsoftware.flog.Flog
import com.phasmidsoftware.xml.Extractor.{extract, extractChildren, extractElementsByLabel, extractSequence, fieldExtractor, none, parse}
import com.phasmidsoftware.xml.Extractors.fieldNamesMaybeDropLast

import scala.Function.uncurried
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

/**
 * Trait which defines many useful Extractors, where the result is an instance of Extractor[T] or MultiExtractor[T].
 */
trait Extractors {

  /**
   * Transforms an `Extractor` for a type `P` into an `Extractor` for `Option[P]`.
   *
   * @return An `Extractor` that can extract an `Option[P]`, which will contain
   *         a value if extraction for type `P` succeeds or be None if it fails.
   */
  def extractorOption[P: Extractor]: Extractor[Option[P]] = implicitly[Extractor[P]].lift

  /**
   * Method to yield an `Extractor` which will convert an Xml `Node` into a sequence of `P` objects
   * where there is evidence of `Extractor[P]`.
   * When invoked, the extractor uses `extractSequence` on a `NodeSeq` formed from matching `label` on the given node.
   *
   * NOTE This method is used to create an extractor that will extract an `Iterable` from a `Node`,
   * whereas we use `MultiExtractor` to extract an `Iterable` from a `NodeSeq`.
   *
   * CONSIDER there must be an alternative to this code.
   *
   * @param label the label of each of the child nodes to be extracted.
   * @tparam P the underlying type of the result.
   *           Required: implicit evidence of type `Extractor[P]`.
   * @return an Extractor of `Iterable[P]`.
   */
  def extractorIterable[P: Extractor](label: String): Extractor[Iterable[P]] = Extractor((node: Node) => extractSequence[P](node / label))

  /**
   * Creates an extractor for enumerations by parsing a string into its corresponding `Enumeration` value.
   * This method invokes `parse` after reformatting the String
   * to match the enumerated type using the (safe) equivalent of the `fromView` method.
   *
   * An example of the use of this method is as follows:
   * <pre>
   * object Shapes extends Enumeration with Extractors with Renderers {
   * val rectangle, cylinder, sphere = Value
   * implicit val extractor: Extractor[Shapes.Value] = extractorEnum[Value, this.type](this)(s => s.toLowerCase)
   * implicit val renderer: Renderer[Shapes.Value] = enumObjectRenderer
   * }
   * </pre>
   *
   * @param e the enumeration type from which the value is extracted
   * @param f a function that transforms the input string before attempting the extraction
   * @return an `Extractor` that performs the extraction of type `P` from the given string
   * @tparam P is the underlying type of the resulting Extractor.
   * @tparam E is the Enumeration type.
   */
  def extractorEnum[P, E <: Enumeration](e: E)(f: String => String): Extractor[P] = parse {
    s => optionToTry(e.values.find(_.toString == f(s)) map (_.asInstanceOf[P]))
  }

  /**
   * Creates a new instance of `MultiExtractorBase` for the specified range,
   * adapting the extracted elements
   * to the type parameter `P` using the implicit `Extractor`.
   *
   * @param range the range of elements to extract
   * @tparam P the underlying type for which there must be evidence of `Extractor[P]`.
   * @return a new `MultiExtractor` instance for sequences of type `P`.
   */
  def multiExtractorBase[P: Extractor](range: Range): MultiExtractor[Seq[P]] = new MultiExtractorBase[P](range) ^^ s"multiExtractorBase with range $range"

  /**
   * Method to yield a `MultiExtractor` of `Seq[T]` such that `T` is the super-type of `P0`.
   *
   * @param construct a function whose sole purpose is to enable type inference
   *                  (`construct` is never referenced in the code).
   * @param labels    the label of the elements we wish to extract (wrapped in `Seq`).
   *                  The one label must correspond to P0.
   * @tparam T  the ultimate underlying type of the resulting `MultiExtractor`.
   * @tparam U  a tuple whose only purpose is type inference.
   * @tparam P0 the first subtype of `T`, for which there must be implicit evidence of `Extractor[P]` and `ClassTag[P]`.
   * @return `MultiExtractor of Seq[T]`.
   */
  def multiExtractor1[T, U <: Product, P0 <: T : {Extractor, ClassTag}](construct: P0 => U, labels: Seq[String]): MultiExtractor[Seq[T]] =
    nodeSeq =>
      labels match {
        case label :: Nil => sequence(extractElementsByLabel[P0](nodeSeq, label))
        case fs => Failure(XmlException(s"multiExtractor1: logic error for labels: $fs"))
      }

  /**
   * Method to yield a MultiExtractor of `Seq[T]` such that T is the super-type of P0.
   *
   * TESTME
   *
   * @param construct a function whose sole purpose is to enable type inference (construct is never referenced in the code).
   * @param labels    the label of the elements we wish to extract (wrapped in Seq). The one label must correspond to P0.
   * @tparam T  the ultimate underlying type of the resulting MultiExtractor.
   * @tparam U  a tuple whose only purpose is type inference.
   * @tparam P0 the first subtype of `T`, for which there must be implicit evidence of `Extractor[P]` and `ClassTag[P]`.
   * @return MultiExtractor of `Seq[T]`.
   */
  def subclassExtractor1[T, U <: Product, P0 <: T : {Extractor, ClassTag}](construct: P0 => U, labels: Seq[String]): SubclassExtractor[T] =
    new SubclassExtractor[T](labels)(multiExtractor1(construct, labels))

  /**
   * Method to yield a MultiExtractor of `Seq[T]` such that T is the super-type of two P-types.
   *
   * @param construct a function whose sole purpose is to enable type inference (construct is never referenced in the code).
   * @param labels    the labels of the elements we wish to extract. These must be in the same sequence as the corresponding P-types.
   * @tparam T  the ultimate underlying type of the resulting MultiExtractor.
   * @tparam U  a tuple whose only purpose is type inference.
   * @tparam P0 the first subtype of `T`, for which there must be implicit evidence of `Extractor[P]` and `ClassTag[P]`.
   * @tparam P1 the second (Extractor-enabled) subtype of T.
   * @return MultiExtractor of `Seq[T]`.
   */
  def multiExtractor2[T, U <: Product, P0 <: T : {Extractor, ClassTag}, P1 <: T : {Extractor, ClassTag}](construct: (P0, P1) => U, labels: Seq[String]): MultiExtractor[Seq[T]] =
    nodeSeq =>
      labels match {
        case label :: fs =>
          val p0sy = sequence(extractElementsByLabel[P0](nodeSeq, label))
          val tsy = multiExtractor1[T, Tuple1[P1], P1](p1 => Tuple1(p1), fs).extract(nodeSeq)
          for (ts1 <- tsy; ts2 <- p0sy) yield ts1 ++ ts2
      }

  /**
   * Method to yield a MultiExtractor of `Seq[T]` such that T is the super-type of three P-types.
   * The labels are "translated" according to the translation table, which may produce more labels than in the original.
   * The first label is used to filter the P0-type child nodes of nodeSeq (the input of the resulting MultiExtractor).
   * Then, the resulting
   *
   * @param construct a function whose sole purpose is to enable type inference (construct is never referenced in the code).
   * @param labels    the labels of the elements we wish to extract. These must be in the same sequence as the corresponding P-types.
   * @tparam T  the ultimate underlying type of the resulting MultiExtractor.
   * @tparam U  a tuple whose only purpose is type inference.
   * @tparam P0 the first subtype of `T`, for which there must be implicit evidence of `Extractor[P]` and `ClassTag[P]`.
   * @tparam P1 the second (Extractor-enabled) subtype of T.
   * @tparam P2 the third (Extractor-enabled) subtype of T.
   * @return MultiExtractor of `Seq[T]`.
   */
  def multiExtractor3[T, U <: Product, P0 <: T : {Extractor, ClassTag}, P1 <: T : {Extractor, ClassTag}, P2 <: T : {Extractor, ClassTag}](construct: (P0, P1, P2) => U, labels: Seq[String]): MultiExtractor[Seq[T]] =
    nodeSeq =>
      labels match {
        case label :: fs =>
          val p0sy = sequence(extractElementsByLabel[P0](nodeSeq, label))
          val tsy = multiExtractor2[T, (P1, P2), P1, P2]((p1, p2) => (p1, p2), fs).extract(nodeSeq)
          for (ts1 <- tsy; ts2 <- p0sy) yield ts1 ++ ts2
      }

  /**
   * Method to yield a `MultiExtractor` of `Seq[T]` such that `T` is the super-type of four P-types.
   *
   * @param construct a function whose sole purpose is to enable type inference (construct is never referenced in the code).
   * @param labels    the labels of the elements we wish to extract.
   *                  These must be in the same sequence as the corresponding P-types.
   * @tparam T  the ultimate underlying type of the resulting `MultiExtractor`.
   * @tparam U  a tuple whose only purpose is type inference.
   * @tparam P0 the first subtype of `T`, for which there must be implicit evidence of `Extractor[P0]` and `ClassTag[P0]`.
   * @tparam P1 the second (Extractor-enabled) subtype of T.
   * @tparam P2 the third (Extractor-enabled) subtype of T.
   * @tparam P3 the fourth (Extractor-enabled) subtype of T.
   * @return MultiExtractor of `Seq[T]`.
   */
  def multiExtractor4[T, U <: Product, P0 <: T : {Extractor, ClassTag}, P1 <: T : {Extractor, ClassTag}, P2 <: T : {Extractor, ClassTag}, P3 <: T : {Extractor, ClassTag}](construct: (P0, P1, P2, P3) => U, labels: Seq[String]): MultiExtractor[Seq[T]] =
    nodeSeq =>
      labels match {
        case label :: fs =>
          val p0sy = sequence(extractElementsByLabel[P0](nodeSeq, label))
          val tsy = multiExtractor3[T, (P1, P2, P3), P1, P2, P3]((p1, p2, p3) => (p1, p2, p3), fs).extract(nodeSeq)
          for (ts1 <- tsy; ts2 <- p0sy) yield ts1 ++ ts2
      }

  /**
   * Method to yield a MultiExtractor of `Seq[T]` such that T is the super-type of five P-types.
   *
   * @param construct a function whose sole purpose is to enable type inference (construct is never referenced in the code).
   * @param labels    the labels of the elements we wish to extract. These must be in the same sequence as the corresponding P-types.
   * @tparam T  the ultimate underlying type of the resulting MultiExtractor.
   * @tparam U  a tuple whose only purpose is type inference.
   * @tparam P0 the first subtype of `T`, for which there must be implicit evidence of `Extractor[P0]` and `ClassTag[P0]`.
   * @tparam P1 the second (Extractor-enabled) subtype of T.
   * @tparam P2 the third (Extractor-enabled) subtype of T.
   * @tparam P3 the fourth (Extractor-enabled) subtype of T.
   * @tparam P4 the fifth (Extractor-enabled) subtype of T.
   * @return MultiExtractor of `Seq[T]`.
   */
  def multiExtractor5[T, U <: Product, P0 <: T : {Extractor, ClassTag}, P1 <: T : {Extractor, ClassTag}, P2 <: T : {Extractor, ClassTag}, P3 <: T : {Extractor, ClassTag}, P4 <: T : {Extractor, ClassTag}](construct: (P0, P1, P2, P3, P4) => U, labels: Seq[String]): MultiExtractor[Seq[T]] =
    nodeSeq =>
      labels match {
        case label :: fs =>
          val p0sy = sequence(extractElementsByLabel[P0](nodeSeq, label))
          val tsy = multiExtractor4[T, (P1, P2, P3, P4), P1, P2, P3, P4]((p1, p2, p3, p4) => (p1, p2, p3, p4), fs).extract(nodeSeq)
          for (ts1 <- tsy; ts2 <- p0sy) yield ts1 ++ ts2
      }

  /**
   * Method to yield a MultiExtractor of `Seq[T]` such that T is the super-type of six P-types.
   *
   * @param construct a function whose sole purpose is to enable type inference (construct is never referenced in the code).
   * @param labels    the labels of the elements we wish to extract. These must be in the same sequence as the corresponding P-types.
   * @tparam T  the ultimate underlying type of the resulting MultiExtractor.
   * @tparam U  a tuple whose only purpose is type inference.
   * @tparam P0 the first subtype of `T`, for which there must be implicit evidence of `Extractor[P0]` and `ClassTag[P0]`.
   * @tparam P1 the second (Extractor-enabled) subtype of T.
   * @tparam P2 the third (Extractor-enabled) subtype of T.
   * @tparam P3 the fourth (Extractor-enabled) subtype of T.
   * @tparam P4 the fifth (Extractor-enabled) subtype of T.
   * @tparam P5 the sixth (Extractor-enabled) subtype of T.
   * @return MultiExtractor of `Seq[T]`.
   */
  def multiExtractor6[T, U <: Product, P0 <: T : {Extractor, ClassTag}, P1 <: T : {Extractor, ClassTag}, P2 <: T : {Extractor, ClassTag}, P3 <: T : {Extractor, ClassTag}, P4 <: T : {Extractor, ClassTag}, P5 <: T : {Extractor, ClassTag}](construct: (P0, P1, P2, P3, P4, P5) => U, labels: Seq[String]): MultiExtractor[Seq[T]] =
    nodeSeq =>
      labels match {
        case label :: fs =>
          val p0sy = sequence(extractElementsByLabel[P0](nodeSeq, label))
          val tsy = multiExtractor5[T, (P1, P2, P3, P4, P5), P1, P2, P3, P4, P5]((p1, p2, p3, p4, p5) => (p1, p2, p3, p4, p5), fs).extract(nodeSeq)
          for (ts1 <- tsy; ts2 <- p0sy) yield ts1 ++ ts2
      }

  /**
   * Method to yield an Extractor[T] where we have an Extractor[B => T].
   * This will occur when we have a case class with an additional parameter set
   * including one parameter of type B.
   *
   * CONSIDER rename this (compose? or extractorCompose?) and couldn't it go inside Extractor?
   *
   * CONSIDER inverting the parametric types so that the resulting type (T) is first.
   *
   * @param extractorBtoT an extractor for the type B => T.
   * @tparam B the type of the value in the additional parameter set of T,
   *           for which there must be evidence of Extractor[B].
   * @tparam T the underlying type of the resulting extractor.
   * @return an Extractor[T] whose method extract will convert a Node into a T.
   */
  def extractorPartial[B <: Product : Extractor, T: ClassTag](extractorBtoT: Extractor[B => T]): Extractor[T] =
    (node: Node) =>
      for {
        b2te <- tryNotNull(extractorBtoT)(s"extractorPartial: extractorBtoT where T is ${implicitly[ClassTag[T]]}")
        be <- tryNotNull(implicitly[Extractor[B]])(s"extractorPartial: extractorB")
        q <- b2te.extract(node)
        b <- be.extract(node)
      } yield q(b)

  /**
   * Extractor which will convert an Xml Node (which is ignored) into an instance of a case object or case class.
   * NOTE that you will have to specify a lambda for the construct function.
   *
   * @param construct a function () => T, usually the apply method of a case object or zero-member case class.
   * @tparam T the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will construct a T while ignoring the input Node.
   */
  def extractor0[T: ClassTag](construct: Unit => T): Extractor[T] = Extractor(Success(construct(())))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with zero members and one auxiliary (non-member) parameter.
   *
   * NOTE: the construct function will have to be an explicitly declared function of the form: B => T.
   * This is because the compiler gets a little confused, otherwise.
   *
   * @param construct a function B => T, an explicitly declared function.
   * @tparam B the type of the value in the additional parameter set of T.
   * @tparam T the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  def extractorPartial0[B, T <: Product : ClassTag](construct: B => T): Extractor[B => T] = Extractor(Success(construct))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with one member.
   *
   * NOTE: this specific extractor provides logging.
   * CONSIDER adding logging to the other extractors.
   *
   * @param construct a function (P0) => T, usually the apply method of a case class.
   * @tparam P0 the (Extractor-enabled) type of the first (only) member of the Product type T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor10[P0: Extractor, T <: Product : ClassTag](construct: P0 => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(extractorPartial1[P0, Unit, T](fTagToFieldExtractor, e0 => _ => construct(e0), dropLast = false, fields).extract(node))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with one member.
   *
   * @param construct a function (P0) => T, usually the apply method of a case class.
   * @tparam P0 the (MultiExtractor-enabled) type of the first (only) member of the Product type T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor01[P0: MultiExtractor, T <: Product : ClassTag](construct: P0 => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(extractorPartial1[P0, Unit, T](childrenExtractor[P0], m0 => _ => construct(m0), dropLast = false, fields).extract(node))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with one member and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0) => B => T, usually the apply method of a case class.
   * @tparam P0 the (Extractor-enabled) type of the first (only) member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  def extractorPartial10[P0: Extractor, B, T <: Product : ClassTag](construct: P0 => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial1(fTagToFieldExtractor, construct, dropLast = true, fields).extract(node)

  /**
   * Creates an extractor that constructs an instance of type T from a node in an abstract syntax tree.
   *
   * @param construct a function that takes an extracted parameter of type P0 and returns a function
   *                  that takes an argument of type B and produces an instance of type T
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the type of the extracted input that serves as the first parameter for construct
   * @tparam B  the input type required to produce the final result of type T
   * @tparam T  the resulting type produced by the extractor
   * @return an extractor function that takes a Node and returns a function of type B => T
   */
  def extractorPartial01[P0: MultiExtractor, B, T <: Product : ClassTag](construct: P0 => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial1[P0, B, T](
        childrenExtractor,
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with two members.
   *
   * @param construct a function (P0,P1) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with two members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor20[P0: Extractor, P1: Extractor, T <: Product : ClassTag](construct: (P0, P1) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial2[P0, P1, Unit, T](
          fTagToFieldExtractor,
          extractorPartial10(_, _),
          (e0, e1) => _ => construct(e0, e1),
          dropLast = false,
          fields
        ).extract(node))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with two members.
   *
   * @param construct a function (P0,P1) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor11[P0: Extractor, P1: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial2[P0, P1, Unit, T](
          fTagToFieldExtractor,
          extractorPartial01(_, _),
          (e0, e1) => _ => construct(e0, e1),
          dropLast = false,
          fields
        ).extract(node))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with two members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor02[P0: MultiExtractor, P1: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial2[P0, P1, Unit, T](
          childrenExtractor,
          extractorPartial01(_, _),
          (m0, m1) => _ => construct(m0, m1),
          dropLast = false,
          fields
        ).extract(node))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with two members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  def extractorPartial20[P0: Extractor, P1: Extractor, B, T <: Product : ClassTag](construct: (P0, P1) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial2[P0, P1, B, T](
        fTagToFieldExtractor,
        extractorPartial10(_, _),
        construct,
        dropLast = true,
        fields).
        extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with two members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  def extractorPartial11[P0: Extractor, P1: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial2[P0, P1, B, T](
        fTagToFieldExtractor,
        extractorPartial01(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with two members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P1, P2) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial02[P0: MultiExtractor, P1: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial2[P0, P1, B, T](
        childrenExtractor,
        extractorPartial01(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members.
   *
   * CONSIDER re-implement all similar extractors using extractorPartial30, etc.
   * This can be done but requires turning dropLast to false when invoking extractorPartial30 (which doesn't currently have such a parameter).
   * See the commented code for how this should be done (but with the change mentioned).
   *
   * @param construct a function (P0,P1,P2) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with three members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor30[P0: Extractor, P1: Extractor, P2: Extractor, T <: Product : ClassTag](construct: (P0, P1, P2) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial3[P0, P1, P2, Unit, T](
          fTagToFieldExtractor,
          extractorPartial20(_, _),
          (e0, e1, e2) => _ => construct(e0, e1, e2),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members.
   *
   * @param construct a function (P0,P1,P2) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with three members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor21[P0: Extractor, P1: Extractor, P2: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial3[P0, P1, P2, Unit, T](
          fTagToFieldExtractor,
          extractorPartial11(_, _),
          (e0, e1, e2) => _ => construct(e0, e1, e2),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with three members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor12[P0: Extractor, P1: MultiExtractor, P2: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial3[P0, P1, P2, Unit, T](
          fTagToFieldExtractor,
          extractorPartial02(_, _),
          (e0, e1, e2) => _ => construct(e0, e1, e2),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with three members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor03[P0: MultiExtractor, P1: MultiExtractor, P2: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial3[P0, P1, P2, Unit, T](
          childrenExtractor,
          extractorPartial02(_, _),
          (e0, e1, e2) => _ => construct(e0, e1, e2),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  def extractorPartial30[P0: Extractor, P1: Extractor, P2: Extractor, B, T <: Product : ClassTag](construct: (P0, P1, P2) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial3[P0, P1, P2, B, T](
        fTagToFieldExtractor,
        extractorPartial20(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  def extractorPartial21[P0: Extractor, P1: Extractor, P2: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial3[P0, P1, P2, B, T](
        fTagToFieldExtractor,
        extractorPartial11(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial12[P0: Extractor, P1: MultiExtractor, P2: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial3[P0, P1, P2, B, T](
        fTagToFieldExtractor,
        extractorPartial02(_, _),
        construct,
        dropLast = true, fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial03[P0: MultiExtractor, P1: MultiExtractor, P2: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial3[P0, P1, P2, B, T](
        childrenExtractor,
        extractorPartial02(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members.
   *
   * @param construct a function (P0,P1,P2,P3) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with four members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor40[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial4[P0, P1, P2, P3, Unit, T](
          fTagToFieldExtractor,
          extractorPartial30(_, _),
          (p0, p1, p2, p3) => _ => construct(p0, p1, p2, p3),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with four members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor31[P0: Extractor, P1: Extractor, P2: Extractor, P3: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial4[P0, P1, P2, P3, Unit, T](
          fTagToFieldExtractor,
          extractorPartial21(_, _),
          (p0, p1, p2, p3) => _ => construct(p0, p1, p2, p3),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with four members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor22[P0: Extractor, P1: Extractor, P2: MultiExtractor, P3: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial4[P0, P1, P2, P3, Unit, T](
          fTagToFieldExtractor,
          extractorPartial12(_, _),
          (p0, p1, p2, p3) => _ => construct(p0, p1, p2, p3),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with four members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor13[P0: Extractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial4[P0, P1, P2, P3, Unit, T](
          fTagToFieldExtractor,
          extractorPartial03(_, _),
          (p0, p1, p2, p3) => _ => construct(p0, p1, p2, p3),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with four members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor04[P0: MultiExtractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial4[P0, P1, P2, P3, Unit, T](
          childrenExtractor,
          extractorPartial03(_, _),
          (p0, p1, p2, p3) => _ => construct(p0, p1, p2, p3),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  def extractorPartial40[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial4[P0, P1, P2, P3, B, T](
        fTagToFieldExtractor,
        extractorPartial30(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial31[P0: Extractor, P1: Extractor, P2: Extractor, P3: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial4[P0, P1, P2, P3, B, T](
        fTagToFieldExtractor,
        extractorPartial21(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial22[P0: Extractor, P1: Extractor, P2: MultiExtractor, P3: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial4[P0, P1, P2, P3, B, T](
        fTagToFieldExtractor,
        extractorPartial12(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial13[P0: Extractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial4[P0, P1, P2, P3, B, T](
        fTagToFieldExtractor,
        extractorPartial03(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial04[P0: MultiExtractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial4[P0, P1, P2, P3, B, T](
        childrenExtractor,
        extractorPartial03(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members.
   *
   * @param construct a function (P0,P1,P2,P3,P4) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with five members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor50[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial5[P0, P1, P2, P3, P4, Unit, T](
          fTagToFieldExtractor,
          extractorPartial40(_, _),
          (p0, p1, p2, p3, p4) => _ => construct(p0, p1, p2, p3, p4),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with five members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor41[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial5[P0, P1, P2, P3, P4, Unit, T](
          fTagToFieldExtractor,
          extractorPartial31(_, _),
          (p0, p1, p2, p3, p4) => _ => construct(p0, p1, p2, p3, p4),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members.
   *
   * @param construct a function (P0,P1,P2,P3,P4) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with five members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor32[P0: Extractor, P1: Extractor, P2: Extractor, P3: MultiExtractor, P4: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial5[P0, P1, P2, P3, P4, Unit, T](
          fTagToFieldExtractor, extractorPartial22(_, _),
          (p0, p1, p2, p3, p4) => _ => construct(p0, p1, p2, p3, p4),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with five members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor23[P0: Extractor, P1: Extractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial5[P0, P1, P2, P3, P4, Unit, T](
          fTagToFieldExtractor,
          extractorPartial13(_, _),
          (p0, p1, p2, p3, p4) => _ => construct(p0, p1, p2, p3, p4),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with five members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor14[P0: Extractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial5[P0, P1, P2, P3, P4, Unit, T](
          fTagToFieldExtractor,
          extractorPartial04(_, _),
          (p0, p1, p2, p3, p4) => _ => construct(p0, p1, p2, p3, p4),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with five members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor05[P0: MultiExtractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial5[P0, P1, P2, P3, P4, Unit, T](
          childrenExtractor,
          extractorPartial04(_, _),
          (p0, p1, p2, p3, p4) => _ => construct(p0, p1, p2, p3, p4),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3, P4) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam B  the type of the value in the additional parameter set of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractorPartial50[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial5[P0, P1, P2, P3, P4, B, T](
        fTagToFieldExtractor,
        extractorPartial40(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3, P4) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial41[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial5[P0, P1, P2, P3, P4, B, T](
        fTagToFieldExtractor,
        extractorPartial31(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3, P4) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial32[P0: Extractor, P1: Extractor, P2: Extractor, P3: MultiExtractor, P4: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial5[P0, P1, P2, P3, P4, B, T](
        fTagToFieldExtractor,
        extractorPartial22(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3, P4) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial23[P0: Extractor, P1: Extractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial5[P0, P1, P2, P3, P4, B, T](
        fTagToFieldExtractor,
        extractorPartial13(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3, P4) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial14[P0: Extractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial5[P0, P1, P2, P3, P4, B, T](
        fTagToFieldExtractor,
        extractorPartial04(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3, P4) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial05[P0: MultiExtractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial5[P0, P1, P2, P3, P4, B, T](
        childrenExtractor,
        extractorPartial04(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4,P5) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (Extractor-enabled) type of the sixth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with six members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor60[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, P5: Extractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial6[P0, P1, P2, P3, P4, P5, Unit, T](
          fTagToFieldExtractor,
          extractorPartial50(_, _),
          (p0, p1, p2, p3, p4, p5) => _ => construct(p0, p1, p2, p3, p4, p5),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4,P5) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with six members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor51[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, P5: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial6[P0, P1, P2, P3, P4, P5, Unit, T](
          fTagToFieldExtractor,
          extractorPartial41(_, _),
          (p0, p1, p2, p3, p4, p5) => _ => construct(p0, p1, p2, p3, p4, p5),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4,P5) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with six members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor42[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: MultiExtractor, P5: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial6[P0, P1, P2, P3, P4, P5, Unit, T](
          fTagToFieldExtractor,
          extractorPartial32(_, _),
          (p0, p1, p2, p3, p4, p5) => _ => construct(p0, p1, p2, p3, p4, p5),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members.
   *
   * @param construct a function (P0,P1,P2,P3,P4,P5) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with six members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor33[P0: Extractor, P1: Extractor, P2: Extractor, P3: MultiExtractor, P4: MultiExtractor, P5: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial6[P0, P1, P2, P3, P4, P5, Unit, T](
          fTagToFieldExtractor,
          extractorPartial23(_, _),
          (p0, p1, p2, p3, p4, p5) => _ => construct(p0, p1, p2, p3, p4, p5),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4,P5) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with six members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor24[P0: Extractor, P1: Extractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, P5: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial6[P0, P1, P2, P3, P4, P5, Unit, T](
          fTagToFieldExtractor,
          extractorPartial14(_, _),
          (p0, p1, p2, p3, p4, p5) => _ => construct(p0, p1, p2, p3, p4, p5),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4,P5) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with six members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor15[P0: Extractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, P5: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(
        extractorPartial6[P0, P1, P2, P3, P4, P5, Unit, T](
          fTagToFieldExtractor,
          extractorPartial05(_, _),
          (p0, p1, p2, p3, p4, p5) => _ => construct(p0, p1, p2, p3, p4, p5),
          dropLast = false,
          fields
        ).extract(node)
      )

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members.
   *
   * TESTME
   *
   * @param construct a function (P0,P1,P2,P3,P4,P5) => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (MultiExtractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (MultiExtractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (MultiExtractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (MultiExtractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam T  the underlying type of the result, a Product with five members.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractor06[P0: MultiExtractor, P1: MultiExtractor, P2: MultiExtractor, P3: MultiExtractor, P4: MultiExtractor, P5: MultiExtractor, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => T, fields: Seq[String] = Nil): Extractor[T] =
    (node: Node) =>
      applyWrappedToUnit(extractorPartial6[P0, P1, P2, P3, P4, P5, Unit, T](
        childrenExtractor,
        extractorPartial05(_, _),
        (p0, p1, p2, p3, p4, p5) => _ => construct(p0, p1, p2, p3, p4, p5),
        dropLast = false,
        fields
      ).extract(node))

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (Extractor-enabled) type of the sixth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial60[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, P5: Extractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial6[P0, P1, P2, P3, P4, P5, B, T](
        fTagToFieldExtractor,
        extractorPartial50(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial51[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, P5: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial6[P0, P1, P2, P3, P4, P5, B, T](
        fTagToFieldExtractor,
        extractorPartial41(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (MultiExtractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  private def extractorPartial42[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: MultiExtractor, P5: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial6[P0, P1, P2, P3, P4, P5, B, T](
        fTagToFieldExtractor,
        extractorPartial32(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with seven members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5, P6) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (Extractor-enabled) type of the sixth member of the Product type T.
   * @tparam P6 the (Extractor-enabled) type of the seventh member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractorPartial70[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, P5: Extractor, P6: Extractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5, P6) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial7[P0, P1, P2, P3, P4, P5, P6, B, T](
        fTagToFieldExtractor,
        extractorPartial60(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with seven members and one auxiliary (non-member) parameter.
   *
   * TESTME
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5, P6) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (Extractor-enabled) type of the sixth member of the Product type T.
   * @tparam P6 the (MultiExtractor-enabled) type of the seventh member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractorPartial61[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, P5: Extractor, P6: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5, P6) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial7[P0, P1, P2, P3, P4, P5, P6, B, T](
        fTagToFieldExtractor,
        extractorPartial51(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with seven members and one auxiliary (non-member) parameter.
   *
   * @param construct a function (P0, P1, P2, P3, P4, P5, P6) => B => T, usually the apply method of a case class.
   * @param fields    an optional sequence of field names to guide the extraction process,
   *                  defaults to an empty sequence
   * @tparam P0 the (Extractor-enabled) type of the first member of the Product type T.
   * @tparam P1 the (Extractor-enabled) type of the second member of the Product type T.
   * @tparam P2 the (Extractor-enabled) type of the third member of the Product type T.
   * @tparam P3 the (Extractor-enabled) type of the fourth member of the Product type T.
   * @tparam P4 the (Extractor-enabled) type of the fifth member of the Product type T.
   * @tparam P5 the (MultiExtractor-enabled) type of the sixth member of the Product type T.
   * @tparam P6 the (MultiExtractor-enabled) type of the seventh member of the Product type T.
   * @tparam B  the type of the non-member parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[T] whose method extract will convert a Node into a Try[T].
   */
  def extractorPartial52[P0: Extractor, P1: Extractor, P2: Extractor, P3: Extractor, P4: Extractor, P5: MultiExtractor, P6: MultiExtractor, B, T <: Product : ClassTag](construct: (P0, P1, P2, P3, P4, P5, P6) => B => T, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      extractorPartial7[P0, P1, P2, P3, P4, P5, P6, B, T](
        fTagToFieldExtractor,
        extractorPartial42(_, _),
        construct,
        dropLast = true,
        fields
      ).extract(node)

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with one member plus
   * an "auxiliary" parameter of type B, declared in its own parameter set.
   *
   * @param fExtractor the extractor of the first (child) element.
   * @param construct  a function P0 => T, usually the apply method of a case class.
   * @param dropLast   if true, then we drop the last declared field (used when T has an auxiliary member)
   * @param fields     a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam P the (Extractor-enabled) type of the first (only) member of the Product type T.
   * @tparam B the type of the auxiliary parameter of T.
   * @tparam T the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial1[P, B, T <: Product : ClassTag](fExtractor: TagToExtractorFunc[P], construct: P => B => T, dropLast: Boolean, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      fieldNamesMaybeDropLast(fields, dropLast) match {
        case member :: Nil => for {
          e0 <- fExtractor(member).extract(node)
        } yield construct(e0)
        case fs => Failure(XmlException(s"extractor1: non-unique field name: $fs"))
      }

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with two members plus
   * an "auxiliary" parameter of type B, declared in its own parameter set.
   *
   * @param fExtractor              the extractor for the first member to yield its (child) element.
   * @param nestedExtractorFunction a function which is used to create an Extractor for the remaining members.
   * @param construct               a function (P0, P1) => B => T, usually the apply method of a case class.
   * @param dropLast                if true, then we drop the last declared field (used when T has an auxiliary member)
   * @param fields                  a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam P0 the type of the first member of the Product type T.
   * @tparam P1 the type of the second member of the Product type T.
   * @tparam B  the type of the auxiliary parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial2[P0, P1, B, T <: Product : ClassTag](fExtractor: TagToExtractorFunc[P0], nestedExtractorFunction: (P1 => B => T, List[String]) => Extractor[B => T], construct: (P0, P1) => B => T, dropLast: Boolean, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      fieldNamesMaybeDropLast(fields, dropLast) match {
        case member0 :: fs =>
          for {
            e0 <- fExtractor(member0).extract(node)
            t <- nestedExtractorFunction(construct.curried(e0), fs).extract(node)
          } yield t
        case _ => Failure(XmlException(s"extractorPartial2: logic error"))
      }

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with three members plus
   * an "auxiliary" parameter of type B, declared in its own parameter set.
   *
   * @param fExtractor              the extractor for the first member to yield its (child) element.
   * @param nestedExtractorFunction a function which is used to create an Extractor for the remaining members.
   * @param construct               a function (P0, P1, P2) => B => T, usually the apply method of a case class.
   * @param dropLast                if true, then we drop the last declared field (used when T has an auxiliary member)
   * @param fields                  a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam P0 the type of the first member of the Product type T.
   * @tparam P1 the type of the second member of the Product type T.
   * @tparam P2 the type of the third member of the Product type T.
   * @tparam B  the type of the auxiliary parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial3[P0, P1, P2, B, T <: Product : ClassTag](fExtractor: TagToExtractorFunc[P0], nestedExtractorFunction: ((P1, P2) => B => T, List[String]) => Extractor[B => T], construct: (P0, P1, P2) => B => T, dropLast: Boolean, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      fieldNamesMaybeDropLast(fields, dropLast) match {
        case member0 :: fs =>
          for {
            e0 <- fExtractor(member0).extract(node)
            t <- nestedExtractorFunction(uncurried(construct.curried(e0)), fs).extract(node)
          } yield t
        case _ => Failure(XmlException(s"extractorPartial3: logic error"))
      }

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with four members plus
   * an "auxiliary" parameter of type B, declared in its own parameter set.
   *
   * @param fExtractor              the extractor for the first member to yield its (child) element.
   * @param nestedExtractorFunction a function which is used to create an Extractor for the remaining members.
   * @param construct               a function (P0, P1, P2, P3) => B => T, usually the apply method of a case class.
   * @param dropLast                if true, then we drop the last declared field (used when T has an auxiliary member)
   * @param fields                  a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam P0 the type of the first member of the Product type T.
   * @tparam P1 the type of the second member of the Product type T.
   * @tparam P2 the type of the third member of the Product type T.
   * @tparam P3 the type of the fourth member of the Product type T.
   * @tparam B  the type of the auxiliary parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial4[P0, P1, P2, P3, B, T <: Product : ClassTag](fExtractor: TagToExtractorFunc[P0], nestedExtractorFunction: ((P1, P2, P3) => B => T, List[String]) => Extractor[B => T], construct: (P0, P1, P2, P3) => B => T, dropLast: Boolean, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      fieldNamesMaybeDropLast(fields, dropLast) match {
        case member0 :: fs =>
          for {
            e0 <- fExtractor(member0).extract(node)
            t <- nestedExtractorFunction(uncurried(construct.curried(e0)), fs).extract(node)
          } yield t
        case _ => Failure(XmlException(s"extractorPartial4: logic error"))
      }

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with five members plus
   * an "auxiliary" parameter of type B, declared in its own parameter set.
   *
   * @param fExtractor              the extractor for the first member to yield its (child) element.
   * @param nestedExtractorFunction a function which is used to create an Extractor for the remaining members.
   * @param construct               a function (P0, P1, P2, P3, P4) => B => T, usually the apply method of a case class.
   * @param dropLast                if true, then we drop the last declared field (used when T has an auxiliary member)
   * @param fields                  a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam P0 the type of the first member of the Product type T.
   * @tparam P1 the type of the second member of the Product type T.
   * @tparam P2 the type of the third member of the Product type T.
   * @tparam P3 the type of the fourth member of the Product type T.
   * @tparam P4 the type of the fifth member of the Product type T.
   * @tparam B  the type of the auxiliary parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial5[P0, P1, P2, P3, P4, B, T <: Product : ClassTag](fExtractor: TagToExtractorFunc[P0], nestedExtractorFunction: ((P1, P2, P3, P4) => B => T, List[String]) => Extractor[B => T], construct: (P0, P1, P2, P3, P4) => B => T, dropLast: Boolean, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      fieldNamesMaybeDropLast(fields, dropLast) match {
        case member0 :: fs =>
          for {
            e0 <- fExtractor(member0).extract(node)
            t <- nestedExtractorFunction(uncurried(construct.curried(e0)), fs).extract(node)
          } yield t
        case _ => Failure(XmlException(s"extractorPartial5: logic error"))
      }

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with six members plus
   * an "auxiliary" parameter of type B, declared in its own parameter set.
   *
   * @param fExtractor              the extractor for the first member to yield its (child) element.
   * @param nestedExtractorFunction a function which is used to create an Extractor for the remaining members.
   * @param construct               a function (P0, P1, P2, P3, P4, P5) => B => T, usually the apply method of a case class.
   * @param dropLast                if true, then we drop the last declared field (used when T has an auxiliary member)
   * @param fields                  a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam P0 the type of the first member of the Product type T.
   * @tparam P1 the type of the second member of the Product type T.
   * @tparam P2 the type of the third member of the Product type T.
   * @tparam P3 the type of the fourth member of the Product type T.
   * @tparam P4 the type of the fifth member of the Product type T.
   * @tparam P5 the type of the sixth member of the Product type T.
   * @tparam B  the type of the auxiliary parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial6[P0, P1, P2, P3, P4, P5, B, T <: Product : ClassTag](fExtractor: TagToExtractorFunc[P0], nestedExtractorFunction: ((P1, P2, P3, P4, P5) => B => T, List[String]) => Extractor[B => T], construct: (P0, P1, P2, P3, P4, P5) => B => T, dropLast: Boolean, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      fieldNamesMaybeDropLast(fields, dropLast) match {
        case member0 :: fs =>
          for {
            e0 <- fExtractor(member0).extract(node)
            t <- nestedExtractorFunction(uncurried(construct.curried(e0)), fs).extract(node)
          } yield t
        case _ => Failure(XmlException(s"extractorPartial6: logic error"))
      }

  /**
   * Extractor which will convert an Xml Node into an instance of a case class with seven members plus
   * an "auxiliary" parameter of type B, declared in its own parameter set.
   *
   * @param fExtractor              the extractor for the first member to yield its (child) element.
   * @param nestedExtractorFunction a function which is used to create an Extractor for the remaining members.
   * @param construct               a function (P0, P1, P2, P3, P4, P5, P6) => B => T, usually the apply method of a case class.
   * @param dropLast                if true, then we drop the last declared field (used when T has an auxiliary member)
   * @param fields                  a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam P0 the type of the first member of the Product type T.
   * @tparam P1 the type of the second member of the Product type T.
   * @tparam P2 the type of the third member of the Product type T.
   * @tparam P3 the type of the fourth member of the Product type T.
   * @tparam P4 the type of the fifth member of the Product type T.
   * @tparam P5 the type of the sixth member of the Product type T.
   * @tparam P6 the type of the sixth member of the Product type T.
   * @tparam B  the type of the auxiliary parameter of T.
   * @tparam T  the underlying type of the result, a Product.
   * @return an Extractor[B => T] whose method extract will convert a Node into a Try[B => T].
   */
  private def extractorPartial7[P0, P1, P2, P3, P4, P5, P6, B, T <: Product : ClassTag](fExtractor: TagToExtractorFunc[P0], nestedExtractorFunction: ((P1, P2, P3, P4, P5, P6) => B => T, List[String]) => Extractor[B => T], construct: (P0, P1, P2, P3, P4, P5, P6) => B => T, dropLast: Boolean, fields: Seq[String] = Nil): Extractor[B => T] =
    (node: Node) =>
      fieldNamesMaybeDropLast(fields, dropLast) match {
        case member0 :: fs =>
          for {
            e0 <- fExtractor(member0).extract(node)
            t <- nestedExtractorFunction(FP.uncurried(construct.curried(e0)), fs).extract(node)
          } yield t
        case _ => Failure(XmlException(s"extractorPartial7: logic error"))
      }

  /**
   * Method to yield an TagToExtractorFunc[P] which in turns invokes fieldExtractor with the given tag.
   *
   * @tparam P the underlying (Extractor-enabled) type of the result.
   * @return an TagToExtractorFunc[P] based on fieldExtractor.
   */
  private def fTagToFieldExtractor[P: Extractor]: TagToExtractorFunc[P] = (tag: String) => fieldExtractor[P](tag)

  /**
   * Method to yield an TagToExtractorFunc[P] which in turns invokes extractChildrenDeprecated with the given tag.
   *
   * @tparam P the underlying (MultiExtractor-enabled) type of the result.
   * @return an TagToExtractorFunc[P] based on fieldExtractor.
   */
  private def childrenExtractor[P: MultiExtractor]: TagToExtractorFunc[P] = (tag: String) => extractChildren[P](tag)(_)

  /**
   * Method to yield an Extractor of `Seq[T]` where each result arises from one of the labels (tags) given.
   *
   * TESTME
   *
   * @param tag    this is a pseudo-tag--not actually present in any XML element but corresponds to a case class member.
   * @param labels the labels of the elements we wish to extract (wrapped in Seq).
   * @tparam T the ultimate underlying type of the resulting MultiExtractor.
   * @return Extractor of `Seq[T]`.
   */
  private def seqExtractorByTag[T](tag: String, labels: Seq[String])(implicit multiExtractor: MultiExtractor[Seq[T]]): Extractor[Seq[T]] = new TagToSequenceExtractorFunc[T] {

    /**
     * Method to yield an Extractor[T], given a label.
     *
     * @param w a String which must match the given tag in order to return a valid Extractor.
     * @return an Extractor[T].
     */
    def apply(w: String): Extractor[Seq[T]] = if (w == tag) {
      println(s"seqExtractorByTag: $w")
      node => {
        val ws: Seq[Try[Seq[T]]] = for (label <- labels) yield multiExtractor.extract(node \ label)
        sequence(ws) map (tss => tss.flatten)
      }
    }
    else (_: Node) => Success(Nil)

    val tags: Seq[String] = labels
    val tsm: MultiExtractor[Seq[T]] = multiExtractor
    val pseudo: String = tag

  }.apply(tag)

  //======================= The following are unused=======================================//

  /**
   * Method to yield an Extractor which can choose from three other extractors.
   * Why is this method not called extractorAlt3? Because I know my Latin ;)
   *
   * @tparam R  result type.
   * @tparam P0 first extractor type.
   * @tparam P1 second extractor type.
   * @tparam P2 third extractor type.
   * @return an Extractor[R].
   */
  def extractorAlia3[R, P0 <: R : Extractor, P1 <: R : Extractor, P2 <: R : Extractor]: Extractor[R] =
    none[R].|[P0]().|[P1]().|[P2]()

  /**
   * Method to yield an Extractor which can choose from four other extractors.
   *
   * @tparam R  result type.
   * @tparam P0 first extractor type.
   *            Required: implicit evidence of type Extractor[P0].
   * @tparam P1 second extractor type.
   *            Required: implicit evidence of type Extractor[P1].
   * @tparam P2 third extractor type.
   *            Required: implicit evidence of type Extractor[P2].
   * @tparam P3 fourth extractor type.
   *            Required: implicit evidence of type Extractor[P3].
   * @return an Extractor[R].
   */
  def extractorAlia4[R, P0 <: R : Extractor, P1 <: R : Extractor, P2 <: R : Extractor, P3 <: R : Extractor]: Extractor[R] =
    none[R].|[P0]().|[P1]().|[P2]().|[P3]()

  /**
   * Method to yield an Extractor which can choose from five other extractors.
   *
   * @tparam R  result type.
   * @tparam P0 first extractor type.
   *            Required: implicit evidence of type Extractor[P0].
   * @tparam P1 second extractor type.
   *            Required: implicit evidence of type Extractor[P1].
   * @tparam P2 third extractor type.
   *            Required: implicit evidence of type Extractor[P2].
   * @tparam P3 fourth extractor type.
   *            Required: implicit evidence of type Extractor[P3].
   * @tparam P4 fifth extractor type.
   *            Required: implicit evidence of type Extractor[P4].
   * @return an Extractor[R].
   */
  def extractorAlia5[R, P0 <: R : Extractor, P1 <: R : Extractor, P2 <: R : Extractor, P3 <: R : Extractor, P4 <: R : Extractor]: Extractor[R] =
    none[R].|[P0]().|[P1]().|[P2]().|[P3]().|[P4]()

  /**
   * Method to yield an Extractor which can choose from six other extractors.
   *
   * @tparam R  result type.
   * @tparam P0 first extractor type.
   *            Required: implicit evidence of type Extractor[P0].
   * @tparam P1 second extractor type.
   *            Required: implicit evidence of type Extractor[P1].
   * @tparam P2 third extractor type.
   *            Required: implicit evidence of type Extractor[P2].
   * @tparam P3 fourth extractor type.
   *            Required: implicit evidence of type Extractor[P3].
   * @tparam P4 fifth extractor type.
   *            Required: implicit evidence of type Extractor[P4].
   * @tparam P5 sixth extractor type.
   *            Required: implicit evidence of type Extractor[P5].
   * @return an Extractor[R].
   */
  def extractorAlia6[R, P0 <: R : Extractor, P1 <: R : Extractor, P2 <: R : Extractor, P3 <: R : Extractor, P4 <: R : Extractor, P5 <: R : Extractor]: Extractor[R] =
    none[R].|[P0]().|[P1]().|[P2]().|[P3]().|[P4]().|[P5]()

  /**
   * Applies the function wrapped within a Try to a unit value.
   *
   * @param fy a Try containing a function from Unit to T
   * @return a Try containing the result of applying the function to a unit value
   */
  private def applyWrappedToUnit[T](fy: Try[Unit => T]): Try[T] = fy map (_.apply(()))

}

/**
 * Companion object to Extractors.
 */
object Extractors extends Extractors {
  /**
   * Preparing the way for when we provide better logging for when things go wrong.
   */
  val flog: Flog = Flog[Extractors]

  /**
   * Int multi extractor.
   *
   * TESTME
   */
  implicit object IntMultiExtractor extends MultiExtractorBase[Int](0 to Int.MaxValue)

  /**
   * Boolean multi extractor.
   *
   * TESTME
   */
  implicit object BooleanMultiExtractor extends MultiExtractorBase[Boolean](0 to Int.MaxValue)

  /**
   * Double multi extractor.
   *
   * TESTME
   */
  implicit object DoubleMultiExtractor extends MultiExtractorBase[Double](0 to Int.MaxValue)

  /**
   * Long multi extractor.
   *
   * TESTME
   */
  implicit object LongMultiExtractor extends MultiExtractorBase[Long](0 to Int.MaxValue)

  /**
   * Optional Int extractor.
   */
  implicit val extractOptionalInt: Extractor[Option[Int]] = extractorOption

  /**
   * Optional Double extractor.
   */
  implicit val extractOptionalDouble: Extractor[Option[Double]] = extractorOption

  /**
   * Optional String extractor.
   */
  implicit val extractorOptionalString: Extractor[Option[String]] = extractorOption[CharSequence].flatMap(xo => Success(xo map (_.toString)))

  /**
   * Method to extract an optional value of type `P` from the provided `NodeSeq`.
   * If the `NodeSeq` is empty, it returns a `Success` containing `None` as `P`.
   * If the `NodeSeq` contains nodes, it uses the implicit `Extractor[P]` to attempt to extract the value.
   *
   * TODO this method is incorrect. Fix it.
   *
   * @param nodeSeq the sequence of XML nodes from which the extraction is performed.
   * @tparam P the type of form Option[T] extracted value.
   *           Requires implicit evidence of an `Extractor[P]`.
   * @return a `Try[P]` containing the extracted value or an error if extraction fails.
   */
  def extractOptional[P: Extractor](nodeSeq: NodeSeq): Try[P] =
    nodeSeq.headOption map extract[P] match { // ASP violation
      case Some(value) if nodeSeq.tail.isEmpty => value
      case Some(_) => Failure(XmlException(s"extractOptional: logic error: too many elements for optional value"))
      case None => Success(None.asInstanceOf[P])
    }

  /**
   * Method to create a sequence extractor for type T.
   *
   * TESTME: not currently used.
   *
   * @param fExtractor a `TagToSequenceExtractorFunc[T]`
   * @tparam T an iterable type.
   * @return a TagToExtractorFunc of `Seq[T]`
   */
  def tagToSequenceExtractorFunc[T <: Iterable[?]](fExtractor: TagToSequenceExtractorFunc[T]): TagToExtractorFunc[Seq[T]] =
    (label: String) => if (fExtractor.valid(label)) fExtractor(label) else none

  /**
   * Return the field names as Seq[String], from either the fields parameter or by reflection into T.
   * Note that fields takes precedence and ClassTag[T] is ignored if fields is used.
   *
   * TODO understand why this no longer seems to be used. It SHOULD be used. Sometimes we should be specifying false.
   *
   * @param fields a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @tparam T the type (typically a case class) from which we will use reflection to get the field names (referred to only if fields is Nil)
   * @return the field names to be used.
   */
  private def fieldNames[T: ClassTag](fields: Seq[String]) = Extractors.fieldNamesMaybeDropLast(fields)

  /**
   * Return the field names as Seq[String], from either the fields parameter or by reflection into T.
   * Note that fields takes precedence and ClassTag[T] is ignored if fields is used.
   *
   * @param fields   a list of field names which, if not empty, is to be used instead of the reflected fields of T (defaults to Nil).
   * @param dropLast true if we should drop the last field from the result of Reflection.extractFieldNames.
   *                 this will occur when we have an auxiliary parameter in our case class.
   * @tparam T the type (typically a case class) from which we will use reflection to get the field names (referred to only if fields is Nil).
   * @return the field names to be used.
   */
  private def fieldNamesMaybeDropLast[T: ClassTag](fields: Seq[String], dropLast: Boolean = false) =
    fields match {
      case Nil =>
        val result = Reflection.extractFieldNames(implicitly[ClassTag[T]], dropLast).toList
        if (dropLast) result.init else result
      case ps => ps
    }
}
