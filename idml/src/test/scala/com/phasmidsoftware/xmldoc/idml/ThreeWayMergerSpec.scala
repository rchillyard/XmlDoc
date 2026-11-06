package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.merge.{PcsMerger, StructuralConflict}
import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

class ThreeWayMergerSpec extends AnyFlatSpec with should.Matchers {

  private def spreadOf(self: String, children: GenericElement*): GenericElement =
    GenericElement("Spread", Seq("Self" -> self), children)

  private def rect(self: String, attrs: (String, String)*): GenericElement =
    GenericElement("Rectangle", ("Self" -> self) +: attrs, Nil)

  private def expectMerged(result: Either[Seq[StructuralConflict], GenericElement]): GenericElement =
    result.getOrElse(fail(s"expected a successful merge, got conflicts: $result"))

  private def expectConflicts(result: Either[Seq[StructuralConflict], GenericElement]): Seq[StructuralConflict] =
    result.swap.getOrElse(fail(s"expected conflicts, got a successful merge: $result"))

  behavior of "ThreeWayMerger.merge, the reason both mergers are wired together"

  it should "cleanly combine disjoint attribute changes to the same node - PcsMerger alone would (wrongly) conflict on the whole content blob" in {
    val base = rect("u1", "Fill" -> "None", "Stroke" -> "None")
    val left = rect("u1", "Fill" -> "Red", "Stroke" -> "None") // left changed Fill only
    val right = rect("u1", "Fill" -> "None", "Stroke" -> "Black") // right changed Stroke only
    PcsMerger.merge(base, left, right).conflicts should not be empty // the blindspot this class exists to fix
    val merged = expectMerged(ThreeWayMerger.merge(base, left, right))
    merged.attributes.toMap should contain allOf("Fill" -> "Red", "Stroke" -> "Black")
  }

  behavior of "ThreeWayMerger.merge, single-sided changes"

  it should "cleanly apply a one-sided structural reorder" in {
    val base = spreadOf("us", rect("u1"), rect("u2"))
    val right = spreadOf("us", rect("u2"), rect("u1"))
    expectMerged(ThreeWayMerger.merge(base, base, right)) shouldBe right
  }

  it should "cleanly apply a one-sided insertion, with the new node's own attributes intact" in {
    val base = spreadOf("us", rect("u1"))
    val left = spreadOf("us", rect("u1"), rect("u2", "Fill" -> "Red"))
    expectMerged(ThreeWayMerger.merge(base, left, base)) shouldBe left
  }

  behavior of "ThreeWayMerger.merge, conflicts from Merger's own attribute-level checks"

  it should "report a Delete/Update conflict (Kaining's 06) even though nothing about it is structural" in {
    val base = spreadOf("us", rect("u1", "Fill" -> "None"))
    val left = spreadOf("us") // left deletes u1
    val right = spreadOf("us", rect("u1", "Fill" -> "Red")) // right just updates it
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.map(_.slot) should contain("(deleted vs updated) of u1")
  }

  behavior of "ThreeWayMerger.merge, conflicts from PcsMerger's own structural checks"

  it should "report both directions of a move/move divergence (Kaining's 05)" in {
    val base = spreadOf("us", rect("u1"), rect("u2"), rect("u3"))
    val left = spreadOf("us", rect("u2"), rect("u3"), rect("u1"))
    val right = spreadOf("us", rect("u2"), rect("u1"), rect("u3"))
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.map(_.slot).toSet shouldBe Set("successor after u1 under us", "predecessor before u1 under us")
  }

  it should "report a unique-parent conflict (Kaining's 10-group-ungroup)" in {
    val g1 = GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x")))
    val y = rect("y")
    val base = spreadOf("us", g1, y)
    val left = spreadOf("us", rect("x"), y) // left ungroups g1
    val right = spreadOf("us", GenericElement("Group", Seq("Self" -> "g1"), Seq(rect("x"), y))) // right moves y into g1
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts.map(_.slot).toSet shouldBe Set("parent of x", "parent of y")
  }

  behavior of "ThreeWayMerger.merge, deduplicating an Insert/Insert collision Merger and PcsMerger both detect"

  it should "report exactly one conflict, not two, for the same node" in {
    val base = spreadOf("us")
    val left = spreadOf("us", rect("u1", "Fill" -> "Yellow"))
    val right = spreadOf("us", rect("u1", "Fill" -> "Magenta"))
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    conflicts should have size 1
    conflicts.head.slot should include("u1")
  }

