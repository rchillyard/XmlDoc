package com.phasmidsoftware.kmldoc

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class KmlEditSpec extends AnyFlatSpec with should.Matchers {

  behavior of "KmlEdit"

  val join = "join"

  it should "apply" in {
    val world = Element("Hello", "World")
    val someElement = Some(Element("Goodbye", "Mr. Chips"))
    val target = KmlEdit(join, 2, world, someElement)
    target.command shouldBe join
    target.op1 shouldBe world
    target.maybeOp2 shouldBe someElement
  }

  it should "parse" in {
    val placemark = "Placemark"
    val inputString = s"""$join $placemark "Medford Branch (#1)" with $placemark "Medford Branch (#2)""""
    val result: IO[KmlEdit] = KmlEdit.parse(inputString)
    val kmlEdit = result.unsafeRunSync()
    kmlEdit shouldBe KmlEdit(join, 2, Element(placemark, "Medford Branch (#1)"), Some(Element(placemark, "Medford Branch (#2)")))
  }

  it should "parseLines" in {

  }

}
