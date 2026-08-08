package com.phasmidsoftware.kmldoc

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class KMLEditParserSpec extends AnyFlatSpec with should.Matchers {

  behavior of "KMLEditParser"

  val parser: KMLEditParser = KMLEditParser.apply
  val placemark = "Placemark"

  it should "blank" in {
    val result: parser.ParseResult[String] = parser.parseAll(parser.blank, "  ")
    result.successful shouldBe true
    result.get shouldBe "  "
  }

  it should "quotedString" in {
    val result: parser.ParseResult[String] = parser.parseAll(parser.quotedString, """"A B"""")
    result.successful shouldBe true
    result.get shouldBe "A B"
  }

  it should "element" in {
    val elementString = """A "B""""
    val result: parser.ParseResult[Element] = parser.parseAll(parser.element, elementString)
    result.successful shouldBe true
    result.get shouldBe Element("A", "B")
  }

  it should "line 0" in {
    val result: parser.ParseResult[KmlEdit] = parser.parseAll(parser.line, "C W X")
    result.successful shouldBe true
    result.get shouldBe KmlEdit("C", 0, Element("W", "X"), None)
  }
  it should "line 1" in {
    val result: parser.ParseResult[KmlEdit] = parser.parseAll(parser.line, "C W X Y Z")
    result.successful shouldBe false
  }
  it should "line 2" in {
    val result: parser.ParseResult[KmlEdit] = parser.parseAll(parser.line, "C W X with Y Z")
    result.successful shouldBe true
    result.get shouldBe KmlEdit("C", 0, Element("W", "X"), Some(Element("Y", "Z")))
  }
  it should "line 3" in {
    val result: parser.ParseResult[KmlEdit] = parser.parseAll(parser.line, """C W "X" with Y "Z"""")
    result.successful shouldBe true
    result.get shouldBe KmlEdit("C", 0, Element("W", "X"), Some(Element("Y", "Z")))
  }
  it should "line 4" in {
    val inputString = s"""join $placemark "Medford Branch (#1)" with $placemark "Medford Branch (#2)""""
    val result: parser.ParseResult[KmlEdit] = parser.parseAll(parser.line, inputString)
    result.successful shouldBe true
    result.get shouldBe KmlEdit("join", 2, Element(placemark, "Medford Branch (#1)"), Some(Element(placemark, "Medford Branch (#2)")))
  }

  it should "parseEdit" in {

  }

}
