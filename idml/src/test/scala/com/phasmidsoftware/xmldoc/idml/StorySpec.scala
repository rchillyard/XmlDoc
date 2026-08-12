package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.{GenericContent, GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import scala.util.{Failure, Success}

class StorySpec extends AnyFlatSpec with should.Matchers {

  private val idmlFile = new File(getClass.getResource("HelloWorld.idml").toURI)

  private def realStoryElement: GenericElement =
    IdmlPackage.open(idmlFile).get.loadPart("Stories/Story_ue6.xml").get.childElements.find(_.tag == "Story").get

  behavior of "Story.fromGenericElement / toGenericElement"

  it should "extract Self and the rest of the attributes from the real Story element" in {
    Story.fromGenericElement(realStoryElement) match {
      case Success(story) =>
        story.self shouldBe "ue6"
        story.attributes should contain("UserText" -> "true")
        story.attributes.map(_._1) should not contain "Self"
      case f => fail(f.toString)
    }
  }

  it should "fail when the element isn't tagged Story" in {
    Story.fromGenericElement(GenericElement("NotAStory", Seq("Self" -> "x"), Nil)) should matchPattern { case Failure(_) => }
  }

  it should "fail when the element has no Self attribute" in {
    Story.fromGenericElement(GenericElement("Story", Seq("UserText" -> "true"), Nil)) should matchPattern { case Failure(_) => }
  }

  it should "round-trip back to an equivalent GenericElement" in {
    val original = realStoryElement
    Story.fromGenericElement(original).map(Story.toGenericElement) shouldBe Success(original)
  }

  behavior of "IdmlPackage.loadStory / loadStories"

  it should "load the real Story by its src path" in {
    IdmlPackage.open(idmlFile).get.loadStory("Stories/Story_ue6.xml") match {
      case Success(story) => story.self shouldBe "ue6"
      case f => fail(f.toString)
    }
  }

  it should "load every Story referenced from designmap.xml" in {
    IdmlPackage.open(idmlFile).get.loadStories match {
      case Success(Seq(story)) =>
        story.self shouldBe "ue6"

        def texts(content: Seq[GenericContent]): Seq[String] =
          content.flatMap {
            case t: GenericText => Seq(t.text)
            case e: GenericElement => texts(e.children)
            case _ => Nil
          }

        texts(story.children) should contain("Hello World!")
      case other => fail(other.toString)
    }
  }
}
