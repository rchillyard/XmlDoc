package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

class MergerSpec extends AnyFlatSpec with should.Matchers {

  private def rect(attrs: (String, String)*): GenericElement = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", ("Self" -> "u1") +: attrs, Nil)))

  behavior of "Merger.merge, synthetic trees"

  it should "combine two sides' changes to different attributes of the same node cleanly" in {
    val base = rect("Fill" -> "None", "Stroke" -> "None")
    val left = rect("Fill" -> "Red", "Stroke" -> "None") // left changed Fill
    val right = rect("Fill" -> "None", "Stroke" -> "Black") // right changed Stroke
    Merger.merge(base, left, right).toSet shouldBe Set(
      MergedAttributeChange("u1", "Fill", Some("None"), Some("Red")),
      MergedAttributeChange("u1", "Stroke", Some("None"), Some("Black"))
    )
  }

  it should "not conflict when both sides make the same change" in {
    val base = rect("Fill" -> "None")
    val left = rect("Fill" -> "Red")
    val right = rect("Fill" -> "Red")
    Merger.merge(base, left, right) shouldBe Seq(MergedAttributeChange("u1", "Fill", Some("None"), Some("Red")))
  }

  it should "report a conflict when both sides change the same attribute differently" in {
    val base = rect("Fill" -> "None")
    val left = rect("Fill" -> "Red")
    val right = rect("Fill" -> "Blue")
    Merger.merge(base, left, right) shouldBe Seq(Conflict("u1", "Fill", Some("None"), Some("Red"), Some("Blue")))
  }

  it should "let deletion win over an update to the same node (Delete/Edit, per Lindholm's default)" in {
    val base = rect("Fill" -> "None")
    val left = GenericElement("Root", Nil, Nil) // left deleted it
    val right = rect("Fill" -> "Red") // right just updated it
    Merger.merge(base, left, right) match {
      case Seq(MergedDelete(node)) => node.self shouldBe Some("u1")
      case other => fail(other.toString)
    }
  }

  it should "keep a deletion agreed on by both sides" in {
    val base = rect("Fill" -> "None")
    val empty = GenericElement("Root", Nil, Nil)
    Merger.merge(base, empty, empty) match {
      case Seq(MergedDelete(node)) => node.self shouldBe Some("u1")
      case other => fail(other.toString)
    }
  }

  it should "pass through an insertion made by only one side" in {
    val base = GenericElement("Root", Nil, Nil)
    val left = rect("Fill" -> "Red")
    val right = GenericElement("Root", Nil, Nil)
    Merger.merge(base, left, right) match {
      case Seq(MergedInsert(node)) => node.self shouldBe Some("u1")
      case other => fail(other.toString)
    }
  }

  it should "apply an untouched-by-the-other-side change from just one side" in {
    val base = rect("Fill" -> "None", "Stroke" -> "None")
    val left = rect("Fill" -> "Red", "Stroke" -> "None")
    val right = base
    Merger.merge(base, left, right) shouldBe Seq(MergedAttributeChange("u1", "Fill", Some("None"), Some("Red")))
  }

  behavior of "Merger.merge, real HelloWorld2 files (Kaining's actual 3-way scenario)"

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  // Note: the "base" here is HelloWorld2C.idml, not the plain HelloWorld2.idml, and that's
  // deliberate, not an oversight - see MERGE.md's "the one gotcha" section. A, B, and C were all
  // produced from the same long-open InDesign session, which had already drifted from what's
  // actually saved in HelloWorld2.idml (different Self values for the same page items) before it
  // ever forked into these three exports. So HelloWorld2.idml doesn't actually share A/B's Self
  // lineage at all and can't serve as their matchable base; C does (confirmed separately that its
  // own u13d is untouched - unmoved, unstyled - i.e. it matches what A and B both started from).
  // The true common ancestor of A and B is that session's unsaved in-memory state right before the
  // fork, which was never itself captured as a file - C is the closest real stand-in we have.
  it should "cleanly combine A's move and B's restyle of u13d, from their real common base (C)" in {
    val base = spread("HelloWorld2C.idml", "Spreads/Spread_ue6.xml")
    val left = spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml")
    val right = spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml")
    val outcomes = Merger.merge(base, left, right)
    val u13dOutcomes = outcomes.collect { case o @ (MergedAttributeChange("u13d", _, _, _) | Conflict("u13d", _, _, _, _)) => o }
    u13dOutcomes.collect { case MergedAttributeChange(_, key, _, _) => key }.toSet should contain allOf("ItemTransform", "FillColor")
    u13dOutcomes.collect { case c: Conflict => c } shouldBe Nil // different attributes changed - no real conflict
  }

  behavior of "Merger.merge, real Mergeable files (a genuinely clean 3-way trio)"

  // Mergeable.idml/Mergeable-Left.idml/Mergeable-Right.idml are the trio MERGE.md's TODO asked
  // for: base and both edits made from an explicit close-then-reopen of the same saved file each
  // time, so - unlike HelloWorld2A/B/C above - there's no drift gotcha to route around here.
  it should "detect a real Insert/Insert conflict: both sides independently added a Rectangle with the same Self" in {
    val base = spread("Mergeable.idml", "Spreads/Spread_ud1.xml")
    val left = spread("Mergeable-Left.idml", "Spreads/Spread_ud1.xml")
    val right = spread("Mergeable-Right.idml", "Spreads/Spread_ud1.xml")
    val outcomes = Merger.merge(base, left, right)
    // The two pre-existing TextFrames (ue5, uee) were untouched by both sides - no edits at all.
    outcomes.collect { case Conflict("ue5" | "uee", _, _, _, _) => () } shouldBe Nil
    outcomes.collect { case MergedAttributeChange("ue5" | "uee", _, _, _) => () } shouldBe Nil
    // Both sides independently drew a new Rectangle - and InDesign's ID allocation turned out to
    // be deterministic enough, from the same starting file, to hand out the identical Self (u11c)
    // to both - a real Insert/Insert collision, not just the defensively-handled theoretical case
    // Merger.reconcileInserts was written for. Left filled it yellow, right filled it magenta, so
    // it's a genuine conflict, not two independent insertions that happen to agree.
    outcomes.collect { case c @ Conflict("u11c", _, _, _, _) => c } should not be empty
  }
}
