package com.phasmidsoftware.xmldoc.render

import com.phasmidsoftware.xmldoc.core.TryUsing
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Success

class RenderersSpec extends AnyFlatSpec with should.Matchers {

  case class Greeting($: String)

  case object MyJunk

  case class Complex(_r: Double, _i: Double)

  case class KV(_k: String, _v: Int)

  case class KVV(_k: String, _v: Int, _b: Boolean)

  case class KVVV(_k: String, _v: Int, _b: Boolean, _x: Double)

  case class KVVVV(_k: String, _v: Int, _b: Boolean, _x: Double, _l: Long)

  case class AuxData(id: Option[String])

  case class WithAuxData($: Double)(val auxData: AuxData)

  behavior of "Renderers (FormatText)"

  import Renderers._

  it should "renderer0" in {
    object MyRenderers extends Renderers {
      implicit val rendererMyJunk: Renderer[MyJunk.type] = renderer0
    }
    import MyRenderers._
    val wy = TryUsing(StateR())(sr => rendererMyJunk.render(MyJunk, FormatText(0), sr))
    wy shouldBe Success("MyJunk${}")
  }

  it should "renderer1" in {
    object MyRenderers extends Renderers {
      val rendererGreeting: Renderer[Greeting] = renderer1.apply(Greeting.apply)
    }
    val wy = TryUsing(StateR())(sr => MyRenderers.rendererGreeting.render(Greeting("Hello"), FormatText(1), sr))
      wy shouldBe Success("Greeting{Hello}")
  }

  it should "renderer2A" in {
    object ComplexRenderers extends Renderers {
      val rendererComplex: Renderer[Complex] = renderer2.apply(Complex.apply)
    }
    val wy = TryUsing(StateR())(sr => ComplexRenderers.rendererComplex.render(Complex(1, -1), FormatText(0), sr))
      wy shouldBe Success("""Complex{r="1" i="-1"}""")
  }

  it should "renderer2B" in {
    object KVRenderers extends Renderers {
      val rendererKV: Renderer[KV] = renderer2.apply(KV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVRenderers.rendererKV.render(KV("a", -1), FormatText(0), sr))
    wy shouldBe Success("""KV{k="a" v="-1"}""")
  }

  it should "renderer4" in {
    object KVVVRenderers extends Renderers {
      val rendererKVVV: Renderer[KVVV] = renderer4.apply(KVVV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVVVRenderers.rendererKVVV.render(KVVV("a", -1, _b = false, math.Pi), FormatText(0), sr))
    wy shouldBe Success("""KVVV{k="a" v="-1" b="0" x="3.141592653589793"}""")
  }

  it should "intRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.intRenderer.render(1, FormatText(0), sr))
    wy shouldBe Success("1")
  }

  it should "stringRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.stringRenderer.render("Hello", FormatText(0), sr))
    wy shouldBe Success("Hello")
  }

