package com.phasmidsoftware.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SmartBufferSpec extends AnyFlatSpec with Matchers {

    behavior of "SmartBuffer"

    it should "apply" in {
        SmartBuffer().result shouldBe ""
    }

    it should "appendPadded 1" in {
        val target1 = SmartBuffer().appendPadded("")
        target1.result shouldBe ""
        SmartBuffer().appendPadded("Hello").result shouldBe "Hello"
    }

    it should "appendPadded 2" in {
        val target = SmartBuffer().appendPadded("Hello")
        target.appendPadded("World!")
        target.result shouldBe "Hello World!"
    }

    it should "appendPadded 2a" in {
        val target = SmartBuffer().appendPadded("Hello")
        target.appendPadded("\nWorld!")
        target.result shouldBe "Hello\nWorld!"
    }

    it should "appendPadded 3" in {
        val target = SmartBuffer().appendPadded("Hello").appendPadded("")
        target.result shouldBe "Hello"
    }

    it should "appendPadded 4" in {
        val target = SmartBuffer().appendPadded("Hello    ").appendPadded("\n ")
        target.result shouldBe "Hello\n "
    }

    it should "append" in {
        SmartBuffer().result shouldBe ""
        SmartBuffer().append(" ").result shouldBe " "
    }

    it should "trim" in {
        val target = SmartBuffer().append("Hello    ")
        target.trim.result shouldBe "Hello"
    }

    it should "clear" in {
        SmartBuffer().clear shouldBe ""
        val target = SmartBuffer().append("Hello")
        target.clear shouldBe "Hello"
        target.result shouldBe ""
    }
}
