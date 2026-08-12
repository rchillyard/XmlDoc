package com.phasmidsoftware.xmldoc.xml

import com.phasmidsoftware.xmldoc.core.{TryUsing, XmlException}

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files}
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Try}

/**
 * Utility for reading the contents of a zip archive (such as an IDML package) in memory,
 * without extracting it to disk. This is deliberately format-agnostic: it knows nothing
 * about IDML's internal structure, only how to list and read the entries of a zip file.
 */
object Zip {

  /**
   * Method to yield the names of all entries in the given zip file, in the order the
   * zip file declares them.
   *
   * @param file the zip file.
   * @return a Try of Seq[String] containing the entry names.
   */
  def entries(file: File): Try[Seq[String]] =
    TryUsing(new ZipFile(file))(zf => Try(zf.entries().asScala.map(_.getName).toSeq))

  /**
   * Method to read the content of a specific entry within a zip file, decoded as UTF-8 text.
   *
   * @param file      the zip file.
   * @param entryName the name of the entry to read (e.g. "designmap.xml").
   * @return a Try of String containing the entry's content.
   */
  def readEntry(file: File, entryName: String): Try[String] =
    TryUsing(new ZipFile(file)) {
      zf =>
        Option(zf.getEntry(entryName)) match {
          case Some(entry) => Try(new String(zf.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8))
          case None => Failure(XmlException(s"Zip.readEntry: no such entry: $entryName"))
        }
    }

  /**
   * Method to write (creating or overwriting) the content of a specific entry within an
   * existing zip file, encoded as UTF-8 text. Unlike `readEntry`, this does mutate `file`.
   *
   * NOTE: `java.util.zip.ZipFile` is read-only, so this uses the JDK's zip filesystem
   * provider instead, which supports updating a single entry in place without having to
   * rewrite the rest of the archive.
   *
   * @param file      the zip file (must already exist).
   * @param entryName the name of the entry to write (e.g. "designmap.xml").
   * @param content   the new content of the entry.
   * @return a Try[Unit], Success if the entry was written.
   */
  def writeEntry(file: File, entryName: String, content: String): Try[Unit] = {
    val uri = URI.create("jar:" + file.toURI)
    TryUsing(FileSystems.newFileSystem(uri, Map("create" -> "false").asJava)) {
      zipfs =>
        Try {
          val path = zipfs.getPath(entryName)
          Option(path.getParent).foreach(Files.createDirectories(_))
          Files.write(path, content.getBytes(StandardCharsets.UTF_8))
          ()
        }
    }
  }
}