  it should "booleanRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.booleanRenderer.render(true, FormatText(0), sr))
    wy shouldBe Success("1")
  }

  it should "doubleRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.doubleRenderer.render(math.Pi, FormatText(0), sr))
    wy shouldBe Success("3.141592653589793")
  }

  it should "doubleRenderer (whole)" in {
    val wy = TryUsing(StateR())(sr => Renderers.doubleRenderer.render(42, FormatText(0), sr))
    wy shouldBe Success("42")
  }

  // CONSIDER eliminating this test
  it should "sequenceRenderer" in {
    object MyRenderers extends Renderers {
      implicit val rendererIntSeq: Renderer[Seq[Int]] = sequenceRenderer[Int]
    }
    import MyRenderers._
    val wy = TryUsing(StateR())(sr => rendererIntSeq.render(Seq(42, 99, 1), FormatText(0), sr))
    wy shouldBe
            Success(
              """[42
                |99
                |1]""".stripMargin)
  }

  it should "renderer5" in {
    object KVVVRenderers extends Renderers {
      val rendererKVVV: Renderer[KVVVV] = renderer5.apply(KVVVV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVVVRenderers.rendererKVVV.render(KVVVV("a", -1, _b = false, math.Pi, 42L), FormatText(0), sr))
    wy shouldBe Success("""KVVVV{k="a" v="-1" b="0" x="3.141592653589793" l="42"}""")
  }

  it should "optionRenderer" in {
    object MyRenderers extends Renderers {
      implicit val rendererIntOption: Renderer[Option[Int]] = optionRenderer[Int]
    }
    import MyRenderers._
    val wy = TryUsing(StateR())(sr => rendererIntOption.render(Some(42), FormatText(0), sr))
    wy shouldBe Success("42")
  }

  it should "longRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.longRenderer.render(42L, FormatText(0), sr))
    wy shouldBe Success("42")
  }

  it should "renderer3" in {
    object KVVRenderers extends Renderers {
      val rendererKVV: Renderer[KVV] = renderer3.apply(KVV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVVRenderers.rendererKVV.render(KVV("a", -1, _b = false), FormatText(0), sr))
    wy shouldBe Success("""KVV{k="a" v="-1" b="0"}""")
  }

  behavior of "lift"

  it should "render Some" in {
    val renderer = implicitly[Renderer[Int]].lift
    val wy = TryUsing(StateR())(sr => renderer.render(Some(42), FormatText(0), sr))
    wy shouldBe Success("42")
  }
  it should "render None" in {
    val renderer = implicitly[Renderer[Int]].lift
    val wy = TryUsing(StateR())(sr => renderer.render(None, FormatText(0), sr))
    wy shouldBe Success("")
  }

  behavior of "Renderers (FormatXML)"

  it should "renderer1" in {
    object MyRenderers extends Renderers {
      val rendererGreeting: Renderer[Greeting] = renderer1.apply(Greeting.apply)
    }
    val wy = TryUsing(StateR())(sr => MyRenderers.rendererGreeting.render(Greeting("Hello"), FormatXML(), sr))
    wy shouldBe Success("<Greeting>Hello</Greeting>")
  }

  // TODO we should render empty id values invisibly.
  it should "renderer1A" in {
    object MyRenderers extends Renderers {
      implicit val rendererOptionString: Renderer[Option[String]] = optionRenderer
      implicit val rendererAuxData: Renderer[AuxData] = renderer1(AuxData.apply)
      implicit val renderer: Renderer[WithAuxData] = renderer1Super(WithAuxData.apply)(_.auxData)
    }
    import MyRenderers._
    val wy = TryUsing(StateR())(sr => Renderer.render(WithAuxData(math.Pi)(AuxData(None)), FormatXML(), sr))
    wy shouldBe Success("""<WithAuxData>3.141592653589793</WithAuxData>""")
  }

  it should "renderer0" in {
    object MyRenderers extends Renderers {
      implicit val rendererMyJunk: Renderer[MyJunk.type] = renderer0
    }
    import MyRenderers._
    val wy = TryUsing(StateR())(sr => rendererMyJunk.render(MyJunk, FormatXML(), sr))
      wy shouldBe Success("<MyJunk$></MyJunk$>")
  }

  it should "intRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.intRenderer.render(1, FormatXML(), sr))
    wy shouldBe Success("1")
  }

  it should "stringRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.stringRenderer.render("Hello", FormatXML(), sr))
    wy shouldBe Success("Hello")
  }

  it should "booleanRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.booleanRenderer.render(true, FormatXML(), sr))
    wy shouldBe Success("1")
  }

  it should "renderer2A" in {
    object ComplexRenderers extends Renderers {
      val rendererComplex: Renderer[Complex] = renderer2.apply(Complex.apply)
    }
    val wy = TryUsing(StateR())(sr => ComplexRenderers.rendererComplex.render(Complex(1, -1), FormatXML(), sr))
    wy shouldBe Success("<Complex r=\"1\" i=\"-1\"></Complex>")
  }

  it should "renderer2B" in {
    object KVRenderers extends Renderers {
      val rendererKV: Renderer[KV] = renderer2.apply(KV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVRenderers.rendererKV.render(KV("a", -1), FormatXML(), sr))
    wy shouldBe Success("""<KV k="a" v="-1"></KV>""")
  }

  it should "renderer4" in {
    object KVVVRenderers extends Renderers {
      val rendererKVVV: Renderer[KVVV] = renderer4.apply(KVVV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVVVRenderers.rendererKVVV.render(KVVV("a", -1, _b = false, math.Pi), FormatXML(), sr))
    wy shouldBe Success("""<KVVV k="a" v="-1" b="0" x="3.141592653589793"></KVVV>""")
  }

  it should "doubleRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.doubleRenderer.render(math.Pi, FormatXML(), sr))
    wy shouldBe Success("3.141592653589793")
  }

  it should "doubleRenderer (whole)" in {
    val wy = TryUsing(StateR())(sr => Renderers.doubleRenderer.render(42, FormatXML(), sr))
    wy shouldBe Success("42")
  }

  // CONSIDER eliminating this test
  it should "sequenceRenderer" in {
    object MyRenderers extends Renderers {
      implicit val rendererIntSeq: Renderer[Seq[Int]] = sequenceRenderer[Int]
    }
    import MyRenderers._
    val wy = TryUsing(StateR())(sr => rendererIntSeq.render(Seq(42, 99, 1), FormatXML(), sr))
    wy shouldBe Success(
      """42
        |99
        |1""".stripMargin)
  }

  it should "renderer5" in {
    object KVVVRenderers extends Renderers {
      val rendererKVVV: Renderer[KVVVV] = renderer5.apply(KVVVV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVVVRenderers.rendererKVVV.render(KVVVV("a", -1, _b = false, math.Pi, 42L), FormatXML(), sr))
    wy shouldBe Success("""<KVVVV k="a" v="-1" b="0" x="3.141592653589793" l="42"></KVVVV>""")
  }

  it should "optionRenderer" in {
    object MyRenderers extends Renderers {
      implicit val rendererIntOption: Renderer[Option[Int]] = optionRenderer[Int]
    }
    import MyRenderers._
    val wy = TryUsing(StateR())(sr => rendererIntOption.render(Some(42), FormatXML(), sr))
    wy shouldBe Success("42")
  }

  it should "longRenderer" in {
    val wy = TryUsing(StateR())(sr => Renderers.longRenderer.render(42L, FormatXML(), sr))
    wy shouldBe Success("42")
  }

  it should "renderer3" in {
    object KVVRenderers extends Renderers {
      val rendererKVV: Renderer[KVV] = renderer3.apply(KVV.apply)
    }
    val wy = TryUsing(StateR())(sr => KVVRenderers.rendererKVV.render(KVV("a", -1, _b = false), FormatXML(), sr))
    wy shouldBe Success("""<KVV k="a" v="-1" b="0"></KVV>""")
  }
}
