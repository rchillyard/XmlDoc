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

  behavior of "ContentMatcher, ignoredAttributes"

  it should "exact-match despite a volatile attribute, avoiding a wrong positional guess when two nodes both change and reorder at once" in {
    def item(kind: String, stamp: Int): GenericElement = GenericElement("Item", Seq("kind" -> kind, "stamp" -> stamp.toString), Nil)
    val base = GenericElement("Root", Nil, Seq(item("a", 1), item("b", 1), item("c", 1)))
    // b is untouched; a and c both reorder *and* have their volatile "stamp" bumped, at once
    val modified = GenericElement("Root", Nil, Seq(item("b", 1), item("c", 2), item("a", 2)))
    val modifiedA = NodePath.root.child(2) // "a" is modified's 3rd child now
    val basesOwnLabelForA = "/0" // NodePath.toString for base's "a", at position 0

    // without ignoring "stamp": only b exact-matches (unchanged); a and c both fail to exact-match
    // (their stamp differs) and fall through to the positional pass, in the wrong relative order -
    // each ends up with the *other*'s base identity.
    ContentMatcher.matchedLabel(base, modified)(NodeRef(modifiedA, item("a", 2))) should not be basesOwnLabelForA

    // ignoring "stamp": a and c each exact-match by their real (kind-based) content regardless of
    // the reorder, so "a" correctly keeps its own base identity.
    ContentMatcher.matchedLabel(base, modified, Set("stamp"))(NodeRef(modifiedA, item("a", 2))) shouldBe basesOwnLabelForA
  }

  it should "keep the base identities of B and C across left deletion and right update" in {
    def paragraph(text: String): GenericElement = GenericElement("Paragraph", Nil,
      Seq(com.phasmidsoftware.xmldoc.xml.GenericText(text)))
    def story(texts: String*): GenericElement = GenericElement("Story", Nil, texts.map(paragraph))
    val base = story("A", "B", "C")
    val left = ContentMatcher.matchTrees(base, story("B", "C"))
    val right = ContentMatcher.matchTrees(base, story("A", "B", "C′"))
    def paths(m: Matching) = m.matched.map { case (b, r) => b.path -> r.path }.toSet
    paths(left) shouldBe Set(NodePath.root -> NodePath.root,
      NodePath(Vector(1)) -> NodePath(Vector(0)), NodePath(Vector(2)) -> NodePath(Vector(1)))
    paths(right) shouldBe Set(NodePath.root -> NodePath.root,
      NodePath(Vector(0)) -> NodePath(Vector(0)), NodePath(Vector(1)) -> NodePath(Vector(1)),
      NodePath(Vector(2)) -> NodePath(Vector(2)))
    left.onlyInLeft.map(_.path) shouldBe Seq(NodePath(Vector(0)))
    left.onlyInRight shouldBe empty
    right.onlyInLeft shouldBe empty
    right.onlyInRight shouldBe empty
    for (m <- Seq(left, right); (matched, unmatched) <- Seq(
      m.matched.map(_._1) -> m.onlyInLeft, m.matched.map(_._2) -> m.onlyInRight)) {
      matched.map(_.path).distinct.size shouldBe matched.size
      (matched.map(_.path).toSet intersect unmatched.map(_.path).toSet) shouldBe empty
    }
  }

  it should "reject ambiguous repeated content when deletion or insertion changes copy counts" in {
    val two = GenericElement("Root", Nil, Seq(rect("same"), rect("anchor"), rect("same")))
    val one = GenericElement("Root", Nil, Seq(rect("anchor"), rect("same")))
    for ((base, modified) <- Seq(two -> one, one -> two)) {
      intercept[IllegalArgumentException](ContentMatcher.matchTrees(base, modified))
        .getMessage should include ("Ambiguous content matching")
      intercept[IllegalArgumentException](ContentMatcher.matchedLabel(base, modified))
    }
    // With unchanged multiplicity, the documented convention is document order only.
    val m = ContentMatcher.matchTrees(two, two)
    m.matched.map { case (b, r) => b.path -> r.path } shouldBe
      Seq(NodePath.root -> NodePath.root, NodePath(Vector(0)) -> NodePath(Vector(0)),
        NodePath(Vector(1)) -> NodePath(Vector(1)), NodePath(Vector(2)) -> NodePath(Vector(2)))
  }

  it should "include every descendant of an unmatched subtree exactly once" in {
    val empty = GenericElement("Root", Nil, Nil)
    val full = GenericElement("Root", Nil, Seq(GenericElement("Group", Nil, Seq(rect("nested")))))
    for ((base, modified) <- Seq(full -> empty, empty -> full)) {
      val m = ContentMatcher.matchTrees(base, modified)
      m.matched.map { case (b, r) => b.path -> r.path } shouldBe Seq(NodePath.root -> NodePath.root)
      val unmatched = if (base == full) m.onlyInLeft else m.onlyInRight
      unmatched.map(_.path) shouldBe Seq(NodePath(Vector(0)), NodePath(Vector(0, 0)))
      val other = if (base == full) m.onlyInRight else m.onlyInLeft
      other shouldBe Seq.empty
    }
  }
}
