package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.{GenericElement, GenericText}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File
import scala.util.{Failure, Success}

class IdmlPackageSpec extends AnyFlatSpec with should.Matchers {

  private val idmlFile = new File(getClass.getResource("HelloWorld.idml").toURI)

  behavior of "IdmlPackage"

  it should "open the package and parse its designmap" in {
    IdmlPackage.open(idmlFile) match {
      case Success(pkg) => 
        pkg.designmap.tag shouldBe "Document"
      case f => 
        fail(f.toString)
    }
  }

  it should "discover every idPkg part referenced from designmap.xml" in {
    IdmlPackage.open(idmlFile) match {
      case Success(pkg) =>
        pkg.parts should contain theSameElementsAs Seq(
          PackagePart("Graphic", "Resources/Graphic.xml"),
          PackagePart("Fonts", "Resources/Fonts.xml"),
          PackagePart("Styles", "Resources/Styles.xml"),
          PackagePart("Preferences", "Resources/Preferences.xml"),
          PackagePart("Tags", "XML/Tags.xml"),
          PackagePart("MasterSpread", "MasterSpreads/MasterSpread_ud8.xml"),
          PackagePart("Spread", "Spreads/Spread_ud1.xml"),
          PackagePart("BackingStory", "XML/BackingStory.xml"),
          PackagePart("Story", "Stories/Story_ue6.xml")
        )
      case f => 
        fail(f.toString)
    }
  }

  it should "load a specific part by its src path" in {
    IdmlPackage.open(idmlFile) match {
      case Success(pkg) =>
        pkg.loadPart("Stories/Story_ue6.xml") match {
          case Success(story) => story.tag shouldBe "idPkg:Story"
          case f => fail(f.toString)
        }
      case f => fail(f.toString)
    }
  }

  it should "fail to load a part whose src doesn't exist in the zip" in {
    IdmlPackage.open(idmlFile) match {
      case Success(pkg) => pkg.loadPart("NoSuchPart.xml") should matchPattern { case Failure(_) => }
      case f => fail(f.toString)
    }
  }

  it should "load all parts of a given type" in {
    IdmlPackage.open(idmlFile) match {
      case Success(pkg) =>
        pkg.loadParts("Story") match
          case Success(Seq(story)) =>
            // the real content, several levels deep inside idPkg:Story/Story/ParagraphStyleRange/CharacterStyleRange/Content
            def texts(e: GenericElement): Seq[String] =
              e.children.flatMap {
                case t: GenericText => Seq(t.text)
                case c: GenericElement => texts(c)
                case _ => Nil
              }
            texts(story) should contain("Hello World!")
          case other => 
            fail(other.toString)
      case f => fail(f.toString)
    }
  }

  it should "return no parts for a type that doesn't appear in this package" in {
    IdmlPackage.open(idmlFile) match {
      case Success(pkg) => pkg.loadParts("NoSuchType") shouldBe Success(Nil)
      case f => fail(f.toString)
    }
  }
}
