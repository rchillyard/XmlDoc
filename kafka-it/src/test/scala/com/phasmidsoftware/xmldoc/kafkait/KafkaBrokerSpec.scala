package com.phasmidsoftware.xmldoc.kafkait

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.json.JsonLines
import com.phasmidsoftware.xmldoc.json.KmlRecords.*
import com.phasmidsoftware.xmldoc.kafka.cats.IOKafka
import com.phasmidsoftware.xmldoc.kafka.zio.ZIOKafka
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Duration
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Integration tests of IOKafka and ZIOKafka: each sends the Points of a real KML file, as JSON, to a Kafka broker
 * (started in this JVM), and reads them back.
 */
class KafkaBrokerSpec extends AnyFlatSpec with Matchers {

  import KafkaBrokerSpec.*

  private val records = stations(samples).unsafeRunSync()

  behavior of "IOKafka.toKafka (with a broker)"

  it should "send records which a consumer can read back as JSON" in {
    EmbeddedKafka.withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 0, controllerPort = 0)) { config =>
      val servers = s"localhost:${config.kafkaPort}"
      val settings = fs2.kafka.ProducerSettings[IO, String, String].withBootstrapServers(servers)
      fs2.Stream.emits(records).through(IOKafka.toKafka("stations-cats", settings)(_.name)).compile.drain.unsafeRunSync()
      consume(servers, "stations-cats", records.size).map(r => (r.key(), r.value())) shouldBe records.map(s => (s.name, JsonLines.line(s)))
    }
  }

  behavior of "StationsToKafka (the pipeline of KAFKA.md, with a broker)"

  it should "stream a KML file's Stations to a topic, which a consumer can read back as JSON" in {
    EmbeddedKafka.withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 0, controllerPort = 0)) { config =>
      val servers = s"localhost:${config.kafkaPort}"
      StationsToKafka.send(samples, servers, "stations-streamed").unsafeRunSync() shouldBe records.size
      consume(servers, "stations-streamed", records.size).map(r => (r.key(), r.value())) shouldBe records.map(s => (s.name, JsonLines.line(s)))
    }
  }

  behavior of "ZIOKafka.toKafka (with a broker)"

  it should "send records which a consumer can read back as JSON" in {
    EmbeddedKafka.withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 0, controllerPort = 0)) { config =>
      val servers = s"localhost:${config.kafkaPort}"
      val producer = zio.ZLayer.scoped(zio.kafka.producer.Producer.make(zio.kafka.producer.ProducerSettings(List(servers))))
      val sent = zio.stream.ZStream.fromIterable(records).via(ZIOKafka.toKafka("stations-zio", zio.kafka.serde.Serde.string)(_.name)).runDrain.provideLayer(producer)
      zio.Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(sent).getOrThrowFiberFailure())
      consume(servers, "stations-zio", records.size).map(r => (r.key(), r.value())) shouldBe records.map(s => (s.name, JsonLines.line(s)))
    }
  }
}

object KafkaBrokerSpec {
  // Read n records from the start of the topic (or as many as arrive within ten seconds).
  def consume(servers: String, topic: String, n: Int): List[ConsumerRecord[String, String]] = {
    val properties: Map[String, AnyRef] = Map("bootstrap.servers" -> servers, "group.id" -> "KafkaBrokerSpec", "auto.offset.reset" -> "earliest")
    Using.resource(new KafkaConsumer[String, String](properties.asJava, new StringDeserializer, new StringDeserializer)) { consumer =>
      consumer.subscribe(List(topic).asJava)
      val deadline = System.currentTimeMillis() + 10000
      LazyList.continually(consumer.poll(Duration.ofMillis(500)).asScala.toList)
        .scanLeft(List.empty[ConsumerRecord[String, String]])(_ ++ _)
        .find(rs => rs.size >= n || System.currentTimeMillis() > deadline)
        .getOrElse(Nil)
    }
  }
}
