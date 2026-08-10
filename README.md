[![Maven Central (core)](https://img.shields.io/maven-central/v/com.phasmidsoftware/xmldoc-core_3?label=maven-central%3A%20core)](https://central.sonatype.com/artifact/com.phasmidsoftware/xmldoc-core_3)
[![Maven Central (kml)](https://img.shields.io/maven-central/v/com.phasmidsoftware/xmldoc-kml_3?label=maven-central%3A%20kml)](https://central.sonatype.com/artifact/com.phasmidsoftware/xmldoc-kml_3)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/245437d8d4004bbe8ed41198b6f57419)](https://app.codacy.com/gh/rchillyard/XmlDoc/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
![GitHub Top Languages](https://img.shields.io/github/languages/top/rchillyard/XmlDoc)
![GitHub](https://img.shields.io/github/license/rchillyard/XmlDoc)
![GitHub last commit](https://img.shields.io/github/last-commit/rchillyard/XmlDoc)
![GitHub issues](https://img.shields.io/github/issues-raw/rchillyard/XmlDoc)
![GitHub issues by-label](https://img.shields.io/github/issues/rchillyard/XmlDoc/bug)

# XmlDoc

A Scala toolkit for reading, writing, and manipulating XML-based document formats. Originally named
KmlDoc and focused solely on KML, it's now split into a generic `core` module (XML parsing,
extraction, and rendering utilities) and format-specific modules built on top of it — currently
`kml`, for parsing, doctoring, and rendering KML files, with further formats (e.g. IDML) planned.

The reference documentation for KML is here:
https://developers.google.com/kml/documentation/kmlreference

Versions
========
Version 1.0.5 Migrate to Scala 3, ...

Version 1.0.4 Many issues fixed. All non-gx entities are implemented now.

Version 1.0.3 Fixed Issue #19, various other mostly cosmetic changes.

Version 1.0.2 Improvements to rendering and extraction with more KML objects possible.

Version 1.0.1 Improvements: first round-trip extract/render of minisample.kml

Version 1.0 Preliminary work. Most of the functionality is working but there is much still to do, especially for the KML_Samples.kml file.




