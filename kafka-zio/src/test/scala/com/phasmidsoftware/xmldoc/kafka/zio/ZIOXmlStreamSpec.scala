package com.phasmidsoftware.xmldoc.kafka.zio

import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.json.JsonLines
import com.phasmidsoftware.xmldoc.json.KmlRecords.*
import com.phasmidsoftware.xmldoc.kml.Placemark
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.kafka.serde.Serde
import zio.stream.ZStream

import java.io.{ByteArrayInputStream, FileNotFoundException}
import scala.jdk.CollectionConverters.*
import scala.util.Success

class ZIOXmlStreamSpec extends AnyFlatSpec with Matchers {

  import ZIOKafkaSpec.*

  behavior of "ZIOXmlStream.extractFile"

  it should "stream the Placemarks of a real KML file, giving the same records as whole-document extraction" in {
    val streamed = runZIO(ZIOXmlStream.extractFile[Placemark](samples, "Placemark").runCollect).map(_.toList)
    streamed.map(_.forall(_.isSuccess)) shouldBe Right(true)
    streamed.map(_.flatMap(_.toOption).flatMap(stationsOf)) shouldBe Right(stations(samples).unsafeRunSync())
  }

  it should "send the streamed records to Kafka as they are read" in {
    val mock = mockProducer()
    val records = ZIOXmlStream.extractFile[Placemark](samples, "Placemark")
      .collect { case Success(p) => p }
      .flatMap(p => ZStream.fromIterable(stationsOf(p)))
    send(mock)(records, ZIOKafka.toKafka("stations", Serde.string)(_.name)).isRight shouldBe true
    mock.history().asScala.toList.map(r => string(r.value())) shouldBe stations(samples).unsafeRunSync().map(JsonLines.line(_))
  }

  it should "fail for a missing file" in {
    runZIO(ZIOXmlStream.extractFile[Placemark]("no-such-file.kml", "Placemark").runDrain) match {
      case Left(_: FileNotFoundException) => succeed
      case x => fail(s"expected a FileNotFoundException, but got $x")
    }
  }

  behavior of "ZIOXmlStream.extract"

  it should "close the input stream when the stream ends" in {
    var closed = false
    def in = new ByteArrayInputStream("<kml><Placemark><name>x</name></Placemark></kml>".getBytes) {
      override def close(): Unit = closed = true
    }
    runZIO(ZIOXmlStream.extract[Placemark]("Placemark", in).runCount) shouldBe Right(1L)
    closed shouldBe true
  }

  it should "fail the stream for a malformed document" in {
    runZIO(ZIOXmlStream.extract[Placemark]("Placemark", new ByteArrayInputStream("<kml><Placemark>".getBytes)).runDrain).isLeft shouldBe true
  }
}
