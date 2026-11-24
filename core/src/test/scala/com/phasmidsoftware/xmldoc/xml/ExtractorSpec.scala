package com.phasmidsoftware.xmldoc.xml

import com.phasmidsoftware.xmldoc.core.FP
import com.phasmidsoftware.xmldoc.core.Text.multiExtractorBase
import com.phasmidsoftware.xmldoc.xml.Extractor.{booleanExtractor, createLazy, doubleExtractor, extractChildren, extractSequence, fieldExtractor, inferAttributeType, intExtractor, longExtractor}
import com.phasmidsoftware.xmldoc.xml.MultiExtractorBase.Positive
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

class ExtractorSpec extends AnyFlatSpec with should.Matchers {

  // Local to this spec, so that the implicit Extractor in scope is always the one the test defines.
  sealed trait Shape

  case class Circle(radius: Int) extends Shape

  case class Square(side: Int) extends Shape

  behavior of "Extractor"

  it should "extract" in {
    val target: Extractor[Int] = (node: Node) => FP.optionToTry(node.text.toIntOption, new NoSuchElementException())
    target.extract(<junk>1</junk>) shouldBe Try(1)
    target.extract(<junk>one</junk>) should matchPattern { case Failure(_) => }
  }

  it should "map" in {
    val node = <junk>1</junk>
    val ext: Extractor[Int] = (node: Node) => FP.optionToTry(node.text.toIntOption, new NoSuchElementException())
    val target: Extractor[Int] = ext.map(x => x + 1)
    target.extract(node) shouldBe Try(2)
  }

  it should "flatMap" in {
    val node = <junk>1</junk>
    val ext: Extractor[Int] = (node: Node) => FP.optionToTry(node.text.toIntOption, new NoSuchElementException())
    val target: Extractor[Int] = ext.flatMap(x => Try(x + 1))
    target.extract(node) shouldBe Try(2)
  }

  it should "parse" in {
    val node = <junk>1</junk>
    val target: Extractor[Int] = Extractor.parse(w => Try(w.toInt))
    target.extract(node) shouldBe Try(1)
  }

  it should "inferAttributeType" in {
    inferAttributeType("hello") shouldBe None
    inferAttributeType("_hello") shouldBe Some(false)
    inferAttributeType("__hello") shouldBe Some(true)
  }

  it should "createLazy" in {
    val target = createLazy(Extractor.parse(w => Try(w.toInt)))
    target.extract(<junk>1</junk>) shouldBe Try(1)
  }

  it should "booleanExtractor" in {
    val target = booleanExtractor
    target.extract(<junk>true</junk>) shouldBe Try(true)
    target.extract(<junk>false</junk>) shouldBe Try(false)
    // An empty element has empty (not absent) text content (Issue #19), and booleanExtractor's
    // own fallback already treats any unrecognized non-empty string as false too - so empty
    // content being false as well is the consistent outcome, not a special-cased failure.
    target.extract(<junk></junk>) shouldBe Try(false)
  }

  it should "doubleExtractor" in {
    val target = doubleExtractor
    target.extract(<junk>1.0</junk>) shouldBe Try(1.0)
    target.extract(<junk>X</junk>) should matchPattern { case Failure(_) => }
  }

  it should "longExtractor" in {
    val target = longExtractor
    target.extract(<junk>1</junk>) shouldBe Try(1)
    target.extract(<junk>X</junk>) should matchPattern { case Failure(_) => }
  }

  it should "fieldExtractor 1" in {
    val xml = <xml>
      <junk>1</junk>
    </xml>
    val target = fieldExtractor[Int]("junk")
    target.extract(xml) shouldBe Success(1)
  }

  it should "fieldExtractor 2" in {
    val xml = <junk>
      1
    </junk>
    val target = fieldExtractor[Int]("junk")
    target.extract(xml) shouldBe Success(1)
  }

  it should "extractChildren" in {
    val xml = <xml>
      <junk>1</junk>
    </xml>
    implicit val xxx: MultiExtractor[Seq[Int]] = multiExtractorBase[Int](Positive)
    val target: Extractor[Seq[Int]] = extractChildren[Seq[Int]]("junk")(_)
    target.extract(xml) shouldBe Success(Seq(1))
  }

  it should "intExtractor" in {
    val target = intExtractor
    target.extract(<junk>1</junk>) shouldBe Try(1)
    target.extract(<junk>X</junk>) should matchPattern { case Failure(_) => }
  }

  it should "extractSequence2" in {
    val target = extractSequence[Int](<junk>1</junk> <junk>2</junk>)
    target shouldBe Success(Seq(1, 2))
  }

  it should "extractSequence1" in {
    val target = extractSequence[Int](<junk>1</junk>)
    target shouldBe Success(Seq(1))
  }

  it should "extractElementsByLabel" in {

  }

  it should "attribute" in {

  }

  it should "apply1" in {

  }

