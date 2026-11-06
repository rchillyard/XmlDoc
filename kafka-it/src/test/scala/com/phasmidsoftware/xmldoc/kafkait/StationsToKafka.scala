package com.phasmidsoftware.xmldoc.kafkait

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.json.KmlRecords.{samples, stationsOf}
import com.phasmidsoftware.xmldoc.kafka.cats.{IOKafka, IOXmlStream}
import com.phasmidsoftware.xmldoc.kml.Placemark
import fs2.Stream
import fs2.kafka.ProducerSettings

import scala.util.Success

/**
 * The whole pipeline of KAFKA.md: stream the Placemarks of a KML file, make a Station of each Point,
 * and send each Station to a Kafka topic as a JSON message (keyed by its name), as the file is read.
 */
object StationsToKafka {

  /**
   * Method to send the Stations of a KML file to a Kafka topic.
   *
   * @param filename the path of a KML file.
   * @param servers  the Kafka bootstrap servers (e.g. `localhost:9092`).
   * @param topic    the topic.
   * @return an IO of the number of Stations sent.
   */
  def send(filename: String, servers: String, topic: String): IO[Long] = {
    val settings = ProducerSettings[IO, String, String].withBootstrapServers(servers)
    IOXmlStream.extractFile[Placemark](filename, "Placemark")
      .collect { case Success(p) => p }
      .flatMap(p => Stream.emits(stationsOf(p)))
      .through(IOKafka.toKafka(topic, settings)(_.name))
      .compile.count
  }
}

/**
 * Sends the Stations of a KML file (by default, KML_Samples.kml) to the topic `stations` of the broker at
 * `localhost:9092` (by default: the one which `docker compose up -d` starts). From the root of the build:
 * {{{
 *   sbt "kafkaIt/Test/runMain com.phasmidsoftware.xmldoc.kafkait.sendStations"
 *   sbt "kafkaIt/Test/runMain com.phasmidsoftware.xmldoc.kafkait.sendStations my.kml localhost:9092"
 * }}}
 *
 * @param args an optional KML file and optional bootstrap servers.
 */
@main def sendStations(args: String*): Unit = {
  val filename = args.headOption.getOrElse(samples)
  val servers = args.lift(1).getOrElse("localhost:9092")
  val n = StationsToKafka.send(filename, servers, "stations").unsafeRunSync()
  println(s"Sent $n stations from $filename to the topic stations at $servers")
}
