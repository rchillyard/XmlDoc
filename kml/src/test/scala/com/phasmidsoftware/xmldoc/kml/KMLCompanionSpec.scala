package com.phasmidsoftware.xmldoc.kml

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.core.TryUsing
import com.phasmidsoftware.xmldoc.kml.KMLCompanion.renderKMLAsFormat
import com.phasmidsoftware.xmldoc.render.{FormatText, FormatXML, Renderer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import scala.util.Try

class KMLCompanionSpec extends AnyFlatSpec with should.Matchers {

  behavior of "KMLCompanion"

  it should "renderFeature" in {

  }

  it should "extractFeatures" in {

  }

  it should "renderFeatures" in {

  }

  it should "loadKML" in {
    val z: IO[Seq[KML]] = KMLCompanion.loadKML(scala.util.Success("kml/src/main/resources/com/phasmidsoftware/xmldoc/kml/sample.kml"))
    val y: Seq[KML] = z.unsafeRunSync()
    import KML.renderer
    val r: Try[String] = TryUsing(KML.stateR)(sr => {
      val kml: KML = y.head

      Renderer.render(kml, FormatXML(0), sr)
    })
    println(r.get)
  }

}
