package com.phasmidsoftware.xmldoc.kml

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import scala.util._

class KMLEditorFuncSpec extends AnyFlatSpec with should.Matchers {

  behavior of "KMLEditor"

  val placemark = "Placemark"
  private val triedFilename1: Success[String] = Success("kml/src/main/resources/com/phasmidsoftware/xmldoc/kml/placemarks.kml")
  private val triedFilename2: Success[String] = Success("kml-it/src/test/resources/invertedPlacemarks.kml")

  it should "processKMLs join 1" in {
    val editor = KMLEditor(Seq(KmlEdit(KmlEdit.JOIN, 2, Element(placemark, "Salem & Lowell RR (#1)"), Some(Element(placemark, "Salem & Lowell RR (#2)"))), KmlEdit(KmlEdit.DELETE, 1, Element(placemark, "Salem & Lowell RR (#2)"), None)))
    val ksi: IO[Seq[KML]] = for {
      ks <- KMLCompanion.loadKML(triedFilename1)
      ks2 = editor.processKMLs(ks)
    } yield ks2
    val result = ksi.unsafeRunSync()
    val feature: Feature = result.head.features.head
    feature match {
      case Document(fs) =>
        val folder = fs.head
        folder match {
          case Folder(features) =>
            features.size shouldBe 1
            val p1 = features.head
            p1 match {
              case p@Placemark(g) =>
                p.featureData.name.$.toString shouldBe "Salem & Lowell RR (#1)Salem & Lowell RR (#2)"
                p.featureData.name.matches("Salem & Lowell RR (#1)Salem & Lowell RR (#2)") shouldBe true
                val lineString = g.head
                lineString match {
                  case LineString(_, cs) =>
                    cs.head.coordinates.size shouldBe 163
                  // TODO check the ordering of the Coordinate values.
                }
            }
        }
    }
  }

  it should "processKMLs joinX" in {
    val editor = KMLEditor(Seq(KmlEdit(KmlEdit.JOINX, 2, Element(placemark, "Salem & Lowell RR (#1)"), Some(Element(placemark, "Salem & Lowell RR (#2)"))), KmlEdit(KmlEdit.DELETE, 1, Element(placemark, "Salem & Lowell RR (#2)"), None)))
    val ksi: IO[Seq[KML]] = for {
      ks <- KMLCompanion.loadKML(triedFilename1)
      ks2 = editor.processKMLs(ks)
    } yield ks2
    val result = ksi.unsafeRunSync()
    val feature: Feature = result.head.features.head
    feature match {
      case Document(fs) =>
        val folder = fs.head
        folder match {
          case Folder(features) =>
            features.size shouldBe 1
            val p1 = features.head
            p1 match {
              case p@Placemark(g) =>
                p.featureData.name.matches("Salem & Lowell RR (#1)") shouldBe true
                val lineString = g.head
                lineString match {
                  case LineString(_, cs) =>
                    cs.head.coordinates.size shouldBe 163
                  // TODO check the ordering of the Coordinate values.
                }
            }
        }
    }
  }

  it should "processKMLs join 2" in {
    val invertCapeCod = KmlEdit(KmlEdit.INVERT, 1, Element(placemark, "Cape Cod Central RR"), None)
    val invertCapeCodA = KmlEdit(KmlEdit.INVERT, 1, Element(placemark, "Cape Cod Central RR A"), None)
    val joinCapeCods = KmlEdit(KmlEdit.JOIN, 2, Element(placemark, "Cape Cod Central RR A"), Some(Element(placemark, "Cape Cod Central RR")))
    val deleteCapeCod = KmlEdit(KmlEdit.DELETE, 1, Element(placemark, "Cape Cod Central RR"), None)
    val editor = KMLEditor(Seq(invertCapeCod, invertCapeCodA, joinCapeCods, deleteCapeCod))
    val ksi: IO[Seq[KML]] = for {
      ks <- KMLCompanion.loadKML(triedFilename2)
      ks2 = editor.processKMLs(ks)
    } yield ks2
    val result = ksi.unsafeRunSync()
    val feature: Feature = result.head.features.head
    feature match {
      case Document(fs) =>
        val folder = fs.head
        folder match {
          case Folder(features) =>
            features.size shouldBe 1
            val p1 = features.head
            p1 match {
              case p@Placemark(g) =>
                p.name.matches("Cape Cod Central RR A Cape Cod Central RR") shouldBe true
                val lineString = g.head
                lineString match {
                  case LineString(_, cs) =>
                    val coordinate: Coordinates = cs.head
                    val coordinates: Seq[Coordinate] = coordinate.coordinates
                    coordinates.size shouldBe 405
                    coordinates.head shouldBe Coordinate("-70.25995", "41.69953", Some("0")) // Yarmouth
                    coordinates.last shouldBe Coordinate("-70.184376", "42.05095", Some("0")) // P-town
                }
            }
        }
    }
  }

}
