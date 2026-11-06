package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.{Failure, Success}

/**
 * Tests of PcsTreeBuilder.build with relation sets which are inconsistent (as a merge with unresolved conflicts could leave them).
 */
class PcsTreeBuilderFailureSpec extends AnyFlatSpec with should.Matchers {

  behavior of "PcsTreeBuilder.build, from an inconsistent relation set"

  private val root = Content.element("r", "R", Nil)

  // The Pcs relations of a parent whose children are the given labels, in order.
  private def chain(parent: String, labels: String*): Set[Pcs] = {
    val siblings = ListStart +: labels.map(SiblingNode(_)) :+ ListEnd
    siblings.zip(siblings.tail).map((p, s) => Pcs(parent, p, s)).toSet
  }

  private def failureMessage(relations: RelationSet, rootLabel: String = "r"): String =
    PcsTreeBuilder.build(relations, rootLabel) match {
      case Failure(x) => x.getMessage
      case Success(e) => fail(s"expected a failure, but built $e")
    }

  it should "fail when a parent has no first child recorded" in {
    failureMessage(RelationSet(Set.empty, Set(root))) should include("no successor recorded after ⊣ under r")
  }

  it should "fail when a child chain stops short" in {
    failureMessage(RelationSet(Set(Pcs("r", ListStart, SiblingNode("a"))), Set(root, Content.element("a", "A", Nil)))) should include("no successor recorded after a under r")
  }

  it should "fail when a child chain has list-start as a successor" in {
    failureMessage(RelationSet(Set(Pcs("r", ListStart, ListStart)), Set(root))) should include("list-start")
  }

  it should "fail when a child chain is cyclic" in {
    val pcs = Set(Pcs("r", ListStart, SiblingNode("a")), Pcs("r", SiblingNode("a"), SiblingNode("a")))
    failureMessage(RelationSet(pcs, Set(root, Content.element("a", "A", Nil)))) should include("child chain under r") // (cyclic, or conflicting: either way, inconsistent)
  }

  it should "fail when a node is its own ancestor" in {
    failureMessage(RelationSet(chain("r", "a") ++ chain("a", "r"), Set(root, Content.element("a", "A", Nil)))) should include("its own ancestor")
  }

  it should "fail when a child has no content" in {
    failureMessage(RelationSet(chain("r", "a"), Set(root))) should include("no content recorded for a")
  }

  it should "fail when a text node has children, or no text" in {
    failureMessage(RelationSet(chain("r", "t") ++ chain("t", "x"), Set(root, Content.text("t", "hi")))) should include("text node t cannot have children")
    failureMessage(RelationSet(chain("r", "t") ++ chain("t"), Set(root, Content("t", "", Nil, None, kind = TextNode)))) should include("text node t has no text payload")
  }

  it should "fail when a CDATA node has children, or no text" in {
    failureMessage(RelationSet(chain("r", "c") ++ chain("c", "x"), Set(root, Content.cdata("c", "a<b")))) should include("CDATA node c cannot have children")
    failureMessage(RelationSet(chain("r", "c") ++ chain("c"), Set(root, Content("c", "", Nil, None, kind = CDataNode)))) should include("CDATA node c has no text payload")
  }

  it should "fail when the root is not an element" in {
    failureMessage(RelationSet(chain("t"), Set(Content.text("t", "hi"))), "t") should include("root t is not an element")
  }

  it should "build an element's legacy text (kept on its Content) as its last child" in {
    PcsTreeBuilder.build(RelationSet(chain("r"), Set(Content("r", "R", Nil, Some("hi")))), "r") shouldBe Success(GenericElement("R", Nil, Seq(GenericText("hi"))))
    PcsTreeBuilder.build(RelationSet(chain("r"), Set(Content("r", "R", Nil, Some("a<b"), textIsCData = true))), "r") shouldBe Success(GenericElement("R", Nil, Seq(GenericCData("a<b"))))
  }
}
