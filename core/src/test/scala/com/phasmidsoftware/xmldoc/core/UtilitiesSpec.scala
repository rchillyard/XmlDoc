package com.phasmidsoftware.xmldoc.core

import cats.effect.unsafe.implicits.global
import com.phasmidsoftware.xmldoc.core.FP.{sequence, sequenceForgiving}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.*

class UtilitiesSpec extends AnyFlatSpec with should.Matchers {

    behavior of "Utilities"

    it should "lensFilter" in {
        Utilities.lensFilter[String, Int](_.length)(2)(Seq("ab", "c", "de", "fgh")) shouldBe Seq("ab", "de")
        Utilities.lensFilter[String, Int](_.length)(5)(Seq("ab", "c")) shouldBe Nil
    }

    it should "show" in {
        val sb = new StringBuilder()
        Utilities.show(<a><b/></a>)(w => sb.append(w)).unsafeRunSync()
        sb.result() shouldBe "node: label=a, length=1, descendants=1, attributes=, children={b}"
    }

    it should "parseUnparsed" in {
        val xml = Utilities.parseUnparsed("""<b c="d">"quoted"</b>""")
        xml.label shouldBe "xml"
        xml.toString shouldBe """<xml><b c="d">"quoted"</b></xml>"""
    }

    it should "renderNodes" in {
        Utilities.renderNodes(<a/> ++ <b/>) shouldBe
          "{node: label=a, length=1, descendants=0, attributes=, children={},node: label=b, length=1, descendants=0, attributes=, children={}}"
    }

    it should "sequence" in {
        val failure = Failure[Int](new NoSuchElementException)
        val success = Success(1)
        sequence(Seq[Try[Int]]()) shouldBe Success(Seq())
        sequence(Seq(success)) shouldBe Success(Seq(1))
        sequence(Seq(success, success)) shouldBe Success(Seq(1, 1))
        sequence(Seq(failure, success)) shouldBe failure
        sequence(Seq(failure)) shouldBe failure
        sequence(Seq(success, failure)) shouldBe failure
        sequence(Seq(failure, failure)) shouldBe failure
    }

    it should "sequenceForgiving non-fatal" in {
        val sb = new StringBuilder()
        val failure = Failure[Int](new NoSuchElementException("test"))
        val success = Success(1)
        val log: Throwable => Unit = x => sb.append(s"${x.getLocalizedMessage}\n")
        sequenceForgiving(log)(Nil).isSuccess shouldBe true
        sequenceForgiving(log)(Seq(success)).isSuccess shouldBe true
        sequenceForgiving(log)(Seq(success, success)).isSuccess shouldBe true
        sb.result() shouldBe ""
        sequenceForgiving(log)(Seq(failure, success)).isSuccess shouldBe true
        sb.result() shouldBe "test\n"
        sb.clear()
        sequenceForgiving(log)(Seq(failure)).isSuccess shouldBe true
        sb.result() shouldBe "test\n"
        sb.clear()
        sequenceForgiving(log)(Seq(success, failure)).isSuccess shouldBe true
        sb.result() shouldBe "test\n"
        sb.clear()
        sequenceForgiving(log)(Seq(failure, failure)).isSuccess shouldBe true
        sb.result() shouldBe "test\ntest\n"
        sb.clear()
    }
    it should "sequenceForgiving fatal" in {
        val sb = new StringBuilder()
        val failure = Failure[Int](new OutOfMemoryError())
        val success = Success(1)
        val log: Throwable => Unit = x => sb.append(s"${x.getLocalizedMessage}\n")
        sequenceForgiving(log)(Nil).isSuccess shouldBe true
        sequenceForgiving(log)(Seq(success)).isSuccess shouldBe true
        sequenceForgiving(log)(Seq(success, success)).isSuccess shouldBe true
        sb.result() shouldBe ""
        sequenceForgiving(log)(Seq(failure, success)).isSuccess shouldBe false
        sb.result() shouldBe ""
        sequenceForgiving(log)(Seq(failure)).isSuccess shouldBe false
        sb.result() shouldBe ""
        sequenceForgiving(log)(Seq(success, failure)).isSuccess shouldBe false
        sb.result() shouldBe ""
        sequenceForgiving(log)(Seq(failure, failure)).isSuccess shouldBe false
        sb.result() shouldBe ""
    }

    it should "renderNode" in {
        val node = <a x="1"><b><c/></b></a>
        Utilities.renderNode(node) shouldBe """node: label=a, length=1, descendants=2, attributes= x="1", children={b}"""
        Utilities.renderNode(node, deep = true) shouldBe
          """node: label=a, length=1, descendants=2, attributes= x="1", children={node: label=b, length=1, descendants=1, attributes=, children={node: label=c, length=1, descendants=0, attributes=, children={}}}"""
    }

}
