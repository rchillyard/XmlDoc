package com.phasmidsoftware.xml

import com.phasmidsoftware.xml.Extractor.*
import com.phasmidsoftware.xml.MultiExtractorBase.{NonNegative, Positive}
import org.scalatest.PrivateMethodTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.util.regex.Matcher
import scala.util.{Failure, Success, Try}
import scala.xml.Elem

class ExtractorsSpec extends AnyFlatSpec with should.Matchers with PrivateMethodTester {

  case class Simple1(id: Int)

  object Simple1 extends Extractors {
    implicit val extractor: Extractor[Simple1] = extractor10(apply)
  }

  case class Simple2(_id: CharSequence)

  case class Simple3(__id: String = "Hello World!")

  object Simple3 {
    def apply(__id: Option[String]): Simple3 = __id match {
      case Some(x) => new Simple3(x)
      case None => new Simple3()
    }
  }

  /**
   * A case object for testing using extractor0.
   */
  case object Empty

  /**
   * A case class with zero members for testing using extractor0.
   */
  case class Junk()

  /**
   * Case class for testing using extractor1.
   *
   * @param empties a list of Empty objects.
   */
  case class Document1(empties: Seq[Empty.type])

  /**
   * Case class similar to Document1, but has two members.
   *
   * @param _id     the identifier of this Document2A.
   * @param empties a sequence of Empty objects.
   */
  case class Document2(_id: Int, empties: Seq[Empty.type])

  /**
   * Case class similar to Document1, but has two members.
   *
   * @param _id     the identifier of this Document2A.
   * @param empties a sequence of Empty objects.
   */
  case class Document2A(_id: Int, empties: Seq[Empty.type])

  /**
   * Companion object for Document2A.
   * This allows us to have more control over how we construct an instance of Document2A.
   */
  object Document2A {
    def apply(id: CharSequence, empties: Seq[Empty.type]): Document2A = Document2A(id.toString.toInt, empties)
  }

  /**
   * Case class similar to Document2, but has two members.
   *
   * @param _id     the identifier of this Document2A.
   * @param empties a sequence of Empty objects.
   */
  case class Document2B(_id: Double, empties: Seq[Empty.type])

  /**
   * Case class similar to Document2, but has three members.
   *
   * @param _id       the identifier of this Document2A.
   * @param empties   a sequence of Empty objects.
   * @param maybejunk an optional Junk object.
   */
  case class Document3(_id: Int, maybejunk: Option[Junk], empties: Seq[Empty.type])

  import Extractors.*

  object MyExtractors extends Extractors {

    // XXX this is to demonstrate the usage of the ChildNames object.
    ChildNames.addTranslation("empties", Seq("empty"))

    implicit val extractEmpty: Extractor[Empty.type] = extractor0[Empty.type](_ => Empty)
    implicit val extractMultiEmpty: MultiExtractor[Seq[Empty.type]] = multiExtractorBase[Empty.type](Positive)
    implicit val extractJunk: Extractor[Junk] = extractor0[Junk](_ => Junk())
    implicit val extractDocument1: Extractor[Document1] = extractor01.apply(Document1)
    implicit val extractMultiDocument1: MultiExtractor[Seq[Document1]] = multiExtractorBase[Document1](Positive)
    val makeDocument2A: (CharSequence, Seq[Empty.type]) => Document2A = Document2A.apply
    implicit val extractDocument2A: Extractor[Document2A] = extractor11(makeDocument2A)
    implicit val extractDocument2: Extractor[Document2] = extractor11.apply(Document2)
    implicit val extractDocument2B: Extractor[Document2B] = extractor11.apply(Document2B)
  }

  behavior of "Extractors$"

  it should "extract normal attribute" in {
    val xml: Elem = <kml id="2.2"></kml>
    import Extractor.*
    fieldExtractor[CharSequence]("_id").extract(xml) should matchPattern { case Success("2.2") => }
  }

  it should "extract absent normal attribute" in {
    val xml: Elem = <kml></kml>
    import Extractor.*
    fieldExtractor[CharSequence]("_id").extract(xml) should matchPattern { case Failure(_) => }
  }

  it should "extract normal optional attribute" in {
    val xml: Elem = <kml id="2.2"></kml>
    import Extractor.*
    fieldExtractor[CharSequence]("__id").extract(xml) should matchPattern { case Success("2.2") => }
  }

  it should "extract absent optional attribute" in {
    val xml: Elem = <kml></kml>
    import Extractor.*
    fieldExtractor[CharSequence]("__id").extract(xml) should matchPattern { case Success(None) => }
  }

  it should "not extract reserved attribute" in {
    val xml: Elem = <kml xmlns="http://www.opengis.net/kml/2.2"></kml>
    import Extractor.*
    fieldExtractor[CharSequence]("_xmlns").extract(xml) should matchPattern { case Failure(_) => }
  }

  it should "not extract plural attribute" in {
    val xml: Elem = <kml xmlns="http://www.opengis.net/kml/2.2"></kml>
    import MyExtractors.*
    fieldExtractor[Empty.type]("documents").extract(xml) should matchPattern { case Failure(_) => }
  }

