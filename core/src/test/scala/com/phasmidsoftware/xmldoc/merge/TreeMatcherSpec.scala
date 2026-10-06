package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class TreeMatcherSpec extends AnyFlatSpec with should.Matchers {

  behavior of "TreeMatcher.matchBySelf, synthetic trees"

  it should "match a node against itself by Self, ignoring everything without one" in {
    val left = GenericElement("Root", Nil, Seq(
      GenericElement("TextFrame", Seq("Self" -> "u1"), Nil),
      GenericElement("Group", Nil, Nil) // no Self at all
    ))
    val right = GenericElement("Root", Nil, Seq(
      GenericElement("TextFrame", Seq("Self" -> "u1"), Nil)
    ))
    val m = TreeMatcher.matchBySelf(left, right)
    m.matched.map { case (l, r) => (l.self, r.self) } shouldBe Seq((Some("u1"), Some("u1")))
    m.onlyInLeft shouldBe Nil
    m.onlyInRight shouldBe Nil
  }

  it should "report a node present only in the left tree" in {
    val left = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)))
    val right = GenericElement("Root", Nil, Nil)
    val m = TreeMatcher.matchBySelf(left, right)
    m.matched shouldBe Nil
    m.onlyInLeft.map(_.self) shouldBe Seq(Some("u1"))
    m.onlyInRight shouldBe Nil
  }

  it should "report a node present only in the right tree" in {
    val left = GenericElement("Root", Nil, Nil)
    val right = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)))
    val m = TreeMatcher.matchBySelf(left, right)
    m.matched shouldBe Nil
    m.onlyInLeft shouldBe Nil
    m.onlyInRight.map(_.self) shouldBe Seq(Some("u1"))
  }

  it should "record the correct NodePath for a deeply nested match" in {
    val leaf = GenericElement("TextFrame", Seq("Self" -> "u1"), Nil)
    val left = GenericElement("Root", Nil, Seq(GenericElement("Spread", Nil, Seq(GenericElement("Page", Nil, Nil), leaf))))
    val right = left
    val m = TreeMatcher.matchBySelf(left, right)
    m.matched.map { case (l, _) => l.path } shouldBe Seq(NodePath(Vector(0, 1)))
  }
}
