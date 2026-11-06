package com.phasmidsoftware.xmldoc.kafka.zio

import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.json.JsonLines
import com.phasmidsoftware.xmldoc.json.KmlRecords.*
import org.apache.kafka.clients.producer.{MockProducer, RecordMetadata}
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.stream.{ZPipeline, ZStream}
import zio.{Chunk, Exit, Runtime, Unsafe, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

/**
 * Tests of ZIOKafka, with the Kafka client's MockProducer (so no broker is needed).
 */
class ZIOKafkaSpec extends AnyFlatSpec with Matchers {

  import ZIOKafkaSpec.*

  behavior of "ZIOKafka.toKafka"

  it should "send the Points of a real KML file, each as a JSON message keyed by its name" in {
    val mock = mockProducer()
    val records = stations(samples).unsafeRunSync()
    send(mock)(ZStream.fromIterable(records), ZIOKafka.toKafka("stations", Serde.string)(_.name)).map(_.size) shouldBe Right(4)
    val sent = mock.history().asScala.toList
    sent.map(_.topic()).distinct shouldBe List("stations")
    sent.map(r => string(r.key())) shouldBe records.map(_.name)
    sent.map(r => string(r.value())) shouldBe records.map(JsonLines.line(_))
  }

  it should "send messages without a key with unitSerializer" in {
    val mock = mockProducer()
    send(mock)(ZStream.fromIterable(someStations), ZIOKafka.toKafka("stations", ZIOKafka.unitSerializer)(_ => ())).map(_.size) shouldBe Right(2)
    mock.history().asScala.toList.map(_.key()) shouldBe List(null, null)
    mock.history().asScala.toList.map(r => string(r.value())).last shouldBe """{"name":"Nowhere","point":{"lon":0.0,"lat":0.0},"elevation":null}"""
  }

  it should "fail when Kafka refuses a message" in {
    val mock = mockProducer()
    mock.sendException = new RuntimeException("refused")
    send(mock)(ZStream.fromIterable(someStations), ZIOKafka.toKafka("stations", ZIOKafka.unitSerializer)(_ => ())).left.map(_.getMessage) shouldBe Left("refused")
  }
}

object ZIOKafkaSpec {
  val someStations: List[Station] = List(Station("Logan", Point(-71.01, 42.37), Some(6.0)), Station("Nowhere", Point(0, 0), None))

  // A MockProducer which acknowledges each message as soon as it is sent.
  def mockProducer(): MockProducer[Array[Byte], Array[Byte]] =
    new MockProducer[Array[Byte], Array[Byte]](true, null, new ByteArraySerializer, new ByteArraySerializer)

  def producer(mock: MockProducer[Array[Byte], Array[Byte]]): ZLayer[Any, Throwable, Producer] =
    ZLayer.scoped(Producer.fromJavaProducer(mock, ProducerSettings()))

  def send[X](mock: MockProducer[Array[Byte], Array[Byte]])(xs: ZStream[Any, Throwable, X], pipeline: ZPipeline[Producer, Throwable, X, RecordMetadata]): Either[Throwable, Chunk[RecordMetadata]] =
    runZIO(xs.via(pipeline).runCollect.provideLayer(producer(mock)))

  def runZIO[A](z: ZIO[Any, Throwable, A]): Either[Throwable, A] = Unsafe.unsafe { implicit u =>
    Runtime.default.unsafe.run(z) match {
      case Exit.Success(a) => Right(a)
      case Exit.Failure(cause) => Left(cause.squash)
    }
  }

  def string(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)
}
