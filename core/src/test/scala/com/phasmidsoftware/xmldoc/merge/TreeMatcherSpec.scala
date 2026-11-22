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

  private def item(id: String, value: String = "old"): GenericElement =
    GenericElement("Item", Seq("Self" -> id, "value" -> value), Nil)

  it should "preserve every identity across moves, updates, insertion and deletion" in {
    val base = GenericElement("Root", Nil, Seq(item("a"), item("b"), item("deleted")))
    val modified = GenericElement("Root", Nil, Seq(
      item("new"), GenericElement("Group", Nil, Seq(item("b", "updated"))), item("a")))
    val m = TreeMatcher.matchBySelf(base, modified)
    m.matched.map { case (b, r) => (b.self.get, b.path, r.self.get, r.path) } shouldBe Seq(
      ("a", NodePath(Vector(0)), "a", NodePath(Vector(2))),
      ("b", NodePath(Vector(1)), "b", NodePath(Vector(1, 0))))
    m.matched(1)._2.element.attributes.toMap.apply("value") shouldBe "updated"
    m.onlyInLeft.map(_.self) shouldBe Seq(Some("deleted"))
    m.onlyInRight.map(_.self) shouldBe Seq(Some("new"))
    for ((matched, unmatched) <- Seq(m.matched.map(_._1) -> m.onlyInLeft, m.matched.map(_._2) -> m.onlyInRight)) {
      matched.map(_.path).distinct.size shouldBe matched.size
      (matched.map(_.path).toSet intersect unmatched.map(_.path).toSet) shouldBe empty
      (matched ++ unmatched).size shouldBe 3
    }
  }

  it should "reject duplicate Self on either side, including ancestor and descendant collisions" in {
    val valid = GenericElement("Root", Nil, Seq(item("x")))
    val duplicate = GenericElement("Root", Seq("Self" -> "x"), Seq(item("x")))
    for ((left, right, side) <- Seq((duplicate, valid, "left"), (valid, duplicate, "right"))) {
      val error = intercept[IllegalArgumentException](TreeMatcher.matchBySelf(left, right))
      error.getMessage should include ("Duplicate Self 'x'")
      error.getMessage should include (side)
      error.getMessage should include ("/, /0")
    }
    TreeMatcher.matchBySelf(valid, valid).matched.size shouldBe 1
  }
}
