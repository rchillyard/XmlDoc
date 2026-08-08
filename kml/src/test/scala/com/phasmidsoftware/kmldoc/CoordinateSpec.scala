package com.phasmidsoftware.kmldoc

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class CoordinateSpec extends AnyFlatSpec with should.Matchers {

    behavior of "Coordinate"

    it should "renderer" in {

    }

    it should "seqRenderer" in {

    }

    it should "apply" in {
        Coordinate("-112.0870267752693,36.0905099328766,0") shouldBe new Coordinate("-112.0870267752693", "36.0905099328766", "0")
    }

}
