package com.phasmidsoftware.xmldoc.xml

import com.phasmidsoftware.xmldoc.core.Text
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success}
import scala.xml.{Elem, Node, NodeSeq, PCData, XML}

class XmlStreamSpec extends AnyFlatSpec with Matchers {

  import XmlStreamSpec.*

  behavior of "XmlStream.elements"

  it should "select elements by tag at any depth, in document order, skipping the rest" in {
    names(XmlStream.elements(XmlStream.reader(input(doc)), "Item")) shouldBe List("a", "b", "c", "d")
  }

  it should "select by a path of tags, or an anchored path" in {
    names(XmlStream.elements(XmlStream.reader(input(doc)), "Folder/Item")) shouldBe List("b", "c")
    names(XmlStream.elements(XmlStream.reader(input(doc)), "/root/Item")) shouldBe List("a", "d")
    names(XmlStream.elements(XmlStream.reader(input(doc)), "/Item")) shouldBe Nil
  }

  it should "not select a selected element's descendants again" in {
    XmlStream.elements(XmlStream.reader(input(doc)), "Folder").map(e => (e \ "Item").size).toList shouldBe List(2)
  }

  it should "build each element as XML.load does (text, CDATA, comments, entities, attributes)" in {
    val loaded: NodeSeq = XML.loadString(doc) \\ "Item"
    val streamed = XmlStream.elements(XmlStream.reader(input(doc)), "Item").toList
    streamed shouldBe loaded.toList
    // NOTE: Elem equality compares an atom's text but not its class (Text or PCData), so compare the classes too.
    streamed.map(shape) shouldBe loaded.toList.map(shape)
  }

  it should "keep CDATA as PCData" in {
    val b = XmlStream.elements(XmlStream.reader(input(doc)), "Item").drop(1).next()
    (b \ "name").head.child.collect { case p: PCData => p.data } shouldBe Seq("<b>")
  }

  it should "match local names, and keep the namespace bindings in scope" in {
    val es = XmlStream.elements(XmlStream.reader(input(namespaced)), "Item").toList
    es.map(_.label) shouldBe List("Item", "Item")
    es.map(_.prefix) shouldBe List(null, "x")
    es.map(_.scope.getURI(null)) shouldBe List("http://default", "http://default")
    es.map(_.scope.getURI("x")) shouldBe List("http://x", "http://x")
    names(XmlStream.elements(XmlStream.reader(input(namespaced)), "/root/Item")) shouldBe List("p", "q")
  }

  behavior of "XmlStream.extract"

  it should "extract each selected element on its own, so one failure doesn't stop the rest" in {
    val results = XmlStream.extract[Item](XmlStream.reader(input(doc)), "Item").toList
    results.map(_.map(i => (i._id, i.name.$.toString))).take(2) shouldBe List(Success(("a", "first")), Success(("b", "<b>")))
    results(2) shouldBe a[Failure[?]]
    results(3).map(i => (i._id, i.name.$.toString)) shouldBe Success(("d", "fourth <4>"))
  }

  behavior of "XmlStream.extractFrom"

  it should "apply a function to the results, closing the stream" in {
    var closed = false
    def in: InputStream = new ByteArrayInputStream(doc.getBytes(StandardCharsets.UTF_8)) {
      override def close(): Unit = closed = true
    }
    XmlStream.extractFrom[Item, Int]("Item", in)(_.count(_.isSuccess)) shouldBe Success(3)
    closed shouldBe true
  }

  it should "fail for a malformed document" in {
    XmlStream.extractFrom[Item, Int]("Item", input("<root><Item id='a'><name>x</name></Item><Item>"))(_.size) shouldBe a[Failure[?]]
  }

  it should "not read external entities" in {
    val xxe = """<?xml version="1.0"?><!DOCTYPE root [<!ENTITY e SYSTEM "file:///etc/passwd">]><root><Item id="a"><name>&e;</name></Item></root>"""
    XmlStream.extractFrom[Item, List[String]]("Item", input(xxe))(_.toList.flatMap(_.toOption).map(_.name.$.toString)) match {
      case Success(names) => names.mkString should not include "root:"
      case Failure(_) => succeed
    }
  }
}

object XmlStreamSpec {
  case class Item(_id: String, name: Text) derives Extractor

  val doc: String =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<root>
      |  <Item id="a"><name>first</name><!-- a comment --></Item>
      |  <Folder>
      |    <Item id="b"><name><![CDATA[<b>]]></name></Item>
      |    <Item id="c"><label>no name &amp; so it fails</label></Item>
      |  </Folder>
      |  <Other><nested>skipped</nested></Other>
      |  <Item id="d"><name>fourth &lt;4&gt;</name></Item>
      |</root>
      |""".stripMargin

  val namespaced: String =
    """<root xmlns="http://default" xmlns:x="http://x">
      |  <Item id="p"><name>plain</name></Item>
      |  <x:Item id="q"><name>prefixed</name></x:Item>
      |</root>
      |""".stripMargin

  def input(w: String): InputStream = new ByteArrayInputStream(w.getBytes(StandardCharsets.UTF_8))

  def names(es: Iterator[Elem]): List[String] = es.map(e => e \@ "id").toList

  // The classes of a node and its descendants.
  def shape(n: Node): String = n.getClass.getSimpleName + n.child.map(shape).mkString("[", ",", "]")
}
