package com.phasmidsoftware.xmldoc.kml

import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.kml.LocalSources.{fileWithSpaceURL, jarURL, resourceContents, withHttpServer}
import java.net.{URI, URL}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

/**
 * Loading KML from URLs which aren't plain file: ones (the resource of a jar, for example).
 */
class KMLLoadSpec extends AnyFlatSpec with should.Matchers {

  behavior of "KMLCompanion.loadKML(URL)"

  private val name = "minisample.kml"
  private val contents = resourceContents(name)
  private lazy val expected: Seq[KML] = KMLCompanion.loadKML(KML.getClass.getResource(name)).unsafeRunSync()

  it should "load a resource" in {
    expected.size shouldBe 1
    expected.head.features.size shouldBe 1
  }

  it should "load KML over HTTP" in {
    withHttpServer(Map(s"/$name" -> contents)) { base =>
      KMLCompanion.loadKML(URI.create(s"$base/$name").toURL).unsafeRunSync() shouldBe expected
    }
  }

  it should "load KML from a jar" in {
    KMLCompanion.loadKML(jarURL(name, contents)).unsafeRunSync() shouldBe expected
  }

  it should "load KML from a file whose path has a space" in {
    val url = fileWithSpaceURL("mini sample.kml", contents)
    url.toString should include("%20")
    KMLCompanion.loadKML(url).unsafeRunSync() shouldBe expected
  }

  it should "fail for a null resource" in {
    val x = the[NoSuchElementException] thrownBy KMLCompanion.loadKML(null: URL).unsafeRunSync()
    x.getMessage should include("is null")
  }
}