  behavior of "ThreeWayMerger.merge, real IDML files"

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  it should "merge HelloWorld2A's move and HelloWorld2B's restyle of u13d into one real tree (base C)" in {
    def u13dOf(spreadEl: GenericElement): GenericElement = spreadEl.childElements.find(_.attributes.toMap.get("Self").contains("u13d")).get
    val base = u13dOf(spread("HelloWorld2C.idml", "Spreads/Spread_ue6.xml"))
    val left = u13dOf(spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml"))
    val right = u13dOf(spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml"))
    val merged = expectMerged(ThreeWayMerger.merge(base, left, right))
    merged.attributes.toMap.get("ItemTransform") shouldBe left.attributes.toMap.get("ItemTransform") // A's move
    merged.attributes.toMap.get("FillColor") shouldBe right.attributes.toMap.get("FillColor") // B's restyle
  }

  // A and B also independently touched an unrelated Link's LinkImportTime (InDesign re-stamps
  // that on export, regardless of user action) - a real instance of the paper's own §7 caveat
  // ("document metadata often changes inconsistently on both sides"), not a bug. This is exactly
  // what EditDetector.defaultIgnoredAttributes exists for.
  it should "merge the whole real Spread cleanly, with the default ignored attributes filtering out the unrelated LinkImportTime noise" in {
    val base = spread("HelloWorld2C.idml", "Spreads/Spread_ue6.xml")
    val left = spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml")
    val right = spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml")
    val merged = expectMerged(ThreeWayMerger.merge(base, left, right))
    val mergedU13d = merged.childElements.find(_.attributes.toMap.get("Self").contains("u13d")).get.attributes.toMap
    val aU13d = left.childElements.find(_.attributes.toMap.get("Self").contains("u13d")).get.attributes.toMap
    val bU13d = right.childElements.find(_.attributes.toMap.get("Self").contains("u13d")).get.attributes.toMap
    mergedU13d.get("ItemTransform") shouldBe aU13d.get("ItemTransform")
    mergedU13d.get("FillColor") shouldBe bU13d.get("FillColor")
  }

  it should "still report the LinkImportTime conflict when a caller opts out of ignoring it" in {
    val base = spread("HelloWorld2C.idml", "Spreads/Spread_ue6.xml")
    val left = spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml")
    val right = spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml")
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right, ignoredAttributes = Set.empty))
    conflicts.map(_.slot) should contain("LinkImportTime of u10d")
  }

  it should "deduplicate the whole-node Insert/Insert report for u11c while allowing descendant diagnostics" in {
    val base = spread("Mergeable.idml", "Spreads/Spread_ud1.xml")
    val left = spread("Mergeable-Left.idml", "Spreads/Spread_ud1.xml")
    val right = spread("Mergeable-Right.idml", "Spreads/Spread_ud1.xml")
    val conflicts = expectConflicts(ThreeWayMerger.merge(base, left, right))
    val slot = "(whole inserted node) of u11c"
    val a = left.childElements.find(_.attributes.contains("Self" -> "u11c")).get
    val b = right.childElements.find(_.attributes.contains("Self" -> "u11c")).get
    IdmlMergeAssertions.assertConflictsEqual(conflicts.filter(_.slot == slot),
      Seq(StructuralConflict(slot, None, Some(a.toString), Some(b.toString))))
  }

  private def mr08Story(values: String*): GenericElement = {
    import com.phasmidsoftware.xmldoc.xml.GenericText
    GenericElement("Story", Seq("Self" -> "s"), values.map(value =>
      GenericElement("ParagraphStyleRange", Nil, Seq(GenericElement("Content", Nil, Seq(GenericText(value)))))))
  }

  it should "preserve B and edited C through the public entry point after anonymous A is deleted" in {
    val base = mr08Story("A", "B", "C")
    val left = mr08Story("B", "C")
    val right = mr08Story("A", "B", "C′")
    ThreeWayMerger.merge(base, left, right) shouldBe Right(mr08Story("B", "C′"))
    ThreeWayMerger.merge(base, right, left) shouldBe Right(mr08Story("B", "C′"))
  }

  it should "reject ambiguous anonymous copy deletion through the public entry point" in {
    val base = mr08Story("same", "anchor", "same")
    intercept[IllegalArgumentException](ThreeWayMerger.merge(base, mr08Story("anchor", "same"), base))
      .getMessage should include ("Ambiguous content matching")
  }

  it should "enforce strict-text-node-v1 for identical, overlapping and disjoint text edits" in {
    val base = mr08Story("one two three")
    val left = mr08Story("ONE two three")
    ThreeWayMerger.merge(base, left, left) shouldBe Right(left)
    ThreeWayMerger.merge(base, base, left) shouldBe Right(left)
    for (right <- Seq(mr08Story("One two three"), mr08Story("one two THREE"));
      (l, r) <- Seq(left -> right, right -> left)) {
      val result = ThreeWayMerger.merge(base, l, r)
      val conflicts = result.swap.toOption.get
      conflicts.size shouldBe 1
      conflicts.head.slot shouldBe "content of /0/0::text[0]"
      conflicts.head.baseValue shouldBe Some("one two three")
      conflicts.head.leftValue shouldBe Some(l.childElements.head.childElements.head.children.collect {
        case com.phasmidsoftware.xmldoc.xml.GenericText(t) => t }.mkString)
      conflicts.head.rightValue shouldBe Some(r.childElements.head.childElements.head.children.collect {
        case com.phasmidsoftware.xmldoc.xml.GenericText(t) => t }.mkString)
    }
  }

