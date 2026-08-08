package com.phasmidsoftware.kmldoc

import cats.effect.IO
import cats.effect.IO.fromTry
import com.phasmidsoftware.core.FP.tryNotNull
import com.phasmidsoftware.kmldoc.KMLCompanion.renderKMLToPrintStream
import com.phasmidsoftware.render.{Format, FormatText}
import com.phasmidsoftware.xml.Extractor.extractMulti
import org.slf4j.{Logger, LoggerFactory}

import java.io.PrintStream
import java.net.URL
import scala.util.Try
import scala.xml.{Elem, XML}

/**
 * Entry point to render and format KML content from a specified resource file
 * and output the result to the standard console.
 *
 * This program utilizes an effectful process to load, render, and format the KML
 * content using library methods, then writes the formatted output to the standard
 * output stream.
 * The rendering and formatting logic is provided by `renderKMLToPrintStream`.
 */
@main
def TestKML(): Unit = {
  import cats.effect.unsafe.implicits.global

  renderKMLToPrintStream("sample.kml", FormatText(0)).unsafeRunSync()
}

/**
 * Companion helper object for the `KML` class.
 * Provides utility methods for loading, rendering,
 * and manipulating KML objects and their features.
 *
 * CONSIDER merging this with object `KML`.
 * NOTE that this object depends on cats effect `IO`.
 */
object KMLCompanion {

  import cats.implicits._

  val logger: Logger = LoggerFactory.getLogger(KML.getClass)

  /**
   * Renders KML content from the specified resource, formats it according to the provided format,
   * and writes the output to the given PrintStream.
   *
   * @param resourceName the name of the resource containing the KML content to be rendered.
   * @param format       the format to be applied to the KML content.
   * @param printStream  the output PrintStream where the formatted content will be written. Defaults to System.out.
   * @return an IO action that, when executed, performs the rendering and output operation.
   */
  def renderKMLToPrintStream(resourceName: String, format: Format, printStream: PrintStream = System.out): IO[Unit] = renderKMLAsFormat(resourceName, format) map (_.mkString("\n")) map printStream.println

  /**
   * Renders KML content from a specified resource and applies the given format, returning the results as a sequence of strings.
   *
   * @param resourceName the name of the resource containing the KML content to be rendered.
   * @param format       the format to be applied to the KML content.
   * @return an IO action that yields a sequence of formatted strings when executed.
   */
  def renderKMLAsFormat(resourceName: String, format: Format): IO[Seq[String]] = for {
    fs <- KMLCompanion.loadKML(KML.getClass.getResource(resourceName))
    ws <- renderKMLs(fs, format)
  } yield ws

  /**
   * Note that this mechanism won't work if, as we wouldn't expect, there is more than one KML element.
   *
   * @param ks     the KML elements.
   * @param format the desired format.
   * @return a Seq[String] wrapped in IO.
   */
  def renderKMLs(ks: Seq[KML], format: Format): IO[Seq[String]] = (ks map (k => renderKML(k, format))).sequence

  /**
   * Renders a `KML` object to a formatted string based on the specified format.
   *
   * @param k      the `KML` object to be rendered.
   * @param format the format specification used to control the rendering of the KML.
   * @return an `IO` action that produces a formatted KML string when executed.
   */
  private def renderKML(k: KML, format: Format): IO[String] = fromTry(KML.renderKml(k, format))

//  def renderFeatures(fs: Seq[Feature], format: Format): IO[Seq[String]] = (fs map (f => renderFeature(f, format))).sequence
//
//  private def renderFeature(f: Feature, format: Format): IO[String] = fromTry(TryUsing(StateR())(sr => implicitly[Renderer[Feature]].render(f, format, sr)))

  /**
   * Loads KML content from the specified resource URL, processes it, and returns a sequence of KML objects.
   *
   * @param resource the URL pointing to the KML resource to be loaded.
   * @return an IO action that yields a sequence of KML objects when executed.
   */
  def loadKML(resource: URL): IO[Seq[KML]] = loadKML(
    for {
      u <- tryNotNull(resource)(s"resource $resource is null")
      p <- tryNotNull(u.getPath)(s"$resource yielded empty filename")
    } yield p
  )

  /**
   * Loads a KML file from the given filename encapsulated in a Try. If the filename is valid, the method reads and processes the KML content.
   *
   * @param triedFilename a Try-wrapped string representing the filename of the KML file to be loaded.
   * @return an IO action that, when executed, yields a sequence of KML objects.
   */
  def loadKML(triedFilename: Try[String]): IO[Seq[KML]] =
    for {
      filename <- fromTry(triedFilename)
      elem <- IO(XML.loadFile(filename))
      fs <- fromTry(extractKML(elem))
    } yield fs


//  def extractFeatures(xml: Elem): Try[Seq[Feature]] = {
//    val kys: Seq[Try[Seq[Feature]]] = for (kml <- xml \\ "kml") yield Extractor.extractAll[Seq[Feature]](kml)
//    kys.headOption match {
//      case Some(ky) => ky
//      case _ => Failure(new NoSuchElementException)
//    }
//  }

  /**
   * Extracts a sequence of `KML` objects from the given XML element.
   *
   * @param xml the XML element from which `KML` objects are to be extracted.
   * @return a `Try` containing a sequence of `KML` objects if the extraction is successful, or a Failure if an error occurs.
   */
  private def extractKML(xml: Elem): Try[Seq[KML]] = extractMulti[Seq[KML]](xml)
}