  it should "extractSequence" in {
    val xml: Elem = <xml>
      <empty></empty> <empty></empty>
    </xml>
    import MyExtractors.*
      val extractedSeq: Try[Seq[Empty.type]] = extractSequence[Empty.type](xml / "empty")
    extractedSeq should matchPattern { case Success(_ :: _ :: Nil) => }
  }

  it should "extractSingleton" in {
    val xml: Elem = <xml id="1"></xml>
    val extracted = extractSingleton[Int](xml / "@id")
    extracted shouldBe Success(1)
  }
  it should "extractSingleton2" in {
    val xml: Elem = <xml id="A"></xml>
    val extracted = extractSingleton[CharSequence](xml / "@id")
    extracted shouldBe Success("A")
  }

  it should "extractOptional1" in {
    val xml: Elem = <xml>
      <empty></empty>
    </xml>
    import MyExtractors.*
    val extracted = extractOptional[Empty.type](xml / "empty")
    extracted shouldBe Success(Empty)
  }

  it should "extractOptional2" in {
    val xml: Elem = <xml>
    </xml>
    import MyExtractors.*
    val extracted = extractOptional[Empty.type](xml / "empty")
    extracted shouldBe Success(None)
  }

  it should "extractor0A" in {
    val xml: Elem = <xml></xml>
    import MyExtractors.*
    val extracted = extractor0[Empty.type](_ => Empty).extract(xml)
    extracted shouldBe Success(Empty)
  }

  it should "extractor0B" in {
    val xml: Elem = <xml></xml>
    import MyExtractors.*
    val extracted = extractor0[Junk](_ => Junk()).extract(xml)
    extracted shouldBe Success(Junk())
  }

  it should "extractor10A" in {
    val xml: Elem = <xml>
      <id>1</id>
    </xml>
    import MyExtractors.*
    val extracted = extractor10(Simple1.apply).extract(xml)
    extracted shouldBe Success(Simple1(1))
  }

  it should "extractor10B" in {
    val xml: Elem = <xml id="1"></xml>
    import MyExtractors.*
    val extracted = extractor10.apply(Simple2).extract(xml)
    extracted shouldBe Success(Simple2("1"))
  }

  it should "extractor10C" in {
    import MyExtractors.*
    val xml: Elem = <xml></xml>
    val construct: Option[String] => Simple3 = Simple3.apply
    val extracted: Try[Simple3] = extractor10(construct).extract(xml)
    extracted.isSuccess shouldBe true
    val result: Simple3 = extracted.get
    result.__id shouldBe "Hello World!"
  }

  it should "multiExtractorBase[Document1]" in {
    val xml: Elem = <xml>
      <document1>
        <empty></empty> <empty></empty>
      </document1>
    </xml>
    import MyExtractors.*
    val extracted: Try[Seq[Document1]] = implicitly[MultiExtractor[Seq[Document1]]].extract(xml / "document1")
    extracted shouldBe Success(List(Document1(List(Empty, Empty))))
  }

  it should "extractor2A" in {
    val xml: Elem = <xml id="1">
      <empty></empty> <empty></empty>
    </xml>
    import MyExtractors.*
    val extracted = implicitly[Extractor[Document2A]].extract(xml)
    extracted shouldBe Success(Document2A(1, List(Empty, Empty)))
  }

  it should "extractor2 (1)" in {
    val xml: Elem = <xml id="1">
      <empty></empty> <empty></empty>
    </xml>
    import MyExtractors.*
    val extracted = implicitly[Extractor[Document2]].extract(xml)
    extracted shouldBe Success(Document2(1, List(Empty, Empty)))
  }

  it should "extractor2B" in {
    val xml: Elem = <xml id="1.0">
      <empty></empty> <empty></empty>
    </xml>
    import MyExtractors.*
    val extracted = implicitly[Extractor[Document2B]].extract(xml)
    extracted shouldBe Success(Document2B(1.0, List(Empty, Empty)))
  }

  it should "extractor3A" in {
    val xml: Elem = <xml id="1">
      <empty></empty> <empty></empty>
    </xml>
    import MyExtractors.*
    implicit val extractMaybeJunk: Extractor[Option[Junk]] = extractorOption
    implicit val extractDocument3: Extractor[Document3] = extractor21.apply(Document3)
    val extracted = implicitly[Extractor[Document3]].extract(xml)
    extracted shouldBe Success(Document3(1, None, List(Empty, Empty)))
  }

  // TODO Issue #22 this should be just like the previous test
  ignore should "extractor3B" in {
    val xml: Elem = <xml id="1">
      <empty></empty> <empty></empty>
      <junk></junk>
    </xml>
    import MyExtractors.*
    implicit val extractMaybeJunk: Extractor[Option[Junk]] = extractorOption
    implicit val extractDocument3: Extractor[Document3] = extractor21.apply(Document3)
    val extracted = implicitly[Extractor[Document3]].extract(xml)
    extracted shouldBe Success(Document3(1, Some(Junk()), List(Empty, Empty)))
  }

