package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import scala.util.{Failure, Success}

class LayoutConstraintsSpec extends AnyFlatSpec with should.Matchers {

  behavior of "LayoutConstraints.fromString / asString"

  it should "parse the real attribute value found in HelloWorld.idml and HelloWorld2.idml" in {
    LayoutConstraints.fromString("FlexibleDimension FixedDimension FlexibleDimension") shouldBe
      Success(LayoutConstraints(DimensionConstraint.FlexibleDimension, DimensionConstraint.FixedDimension, DimensionConstraint.FlexibleDimension))
  }

  it should "round-trip back to the original attribute value text" in {
    val original = "FixedDimension FlexibleDimension FixedDimension"
    LayoutConstraints.fromString(original).map(LayoutConstraints.asString) shouldBe Success(original)
  }

  it should "fail on the wrong number of values" in {
    LayoutConstraints.fromString("FixedDimension FlexibleDimension") should matchPattern { case Failure(_) => }
  }

  it should "fail on an unrecognized value" in {
    LayoutConstraints.fromString("FixedDimension ProportionalDimension FixedDimension") should matchPattern { case Failure(_) => }
  }

  behavior of "the real HelloWorld2.idml sample"

  private val idmlFile = new File(getClass.getResource("HelloWorld2.idml").toURI)

  private def spreadPageItems: Seq[GenericElement] =
    IdmlPackage.open(idmlFile).get.loadPart("Spreads/Spread_ud1.xml").get
      .childElements.find(_.tag == "Spread").get
      .childElements

  it should "parse every page item's HorizontalLayoutConstraints and VerticalLayoutConstraints" in {
    val pageItems = spreadPageItems.filter(e => e.tag == "TextFrame" || e.tag == "Rectangle")
    pageItems.size should be >= 2
    pageItems.map(_.tag) should contain allOf("TextFrame", "Rectangle")
    for (item <- pageItems) {
      for (attr <- Seq("HorizontalLayoutConstraints", "VerticalLayoutConstraints")) {
        item.attributes.toMap.get(attr) match {
          case Some(value) => LayoutConstraints.fromString(value) shouldBe a[Success[?]]
          case None => fail(s"${item.tag} (${item.attributes.toMap.getOrElse("Self", "?")}) has no $attr attribute")
        }
      }
    }
  }
}
