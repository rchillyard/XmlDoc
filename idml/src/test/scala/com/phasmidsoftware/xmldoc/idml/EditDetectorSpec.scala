package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.xml.GenericElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.File

class EditDetectorSpec extends AnyFlatSpec with should.Matchers {

  behavior of "EditDetector.detectEdits, synthetic trees"

  it should "detect an inserted node" in {
    val base = GenericElement("Root", Nil, Nil)
    val modified = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)))
    EditDetector.detectEdits(base, modified) match {
      case Seq(Inserted(node)) => node.self shouldBe Some("u1")
      case other => fail(other.toString)
    }
  }

  it should "detect a deleted node" in {
    val base = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)))
    val modified = GenericElement("Root", Nil, Nil)
    EditDetector.detectEdits(base, modified) match {
      case Seq(Deleted(node)) => node.self shouldBe Some("u1")
      case other => fail(other.toString)
    }
  }

  it should "detect a content update, reporting only the attributes that actually changed" in {
    val base = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1", "Fill" -> "Red", "Stroke" -> "Black"), Nil)))
    val modified = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1", "Fill" -> "Blue", "Stroke" -> "Black"), Nil)))
    EditDetector.detectEdits(base, modified) match {
      case Seq(Updated(b, m, changes)) =>
        b.self shouldBe Some("u1")
        m.self shouldBe Some("u1")
        changes shouldBe Seq(("Fill", Some("Red"), Some("Blue")))
      case other => fail(other.toString)
    }
  }

  it should "report no edit at all for an unchanged matched node" in {
    val tree = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1"), Nil)))
    EditDetector.detectEdits(tree, tree) shouldBe Nil
  }

  it should "detect an attribute that was added, and one that was removed" in {
    val base = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1", "Locked" -> "false"), Nil)))
    val modified = GenericElement("Root", Nil, Seq(GenericElement("Rectangle", Seq("Self" -> "u1", "FillColor" -> "Color/Red"), Nil)))
    EditDetector.detectEdits(base, modified) match {
      case Seq(Updated(_, _, changes)) =>
        changes should contain allOf(("Locked", Some("false"), None), ("FillColor", None, Some("Color/Red")))
      case other => fail(other.toString)
    }
  }

  behavior of "EditDetector.detectEdits, real HelloWorld2 files"

  private def spread(resourceName: String, path: String): GenericElement =
    IdmlPackage.open(new File(getClass.getResource(resourceName).toURI)).get.loadPart(path).get
      .childElements.find(_.tag == "Spread").get

  it should "detect the one real insertion between HelloWorld2 and HelloWorld2D, with no false deletions" in {
    val base = spread("HelloWorld2.idml", "Spreads/Spread_ud1.xml")
    val d = spread("HelloWorld2D.idml", "Spreads/Spread_ud1.xml")
    val edits = EditDetector.detectEdits(base, d)
    edits.collect { case Inserted(n) => n.self }.flatten should contain("u141")
    edits.collect { case Deleted(n) => n.self }.flatten shouldBe empty
  }

  it should "detect the real content update to u13d between HelloWorld2A and HelloWorld2B" in {
    val a = spread("HelloWorld2A.idml", "Spreads/Spread_ue6.xml")
    val b = spread("HelloWorld2B.idml", "Spreads/Spread_ue6.xml")
    val edits = EditDetector.detectEdits(a, b)
    val u13dUpdate = edits.collectFirst { case u@Updated(base, _, _) if base.self.contains("u13d") => u }
    u13dUpdate shouldBe defined
    u13dUpdate.get.changedAttributes.map(_._1).toSet should contain allOf("ItemTransform", "FillColor")
  }
}