  it should "insert a Self-bearing text element without duplicating its character data" in {
    import com.phasmidsoftware.xmldoc.xml.{GenericText, GenericCData}
    val base = GenericElement("Story", Seq("Self" -> "s"), Nil)
    val inserted = GenericElement("Story", Seq("Self" -> "s"), Seq(
      GenericElement("Content", Seq("Self" -> "new"), Seq(GenericText(" a "), GenericCData("b")))))
    ThreeWayMerger.merge(base, inserted, base) shouldBe Right(inserted)
    ThreeWayMerger.merge(base, base, inserted) shouldBe Right(inserted)
  }

  it should "preserve a style wrapper change and exact mixed content" in {
    import com.phasmidsoftware.xmldoc.xml.{GenericText, GenericCData}
    val base = GenericElement("Story", Seq("Self" -> "s"), Seq(GenericText(" "),
      GenericElement("CharacterStyleRange", Seq("AppliedCharacterStyle" -> "Old"), Seq(
        GenericText("a"), GenericElement("Br", Nil, Nil), GenericCData("b"))), GenericText("\n")))
    val updated = base.copy(children = base.children.map {
      case e: GenericElement => e.copy(attributes = Seq("AppliedCharacterStyle" -> "New"))
      case other => other
    })
    ThreeWayMerger.merge(base, updated, base) shouldBe Right(updated)
  }


  it should "support anonymous roots and structural style wrapping without losing mixed content" in {
    import com.phasmidsoftware.xmldoc.xml.GenericText
    val base = mr08Story("A", "B", "C").copy(attributes = Nil)
    val left = mr08Story("B", "C").copy(attributes = Nil)
    val right = mr08Story("A", "B", "C′").copy(attributes = Nil)
    ThreeWayMerger.merge(base, left, right) shouldBe Right(mr08Story("B", "C′").copy(attributes = Nil))
    val plain = GenericElement("Story", Seq("Self" -> "s"), Seq(GenericElement("Content", Nil, Seq(GenericText(" text ")))))
    val wrapped = GenericElement("Story", Seq("Self" -> "s"), Seq(
      GenericElement("CharacterStyleRange", Seq("AppliedCharacterStyle" -> "Bold"), plain.children)))
    ThreeWayMerger.merge(plain, wrapped, plain) shouldBe Right(wrapped)
    ThreeWayMerger.merge(plain, plain, wrapped) shouldBe Right(wrapped)
  }
  private def xml(s: String): GenericElement = GenericElement.fromElem(scala.xml.XML.loadString(s))

  it should "merge independent insertions with anonymous descendants and preserve every whitespace node" in {
    val a = "<Item Self='a'><Properties Value='a'/></Item>"
    val b = "<Item Self='b'><Properties Value='b'/></Item>"
    val c = "<Item Self='c'><Properties Value='c'/></Item>"
    val x = "<Item Self='x'><Properties Value='x'/></Item>"
    val y = "<Item Self='y'><Properties Value='y'/></Item>"
    def root(items: String*) = xml(items.mkString("<Root Self='r'>\n  ", "\n  ", "\n</Root>"))
    val base = root(a, b, c)
    Seq((root(a, x, b, c), root(a, b, y, c)), (root(a, b, y, c), root(a, x, b, c))).foreach { case (l, r) =>
      IdmlMergeAssertions.assertXmlEquals(expectMerged(ThreeWayMerger.merge(base, l, r)), root(a, x, b, y, c))
    }
    // Two different insertions into the same gap remain a real strict relation conflict.
    expectConflicts(ThreeWayMerger.merge(base, root(a, x, b, c), root(a, y, b, c))) should not be empty
  }

  it should "keep moved anonymous descendants matched for an independent property edit" in {
    val a = "<Item Self='a'><Properties Value='base'/></Item>"
    val edited = "<Item Self='a'><Properties Value='edited'/></Item>"
    val base = xml(s"<Root Self='r'><Group Self='g'>$a</Group><Item Self='b'/></Root>")
    val left = xml(s"<Root Self='r'><Group Self='g'/><Item Self='b'/>$a</Root>")
    val right = xml(s"<Root Self='r'><Group Self='g'>$edited</Group><Item Self='b'/></Root>")
    val expected = xml(s"<Root Self='r'><Group Self='g'/><Item Self='b'/>$edited</Root>")
    IdmlMergeAssertions.assertXmlEquals(expectMerged(ThreeWayMerger.merge(base, left, right)), expected)
  }

  it should "preserve consecutive text and CDATA while merging an element insertion with a text edit" in {
    import com.phasmidsoftware.xmldoc.xml.{GenericText, GenericCData}
    val a = rect("a")
    val b = rect("b")
    val x = rect("x")
    val base = GenericElement("Root", Seq("Self" -> "r"), Seq(GenericText(" lead "), GenericCData("<raw>"), a, GenericText(" middle "), b))
    val left = base.copy(children = Seq(GenericText(" lead "), GenericCData("<raw>"), a, GenericText(" inserted "), x, GenericText(" middle "), b))
    val right = base.copy(children = Seq(GenericText(" changed "), GenericCData("<raw>"), a, GenericText(" middle "), b))
    val expected = left.copy(children = GenericText(" changed ") +: left.children.tail)
    IdmlMergeAssertions.assertXmlEquals(expectMerged(ThreeWayMerger.merge(base, left, right)), expected)
  }

}