  it should "apply2" in {

  }

  it should "optional" in {

  }

  it should "extractAll" in {

  }

  it should "unitExtractor" in {

  }

  it should "extractSingleton" in {

  }

  behavior of "Plural"

  it should "match success" in {
    val result = Plural.parseAll(Plural.endsInS, "oranges".reverse)
    result should matchPattern { case Plural.Success("egnaro", _) => }
  }

  it should "match orange" in {
    "oranges" match {
      case Plural(s) => s shouldBe "orange"
    }
  }

  it should "match empty" in {
    "empties" match {
      case Plural(s) => s shouldBe "empty"
    }
  }

  it should "not match innerBoundaryIs" in {
    "innerBoundaryIs" match {
      case Plural(_) => fail("should not match")
      case _ =>
    }
  }

  it should "match mouse" in {
    "mice" match {
      case Plural(s) => s shouldBe "mouse"
    }
  }

  // The three members #107 listed. Local types are used throughout, so that implicit resolution
  // picks up exactly the Extractor each test defines and nothing from Extractor's companion.
  behavior of "Extractor.|"

  it should "fall back to the alternative when this extractor fails" in {
    implicit val circleExtractor: Extractor[Circle] = (node: Node) => Try(Circle(node.text.toInt))
    val failing: Extractor[Shape] = (_: Node) => Failure(new NoSuchElementException("not a Shape"))
    val target: Extractor[Shape] = failing.|[Circle]()
    target.extract(<junk>3</junk>) shouldBe Success(Circle(3))
  }

  it should "not consult the alternative when this extractor succeeds" in {
    implicit val circleExtractor: Extractor[Circle] = (_: Node) => fail("the alternative should not have been consulted")
    val primary: Extractor[Shape] = (node: Node) => Try(Square(node.text.toInt))
    primary.|[Circle]().extract(<junk>3</junk>) shouldBe Success(Square(3))
  }

  it should "fail when both fail" in {
    implicit val circleExtractor: Extractor[Circle] = (_: Node) => Failure(new NoSuchElementException("no Circle"))
    val failing: Extractor[Shape] = (_: Node) => Failure(new NoSuchElementException("not a Shape"))
    // the alternative's failure is the one reported, orElse having discarded the first
    failing.|[Circle]().extract(<junk>3</junk>) should matchPattern { case Failure(_) => }
  }

  behavior of "MultiExtractor.apply"

  it should "build a MultiExtractor from a NodeSeq => Try function" in {
    val target: MultiExtractor[Seq[Int]] = MultiExtractor[Seq[Int]](ns => Try(ns.map(_.text.toInt)))
    target.extract(<xml><a>1</a><a>2</a></xml> \ "a") shouldBe Success(Seq(1, 2))
  }

  it should "propagate the function's failure" in {
    val target: MultiExtractor[Seq[Int]] = MultiExtractor[Seq[Int]](ns => Try(ns.map(_.text.toInt)))
    target.extract(<xml><a>one</a></xml> \ "a") should matchPattern { case Failure(_) => }
  }

  behavior of "SubclassExtractor"

  // The point of the TESTME: extract uses sequenceForgiving, so a label which fails is logged and
  // dropped rather than failing the whole extraction.
  it should "keep the labels which extract and forgive the one which does not" in {
    implicit val tsm: MultiExtractor[Seq[Int]] = (nodeSeq: NodeSeq) =>
      if (nodeSeq.exists(_.text == "bad")) Failure(new NoSuchElementException("bad"))
      else Try(nodeSeq.map(_.text.toInt))
    val target = new SubclassExtractor[Int](Seq("good", "rotten"))
    target.extract(<xml><good>1</good><good>2</good><rotten>bad</rotten></xml>) shouldBe Success(Seq(1, 2))
  }

  it should "succeed with nothing when every label fails" in {
    implicit val tsm: MultiExtractor[Seq[Int]] = (_: NodeSeq) => Failure(new NoSuchElementException("always"))
    val target = new SubclassExtractor[Int](Seq("good", "rotten"))
    // sequenceForgiving forgives them all, so this is a Success of no elements, not a Failure
    target.extract(<xml><good>1</good></xml>) shouldBe Success(Nil)
  }

  it should "flatten the results of several labels" in {
    implicit val tsm: MultiExtractor[Seq[Int]] = (nodeSeq: NodeSeq) => Try(nodeSeq.map(_.text.toInt))
    val target = new SubclassExtractor[Int](Seq("a", "b"))
    target.extract(<xml><a>1</a><b>2</b><a>3</a></xml>) shouldBe Success(Seq(1, 3, 2))
  }
  //  it should "extractMulti" in {
  //    val xml = <xml><junk>1</junk></xml>
  //    implicit val xxx: MultiExtractor[Int] = (nodeSeq: NodeSeq) => ???
  //    val z: Try[Int] = extractMulti[Int](xml)
  //
  //  }

}