  it should "match attribute" in {
    attribute.matches("_id") shouldBe true
    val matcher: Matcher = attribute.pattern.matcher("_id")
    matcher.matches() shouldBe true
    matcher.groupCount() shouldBe 1
    matcher.group(0) shouldBe "_id"
    matcher.group(1) shouldBe "id"
  }

  it should "match optional attribute" in {
    attribute.matches("__id") shouldBe true
    val matcher: Matcher = optionalAttribute.pattern.matcher("__id")
    matcher.matches() shouldBe true
    matcher.groupCount() shouldBe 1
    matcher.group(0) shouldBe "__id"
    matcher.group(1) shouldBe "id"
  }

  it should "match optional" in {
    optional.matches("xs") shouldBe false
    val matcher: Matcher = optional.pattern.matcher("maybeXs")
    matcher.matches() shouldBe true
    matcher.groupCount() shouldBe 1
    matcher.group(0) shouldBe "maybeXs"
    matcher.group(1) shouldBe "Xs"
    optional.unapplySeq("maybeXs") shouldBe Some(List("xs"))
  }

  it should "fieldExtractor String" in {
    val we = fieldExtractor[CharSequence]("_id")
    we.extract(<xml id="xyz"></xml>) shouldBe Success("xyz")
  }

  it should "fieldExtractor Int" in {
    val ie: Extractor[Int] = fieldExtractor[Int]("_id")
    ie.extract(<xml id="1"></xml>) shouldBe Success(1)
  }

  it should "fieldExtractor Boolean" in {
    val be: Extractor[Boolean] = fieldExtractor[Boolean]("_ok")
    be.extract(<xml ok="true"></xml>) shouldBe Success(true)
  }

  it should "fieldExtractor Double" in {
    val de: Extractor[Double] = fieldExtractor[Double]("_weight")
    de.extract(<xml weight="42.0"></xml>) shouldBe Success(42)
  }

  it should "fieldExtractor Long" in {
    val le: Extractor[Long] = fieldExtractor[Long]("_id")
    le.extract(<xml id="42"></xml>) shouldBe Success(42)
  }

  behavior of "lift"

  it should "extractOptional1" in {
    val xml: Elem = <xml id="1">
      <empty></empty> <empty></empty>
    </xml>
    import MyExtractors.*
    implicit val extractMaybeJunk: Extractor[Option[Junk]] = implicitly[Extractor[Junk]].lift
    implicit val extractDocument3: Extractor[Document3] = extractor21.apply(Document3)
    val extractor = implicitly[Extractor[Document3]]
    extractor.extract(xml) shouldBe Success(Document3(1, None, List(Empty, Empty)))
  }

  it should "extractOptional2" in {
    val xml: Elem = <xml id="1">
      <empty></empty> <empty></empty>
    </xml>
    import MyExtractors.*
    implicit val extractMaybeJunk: Extractor[Option[Junk]] = extractorOption
    implicit val extractDocument3: Extractor[Document3] = extractor21.apply(Document3)
    val extractor = implicitly[Extractor[Document3]]
    extractor.lift.extract(xml) shouldBe Success(Some(Document3(1, None, List(Empty, Empty))))
  }

  behavior of "Extractors"

  // CONSIDER add tests for extractor1, etc.

  it should "extractorIterable" in {
    implicit val ee: Extractor[Empty.type] = MyExtractors.extractor0[Empty.type](_ => Empty)
    val xml: Elem = <xml>
      <empty></empty> <empty></empty>
    </xml>
    val extractedSeq: Try[Iterable[Empty.type]] = MyExtractors.extractorIterable[Empty.type]("empty").extract(xml)
    extractedSeq should matchPattern { case Success(_ :: _ :: Nil) => }
  }

  //noinspection ScalaUnusedSymbol
  it should "extractorEnum" in {
    object Shapes extends Enumeration with Extractors {
      type Shape = Value
      val Rectangle, Cylinder, Sphere = Value
      val extractor: Extractor[Shape] = extractorEnum[Shape, this.type](this)(s => s.toLowerCase)
      private val xml = <shape>rectangle</shape>
      extractor.extract(xml) shouldBe Success(Rectangle)
    }
  }

  case class MyContainer(simple1: Simple1, simple2: Simple1, simple4s: Seq[Simple4])

  object MyContainer {
    implicit val extractor: Extractor[MyContainer] = extractor21(apply)
    implicit val extractorSeq: MultiExtractor[Seq[MyContainer]] = multiExtractorBase[MyContainer](NonNegative)
  }


  case class Simple4(x: Int)

  object Simple4 extends Extractors {
    implicit val extractor: Extractor[Simple4] = extractor10(apply)
    implicit val extractorSeq: MultiExtractor[Seq[Simple4]] = multiExtractorBase[Simple4](Positive)
  }

  // NOTE Test for Issue #23 (supposedly)
  it should "extract MyContainer without inner boundary" in {
    val xml = <xml>
      <Polygon>
        <simple1>1</simple1>
        <simple1>2</simple1>
      </Polygon>
    </xml>
    extractAll[Seq[MyContainer]](xml) match {
      case Success(_) =>
      case Failure(x) => fail("could not extract MyContainer", x)
    }
  }

}
