package com.phasmidsoftware.xmldoc.kafka.zio

import com.phasmidsoftware.xmldoc.xml.{Extractor, XmlStream}
import zio.ZIO
import zio.stream.ZStream

import java.io.{FileInputStream, InputStream}
import scala.util.Try

/**
 * Streaming extraction as a ZIO `ZStream`: each element of a (large) XML document which matches a selector, extracted as
 * a `T` as soon as it has been read, so that the document is never in memory whole. See `XmlStream` (in `core`) for
 * selectors. For example, to send the Placemarks of a KML file to Kafka as they are read:
 * {{{
 *   ZIOXmlStream.extractFile[Placemark]("big.kml", "Placemark")
 *     .collect { case Success(p) => p }
 *     .map(toStation)
 *     .via(ZIOKafka.toKafka("stations", Serde.string)(_.name))
 * }}}
 */
object ZIOXmlStream {

  /**
   * The number of results read at a time (as in `IOXmlStream`), which bounds how many extracted elements the stream holds.
   * (Without it, `ZStream.fromIterator` would read ZIO's default chunk, 4096 elements, at a time.)
   */
  val chunkSize: Int = 64

  /**
   * Method to stream the results of extracting each selected element of the document read from an input stream.
   * The input stream is opened when the stream starts, and closed when it ends.
   * The elements are read `chunkSize` at a time.
   *
   * @param selector a tag (e.g. `Placemark`) or a path of tags (e.g. `Folder/Placemark`).
   * @param in       the input stream (evaluated when the stream starts).
   * @tparam T the type of each selected element, which has an `Extractor`.
   * @return a stream of the results of extracting each selected element (a malformed document fails the stream).
   */
  def extract[T: Extractor](selector: String, in: => InputStream): ZStream[Any, Throwable, Try[T]] =
    for {
      i <- ZStream.acquireReleaseWith(ZIO.attemptBlocking(in))(i => ZIO.attemptBlocking(i.close()).orDie)
      r <- ZStream.acquireReleaseWith(ZIO.attemptBlocking(XmlStream.reader(i)))(r => ZIO.attemptBlocking(r.close()).orDie)
      t <- ZStream.blocking(ZStream.fromIterator(XmlStream.extract[T](r, selector), chunkSize))
    } yield t

  /**
   * Method to stream the results of extracting each selected element of an XML file.
   *
   * @param filename the name of the file.
   * @param selector a tag (e.g. `Placemark`) or a path of tags (e.g. `Folder/Placemark`).
   * @tparam T the type of each selected element, which has an `Extractor`.
   * @return a stream of the results of extracting each selected element.
   */
  def extractFile[T: Extractor](filename: String, selector: String): ZStream[Any, Throwable, Try[T]] =
    extract[T](selector, new FileInputStream(filename))
}
