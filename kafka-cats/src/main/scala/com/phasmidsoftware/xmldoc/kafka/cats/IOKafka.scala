package com.phasmidsoftware.xmldoc.kafka.cats

import cats.effect.IO
import com.phasmidsoftware.tableparser.kafka.cats.IOKafka as TableParserKafka
import com.phasmidsoftware.xmldoc.json.JsonLines
import fs2.Pipe
import fs2.kafka.producer.MkProducer
import fs2.kafka.{ProducerResult, ProducerSettings}
import io.circe.Encoder

/**
 * Sending records to Kafka, one JSON message per record, with fs2-kafka (cats-effect `IO` and fs2 streams).
 *
 * The sending is TableParser's (`tableparser-kafka-cats`); the message value is the record's JSON, as
 * `JsonLines.line` writes it (and as `SparkSchema` describes it, for the Spark job which reads the topic).
 */
object IOKafka {

  /**
   * Method to get a pipe which sends each record to a Kafka topic, as JSON.
   * {{{
   *   val settings = ProducerSettings[IO, String, String].withBootstrapServers("localhost:9092")
   *   Stream.emits(stations).through(IOKafka.toKafka("stations", settings)(_.name)).compile.drain
   * }}}
   *
   * @param topic    the topic.
   * @param settings the producer settings (with the key type `K`: use `Unit` for messages without a key).
   * @param key      a function to get a record's key.
   * @param mk       how to make the producer (the default makes a real one, from the settings).
   * @tparam K the type of the key.
   * @tparam X the type of each record, which has a circe `Encoder`.
   * @return a pipe from records to the results of sending them.
   */
  def toKafka[K, X: Encoder](topic: String, settings: ProducerSettings[IO, K, String])(key: X => K)(using mk: MkProducer[IO]): Pipe[IO, X, ProducerResult[K, String]] =
    TableParserKafka.toKafkaWith(topic, settings)(key, JsonLines.line[X])
}
