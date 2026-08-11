package com.phasmidsoftware.xmldoc.xml

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import scala.util.Success

class GenericElementSpec extends AnyFlatSpec with should.Matchers {

  private val idmlFile = new File(getClass.getResource("HelloWorld.idml").toURI)

  // Round-tripping doesn't guarantee byte-identical XML text (attribute-quoting style and
  // entity-encoding choices are normalized), only that a second round trip is a fixed point.
  private def roundTrip(content: GenericContent): GenericContent =
    GenericElement.fromNode(scala.xml.XML.loadString(GenericElement.toXmlString(content)))

  behavior of "GenericElement.fromNode / toXmlString, synthetic content"

  it should "round-trip a simple element with no attributes or children" in {
    val original = GenericElement.fromNode(<foo/>)
    roundTrip(original) shouldBe original
  }

  it should "round-trip an element with attributes, preserving their order" in {
    val original = GenericElement.fromNode(<foo a="1" b="2" c="3"/>)
    original shouldBe GenericElement("foo", Seq("a" -> "1", "b" -> "2", "c" -> "3"), Nil)
    roundTrip(original) shouldBe original
  }

  it should "round-trip nested elements and text content" in {
    val original = GenericElement.fromNode(<foo><bar>Hello World!</bar></foo>)
    original shouldBe GenericElement("foo", Nil, Seq(GenericElement("bar", Nil, Seq(GenericText("Hello World!")))))
    roundTrip(original) shouldBe original
  }

  it should "escape text and attribute values that contain XML metacharacters" in {
    val original = GenericElement("foo", Seq("a" -> """<b & "c">"""), Seq(GenericText("""<x & y>""")))
    val rendered = GenericElement.toXmlString(original)
    rendered should not include "<b & \"c\">"
    roundTrip(original) shouldBe original
  }

  it should "preserve a tab character in an attribute value" in {
    // XML 1.0 attribute-value normalization replaces a *literal* tab/newline/CR in an
    // attribute value with a plain space when parsed - only a character reference survives.
    // designmap.xml has exactly this: EndnoteSeparatorText="&#x9;".
    val original = GenericElement("foo", Seq("a" -> "before\tafter"), Nil)
    GenericElement.toXmlString(original) should include("&#9;")
    roundTrip(original) shouldBe original
  }

  it should "round-trip a qualified (namespaced) tag name as a literal string" in {
    val node = scala.xml.XML.loadString("""<idPkg:Story xmlns:idPkg="http://ns.adobe.com/AdobeInDesign/idml/1.0/packaging"/>""")
    val original = GenericElement.fromNode(node)
    original.asInstanceOf[GenericElement].tag shouldBe "idPkg:Story"
    roundTrip(original) shouldBe original
  }

  behavior of "GenericElement.fromNode / toXmlString, CDATA"

  it should "keep CDATA distinct from plain text" in {
    val node = scala.xml.XML.loadString("<foo><![CDATA[<not-a-tag>]]></foo>")
    val original = GenericElement.fromNode(node)
    original shouldBe GenericElement("foo", Nil, Seq(GenericCData("<not-a-tag>")))
    GenericElement.toXmlString(original) should include("<![CDATA[<not-a-tag>]]>")
    roundTrip(original) shouldBe original
  }

  it should "split a CDATA section whose content contains the terminator ']]>'" in {
    // A CDATA section can't contain "]]>" literally, so this can only be represented as two
    // adjacent CDATA sections in valid XML - and re-parsing genuinely yields two separate
    // GenericCData nodes (that's an inherent XML limitation, not a round-trip bug), so the
    // fixed-point property from the other tests doesn't apply here. What must still hold:
    // valid, parseable XML, and the concatenated text content is preserved exactly.
    val original = GenericElement("foo", Nil, Seq(GenericCData("weird ]]> content")))
    val reparsed = roundTrip(original).asInstanceOf[GenericElement] // throws if toXmlString produced invalid XML
    reparsed.children should matchPattern { case Seq(GenericCData(_), GenericCData(_)) => }
    reparsed.children.collect { case GenericCData(text) => text }.mkString shouldBe "weird ]]> content"
  }

  behavior of "GenericElement.fromNode / toXmlString, against the real HelloWorld.idml sample"

  it should "round-trip designmap.xml in full" in {
    Zip.readEntry(idmlFile, "designmap.xml") match {
      case Success(text) =>
        val original = GenericElement.fromNode(scala.xml.XML.loadString(text))
        roundTrip(original) shouldBe original
      case f => fail(f.toString)
    }
  }

  it should "correctly decode an entity-encoded attribute value from designmap.xml" in {
    // The source has SingleQuotes="&apos;&apos;" DoubleQuotes="&quot;&quot;" on this element;
    // a correct parse decodes those to two literal ' and two literal " characters respectively.
    Zip.readEntry(idmlFile, "designmap.xml") match {
      case Success(text) =>
        val document = GenericElement.fromNode(scala.xml.XML.loadString(text)).asInstanceOf[GenericElement]
        val noLanguage = document.children.collectFirst {
          case e: GenericElement if e.attributes.contains("Name" -> "$ID/[No Language]") => e
        }
        noLanguage shouldBe defined
        noLanguage.get.attributes should contain("SingleQuotes" -> "''")
        noLanguage.get.attributes should contain("DoubleQuotes" -> "\"\"")
      case f => fail(f.toString)
    }
  }

  it should "round-trip the actual story text (Hello World!)" in {
    Zip.readEntry(idmlFile, "Stories/Story_ue6.xml") match {
      case Success(text) =>
        val original = GenericElement.fromNode(scala.xml.XML.loadString(text))
        GenericElement.toXmlString(original) should include("Hello World!")
        roundTrip(original) shouldBe original
      case f => fail(f.toString)
    }
  }

  it should "round-trip the real CDATA-bearing content in Resources/Graphic.xml" in {
    Zip.readEntry(idmlFile, "Resources/Graphic.xml") match {
      case Success(text) =>
        val original = GenericElement.fromNode(scala.xml.XML.loadString(text))
        GenericElement.toXmlString(original) should include("<![CDATA[AAAAAUBv4AAAAAAAAAAAAAAAAAAAAAAAAAAAAA==]]>")
        roundTrip(original) shouldBe original
      case f => fail(f.toString)
    }
  }
}
