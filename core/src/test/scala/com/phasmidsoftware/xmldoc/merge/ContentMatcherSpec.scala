package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class ContentMatcherSpec extends AnyFlatSpec with should.Matchers {

  private def rect(kind: String): GenericElement = GenericElement("Rectangle", Seq("kind" -> kind), Nil)

  behavior of "ContentMatcher.matchTrees"

  it should "match an untouched tree to itself, node for node" in {
    val tree = GenericElement("Root", Nil, Seq(rect("a"), rect("b")))
    val m = ContentMatcher.matchTrees(tree, tree)
    m.matched.map { case (b, r) => (b.path, r.path) } shouldBe Seq(
      NodePath.root -> NodePath.root,
      NodePath.root.child(0) -> NodePath.root.child(0),
      NodePath.root.child(1) -> NodePath.root.child(1)
    )
    m.onlyInLeft shouldBe Nil
    m.onlyInRight shouldBe Nil
  }

  it should "match an untouched child by content even after a deletion shifts its position" in {
    val base = GenericElement("Root", Nil, Seq(rect("a"), rect("b"), rect("c")))
    val modified = GenericElement("Root", Nil, Seq(rect("b"), rect("c"))) // a deleted
    val m = ContentMatcher.matchTrees(base, modified)
    val byBasePath = m.matched.map { case (b, r) => b.path -> r.path }.toMap
    byBasePath.get(NodePath.root.child(1)) shouldBe Some(NodePath.root.child(0)) // b: base's 2nd, modified's 1st
    byBasePath.get(NodePath.root.child(2)) shouldBe Some(NodePath.root.child(1)) // c: base's 3rd, modified's 2nd
    m.onlyInLeft.map(_.path) shouldBe Seq(NodePath.root.child(0)) // a, correctly identified as deleted
    m.onlyInRight shouldBe Nil
  }

  it should "fall back to positional matching for the one remaining candidate on each side after exact matching" in {
    val base = GenericElement("Root", Nil, Seq(rect("a"), rect("b"), rect("c")))
    val modified = GenericElement("Root", Nil, Seq(rect("a"), rect("b"), rect("c-updated"))) // only c's content changed
    val m = ContentMatcher.matchTrees(base, modified)
    val byBasePath = m.matched.map { case (b, r) => b.path -> r.path }.toMap
    byBasePath.get(NodePath.root.child(2)) shouldBe Some(NodePath.root.child(2)) // c matched to c-updated, despite differing content
    m.onlyInLeft shouldBe Nil
    m.onlyInRight shouldBe Nil
  }

  behavior of "ContentMatcher.matchedLabel"

  it should "give a node whose position shifted the same label base gave its matched counterpart" in {
    val base = GenericElement("Root", Nil, Seq(rect("a"), rect("b"), rect("c")))
    val modified = GenericElement("Root", Nil, Seq(rect("b"), rect("c"))) // a deleted
    val labelOf = ContentMatcher.matchedLabel(base, modified)
    val modifiedB = NodeRef(NodePath.root.child(0), rect("b")) // b is now at position 0 in modified
    labelOf(modifiedB) shouldBe Pcs.label(NodeRef(NodePath.root.child(1), rect("b"))) // base's own label for b
  }

  it should "give an unmatched (genuinely new) node a label that can never collide with a real base path" in {
    val base = GenericElement("Root", Nil, Seq(rect("a")))
    val modified = GenericElement("Root", Nil, Seq(rect("a"), rect("new")))
    val labelOf = ContentMatcher.matchedLabel(base, modified)
    val newNode = NodeRef(NodePath.root.child(1), rect("new"))
    labelOf(newNode) shouldBe "+/1"
  }
}
