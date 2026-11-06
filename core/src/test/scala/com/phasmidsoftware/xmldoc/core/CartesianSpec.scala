package com.phasmidsoftware.xmldoc.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class CartesianSpec extends AnyFlatSpec with should.Matchers {

  private val p = Cartesian(1, 2, 3)
  private val q = Cartesian(4, 6, 3)

  behavior of "Cartesian"

  it should "give the vector from one point to another" in {
    (p vector q) shouldBe Cartesian(3, 4, 0)
    (q vector p) shouldBe Cartesian(-3, -4, 0)
  }

  it should "give the distance between two points" in {
    (p distance q) shouldBe 5.0
    (p distance p) shouldBe 0.0
  }

  it should "give the dot product of two points" in {
    p.dotProduct(q) shouldBe 25.0
    p.dotProduct(Cartesian(0, 0, 0)) shouldBe 0.0
  }

  it should "give the difference of a coordinate" in {
    p.diff(q)(_.y) shouldBe 4.0
  }
}
