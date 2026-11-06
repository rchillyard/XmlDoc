package com.phasmidsoftware.xmldoc.kml

import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.core.TryUsing
import com.phasmidsoftware.xmldoc.render.{FormatXML, Renderer, StateR}
import com.phasmidsoftware.xmldoc.xml.XmlStream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.FileInputStream
import scala.util.{Success, Try}

/**
 * Streaming extraction of a real KML file gives the same Placemarks, in the same order, as whole-document extraction.
 */
class KmlStreamSpec extends AnyFlatSpec with Matchers {

  import KmlStreamSpec.*

  behavior of "XmlStream (with KML)"

  for (file <- files) it should s"stream the Placemarks of $file as whole-document extraction gives them" in {
    val whole: Seq[Placemark] = KMLCompanion.loadKML(Success(file)).unsafeRunSync() flatMap (k => placemarks(k.features))
    val streamed: Try[List[Try[Placemark]]] = XmlStream.extractFrom[Placemark, List[Try[Placemark]]](new FileInputStream(file), "Placemark")(_.toList)
    whole should not be empty
    streamed.map(_.map(_.flatMap(render))) shouldBe Success(whole.toList.map(render))
  }

  it should "stream the Placemarks of a Folder only, with a path selector" in {
    val file = files.head
    val fs: Seq[Folder] = KMLCompanion.loadKML(Success(file)).unsafeRunSync() flatMap (k => folders(k.features))
    val expected = fs.flatMap(_.features.collect { case p: Placemark => p }).toList.map(render)
    XmlStream.extractFrom[Placemark, List[Try[String]]](new FileInputStream(file), "Folder/Placemark")(_.map(_.flatMap(render)).toList) shouldBe Success(expected)
  }
}

object KmlStreamSpec {
  val files: List[String] = List("kml/src/test/resources/KML_Samples.kml", "kml/src/test/resources/altitudemode_reference.kml")

  def render(p: Placemark): Try[String] = TryUsing(StateR())(sr => Renderer.render[Placemark](p, FormatXML(), sr))

  def placemarks(fs: Seq[Feature]): Seq[Placemark] = fs flatMap {
    case p: Placemark => Seq(p)
    case h: HasFeatures => placemarks(h.features)
    case _ => Nil
  }

  // All the Folders, in document order.
  def folders(fs: Seq[Feature]): Seq[Folder] = fs flatMap {
    case f: Folder => f +: folders(f.features)
    case h: HasFeatures => folders(h.features)
    case _ => Nil
  }
}
