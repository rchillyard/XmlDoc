package com.phasmidsoftware.core

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
    val z = xml.child
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
    val z = xml.child

    Using(new StringWriter()) {
      writer =>
        scala.xml.XML.write(writer, xml, "UTF-8", false, null)
        println("fromXML:" + writer.toString)
    }
  }
}
