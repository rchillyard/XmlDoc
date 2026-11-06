package com.phasmidsoftware.xmldoc.xml

import com.phasmidsoftware.xmldoc.core.Derivation
import com.phasmidsoftware.xmldoc.core.FP.sequence

import scala.annotation.tailrec
import scala.quoted.*
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

/**
 * How one field of a case class is extracted from a `Node`, given the field's name.
 * A field whose type is a `Seq` is extracted from child elements (with a `MultiExtractor`);
 * any other field, from an attribute, the text, or a child element, according to its name (with an `Extractor`).
 * See `Extractor.fieldExtractor` and `Extractor.extractChildren`.
 */
sealed trait FieldExtractor {
  /**
   * Method to extract the value of a field.
   *
   * @param field the name of the field.
   * @param node  the node of the element to which the field belongs.
   * @return the value of the field, wrapped in `Try`.
   */
  def extract(field: String, node: Node): Try[Any]
}

object FieldExtractor {

  /**
   * A field which is extracted by an `Extractor` (see `Extractor.fieldExtractor`).
   *
   * @param e (by name) the `Extractor` for the field's type.
   * @tparam P the field's type.
   * @return a `FieldExtractor`.
   */
  def single[P](e: => Extractor[P]): FieldExtractor = new FieldExtractor {
    private lazy val pe = e

    def extract(field: String, node: Node): Try[Any] = Extractor.fieldExtractor[P](field)(using pe).extract(node)
  }

  /**
   * A field which is extracted from child elements by a `MultiExtractor` (see `Extractor.extractChildren`).
   *
   * @param m (by name) the `MultiExtractor` for the field's type.
   * @tparam P the field's type (a `Seq`).
   * @return a `FieldExtractor`.
   */
  def multi[P](m: => MultiExtractor[P]): FieldExtractor = new FieldExtractor {
    private lazy val pm = m

    def extract(field: String, node: Node): Try[Any] = Extractor.extractChildren[P](field)(node)(using pm)
  }
}

/**
 * The `Extractor` of a case class `T`, as derived by `Extractor.derived`.
 *
 * NOTE: the field extractors (and the auxiliary extractor) are passed by name and evaluated only on the first extraction,
 * so that mutually recursive types (e.g. `Geometry` and `MultiGeometry`) need no `createLazy`.
 *
 * @param typeName   the name of `T` (used in messages).
 * @param fieldNames the names of the fields of the first parameter list of `T`, in declaration order.
 * @param fields     (by name) how each field is extracted, in the same order.
 * @param aux        (by name) the `Extractor` of the auxiliary parameter of `T`, if any: it is extracted from the same node.
 * @param construct  a function to construct a `T` from the values of its fields and its auxiliary parameter.
 * @tparam T the type of the result.
 */
class ProductExtractor[T](typeName: String, fieldNames: List[String], fields: => List[FieldExtractor], aux: => Option[Extractor[?]], construct: (Seq[Any], Option[Any]) => T) extends Extractor[T] {

  private lazy val namedFields: List[(String, FieldExtractor)] = fieldNames zip fields
  private lazy val maybeAux: Option[Extractor[?]] = aux

  def extract(node: Node): Try[T] = {
    // The fields are extracted in order, stopping at the first failure.
    @tailrec
    def inner(fs: List[(String, FieldExtractor)], xs: List[Any]): Try[List[Any]] = fs match {
      case Nil => Success(xs.reverse)
      case (f, fe) :: tail => fe.extract(f, node) match {
        case Success(x) => inner(tail, x :: xs)
        case Failure(e) => Failure(e)
      }
    }

    for {
      xs <- inner(namedFields, Nil)
      ao <- maybeAux match {
        case Some(e) => e.extract(node) map (Some(_))
        case None => Success(None)
      }
    } yield construct(xs, ao)
  }

  override def toString: String = s"derived Extractor[$typeName]"
}

/**
 * The `MultiExtractor` of `Seq[T]`, where `T` is sealed, as derived by `MultiExtractor.derived`.
 * Each element of the `NodeSeq` whose label is the name of a (case class) subtype of `T` is extracted by that subtype's `Extractor`;
 * the others are ignored. Document order is preserved.
 *
 * @param typeName the name of `T` (used in messages).
 * @param cases    (by name) the tag and `Extractor` of each subtype of `T`.
 * @tparam T the (sealed) super-type.
 */
class SumMultiExtractor[T](typeName: String, cases: => List[(String, Extractor[? <: T])]) extends MultiExtractor[Seq[T]] {

