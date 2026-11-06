package com.phasmidsoftware.xmldoc.kafka.zio

import com.phasmidsoftware.tableparser.kafka.zio.ZIOKafka as TableParserKafka
import com.phasmidsoftware.xmldoc.json.JsonLines
import io.circe.Encoder
import org.apache.kafka.clients.producer.RecordMetadata
import zio.kafka.producer.Producer
import zio.kafka.serde.Serializer
import zio.stream.ZPipeline

/**
 * Sending records to Kafka, one JSON message per record, with zio-kafka (ZIO and ZStream).
 *
 * The sending is TableParser's (`tableparser-kafka-zio`); the message value is the record's JSON, as
 * `JsonLines.line` writes it (and as `SparkSchema` describes it, for the Spark job which reads the topic).
 */
object ZIOKafka {

  /**
   * A serializer for a `Unit` key, i.e. for messages without a key.
   */
  val unitSerializer: Serializer[Any, Unit] = TableParserKafka.unitSerializer

  /**
   * Method to get a pipeline which sends each record to a Kafka topic, as JSON. It needs a `Producer`:
   * {{{
   *   val producer = ZLayer.scoped(Producer.make(ProducerSettings(List("localhost:9092"))))
   *   ZStream.fromIterable(stations).via(ZIOKafka.toKafka("stations", Serde.string)(_.name)).runDrain.provideLayer(producer)
   * }}}
   *
   * @param topic         the topic.
   * @param keySerializer the serializer of the key (`Serde.string`, say, or `unitSerializer` for messages without a key).
   * @param key           a function to get a record's key.
   * @tparam K the type of the key.
   * @tparam X the type of each record, which has a circe `Encoder`.
   * @return a pipeline from records to the metadata of the sent messages.
   */
  def toKafka[K, X: Encoder](topic: String, keySerializer: Serializer[Any, K])(key: X => K): ZPipeline[Producer, Throwable, X, RecordMetadata] =
    TableParserKafka.toKafkaWith(topic, keySerializer)(key, JsonLines.line[X])
}
