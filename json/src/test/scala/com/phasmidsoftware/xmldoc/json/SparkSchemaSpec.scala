package com.phasmidsoftware.xmldoc.json

import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.kml.*
import io.circe.derivation.{Configuration, ConfiguredEnumEncoder}
import io.circe.parser.parse
import io.circe.{Encoder, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Success

object SparkSchemaSpec {
  given Configuration = Configuration.default

  case class Point(lon: Double, lat: Double) derives Encoder.AsObject, SparkSchema

  case class Station(name: String, point: Point, elevation: Option[Double]) derives Encoder.AsObject, SparkSchema

  enum Mode derives ConfiguredEnumEncoder, SparkSchema {
    case Ground, Air
  }

  case class Meters(value: Double) extends AnyVal

  object Meters {
    given Encoder[Meters] = Encoder[Double].contramap(_.value)
    given SparkSchema[Meters] = SparkSchema[Double].as
  }

  case class Track(order: Int, `the name`: String, modes: List[Mode], heights: Vector[Meters], tags: Set[String], counts: Map[String, Long], stations: Seq[Station])
    derives Encoder.AsObject, SparkSchema

  case class Atoms(b: Boolean, y: Byte, s: Short, i: Int, l: Long, f: Float, d: Double, n: BigDecimal, date: java.time.LocalDate, time: java.time.Instant)
    derives Encoder.AsObject, SparkSchema

  /**
   * Method to check that some JSON is what Spark's `from_json` would read with the given type: the right kind of
   * value, and for a struct exactly the right field names. Any value may be null (Spark's fields are all nullable).
   */
  def conforms(json: Json, t: SparkType): Boolean = json.isNull || (t match {
    case SparkType.Atomic("STRING" | "DATE" | "TIMESTAMP") => json.isString
    case SparkType.Atomic("BOOLEAN") => json.isBoolean
    case SparkType.Atomic("TINYINT" | "SMALLINT" | "INT" | "BIGINT") => json.asNumber.exists(_.toLong.isDefined)
    case SparkType.Atomic(_) => json.isNumber
    case SparkType.Array(e) => json.asArray.exists(_.forall(conforms(_, e)))
    case SparkType.Map(v) => json.asObject.exists(_.values.forall(conforms(_, v)))
    case SparkType.Struct(fields) => json.asObject.exists { o =>
      o.keys.toSet == fields.map(_._1).toSet && fields.forall((name, ft) => o(name).exists(conforms(_, ft)))
    }
  })
}

class SparkSchemaSpec extends AnyFlatSpec with Matchers {

  import SparkSchemaSpec.*

  behavior of "SparkSchema.ddl"

  it should "derive the schema of a record with a nested case class and an Option" in {
    SparkSchema.ddl[Station] shouldBe "`name` STRING, `point` STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>, `elevation` DOUBLE"
  }

  it should "map the standard types" in {
    SparkSchema.ddl[Atoms] shouldBe "`b` BOOLEAN, `y` TINYINT, `s` SMALLINT, `i` INT, `l` BIGINT, `f` FLOAT, `d` DOUBLE, `n` DECIMAL(38,18), `date` DATE, `time` TIMESTAMP"
  }

  it should "map collections, maps, enums and value classes, and quote every name" in {
    SparkSchema.ddl[Track] shouldBe "`order` INT, `the name` STRING, `modes` ARRAY<STRING>, `heights` ARRAY<DOUBLE>, `tags` ARRAY<STRING>, `counts` MAP<STRING, BIGINT>, " +
      "`stations` ARRAY<STRUCT<`name`: STRING, `point`: STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>, `elevation`: DOUBLE>>"
  }

  it should "give the type itself for a non-record type" in {
    SparkSchema.ddl[Seq[Int]] shouldBe "ARRAY<INT>"
  }

  it should "not derive for a sum type with a non-singleton case, or a field with no schema" in {
    """sealed trait Shape; case class Circle(r: Double) extends Shape; SparkSchema.derived[Shape]""" shouldNot compile
    """case class Odd(c: Char); SparkSchema.derived[Odd]""" shouldNot compile
  }

  it should "double a backtick in a name" in {
    SparkType.quote("a`b") shouldBe "`a``b`"
  }

  behavior of "JsonLines"

  private val stations = Seq(Station("Boston", Point(-71.06, 42.36), Some(43.0)), Station("Nowhere", Point(0, 0), None))

  it should "render one record per line" in {
    JsonLines.render(stations) shouldBe
      """{"name":"Boston","point":{"lon":-71.06,"lat":42.36},"elevation":43.0}
        |{"name":"Nowhere","point":{"lon":0.0,"lat":0.0},"elevation":null}
        |""".stripMargin
  }

  it should "write what the schema describes (round trip)" in {
    val schema = SparkSchema[Station].sparkType
    JsonLines.lines(stations).toSeq.map(parse) foreach {
      case Right(json) => conforms(json, schema) shouldBe true
      case Left(e) => fail(e)
    }
  }

  it should "write what the schema describes, for collections, maps, enums and value classes" in {
    val track = Track(1, "x", List(Mode.Ground, Mode.Air), Vector(Meters(2.5)), Set("a"), Map("k" -> 3L), stations)
    val json = parse(JsonLines.line(track))
    json.map(_.hcursor.downField("modes").focus) shouldBe Right(Some(Json.arr(Json.fromString("Ground"), Json.fromString("Air"))))
    json.map(conforms(_, SparkSchema[Track].sparkType)) shouldBe Right(true)
  }

  it should "find a mismatch between JSON and a schema" in {
    parse("""{"name":"Boston","point":{"lon":-71.06},"elevation":43.0}""").map(conforms(_, SparkSchema[Station].sparkType)) shouldBe Right(false)
    parse("""{"name":1,"point":{"lon":-71.06,"lat":42.36},"elevation":43.0}""").map(conforms(_, SparkSchema[Station].sparkType)) shouldBe Right(false)
  }

  behavior of "records from a real KML file"

  private def placemarks(fs: Seq[Feature]): Seq[Placemark] = fs flatMap {
    case p: Placemark => Seq(p)
    case h: HasFeatures => placemarks(h.features)
    case _ => Nil
  }

  private def toStations(p: Placemark): Seq[Station] = for {
    case point: com.phasmidsoftware.xmldoc.kml.Point <- p.Geometry
    cs <- point.coordinates
    c <- cs.coordinates
  } yield Station(p.name.$.toString, Point(c.long.toDouble, c.lat.toDouble), c.maybeAlt.map(_.toDouble))

  it should "send the Points of KML_Samples.kml as JSON which the schema describes" in {
    val kmls = KMLCompanion.loadKML(Success("kml/src/test/resources/KML_Samples.kml")).unsafeRunSync()
    val records = kmls.flatMap(k => placemarks(k.features)).flatMap(toStations)
    records.size shouldBe 4
    records.head shouldBe Station("Simple placemark", Point(-122.0822035425683, 37.42228990140251), Some(0.0))
    val schema = SparkSchema[Station].sparkType
    JsonLines.lines(records).toSeq.map(parse(_).map(conforms(_, schema))) shouldBe Seq.fill(4)(Right(true))
  }
}
