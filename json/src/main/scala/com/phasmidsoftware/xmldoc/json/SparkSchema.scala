package com.phasmidsoftware.xmldoc.json

import scala.compiletime.{constValueTuple, erasedValue, error, summonFrom}
import scala.deriving.Mirror

/**
 * Type class giving the Spark SQL type of the JSON which circe writes for a `T`.
 *
 * For a record case class, derive it alongside its circe encoder:
 * {{{
 *   case class Point(lon: Double, lat: Double) derives Encoder.AsObject, SparkSchema
 *   case class Station(name: String, point: Point, elevation: Option[Double]) derives Encoder.AsObject, SparkSchema
 *
 *   SparkSchema.ddl[Station] // "`name` STRING, `point` STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>, `elevation` DOUBLE"
 * }}}
 * and read the JSON in Spark with `from_json(col("value"), schema)`.
 *
 * The mapping follows circe's default encoders: `String` → `STRING`, `Int` → `INT`, `Long` → `BIGINT`,
 * `Double` → `DOUBLE`, `Boolean` → `BOOLEAN` (and so on), `Option[X]` → `X` (written as `null` when empty),
 * `Seq[X]` (also `List`, `Vector`, `Set`) → `ARRAY<X>`, `Map[String, X]` → `MAP<STRING, X>`,
 * a case class → `STRUCT<...>`, and an enum whose cases are all singletons → `STRING` (encode it with circe's
 * `ConfiguredEnumEncoder`, which writes each case as its name). A value class has no `Mirror`: give it the schema
 * of the type it's written as, with `as` (below).
 *
 * A case class with a second parameter list (such as `kml`'s `Placemark`) has no `Mirror`, and so can't derive
 * a `SparkSchema` (or a circe encoder): build a plain record from it instead.
 *
 * @tparam T the type of a value written as JSON.
 */
trait SparkSchema[T] {
  /**
   * @return the Spark SQL type of a `T`'s JSON.
   */
  def sparkType: SparkType

  /**
   * Method to give another type, `U`, which is written as a `T`, the same schema: for example a value class
   * (which has no `Mirror`, and so can't be derived):
   * {{{
   *   case class Meters(value: Double) extends AnyVal
   *   object Meters {
   *     given Encoder[Meters] = Encoder[Double].contramap(_.value)
   *     given SparkSchema[Meters] = SparkSchema[Double].as
   *   }
   * }}}
   *
   * @tparam U the type which is written as a `T`.
   * @return a `SparkSchema[U]`.
   */
  def as[U]: SparkSchema[U] = SparkSchema.instance(sparkType)
}

object SparkSchema {

  /**
   * Method to summon the `SparkSchema` of `T`.
   *
   * @tparam T the type.
   * @return the given `SparkSchema[T]`.
   */
  def apply[T](using s: SparkSchema[T]): SparkSchema[T] = s

  /**
   * Method to get the DDL schema string of a record type `T`, as used by Spark's `from_json` and `DataType.fromDDL`:
   * for a case class, its fields (e.g. "`name` STRING, `elevation` DOUBLE"); for any other type, the type itself.
   *
   * @tparam T the type of a record.
   * @return the DDL schema string.
   */
  def ddl[T](using s: SparkSchema[T]): String = s.sparkType match {
    case SparkType.Struct(fields) => fields.map((name, t) => s"${SparkType.quote(name)} ${t.sql}").mkString(", ")
    case t => t.sql
  }

  /**
   * Method to create a `SparkSchema[T]`.
   *
   * @param t the Spark SQL type (evaluated once, when first needed).
   * @tparam T the type.
   * @return a `SparkSchema[T]`.
   */
  def instance[T](t: => SparkType): SparkSchema[T] = new SparkSchema[T] {
    lazy val sparkType: SparkType = t
  }

  given SparkSchema[String] = instance(SparkType.string)
  given SparkSchema[Boolean] = instance(SparkType.boolean)
  given SparkSchema[Byte] = instance(SparkType.byte)
  given SparkSchema[Short] = instance(SparkType.short)
  given SparkSchema[Int] = instance(SparkType.int)
  given SparkSchema[Long] = instance(SparkType.long)
  given SparkSchema[Float] = instance(SparkType.float)
  given SparkSchema[Double] = instance(SparkType.double)
  given SparkSchema[BigDecimal] = instance(SparkType.decimal)
  given SparkSchema[java.time.LocalDate] = instance(SparkType.date)
  given SparkSchema[java.time.Instant] = instance(SparkType.timestamp)

  given optionSchema[X](using x: SparkSchema[X]): SparkSchema[Option[X]] = instance(x.sparkType)

  given seqSchema[X](using x: SparkSchema[X]): SparkSchema[Seq[X]] = instance(SparkType.Array(x.sparkType))

  given listSchema[X](using x: SparkSchema[X]): SparkSchema[List[X]] = instance(SparkType.Array(x.sparkType))

  given vectorSchema[X](using x: SparkSchema[X]): SparkSchema[Vector[X]] = instance(SparkType.Array(x.sparkType))

  given setSchema[X](using x: SparkSchema[X]): SparkSchema[Set[X]] = instance(SparkType.Array(x.sparkType))

  given mapSchema[X](using x: SparkSchema[X]): SparkSchema[Map[String, X]] = instance(SparkType.Map(x.sparkType))

  /**
   * Method to derive the `SparkSchema` of a case class (a `STRUCT` of its fields) or of an enum whose cases are all
   * singletons (a `STRING`). A field whose type has no given `SparkSchema` is derived in turn.
   *
   * @param m the `Mirror` of `T`.
   * @tparam T a case class or an enum.
   * @return a `SparkSchema[T]`.
   */
  inline def derived[T](using m: Mirror.Of[T]): SparkSchema[T] = inline m match {
    case p: Mirror.ProductOf[T] =>
      val names = constValueTuple[p.MirroredElemLabels].toList.map(_.toString)
      val schemas = schemasOf[p.MirroredElemTypes]
      instance(SparkType.Struct(names zip schemas.map(_.sparkType)))
    case s: Mirror.SumOf[T] =>
      requireSingletons[s.MirroredElemTypes]
      instance(SparkType.string)
  }

  private inline def schemasOf[Ts <: Tuple]: List[SparkSchema[?]] = inline erasedValue[Ts] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts) => schemaOf[t] :: schemasOf[ts]
  }

  private inline def schemaOf[T]: SparkSchema[T] = summonFrom {
    case s: SparkSchema[T] => s
    case m: Mirror.Of[T] => derived[T](using m)
    case _ => error("no SparkSchema for a field's type: give one, or use a case class, an enum, or one of the standard types")
  }

  private inline val notSingletons = "cannot derive a SparkSchema for a sum type unless all its cases are singletons (an enum without parameters)"

  private inline def requireSingletons[Ts <: Tuple]: Unit = inline erasedValue[Ts] match {
    case _: EmptyTuple => ()
    case _: (t *: ts) =>
      summonFrom {
        case m: Mirror.ProductOf[`t`] => inline erasedValue[m.MirroredElemTypes] match {
          case _: EmptyTuple => requireSingletons[ts]
          case _ => error(notSingletons)
        }
        case _ => error(notSingletons)
      }
  }
}
