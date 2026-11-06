package com.phasmidsoftware.xmldoc.kafka.cats

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.json.JsonLines
import com.phasmidsoftware.xmldoc.json.KmlRecords.*
import com.phasmidsoftware.xmldoc.kml.Placemark
import fs2.kafka.producer.MkProducer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, FileNotFoundException, InputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Success

class IOXmlStreamSpec extends AnyFlatSpec with Matchers {

  import IOKafkaSpec.*

  behavior of "IOXmlStream.extractFile"

  it should "stream the Placemarks of a real KML file, giving the same records as whole-document extraction" in {
    val streamed = IOXmlStream.extractFile[Placemark](samples, "Placemark").compile.toList.unsafeRunSync()
    streamed.forall(_.isSuccess) shouldBe true
    streamed.flatMap(_.toOption).flatMap(stationsOf) shouldBe stations(samples).unsafeRunSync()
  }

  it should "send the streamed records to Kafka as they are read" in {
    val mock = mockProducer()
    given MkProducer[IO] = mkProducer(mock)
    IOXmlStream.extractFile[Placemark](samples, "Placemark")
      .collect { case Success(p) => p }
      .flatMap(p => fs2.Stream.emits(stationsOf(p)))
      .through(IOKafka.toKafka("stations", stringSettings)(_.name))
      .compile.drain.unsafeRunSync()
    mock.history().asScala.toList.map(r => string(r.value())) shouldBe stations(samples).unsafeRunSync().map(JsonLines.line(_))
  }

  it should "fail for a missing file" in {
    IOXmlStream.extractFile[Placemark]("no-such-file.kml", "Placemark").compile.drain.attempt.unsafeRunSync() match {
      case Left(_: FileNotFoundException) => succeed
      case x => fail(s"expected a FileNotFoundException, but got $x")
    }
  }

  behavior of "IOXmlStream.extract"

  it should "close the input stream when the stream ends" in {
    var closed = false
    val in: IO[InputStream] = IO(new ByteArrayInputStream("<kml><Placemark><name>x</name></Placemark></kml>".getBytes) {
      override def close(): Unit = closed = true
    })
    IOXmlStream.extract[Placemark]("Placemark", in).compile.toList.unsafeRunSync().size shouldBe 1
    closed shouldBe true
  }

  it should "fail the stream for a malformed document" in {
    val in: IO[InputStream] = IO(new ByteArrayInputStream("<kml><Placemark>".getBytes))
    IOXmlStream.extract[Placemark]("Placemark", in).compile.drain.attempt.unsafeRunSync().isLeft shouldBe true
  }
}
