package com.phasmidsoftware.xmldoc.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.StringWriter
import scala.util.Using

class XMLSpec extends AnyFlatSpec with should.Matchers {

  behavior of "XML"

  it should "parse BalloonStyle" in {
    val xml = <BalloonStyle>
      <text>Hello World!</text>
    </BalloonStyle>

    xml.toString shouldBe
      """<BalloonStyle>
        |      <text>Hello World!</text>
        |    </BalloonStyle>""".stripMargin
    Using(new StringWriter()) {
      sw =>
        scala.xml.XML.write(sw, xml, "UTF-8", false, null)
        println(sw.toString)
    }
  }

  it should "parse BalloonStyle with CDATA" in {
    val xml = <BalloonStyle>
      <text>
        <![CDATA[<h3>$[name]</h3>]]>
      </text>
    </BalloonStyle>

    xml.toString shouldBe
      """<BalloonStyle>
        |      <text>
        |        &lt;h3&gt;$[name]&lt;/h3&gt;
        |      </text>
        |    </BalloonStyle>""".stripMargin

    Using(new StringWriter()) {
      writer =>
        scala.xml.XML.write(writer, xml, "UTF-8", false, null)
        println("fromXML:" + writer.toString)
    }
  }

  behavior of "Text"

  it should "merge plain text with a space" in {
    (Text("a") merge Text("b")) shouldBe Some(Text("a b"))
  }

  it should "merge two CDATA texts as CDATA" in {
    (Text(CDATA("a")) merge Text(CDATA("b"))) shouldBe Some(Text(CDATA("ab")))
  }

  it should "match a name" in {
    Text("Logan").matches("Logan") shouldBe true
    Text("Logan").matches("Boston") shouldBe false
    Text("").matches("") shouldBe false
    Text(CDATA("Logan")).matches("Logan") shouldBe true
  }

  it should "be equal only to a Text with equal content" in {
    Text("a") shouldBe Text("a")
    Text("a") should not be Text("b")
    Text("a").equals("a") shouldBe false
  }

  behavior of "CDATA"

  it should "be a CharSequence of its content" in {
    val c = CDATA("hello", "\n", "\n")
    c.length() shouldBe 5
    c.charAt(1) shouldBe 'e'
    c.subSequence(1, 3).toString shouldBe "el"
    c.content shouldBe "hello"
  }

  it should "render as XML" in {
    CDATA("<b>x</b>").toXML shouldBe scala.util.Success("<![CDATA[<b>x</b>]]>")
    CDATA("x").toString shouldBe "x"
    CDATA.wrapped("x").content shouldBe "x"
  }

  it should "merge, keeping the outer prefix and postfix" in {
    (CDATA("a") merge CDATA("b")) shouldBe Some(CDATA("ab"))
  }

  it should "be extracted from a node whose only child is CDATA" in {
    CDATA.unapply(<text>{PCData("a<b")}</text>) shouldBe Some(CDATA("a<b"))
  }

  it should "be extracted from a node whose CDATA is surrounded by text" in {
    CDATA.unapply(<text>{scala.xml.Text("\n")}{PCData("a&b")}{scala.xml.Text("\n")}</text>).map(_.content) shouldBe Some("a&b")
  }

  it should "not be extracted from a node with plain text" in {
    CDATA.unapply(<text>plain</text>) shouldBe None
  }

  behavior of "PCData"

  it should "render as a CDATA section, splitting any ]]>" in {
    PCData("a<b").toString shouldBe "<![CDATA[a<b]]>"
    PCData("x]]>y").toString shouldBe "<![CDATA[x]]]]><![CDATA[>y]]>"
  }

  it should "unapply only text which needs escaping" in {
    PCData.unapply(<a>x &amp; y</a>) shouldBe Some("x & y")
    PCData.unapply(<a>x</a>) shouldBe None
  }

  behavior of "XmlException"

  it should "have a message and no cause" in {
    val x = XmlException("oops")
    x.getMessage shouldBe "oops"
    x.getCause shouldBe null
  }
}
