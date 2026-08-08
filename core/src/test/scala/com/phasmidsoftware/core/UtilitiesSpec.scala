package com.phasmidsoftware.core

import com.phasmidsoftware.core.FP.{sequence, sequenceForgiving}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.*

class UtilitiesSpec extends AnyFlatSpec with should.Matchers {

    behavior of "Utilities"

    it should "lensFilter" in {

    }

    it should "show" in {

    }

    it should "parseUnparsed" in {

    }

    it should "renderNodes" in {

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

    }

}
