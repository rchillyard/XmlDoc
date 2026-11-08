package com.phasmidsoftware.xmldoc.kml

import com.sun.net.httpserver.HttpServer
import java.io.{File, FileOutputStream}
import java.net.{InetSocketAddress, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.{JarEntry, JarOutputStream}
import scala.io.Source
import scala.util.Using

/**
 * Sources of KML at URLs other than plain file: ones, for tests: a local HTTP server, a jar, and a file whose path has
 * a space. Adapted from TableParser's LocalSources (its issue #128).
 */
object LocalSources {

  /**
   * The contents of a resource (of the kml package), as a String.
   *
   * @param name the name of the resource, such as "minisample.kml".
   * @return its contents.
   */
  def resourceContents(name: String): String =
    Using.resource(Source.fromURL(KML.getClass.getResource(name), "UTF-8"))(_.mkString)

  /**
   * Serve some files over HTTP, from a server on a free port of localhost, while f runs.
   *
   * @param files the files, each a path (such as "/minisample.kml") and its contents.
   * @param f     a function of the server's base URL (such as http://localhost:51234).
   * @tparam X the result type.
   * @return the result of f.
   */
  def withHttpServer[X](files: Map[String, String])(f: String => X): X = {
    val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    files foreach { case (path, contents) =>
      server.createContext(path, exchange => {
        val bytes = contents.getBytes(StandardCharsets.UTF_8)
        exchange.getResponseHeaders.add("Content-Type", "application/vnd.google-earth.kml+xml")
        exchange.sendResponseHeaders(200, bytes.length.toLong)
        Using.resource(exchange.getResponseBody)(_.write(bytes))
      })
    }
    server.start()
    try f(s"http://localhost:${server.getAddress.getPort}")
    finally server.stop(0)
  }

  /**
   * Make a (temporary) jar which holds one file, and give the jar: URL of that file.
   *
   * @param name     the name of the file in the jar.
   * @param contents its contents.
   * @return a URL such as jar:file:/tmp/sources123.jar!/minisample.kml.
   */
  def jarURL(name: String, contents: String): URL = {
    val jar = File.createTempFile("sources", ".jar")
    jar.deleteOnExit()
    Using.resource(new JarOutputStream(new FileOutputStream(jar))) { out =>
      out.putNextEntry(new JarEntry(name))
      out.write(contents.getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
    }
    java.net.URI.create(s"jar:${jar.toURI}!/$name").toURL
  }

  /**
   * Make a (temporary) file, in a directory whose name has a space, and give its file: URL (in which each space is %20).
   *
   * @param name     the name of the file (which may also have a space).
   * @param contents its contents.
   * @return the file's URL.
   */
  def fileWithSpaceURL(name: String, contents: String): URL = {
    val dir = Files.createTempDirectory("xmldoc sources").toFile
    // Files registered later with deleteOnExit are deleted first, so the directory is registered before its file.
    dir.deleteOnExit()
    val file = new File(dir, name)
    file.deleteOnExit()
    Files.writeString(file.toPath, contents, StandardCharsets.UTF_8)
    file.toURI.toURL
  }
}
