package com.phasmidsoftware.kmldoc

import cats.effect.IO
import cats.implicits._
import com.phasmidsoftware.args.Args
import com.phasmidsoftware.core.FP.mapTryGuarded
import com.phasmidsoftware.kmldoc.KMLCompanion.renderKMLs
import com.phasmidsoftware.kmldoc.KMLEditor.{addExtension, write}
import com.phasmidsoftware.render.FormatXML
import org.slf4j.{Logger, LoggerFactory}

import java.io.{BufferedWriter, File, FileWriter, Writer}
import scala.annotation.tailrec
import scala.io.Source
import scala.util._

/**
 * Case class to represent a set of KmlEdit objects.
 *
 * @param edits a sequence of edits.
 */
case class KMLEditor(edits: Seq[KmlEdit]) {

  KMLEditor.logger.info(s"""KMLEditor: ${edits.mkString(", ")}""")

  /**
   * Method to process the file defined by baseFilename by parsing it, editing it, and writing it out.
   * The input file extension is ".kml" and the output file suffix is "_out.kml"
   *
   * @param filename the input filename, including the extension (".kml")
   * @return an IO[Unit] which needs to be run.
   */
  def process(filename: Try[String]): IO[Unit] = {
    val kml = """.kml"""
    val baseFilename = filename flatMap (f => if (f.endsWith(kml)) Success(f.replaceAll("""\""" + kml, "")) else Failure(new Exception("Syntax error: filename does not end with .kml")))
    val inputFile = addExtension(baseFilename, kml)
    val outExt = "_out" + kml
    val outputFile = addExtension(baseFilename, outExt)
    inputFile foreach (f => KMLEditor.logger.info(s"KMLEditor.process from $f"))
    outputFile foreach (f => KMLEditor.logger.info(s"KMLEditor.process to $f"))
    processFromTo(inputFile, outputFile)
  }

  /**
   * Method to process the file defined by baseFilename by parsing it, editing it, and writing it out.
   * The input file extension is ".kml" and the output file suffix is "_out.kml"
   *
   * @param inputFile  the input file name, wrapped in Try.
   * @param outputFile the output file name, wrapped in Try.
   * @return an IO[Unit] which needs to be run.
   */
  private def processFromTo(inputFile: Try[String], outputFile: Try[String]): IO[Unit] = {
    val qsi: IO[Seq[Writer]] = for {
      w <- IO.fromTry(outputFile)
      f <- IO(new File(w))
      bW = new BufferedWriter(new FileWriter(f, false))
      ks <- KMLCompanion.loadKML(inputFile)
      ks2 = processKMLs(ks)
      ws <- renderKMLs(ks2, FormatXML(0))
      qs <- write(bW, ws).sequence
    } yield qs

    qsi map (qs => qs.reduce((q, _) => q)) flatMap (q => IO(q.close()))
  }

  /**
   * Method to process a sequence of KML objects.
   * In practice, there is only ever one such object in a KML file.
   *
   * @param ks a sequence of KML.
   * @return a sequence of KML, quite possibly different from the input sequence.
   */
  def processKMLs(ks: Seq[KML]): Seq[KML] = ks map processKML

  /**
   * Method to process a KML object.
   *
   * @param k a KML.
   * @return a sequence of KML, quite possibly different from the input sequence.
   */
  def processKML(k: KML): KML = {
    @tailrec
    def inner(r: KML, es: Seq[KmlEdit]): KML = es match {
      case Nil => r
      case e :: _es => inner(r.edit(e).getOrElse(r), _es)
    }

    inner(k, edits to List)
  }
}

/**
 * Singleton object providing functionalities to parse and process KML files.
 */
object KMLEditor {
  /**
   * Logger instance for the KMLEditor object.
   * Used to log messages and events related to the processing and editing of KML files.
   */
  val logger: Logger = LoggerFactory.getLogger(KMLEditor.getClass)

  /**
   * Method to construct a KMLEditor from a filename.
   *
   * @param wy the filename of the edits (wrapped in Try).
   * @return a KMLEditor, wrapped in IO.
   */
  def parse(wy: Try[String]): IO[KMLEditor] = for {
    w <- IO.fromTry(wy)
    s = Source.fromFile(w)
    es <- KmlEdit.parseLines(s.getLines())
    kE = KMLEditor(es filter (_.isValid))
  } yield kE

  /**
   * Method to edit a KML file.
   *
   * @param way the command line arguments, wrapped in Try.
   * @return an IO[Unit] which should be run.
   */
  def processKML(way: Try[Args[String]]): IO[Unit] = {
    val wsy = mapTryGuarded[Args[String], Seq[String]](_.size > 1, "size > 1")(_.operands)(way)
    for {
      editor <- parse(wsy map (_.last))
      result <- editor.process(wsy map (_.head))
    } yield result
  }

  /**
   * Writes a sequence of strings to a buffered writer, returning a sequence of IO operations
   * that represent the writing actions for each string.
   *
   * @param bW the BufferedWriter where the strings will be written.
   * @param ws the sequence of strings to write to the BufferedWriter.
   * @return a sequence of IO operations containing the Writer after each string has been appended.
   */
  private def write(bW: BufferedWriter, ws: Seq[String]): Seq[IO[Writer]] = for (w <- ws) yield IO(bW.append(w))

  /**
   * Appends a provided extension to the string contained within a Try, if the Try is successful.
   *
   * @param triedBasename the base name wrapped in a Try. If successful, contains the string to which the extension will be appended.
   * @param ext           the extension to append to the base name.
   * @return a Try containing the resulting string with the appended extension if the input Try is successful,
   *         otherwise propagates the failure of the input Try.
   */
  private def addExtension(triedBasename: Try[String], ext: String): Try[String] = triedBasename map (_ + ext)
}
