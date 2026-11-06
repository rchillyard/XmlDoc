package com.phasmidsoftware.xmldoc.render

import com.phasmidsoftware.xmldoc.core.{Derivation, XmlException}
import com.phasmidsoftware.xmldoc.render.Renderer.{doNestedRender, renderOuter}
import com.phasmidsoftware.xmldoc.xml.Extractor

import scala.quoted.*
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * The `Renderer` of a case class `T`, as derived by `Renderer.derived`.
 * It renders exactly as the `rendererN` methods (or, with an auxiliary parameter, the `rendererNSuper` methods) did.
 *
 * NOTE: the field renderers (and the auxiliary renderer) are passed by name and evaluated only on the first rendering,
 * so that mutually recursive types (e.g. `Geometry` and `MultiGeometry`) need no `createLazy`.
 *
 * @param typeName the name of `T` (used in messages).
 * @param fields   (by name) the `Renderer` of each field of the first parameter list of `T`, in declaration order.
 * @param aux      (by name) the name of the auxiliary parameter of `T`, a function to get its value, and its `Renderer`, if `T` has one.
 * @tparam T the type to be rendered.
 */
class ProductRenderer[T: ClassTag](typeName: String, fields: => List[Renderer[?]], aux: => Option[(String, T => Any, Renderer[?])]) extends Renderer[T] {

  private lazy val fieldRenderers: Vector[Renderer[Any]] = fields.toVector map (_.asInstanceOf[Renderer[Any]])
  private lazy val maybeAux: Option[(String, T => Any, Renderer[Any])] = aux map { case (n, f, r) => (n, f, r.asInstanceOf[Renderer[Any]]) }

  def render(t: T, format: Format, stateR: StateR): Try[String] = {
    val p = t.asInstanceOf[Product]
    maybeAux match {
      case None =>
        renderFields(p, fieldRenderers.size, format, stateR)
      case Some((auxName, f, r)) =>
        // NOTE the name passed to doNestedRender is that of the first field, as rendererNSuper did,
        // unless that is an attribute (in which case doNestedRender would drop the auxiliary content): then it's the auxiliary's own name.
        val name = if (fieldRenderers.isEmpty) "" else p.productElementName(0) match {
          case n if Extractor.inferAttributeType(n).isDefined => auxName
          case n => n
        }
        for {
          wInner <- r.render(f(t), format, stateR.recurse)
          wOuter <- if (fieldRenderers.isEmpty) Success("") else renderFields(p, fieldRenderers.size, format, stateR.recurse)
          result <- doNestedRender[T](format, stateR, wInner, wOuter, name)
        } yield result
    }
  }

  // Render the first n fields of p: the first n-1 (recursively, as the interior), then the nth.
  private def renderFields(p: Product, n: Int, format: Format, stateR: StateR): Try[String] =
    if (n == 0) doNestedRender[T](format, stateR, "", "", "")
    else for {
      wInner <- if (n == 1) Success("") else renderFields(p, n - 1, format, stateR.recurse)
      wOuter <- renderOuter(p, p.productElement(n - 1), n - 1, format.indent)(using fieldRenderers(n - 1))
      result <- doNestedRender[T](format, stateR, wInner, wOuter, p.productElementName(n - 1))
    } yield result

  override def toString: String = s"derived Renderer[$typeName]"
}

/**
 * The `Renderer` of a sealed trait (or sealed abstract class) `T`, as derived by `Renderer.derived`:
 * a value is rendered by the `Renderer` of its own (case class) subtype.
 *
 * @param typeName the name of `T` (used in messages).
 * @param cases    (by name) a test for, and the `Renderer` of, each subtype of `T`.
 * @tparam T the (sealed) super-type.
 */
class SumRenderer[T](typeName: String, cases: => List[(Any => Boolean, Renderer[?])]) extends Renderer[T] {

  private lazy val renderers: List[(Any => Boolean, Renderer[?])] = cases

  def render(t: T, format: Format, stateR: StateR): Try[String] =
    renderers find (_._1(t)) match {
      case Some((_, r)) => r.asInstanceOf[Renderer[T]].render(t, format, stateR)
      case None => Failure(XmlException(s"derived Renderer[$typeName]: no renderer for ${t.getClass}"))
    }

  override def toString: String = s"derived Renderer[$typeName]"
}

/**
 * The macro behind `Renderer.derived`.
 */
object DerivedRenderers {

  /**
   * Implementation of `Renderer.derived`.
   *
   * @tparam T a case class, or a sealed trait or sealed abstract class.
   * @return an expression for the derived `Renderer[T]`.
   */
  def impl[T: Type](using q: Quotes): Expr[Renderer[T]] =
    if (Derivation.isSealed[T]) sumImpl[T] else productImpl[T]

  private def productImpl[T: Type](using q: Quotes): Expr[Renderer[T]] = {
    import q.reflect.*
    val name = Derivation.typeName[T]
    val (fields, maybeAux) = Derivation.shape[T]
    val fieldRenderers = Expr.ofList(fields map {
      case (f, ft) => ft.asType match {
        case '[p] => rendererFor[p](name, f)
      }
    })
    val auxRenderer: Expr[Option[(String, T => Any, Renderer[?])]] = maybeAux match {
      case Some((f, at)) => at.asType match {
        case '[a] => '{ Some((${ Expr(f) }, (t: T) => ${ Derivation.select[T]('t, f) }, ${ rendererFor[a](name, f) })) }
      }
      case None => '{ None }
    }
    val classTag = Expr.summon[ClassTag[T]] getOrElse report.errorAndAbort(s"Renderer.derived: no ClassTag for $name")
    '{ new ProductRenderer[T](${ Expr(name) }, $fieldRenderers, $auxRenderer)(using $classTag) ^^ ${ Expr(s"derived renderer of $name") } }
  }

  private def sumImpl[T: Type](using q: Quotes): Expr[Renderer[T]] = {
    val name = Derivation.typeName[T]
    val cases = Expr.ofList(Derivation.leaves[T] map { lt =>
      lt.asType match {
        case '[l] => '{ ((x: Any) => x.isInstanceOf[l], ${ rendererFor[l](name, lt.typeSymbol.name) }) }
      }
    })
    '{ new SumRenderer[T](${ Expr(name) }, $cases) ^^ ${ Expr(s"derived renderer of $name") } }
  }

  // Prefer a given Renderer[P]; otherwise, lift X's Renderer for an Option[X] or a Seq[X], or derive one for a case class.
  private def rendererFor[P: Type](owner: String, field: String)(using q: Quotes): Expr[Renderer[P]] = {
    import q.reflect.*
    Expr.summon[Renderer[P]] getOrElse {
      Type.of[P] match {
        case '[Option[x]] => '{ ${ rendererFor[x](owner, field) }.lift.asInstanceOf[Renderer[P]] }
        case '[Seq[x]] if TypeRepr.of[P] =:= TypeRepr.of[Seq[x]] => '{ Renderer.sequenceOf[x](using ${ rendererFor[x](owner, field) }).asInstanceOf[Renderer[P]] }
        case _ if Derivation.isCaseClass[P] || Derivation.isSealed[P] => '{ Renderer.derived[P] }
        case _ => report.errorAndAbort(s"cannot derive a Renderer for $owner: no Renderer[${Type.show[P]}] for $field, and ${Type.show[P]} is not a case class")
      }
    }
  }
}
