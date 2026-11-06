package com.phasmidsoftware.xmldoc.core

import scala.quoted.*

/**
 * Compile-time helpers for deriving type class instances (`Extractor`, `MultiExtractor`, `Renderer`) from the shape of
 * a case class or of a sealed hierarchy.
 *
 * NOTE: these use a macro over the primary constructor rather than a `scala.deriving.Mirror`,
 * because Scala 3 synthesizes no `Mirror` for a case class with a second parameter list, and the
 * auxiliary-parameter pattern (e.g. `case class Placemark(Geometry: Seq[Geometry])(val featureData: FeatureData)`)
 * is used throughout `kml`. See DESIGN.md.
 */
object Derivation {

  /**
   * Method to get the fields (and the auxiliary parameter, if any) of the case class `T`.
   * The case class may have at most two parameter lists, and the second may have only one parameter.
   *
   * @tparam T a case class.
   * @return a pair of the fields (name and type of each) and the optional auxiliary parameter.
   */
  def shape[T: Type](using q: Quotes): (List[(String, q.reflect.TypeRepr)], Option[(String, q.reflect.TypeRepr)]) = {
    import q.reflect.*
    val tpe = TypeRepr.of[T]
    val cls = tpe.typeSymbol
    if (!isCaseClassSymbol(cls)) report.errorAndAbort(s"cannot derive for ${tpe.show}: it is not a case class")
    val termLists = cls.primaryConstructor.paramSymss.filterNot(_.exists(_.isTypeParam))

    def typed(ps: List[Symbol]): List[(String, TypeRepr)] = ps map (p => p.name -> tpe.memberType(cls.fieldMember(p.name)))

    termLists match {
      case Nil => (Nil, None)
      case first :: Nil => (typed(first), None)
      case first :: (aux :: Nil) :: Nil => (typed(first), typed(List(aux)).headOption)
      case _ => report.errorAndAbort(s"cannot derive for ${tpe.show}: its second parameter list (if any) must have exactly one parameter, and there must be no third")
    }
  }

  /**
   * Method to construct a `T` from the values of its fields and of its auxiliary parameter (if any).
   *
   * @param xs the values of the fields of the first parameter list, in declaration order.
   * @param ao the value of the auxiliary parameter (`None` if `T` has no auxiliary parameter).
   * @tparam T a case class.
   * @return an expression which constructs a `T`.
   */
  def construct[T: Type](xs: Expr[Seq[Any]], ao: Expr[Option[Any]])(using q: Quotes): Expr[T] = {
    import q.reflect.*
    val tpe = TypeRepr.of[T]
    val (fields, maybeAux) = shape[T]
    val args = fields.zipWithIndex map {
      case ((_, ft), i) => ft.asType match {
        case '[f] => '{ $xs(${ Expr(i) }).asInstanceOf[f] }.asTerm
      }
    }
    val ctor = Select(New(Inferred(tpe)), tpe.typeSymbol.primaryConstructor).appliedToTypes(tpe.typeArgs).appliedToArgs(args)
    val term = maybeAux match {
      case Some((_, at)) => at.asType match {
        case '[a] => ctor.appliedToArgs(List('{ $ao.get.asInstanceOf[a] }.asTerm))
      }
      case None => ctor
    }
    term.asExprOf[T]
  }

  /**
   * Method to get the value of the auxiliary parameter of a `T`.
   *
   * @param t    an expression for a `T`.
   * @param name the name of the auxiliary parameter (a `val`).
   * @tparam T a case class.
   * @return an expression for the value of the auxiliary parameter.
   */
  def select[T: Type](t: Expr[T], name: String)(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    Select(t.asTerm, TypeRepr.of[T].typeSymbol.fieldMember(name)).asExpr
  }

  /**
   * Method to get the concrete (case class) subtypes of a sealed trait or sealed abstract class `T`,
   * flattening any sealed sub-traits (e.g. `Container`, under `Feature`).
   *
   * @tparam T a sealed trait or sealed abstract class.
   * @return the case classes which extend `T`, in declaration order.
   */
  def leaves[T: Type](using q: Quotes): List[q.reflect.TypeRepr] = {
    import q.reflect.*
    val tpe = TypeRepr.of[T]

    def inner(s: Symbol): List[Symbol] =
      if (isCaseClassSymbol(s)) List(s)
      else if (s.flags.is(Flags.Sealed)) s.children flatMap inner
      else report.errorAndAbort(s"cannot derive for ${tpe.show}: its subtype ${s.fullName} is neither a case class nor sealed")

    val cls = tpe.typeSymbol
    if (!cls.flags.is(Flags.Sealed)) report.errorAndAbort(s"cannot derive for ${tpe.show}: it is not sealed")
    (cls.children flatMap inner).distinct map (_.typeRef)
  }

  /**
   * Method to determine whether `T` is a sealed trait or sealed abstract class.
   *
   * @tparam T a type.
   * @return true if `T` is sealed.
   */
  def isSealed[T: Type](using q: Quotes): Boolean = {
    import q.reflect.*
    TypeRepr.of[T].typeSymbol.flags.is(Flags.Sealed)
  }

  /**
   * Method to determine whether `T` is a case class (and not a case object).
   *
   * @tparam T a type.
   * @return true if `T` is a case class.
   */
  def isCaseClass[T: Type](using q: Quotes): Boolean = {
    import q.reflect.*
    isCaseClassSymbol(TypeRepr.of[T].typeSymbol)
  }

  private def isCaseClassSymbol(using q: Quotes)(s: q.reflect.Symbol): Boolean = {
    import q.reflect.*
    s.isClassDef && s.flags.is(Flags.Case) && !s.flags.is(Flags.Module)
  }

  /**
   * The simple name of the type `T` (used for messages and for tags).
   *
   * @tparam T a type.
   * @return the simple name of `T`.
   */
  def typeName[T: Type](using q: Quotes): String = {
    import q.reflect.*
    TypeRepr.of[T].typeSymbol.name
  }
}
