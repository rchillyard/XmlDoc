package com.phasmidsoftware.xml

import com.phasmidsoftware.core.FP
import com.phasmidsoftware.core.Text.multiExtractorBase
import com.phasmidsoftware.xml.Extractor.{booleanExtractor, createLazy, doubleExtractor, extractChildren, extractSequence, fieldExtractor, inferAttributeType, intExtractor, longExtractor}
import com.phasmidsoftware.xml.MultiExtractorBase.Positive
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.{Failure, Success, Try}
import scala.xml.Node

class ExtractorSpec extends AnyFlatSpec with should.Matchers {

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
    target.extract(<junk></junk>) should matchPattern { case Failure(_) => }
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
      case Plural(s) => fail("should not match")
      case _ =>
    }
  }

  it should "match mouse" in {
    "mice" match {
      case Plural(s) => s shouldBe "mouse"
    }
  }
  //  it should "extractMulti" in {
  //    val xml = <xml><junk>1</junk></xml>
  //    implicit val xxx: MultiExtractor[Int] = (nodeSeq: NodeSeq) => ???
  //    val z: Try[Int] = extractMulti[Int](xml)
  //
  //  }

}
