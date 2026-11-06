package com.phasmidsoftware.xmldoc.core

import com.phasmidsoftware.xmldoc.core.FP._
import java.io.InputStream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import scala.collection.mutable
import scala.io.Source
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FPSpec extends AnyFlatSpec with should.Matchers {

  behavior of "FP"

  it should "sequence an Iterable" in {
    sequence(List(Some(1), Some(2))) shouldBe Some(List(1, 2))
    sequence(List(Some(1), None)) shouldBe None
  }

  it should "sequenceForgivingTransform" in {

    val failure = Failure[Int](new NoSuchElementException("test"))
    val success = Success(1)

    val triedInts1: Seq[Try[Int]] = Seq()
    sequenceForgivingTransform[Int](triedInts1)(x => Success(Some(x)), _ => Success(None)) shouldBe Success(Nil)
    val triedInts2 = Seq(success, failure)
    sequenceForgivingTransform[Int](triedInts2)(x => Success(Some(x)), _ => Success(None)) shouldBe Success(List(1))
  }

  it should "optionToTry" in {

  }

  it should "indexFound" in {
    indexFound("junk", 0) shouldBe Success(0)
    indexFound("junk", -1) should matchPattern { case Failure(FPException("Header column 'junk' not found", None)) => }
  }

  it should "sequence" in {
    val try1 = Success(1)
    val try2 = Success(2)
    val try3 = Failure(FPException(""))
    sequence(Seq(try1, try2)) shouldBe Success(Seq(1, 2))
    val result: Try[Iterable[Int]] = sequence(Seq(try1, try3))
    result should matchPattern { case Failure(_) => }
  }

  it should "sequenceForgivingWith" in {
    val try2 = Success(1)
    val try3 = Success(2)
    val try1 = Failure(FPException(""))
    val sb = new mutable.StringBuilder()
    val handleException: PartialFunction[Throwable, Try[Option[Int]]] = {
      case NonFatal(x) => sb.append(s"forgiving: $x"); Success(None)
      case x => Failure(x)
    }
    val result: Try[Iterable[Int]] = sequenceForgivingWith(Seq(try1, try2, try3))(handleException)
    result should matchPattern { case Success(List(1, 2)) => }
    sb.toString shouldBe "forgiving: com.phasmidsoftware.xmldoc.core.FPException: "
  }

  it should "sequenceForgivingTransform 1" in {
    val sb = new mutable.StringBuilder()
    val handleException: PartialFunction[Throwable, Try[Option[Int]]] = {
      case NonFatal(x) => sb.append(s"forgiving: $x"); Success(None)
      case x => Failure(x)
    }
    val xs = Seq(Success(0), Success(1), Success(2))
    val result: Try[Iterable[Int]] = sequenceForgivingTransform(xs)(x => Success(Some(x + 1)), handleException)
    result should matchPattern { case Success(List(1, 2, 3)) => }
    sb.toString shouldBe ""
  }

  it should "sequenceForgivingTransform 2" in {
    val sb = new mutable.StringBuilder()
    val handleException: PartialFunction[Throwable, Try[Option[Int]]] = {
      case NonFatal(x) => sb.append(s"forgiving: $x"); Success(None)
      case x => Failure(x)
    }
    val xs = Seq(Failure(FPException("")), Success(1), Success(2))
    val result: Try[Iterable[Int]] = sequenceForgivingTransform(xs)(x => Success(Some(x + 1)), handleException)
    result should matchPattern { case Success(List(2, 3)) => }
    sb.toString shouldBe "forgiving: com.phasmidsoftware.xmldoc.core.FPException: "
  }

  it should "sequenceForgiving 0" in {
    val try2 = Success(1)
    val try3 = Success(2)
    val try1 = Failure(FPException(""))
    val result: Try[Seq[Int]] = sequenceForgiving(x => System.err.println(s"failure ignored: $x"))(Seq(try1, try2, try3))
    result should matchPattern { case Success(List(1, 2)) => }
  }

  it should "sequenceForgiving 2" in {
    val try1 = Success(1)
    val try2 = Success(2)
    val try3 = Failure(new OutOfMemoryError(""))
    val result: Try[Iterable[Int]] = sequenceForgiving(x => System.err.println(s"failure ignored: $x"))(Seq(try1, try2, try3))
    result should matchPattern { case Failure(_) => }
  }
  it should "tryToOption" in {
    val sb = new mutable.StringBuilder

    def logFunction(x: Throwable): Unit = {
      sb.append(x.getLocalizedMessage)
    }

    val f: Try[Int] => Option[Int] = tryToOption(logFunction)(_)
    f(Success(1)) shouldBe Some(1)
    sb.toString() shouldBe ""
    f(Failure(new Exception("failure"))) shouldBe None
    sb.toString() shouldBe "failure"
  }

  behavior of "TryUsing"

  it should "return success" in {
    lazy val i: InputStream = getClass.getResourceAsStream("oneLineResource.txt")
    println(i)
    val zy: Try[String] = TryUsing(Source.fromInputStream(i))(s => Try(s.getLines().toList.head))
    zy should matchPattern { case Success("Hello World!") => }
  }

  it should "return failure(0)" in {
    val wy = TryUsing(Source.fromResource(null))(s => Try(s.getLines().toList.head))
    wy should matchPattern { case Failure(_) => }
    wy.recover {
      case _: NullPointerException => Success(())
      case e => fail(s"wrong exception: $e")
    }
  }

  it should "return failure(1)" in {
    lazy val i: InputStream = getClass.getResourceAsStream(null)
    val wy = TryUsing(Source.fromInputStream(i))(s => Try(s.getLines().toList.head))
    wy should matchPattern { case Failure(_) => }
    wy.recover {
      case _: NullPointerException => Success(())
      case e => fail(s"wrong exception: $e")
    }
  }

  it should "return failure(2)" in {
    lazy val i: InputStream = getClass.getResourceAsStream("emptyResource.txt")
    val wy = TryUsing(Source.fromInputStream(i))(s => Try(s.getLines().toList.head))
    wy should matchPattern { case Failure(_) => }
    wy.recover {
      case _: NoSuchElementException => Success(())
      case e => fail(s"wrong exception: $e")
    }
  }

  behavior of "resourceForClass"
  it should "get resources in this package" in {
    resourceForClass("testFile.txt", getClass) should matchPattern { case Success(_) => }
    resourceForClass("testFile.txt") should matchPattern { case Success(_) => }
    resourceForClass(".txt", getClass) should matchPattern { case Failure(_) => }
  }

  behavior of "resource"
  it should "find a resource relative to a class" in {
    resource[FPSpec]("testFile.txt") should matchPattern { case Success(_) => }
    resource[FPSpec]("noSuchFile.txt") should matchPattern { case Failure(FPException(_, None)) => }
  }

  behavior of "sequence (Iterator)"
  it should "sequence an Iterator of Try" in {
    sequence(Iterator(Success(1), Success(2))).map(_.toList) shouldBe Success(List(1, 2))
    sequence(Iterator[Try[Int]](Success(1), Failure(FPException("bad")))) shouldBe Failure(FPException("bad"))
  }

  behavior of "partition"
  it should "partition an Iterable of Try into successes and failures" in {
    val bad = Failure(FPException("bad"))
    val (successes, failures) = partition(List[Try[Int]](Success(1), bad, Success(2)))
    successes shouldBe List(Success(1), Success(2))
    failures shouldBe List(bad)
  }
  it should "partition an Iterator of Try into successes and failures" in {
    val bad = Failure(FPException("bad"))
    val (successes, failures) = partition(Iterator[Try[Int]](Success(1), bad, Success(2)))
    successes.toList shouldBe List(Success(1), Success(2))
    failures.toList shouldBe List(bad)
  }

  behavior of "tryToOption"
  it should "turn a Success into Some, and a non-fatal Failure into None (calling f)" in {
    val sb = new StringBuilder()
    val log: Throwable => Unit = x => sb.append(x.getMessage)
    tryToOption(log)(Success(1)) shouldBe Some(1)
    sb.result() shouldBe ""
    tryToOption(log)(Failure(FPException("bad"))) shouldBe None
    sb.result() shouldBe "bad"
  }
  it should "throw a fatal Failure" in {
    an[InterruptedException] should be thrownBy tryToOption(_ => ())(Failure(new InterruptedException("fatal")))
  }

  behavior of "optionToTry and tryNotNull"
  it should "optionToTry" in {
    optionToTry(Some(1)) shouldBe Success(1)
    optionToTry(None) should matchPattern { case Failure(_: NoSuchElementException) => }
    optionToTry(None, FPException("none")) shouldBe Failure(FPException("none"))
  }
  it should "tryNotNull" in {
    tryNotNull("x")("never") shouldBe Success("x")
    tryNotNull(null: String)("missing") match {
      case Failure(x: NoSuchElementException) => x.getMessage shouldBe "tryNotNull: null: missing"
      case x => fail(s"expected a failure, but got $x")
    }
  }

  behavior of "uncurried"
  it should "uncurry functions of arity 6, 7 and 8" in {
    val f6: Int => Int => Int => Int => Int => Int => Int = a => b => c => d => e => f => a + b + c + d + e + f
    val f7: Int => Int => Int => Int => Int => Int => Int => Int = a => b => c => d => e => f => g => a + b + c + d + e + f + g
    val f8: Int => Int => Int => Int => Int => Int => Int => Int => Int = a => b => c => d => e => f => g => h => a + b + c + d + e + f + g + h
    uncurried(f6)(1, 2, 3, 4, 5, 6) shouldBe 21
    uncurried(f7)(1, 2, 3, 4, 5, 6, 7) shouldBe 28
    uncurried(f8)(1, 2, 3, 4, 5, 6, 7, 8) shouldBe 36
  }

  behavior of "mapTryGuarded"
  it should "map a Success which satisfies the predicate, and fail otherwise" in {
    val half = mapTryGuarded[Int, Int](_ % 2 == 0, "even")(_ / 2)
    half(Success(4)) shouldBe Success(2)
    half(Success(3)) shouldBe Failure(FPException("mapTryGuarded: even failed for 3"))
    half(Failure(FPException("bad"))) shouldBe Failure(FPException("bad"))
    mapTryGuarded[Int, Int](_ > 0)(identity)(Success(0)) shouldBe Failure(FPException("mapTryGuarded: predicate failed for 0"))
  }

  behavior of "TryUsing"
  it should "use a Try of a resource" in {
    var closed = false
    def resource: AutoCloseable = () => closed = true
    TryUsing(Success(resource))(_ => Success(42)) shouldBe Success(42)
    closed shouldBe true
    TryUsing(Failure[AutoCloseable](FPException("no resource")))(_ => Success(42)) shouldBe Failure(FPException("no resource"))
  }


}