  private lazy val lookup: Map[String, Extractor[? <: T]] = cases.toMap

  def extract(nodeSeq: NodeSeq): Try[Seq[T]] =
    sequence(for (node <- nodeSeq if lookup.contains(node.label)) yield lookup(node.label).extract(node).map(t => t: T))

  override def toString: String = s"derived MultiExtractor[Seq[$typeName]]"
}

/**
 * The macros behind `Extractor.derived` and `MultiExtractor.derived`.
 */
object DerivedExtractors {

  /**
   * Implementation of `Extractor.derived`.
   *
   * @tparam T a case class.
   * @return an expression for the derived `Extractor[T]`.
   */
  def productImpl[T: Type](using q: Quotes): Expr[Extractor[T]] = {
    import q.reflect.*
    if (Derivation.isSealed[T])
      report.errorAndAbort(s"Extractor.derived: ${Type.show[T]} is sealed: use MultiExtractor.derived to get a MultiExtractor[Seq[${Type.show[T]}]]")
    val name = Derivation.typeName[T]
    val (fields, maybeAux) = Derivation.shape[T]
    val fieldNames = Expr(fields map (_._1))
    val fieldExtractors = Expr.ofList(fields map {
      case (f, ft) => ft.asType match {
        case '[p] => fieldExtractor[p](name, f)
      }
    })
    val auxExtractor: Expr[Option[Extractor[?]]] = maybeAux match {
      case Some((f, at)) => at.asType match {
        case '[a] => '{ Some(${ extractorFor[a](name, f) }) }
      }
      case None => '{ None }
    }
    '{ new ProductExtractor[T](${ Expr(name) }, $fieldNames, $fieldExtractors, $auxExtractor, (xs, ao) => ${ Derivation.construct[T]('xs, 'ao) }) ^^ ${ Expr(s"derived extractor of $name") } }
  }

  /**
   * Implementation of `MultiExtractor.derived`.
   *
   * @tparam T a sealed trait or sealed abstract class.
   * @return an expression for the derived `MultiExtractor[Seq[T]]`.
   */
  def sumImpl[T: Type](using q: Quotes): Expr[MultiExtractor[Seq[T]]] = {
    val name = Derivation.typeName[T]
    val cases = Expr.ofList(Derivation.leaves[T] map { lt =>
      lt.asType match {
        case '[l] => '{ (${ Expr(lt.typeSymbol.name) }, ${ extractorFor[l](name, lt.typeSymbol.name) }.asInstanceOf[Extractor[? <: T]]) }
      }
    })
    '{ new SumMultiExtractor[T](${ Expr(name) }, $cases) ^^ ${ Expr(s"derived multi-extractor of $name") } }
  }

  // A Seq field is extracted from child elements by a MultiExtractor; any other field by an Extractor.
  private def fieldExtractor[P: Type](owner: String, field: String)(using q: Quotes): Expr[FieldExtractor] = {
    import q.reflect.*
    Type.of[P] match {
      case '[Seq[x]] if TypeRepr.of[P] =:= TypeRepr.of[Seq[x]] =>
        '{ FieldExtractor.multi[P](${ multiExtractorFor[P, x](owner, field) }) }
      case _ =>
        '{ FieldExtractor.single[P](${ extractorFor[P](owner, field) }) }
    }
  }

  // Prefer a given MultiExtractor[Seq[X]]; otherwise, extract every one of the children with X's Extractor.
  private def multiExtractorFor[P: Type, X: Type](owner: String, field: String)(using q: Quotes): Expr[MultiExtractor[P]] =
    Expr.summon[MultiExtractor[P]] getOrElse
            '{ MultiExtractorBase[X](MultiExtractorBase.NonNegative)(using ${ extractorFor[X](owner, field) }).asInstanceOf[MultiExtractor[P]] }

  // Prefer a given Extractor[P]; otherwise, lift X's Extractor for an Option[X], or derive one for a case class.
  private def extractorFor[P: Type](owner: String, field: String)(using q: Quotes): Expr[Extractor[P]] = {
    import q.reflect.*
    Expr.summon[Extractor[P]] getOrElse {
      Type.of[P] match {
        case '[Option[x]] => '{ ${ extractorFor[x](owner, field) }.lift.asInstanceOf[Extractor[P]] }
        case _ if Derivation.isCaseClass[P] => '{ Extractor.derived[P] }
        case _ => report.errorAndAbort(s"cannot derive an Extractor for $owner: no Extractor[${Type.show[P]}] for $field, and ${Type.show[P]} is not a case class")
      }
    }
  }
}

