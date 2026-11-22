package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import com.phasmidsoftware.xmldoc.merge.StructuralConflict
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IdmlReferenceValidatorSpec extends AnyFlatSpec with Matchers {
  import IdmlReferenceValidator.*
  private def xml(s: String) = GenericElement.fromElem(scala.xml.XML.loadString(s))
  private def frame(id: String, prev: String = "n", next: String = "n", story: String = "s") =
    s"""<TextFrame Self="$id" ParentStory="$story" PreviousTextFrame="$prev" NextTextFrame="$next"/>"""
  private def view(frames: String, body: String = "<Content> a </Content>"): View = Map(
    "Spreads/s.xml" -> xml(s"<Spread Self='spread'>$frames</Spread>"),
    "Stories/s.xml" -> xml(s"<Story Self='s'><ParagraphStyleRange>$body</ParagraphStyleRange></Story>"),
    "Stories/t.xml" -> xml("<Story Self='t'><Content>other</Content></Story>"))
  private val base = view(frame("a", next = "b") + frame("b", prev = "a"))
  private def intent(l: View, r: View) = intentConflicts(base, l, r, EditDetector.defaultIgnoredAttributes)

  "Candidate references" should "accept tfX insertion with reciprocal links and endpoints" in {
    val v = view(frame("a", next = "x") + frame("x", "a", "b") + frame("b", "x"))
    requireIdentities(v)
    validate(v) shouldBe empty
    intent(v, base) shouldBe empty
  }

  it should "reject missing targets, one-way links, cross-Story links and reciprocal cycles with context" in {
    val invalid = Seq(
      view(frame("a", next = "absent")) -> "missing TextFrame",
      view(frame("a", next = "b") + frame("b")) -> "non-reciprocal",
      view(frame("a", next = "b") + frame("b", "a", story = "t")) -> "different ParentStory",
      view(frame("a", "b", "b") + frame("b", "a", "a")) -> "thread cycle",
      view(frame("a", story = "absent")) -> "missing Story")
    invalid.foreach { case (v, reason) =>
      val errors = validate(v)
      errors.exists(_.conflicts.exists(_.slot.contains(reason))) shouldBe true
      all(errors.map(_.src)) shouldBe "Spreads/s.xml"
      all(errors.flatMap(_.conflicts).map(_.slot)) should include("Self=")
      all(errors.flatMap(_.conflicts).map(_.slot)) should include("attribute=")
      all(errors.flatMap(_.conflicts).map(_.slot)) should include("target=")
    }
  }

  it should "allow only empty and n link sentinels, without interpreting resource strings" in {
    validate(view(frame("a", "", ""))) shouldBe empty
    val v = view(frame("a").replace("/>", " AppliedObjectStyle='external/style'/>"))
    validate(v) shouldBe empty
    validate(view(frame("a", next = "None"))) should not be empty
    validate(view(frame("a", story = "n"))) should not be empty
  }

  it should "reject local duplicate Self and package frame/Story collisions, keeping categories separate" in {
    intercept[Exception](requireIdentities(view(frame("a") + frame("a")))).getMessage should include("Self=a")
    intercept[Exception](requireIdentities(base.updated("other.xml", xml(frame("a"))))).getMessage should include("duplicate package identity")
    intercept[Exception](requireIdentities(base.updated("other.xml", xml("<Story Self='s'/>")))).getMessage should include("Story Self=s")
    requireIdentities(view(frame("s"))) // Story s and frame s are different categories
  }

  "Story dependency detection" should "separate valid references from symmetric layout/body intent conflicts" in {
    val layout = view(frame("a"))
    val edited = view(frame("a", next = "b") + frame("b", "a"), "<Content> changed </Content>")
    validate(layout) shouldBe empty
    val expected = intent(layout, edited)
    IdmlMergeAssertions.assertPartConflictsEqual(expected, Seq("a", "b").map { id =>
      PartConflict("Spreads/s.xml", Seq(StructuralConflict(
        s"Self=$id attribute=layout target=s body=Stories/s.xml/Story[s]/*",
        Some(s"frame=$id Story=s"), Some(s"frame=$id layout changed"), Some("Story=s body edited"))))
    })
    IdmlMergeAssertions.assertPartConflictsEqual(intent(edited, layout), expected, swapActualSides = true)
    intent(layout, base) shouldBe empty
    intent(base, edited) shouldBe empty
    intent(layout, layout) shouldBe empty
    val unrelated = base.updated("Stories/t.xml", xml("<Story Self='t'><Content>edited</Content></Story>"))
    intent(layout, unrelated) shouldBe empty
    val ignored = view(frame("a", next = "b") + frame("b", "a"), "<Content StyleUniqueId='new'> a </Content>")
    intent(layout, ignored) shouldBe empty
  }

  it should "include styles, nesting, CDATA and exact whitespace in the body, excluding root metadata" in {
    val layout = view(frame("a", story = "t") + frame("b", story = "t"))
    Seq("<Content>a</Content>", "<Content AppliedCharacterStyle='new'> a </Content>",
      "<CharacterStyleRange><Content> a </Content></CharacterStyleRange>", "<Content><![CDATA[ a ]]></Content>")
      .foreach { body => intent(layout, view(frame("a", next = "b") + frame("b", "a"), body)) should not be empty }
    val metadata = base.updated("Stories/s.xml", base("Stories/s.xml").copy(attributes = Seq("Self" -> "s", "Title" -> "new")))
    intent(layout, metadata) shouldBe empty
  }

  it should "include both old/new Stories and edge endpoints in the affected set" in {
    val layout = view(frame("a", story = "t") + frame("b"))
    val edited = base.updated("Stories/t.xml", xml("<Story Self='t'><Content>edited</Content></Story>"))
    intent(layout, edited).flatMap(_.conflicts).exists(_.slot.contains("target=t")) shouldBe true
  }
}
