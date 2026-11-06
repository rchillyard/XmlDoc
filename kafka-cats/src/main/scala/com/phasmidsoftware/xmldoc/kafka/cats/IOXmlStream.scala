package com.phasmidsoftware.xmldoc.kafka.cats

import cats.effect.IO
import com.phasmidsoftware.xmldoc.xml.{Extractor, XmlStream}
import fs2.Stream

import java.io.{FileInputStream, InputStream}
import scala.util.Try

/**
 * Streaming extraction as an fs2 `Stream`: each element of a (large) XML document which matches a selector, extracted as
 * a `T` as soon as it has been read, so that the document is never in memory whole. See `XmlStream` (in `core`) for
 * selectors. For example, to send the Placemarks of a KML file to Kafka as they are read:
 * {{{
 *   IOXmlStream.extractFile[Placemark]("big.kml", "Placemark")
 *     .collect { case Success(p) => p }
 *     .map(toStation)
 *     .through(IOKafka.toKafka("stations", settings)(_.name))
 * }}}
 */
object IOXmlStream {

  /**
   * Method to stream the results of extracting each selected element of the document read from an input stream.
   * The input stream is opened when the stream starts, and closed when it ends.
   *
   * @param in        an IO of the input stream.
   * @param selector  a tag (e.g. `Placemark`) or a path of tags (e.g. `Folder/Placemark`).
   * @param chunkSize the number of results read at a time.
   * @tparam T the type of each selected element, which has an `Extractor`.
   * @return a stream of the results of extracting each selected element (a malformed document fails the stream).
   */
  def extract[T: Extractor](in: IO[InputStream], selector: String, chunkSize: Int = 64): Stream[IO, Try[T]] =
    for {
      i <- Stream.bracket(in)(i => IO.blocking(i.close()))
      r <- Stream.bracket(IO.blocking(XmlStream.reader(i)))(r => IO.blocking(r.close()))
      t <- Stream.fromBlockingIterator[IO](XmlStream.extract[T](r, selector), chunkSize)
    } yield t

  /**
   * Method to stream the results of extracting each selected element of an XML file.
   *
   * @param filename the name of the file.
   * @param selector a tag (e.g. `Placemark`) or a path of tags (e.g. `Folder/Placemark`).
   * @tparam T the type of each selected element, which has an `Extractor`.
   * @return a stream of the results of extracting each selected element.
   */
  def extractFile[T: Extractor](filename: String, selector: String): Stream[IO, Try[T]] =
    extract[T](IO.blocking(new FileInputStream(filename)), selector)
}
