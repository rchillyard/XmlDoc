package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class PcsSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Pcs.relations, synthetic trees"

  it should "give a childless node just one pcs chain link, from ListStart straight to ListEnd" in {
    val root = GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)
    val rs = Pcs.relations(root)
    rs.pcs shouldBe Set(Pcs("u1", ListStart, ListEnd))
    rs.content shouldBe Set(Content("u1", "Rectangle", Seq("Self" -> "u1")))
  }

  it should "chain two children from ListStart to ListEnd via their Self labels" in {
    val root = GenericElement("Root", Nil, Seq(
      GenericElement("Rectangle", Seq("Self" -> "u1"), Nil),
      GenericElement("TextFrame", Seq("Self" -> "u2"), Nil)
    ))
    val rs = Pcs.relations(root)
    rs.pcs should contain allOf(
      Pcs("/", ListStart, SiblingNode("u1")),
      Pcs("/", SiblingNode("u1"), SiblingNode("u2")),
      Pcs("/", SiblingNode("u2"), ListEnd)
    )
  }

  it should "fall back to a NodePath label for a child with no Self, distinct from any Self label" in {
    val root = GenericElement("Root", Nil, Seq(GenericElement("Group", Nil, Nil))) // no Self
    val rs = Pcs.relations(root)
    rs.pcs should contain(Pcs("/", ListStart, SiblingNode("/0")))
    rs.content should contain(Content("/0", "Group", Nil))
  }
}
