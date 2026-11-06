package com.phasmidsoftware.xmldoc.kafka.cats

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.json.KmlRecords.*
import com.phasmidsoftware.xmldoc.json.{JsonLines, SparkSchema}
import fs2.Stream
import fs2.kafka.producer.MkProducer
import fs2.kafka.{KafkaByteProducer, ProducerSettings}
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

/**
 * Tests of IOKafka, with the Kafka client's MockProducer (so no broker is needed).
 */
class IOKafkaSpec extends AnyFlatSpec with Matchers {

  import IOKafkaSpec.*

  behavior of "IOKafka.toKafka"

  it should "send the Points of a real KML file, each as a JSON message keyed by its name" in {
    val mock = mockProducer()
    given MkProducer[IO] = mkProducer(mock)
    val records = stations(samples).unsafeRunSync()
    val results = Stream.emits(records).through(IOKafka.toKafka("stations", stringSettings)(_.name)).compile.toList.unsafeRunSync()
    results.flatMap(_.toList).size shouldBe 4
    val sent = mock.history().asScala.toList
    sent.map(_.topic()).distinct shouldBe List("stations")
    sent.map(r => string(r.key())) shouldBe records.map(_.name)
    sent.map(r => string(r.value())) shouldBe records.map(JsonLines.line(_))
    string(sent.head.value()) shouldBe """{"name":"Simple placemark","point":{"lon":-122.0822035425683,"lat":37.42228990140251},"elevation":0.0}"""
    SparkSchema.ddl[Station] shouldBe "`name` STRING, `point` STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>, `elevation` DOUBLE"
  }

  it should "send messages without a key when the key type is Unit" in {
    val mock = mockProducer()
    given MkProducer[IO] = mkProducer(mock)
    Stream.emits(someStations).through(IOKafka.toKafka("stations", unitSettings)(_ => ())).compile.drain.unsafeRunSync()
    mock.history().asScala.toList.map(_.key()) shouldBe List(null, null)
    mock.history().asScala.toList.map(r => string(r.value())).last shouldBe """{"name":"Nowhere","point":{"lon":0.0,"lat":0.0},"elevation":null}"""
  }

  it should "fail when Kafka refuses a message" in {
    val mock = mockProducer()
    mock.sendException = new RuntimeException("refused")
    given MkProducer[IO] = mkProducer(mock)
    val result = Stream.emits(someStations).through(IOKafka.toKafka("stations", unitSettings)(_ => ())).compile.drain.attempt.unsafeRunSync()
    result.left.map(_.getMessage) shouldBe Left("refused")
  }
}

object IOKafkaSpec {
  val someStations: List[Station] = List(Station("Logan", Point(-71.01, 42.37), Some(6.0)), Station("Nowhere", Point(0, 0), None))

  // The bootstrap servers are never contacted, as MkProducer makes a MockProducer instead.
  val stringSettings: ProducerSettings[IO, String, String] = ProducerSettings[IO, String, String].withBootstrapServers("localhost:9092")
  val unitSettings: ProducerSettings[IO, Unit, String] = ProducerSettings[IO, Unit, String].withBootstrapServers("localhost:9092")

  // A MockProducer which acknowledges each message as soon as it is sent.
  def mockProducer(): MockProducer[Array[Byte], Array[Byte]] =
    new MockProducer[Array[Byte], Array[Byte]](true, null, new ByteArraySerializer, new ByteArraySerializer)

  def mkProducer(mock: MockProducer[Array[Byte], Array[Byte]]): MkProducer[IO] = new MkProducer[IO] {
    def apply[G[_]](settings: ProducerSettings[G, ?, ?]): IO[KafkaByteProducer] = IO.pure(mock)
  }

  def string(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)
}
