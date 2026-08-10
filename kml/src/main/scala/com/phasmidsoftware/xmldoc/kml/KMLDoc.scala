package com.phasmidsoftware.xmldoc.kml

import com.phasmidsoftware.args.Args

/**
 * Entry point for processing KML files with specified edits.
 *
 * Takes two command-line arguments: the base name of the KML file to be processed
 * and the filename containing the edits to apply.
 *
 * If the required command-line arguments are not provided, an error message is displayed.
 * When the correct inputs are provided, the KML file is processed by delegating to the `KMLEditor` object.
 */
@main
def KMLDoc(args: String*): Unit =
  if (args.length < 2) System.err.println(s"Syntax: KMLDoc basename edits")
  else {
    import cats.effect.unsafe.implicits.global

    val arguments = Args.parse(args.toArray)
    KMLEditor.processKML(arguments).unsafeRunSync()
  }
