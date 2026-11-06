package com.phasmidsoftware.xmldoc.xml

import com.phasmidsoftware.xmldoc.core.{Text, TryUsing}
import com.phasmidsoftware.xmldoc.render.*
import com.phasmidsoftware.xmldoc.render.Renderers.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.annotation.nowarn
import scala.util.{Success, Try}
import scala.xml.{Elem, XML}

// NOTE: these types are top-level (not local to the spec) so that they can use `derives`.

// Attributes and text.
case class Reading(_hour: Int, $: Double) derives Extractor, Renderer

// No given instances: Elevation is derived wherever it's needed (here, as Option[Elevation] in Station).
case class Elevation($: Double)

// An attribute, a required child, an optional child, and repeated children.
case class Station(_id: String, name: Text, maybeElevation: Option[Elevation], Readings: Seq[Reading]) derives Extractor, Renderer

// The auxiliary-parameter pattern.
case class Meta(name: Text, maybeNote: Option[Text]) derives Extractor, Renderer

case class Tagged(value: Text)(val meta: Meta) derives Extractor, Renderer

// A sealed hierarchy (with an auxiliary parameter in one subtype, and a sealed sub-trait).
sealed trait Shape

case class Circle(_r: Double) extends Shape derives Extractor, Renderer

sealed trait Polygonal extends Shape

case class Square(_side: Double)(val meta: Meta) extends Polygonal derives Extractor, Renderer

case class Triangle(_a: Double, _b: Double, _c: Double) extends Polygonal derives Extractor, Renderer

object Shape {
  given MultiExtractor[Seq[Shape]] = MultiExtractor.derived

  given Renderer[Shape] = Renderer.derived
}

case class Drawing(Shape: Seq[Shape]) derives Extractor, Renderer

// A recursive sealed hierarchy: needs no createLazy.
sealed trait Tree

case class Leaf(_value: Int) extends Tree derives Extractor, Renderer

case class Branch(_label: String, Tree: Seq[Tree]) extends Tree derives Extractor, Renderer

object Tree {
  given MultiExtractor[Seq[Tree]] = MultiExtractor.derived

  given Renderer[Tree] = Renderer.derived
}

// For comparison with the (deprecated) ladder.
case class Pair2(_key: String, value: Text)

@nowarn("cat=deprecation")
object Pair2Ladder extends Extractors with Renderers {
  val extractor: Extractor[Pair2] = extractor20(Pair2.apply)
  val renderer: Renderer[Pair2] = renderer2(Pair2.apply)
}

class DerivationSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Extractor.derived and Renderer.derived"

  private def render[T: Renderer](t: T): Try[String] = TryUsing(StateR())(sr => implicitly[Renderer[T]].render(t, FormatXML(), sr))

  // Render, re-parse (a genuine parse), and extract again.
  private def roundTrip[T: {Extractor, Renderer}](t: T): Try[T] =
    for {
      w <- render(t)
      node <- Try(XML.loadString(w))
      result <- Extractor.extract[T](node)
    } yield result

  private val station: Elem =
    <station id="S1">
      <name>Logan</name>
      <elevation>6.0</elevation>
      <Reading hour="1">12.5</Reading>
      <Reading hour="2">13.0</Reading>
    </station>

  it should "extract attributes and text" in {
    Extractor.extract[Reading](<Reading hour="3">1.5</Reading>) shouldBe Success(Reading(3, 1.5))
  }

  it should "extract an attribute, required, optional and repeated children" in {
    Extractor.extract[Station](station) shouldBe Success(Station("S1", Text("Logan"), Some(Elevation(6.0)), Seq(Reading(1, 12.5), Reading(2, 13.0))))
  }

  it should "extract an absent optional child as None" in {
    Extractor.extract[Station](<station id="S2"><name>Back Bay</name><Reading hour="1">2.0</Reading></station>) shouldBe Success(Station("S2", Text("Back Bay"), None, Seq(Reading(1, 2.0))))
  }

  it should "fail to extract a plural field with no children, as the ladder does (unless its tag is registered with TagProperties.addMustMatch)" in {
    Extractor.extract[Station](<station id="S2"><name>Back Bay</name></station>).isFailure shouldBe true
  }

  it should "fail to extract when a required child is missing" in {
    Extractor.extract[Station](<station id="S3"/>).isFailure shouldBe true
  }

  it should "round-trip a Station" in {
    val s = Extractor.extract[Station](station).get
    roundTrip(s) shouldBe Success(s)
  }

  it should "render a Station" in {
    val s = Station("S1", Text("Logan"), Some(Elevation(6.0)), Seq(Reading(1, 12.5)))
    val w = render(s).get
    w should include("""id="S1"""")
    w should include("<name>Logan</name>")
    w should include("<elevation>6</elevation>")
    w should include("""<Reading hour="1">12.5</Reading>""")
  }

  it should "extract the auxiliary parameter from the same node" in {
    val t = Extractor.extract[Tagged](<tagged><value>v</value><name>n</name><note>x</note></tagged>).get
    t shouldBe Tagged(Text("v"))(Meta(Text("n"), Some(Text("x"))))
    // NOTE equality of case classes ignores the second parameter list.
    t.meta shouldBe Meta(Text("n"), Some(Text("x")))
  }

  it should "round-trip the auxiliary parameter" in {
    val t = Tagged(Text("v"))(Meta(Text("n"), None))
    val r = roundTrip(t).get
    r shouldBe t
    r.meta shouldBe t.meta
  }

  it should "extract a sealed hierarchy in document order" in {
    val d = Extractor.extract[Drawing](<drawing><Square side="2"><name>sq</name></Square><Circle r="1"/><Triangle a="3" b="4" c="5"/><junk/></drawing>).get
    d.Shape shouldBe Seq(Square(2)(Meta(Text("sq"), None)), Circle(1), Triangle(3, 4, 5))
  }

  it should "round-trip a sealed hierarchy" in {
    val d = Drawing(Seq(Circle(1.5), Square(2)(Meta(Text("sq"), None)), Triangle(3, 4, 5)))
    val r = roundTrip(d).get
    r shouldBe d
    r.Shape(1).asInstanceOf[Square].meta shouldBe Meta(Text("sq"), None)
  }

  it should "round-trip a recursive hierarchy" in {
    val t = Branch("root", Seq(Leaf(1), Branch("inner", Seq(Leaf(2), Leaf(3))), Leaf(4)))
    roundTrip(t) shouldBe Success(t)
  }

  it should "extract and render exactly as the ladder does" in {
    given Extractor[Pair2] = Extractor.derived
    given Renderer[Pair2] = Renderer.derived
    val node = <pair key="k"><value>v</value></pair>
    Extractor.extract[Pair2](node) shouldBe Pair2Ladder.extractor.extract(node)
    val p = Pair2("k", Text("v"))
    render(p) shouldBe TryUsing(StateR())(sr => Pair2Ladder.renderer.render(p, FormatXML(), sr))
  }
}
