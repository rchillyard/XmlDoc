package com.phasmidsoftware.xmldoc.kml

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.core.Text
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Success

/**
 * Integration tests against real, third-party KML files (sourced from Google's kml-samples
 * repository) exercising MultiGeometry and ExtendedData/SchemaData/SimpleData - features not
 * covered by any of the other, hand-crafted, sample files in this project.
 */
class KmlSamplesFuncSpec extends AnyFlatSpec with should.Matchers {

  behavior of "MultiGeometry samples"

  it should "extract polygon-point.kml (Point + Polygon in one MultiGeometry)" in {
    val ksi: IO[Seq[KML]] = KMLCompanion.loadKML(Success("kml-it/src/test/resources/multigeometry-polygon-point.kml"))
    val result = ksi.unsafeRunSync()
    result.head.features.head match {
      case p: Placemark =>
        p.name shouldBe Text("Adelaide")
        p.Geometry.size shouldBe 1
        p.Geometry.head match {
          case mg: MultiGeometry =>
            mg.Geometry.size shouldBe 2
            mg.Geometry.head shouldBe a[Point]
            mg.Geometry.last match {
              case polygon: Polygon =>
                polygon.innerBoundaryIs shouldBe Nil
              case x => fail(s"expected a Polygon but got $x")
            }
          case x => fail(s"expected a MultiGeometry but got $x")
        }
      case x => fail(s"expected a Placemark but got $x")
    }
  }

  it should "extract multigeometry-linestrings.kml (10 LineStrings in one MultiGeometry)" in {
    val ksi: IO[Seq[KML]] = KMLCompanion.loadKML(Success("kml-it/src/test/resources/multigeometry-linestrings.kml"))
    val result = ksi.unsafeRunSync()
    result.head.features.head match {
      case Document(features) =>
        features.head match {
          case Folder(placemarks) =>
            placemarks.head match {
              case p: Placemark =>
                p.name shouldBe Text("MultiGeometry")
                p.Geometry.size shouldBe 1
                p.Geometry.head match {
                  case mg: MultiGeometry =>
                    mg.Geometry.size shouldBe 10
                    mg.Geometry.foreach(_ shouldBe a[LineString])
                  case x => fail(s"expected a MultiGeometry but got $x")
                }
              case x => fail(s"expected a Placemark but got $x")
            }
          case x => fail(s"expected a Folder but got $x")
        }
      case x => fail(s"expected a Document but got $x")
    }
  }

  behavior of "ExtendedData samples"

  it should "extract schemadata-trailhead.kml (ExtendedData/SchemaData/SimpleData, and silently skip the unsupported Schema element)" in {
    val ksi: IO[Seq[KML]] = KMLCompanion.loadKML(Success("kml-it/src/test/resources/schemadata-trailhead.kml"))
    val result = ksi.unsafeRunSync()
    result.head.features.head match {
      case Document(features) =>
        features.size shouldBe 2 // the Schema element is not a Feature, so it's not among these
        features.head match {
          case p: Placemark =>
            p.name shouldBe Text("Easy trail")
            p.featureData.maybeExtendedData shouldBe Some(
              ExtendedData(Nil, Seq(SchemaData("#TrailHeadTypeId", Seq(
                SimpleData("TrailHeadName", "Pi in the sky"),
                SimpleData("TrailLength", "3.14159"),
                SimpleData("ElevationGain", "10")
              ))))
            )
          case x => fail(s"expected a Placemark but got $x")
        }
      case x => fail(s"expected a Document but got $x")
    }
  }
}
