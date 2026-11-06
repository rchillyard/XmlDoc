package com.phasmidsoftware.xmldoc.json

import cats.effect.IO
import com.phasmidsoftware.xmldoc.kml.{Feature, HasFeatures, KMLCompanion, Placemark}
import io.circe.Encoder

import scala.util.Success

/**
 * Plain records built from the Points of a KML file, as a student would: for the tests of `json` and of the Kafka modules.
 */
object KmlRecords {

  case class Point(lon: Double, lat: Double) derives Encoder.AsObject, SparkSchema

  case class Station(name: String, point: Point, elevation: Option[Double]) derives Encoder.AsObject, SparkSchema

  /**
   * The path (from the root of the build) of a real KML file with four Points.
   */
  val samples: String = "kml/src/test/resources/KML_Samples.kml"

  /**
   * Method to read the Stations (one per Point) of a KML file.
   *
   * @param filename the path of a KML file.
   * @return an IO of the Stations, in document order.
   */
  def stations(filename: String): IO[Seq[Station]] =
    KMLCompanion.loadKML(Success(filename)) map (_ flatMap (k => placemarks(k.features)) flatMap toStations)

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
}
