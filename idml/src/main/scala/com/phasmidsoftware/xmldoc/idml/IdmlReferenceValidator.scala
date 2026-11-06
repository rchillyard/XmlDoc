package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.core.XmlException
import com.phasmidsoftware.xmldoc.merge.StructuralConflict
import com.phasmidsoftware.xmldoc.xml.{GenericContent, GenericElement}

/** Validates the logical candidate, before any ZIP entry is written. No layout inference. */
object IdmlReferenceValidator {
  type View = Map[String, GenericElement]
  private val links = Seq("NextTextFrame", "PreviousTextFrame")
  private val topology = "ParentStory" +: links
  private def attr(e: GenericElement, key: String): Option[String] = e.attributes.toMap.get(key)
  private def ref(e: GenericElement, key: String): Option[String] = attr(e, key).filter(v => v.nonEmpty && v != "n")
  private def nodes(e: GenericElement): Seq[GenericElement] = e +: e.childElements.flatMap(nodes)
  private case class Located(src: String, element: GenericElement) {
    val id: String = attr(element, "Self").getOrElse("")
  }
  private def located(view: View): Seq[Located] = view.toSeq.sortBy(_._1).flatMap { case (s, e) => nodes(e).map(Located(s, _)) }
  private def index(view: View, tag: String): Map[String, Located] =
    located(view).filter(_.element.tag == tag).map(e => e.id -> e).toMap

  /** Input identity errors are failures, never normal merge conflicts. Categories stay separate. */
  def requireIdentities(view: View): Unit = {
    view.toSeq.sortBy(_._1).foreach { case (src, root) =>
      nodes(root).flatMap(e => attr(e, "Self")).groupBy(identity).toSeq.sortBy(_._1).foreach { case (id, copies) =>
        if (copies.size > 1) throw XmlException(s"$src Self=$id attribute=Self target=$id: duplicate identity")
      }
    }
    Seq("TextFrame", "Story").foreach { tag =>
      val entries = located(view).filter(_.element.tag == tag)
      entries.foreach { e => if (e.id.isEmpty) throw XmlException(s"${e.src} $tag attribute=Self target=(missing)") }
      entries.groupBy(_.id).toSeq.sortBy(_._1).foreach { case (id, copies) =>
        if (copies.size > 1) throw XmlException(s"${copies.map(_.src).mkString(",")} $tag Self=$id attribute=Self target=$id: duplicate package identity")
      }
    }
  }

  def validate(view: View): Seq[PartConflict] = {
    val frames = index(view, "TextFrame")
    val stories = index(view, "Story")
    val errors = scala.collection.mutable.ArrayBuffer.empty[PartConflict]
    def error(f: Located, key: String, target: Option[String], reason: String): Unit =
      errors += PartConflict(f.src, Seq(StructuralConflict(s"Self=${f.id} attribute=$key target=${target.getOrElse("(missing)")}: $reason", None, target, target)))
    frames.toSeq.sortBy(_._1).foreach { case (_, f) =>
      val story = ref(f.element, "ParentStory")
      if (!story.exists(stories.contains)) error(f, "ParentStory", story, "missing Story")
      links.foreach { key =>
        ref(f.element, key).foreach { target =>
          frames.get(target) match {
            case None => error(f, key, Some(target), "missing TextFrame")
            case Some(other) =>
              val reverse = if (key == "NextTextFrame") "PreviousTextFrame" else "NextTextFrame"
              if (!ref(other.element, reverse).contains(f.id)) error(f, key, Some(target), s"non-reciprocal $reverse")
              if (ref(other.element, "ParentStory") != story) error(f, key, Some(target), "different ParentStory")
          }
        }
      }
      val seen = scala.collection.mutable.Set.empty[String]
      var current = Option(f.id)
      while (current.exists(frames.contains) && !current.exists(seen.contains)) {
        val id = current.get
        seen += id
        current = ref(frames(id).element, "NextTextFrame")
      }
      if (current.exists(seen.contains)) error(f, "NextTextFrame", current, "thread cycle")
    }
    errors.toSeq
  }

  /** Story body = all ordered Story children, recursively, including text/CDATA and styles.
    * Root Story attributes are metadata; ignored attributes are excluded at every body depth. */
  private def body(e: GenericElement, ignored: Set[String]): Seq[GenericContent] = {
    def canonical(c: GenericContent): GenericContent = c match {
      case n: GenericElement => n.copy(attributes = n.attributes.filterNot(a => ignored(a._1)).sortBy(_._1), children = n.children.map(canonical))
      case other => other
    }
    e.children.map(canonical)
  }

  def intentConflicts(base: View, left: View, right: View, ignored: Set[String]): Seq[PartConflict] = {
    val bf = index(base, "TextFrame")
    val bs = index(base, "Story")
    def direction(layout: View, content: View, layoutIsLeft: Boolean): Seq[PartConflict] = {
      val lf = index(layout, "TextFrame")
      val cs = index(content, "Story")
      bf.toSeq.sortBy(_._1).flatMap { case (id, before) =>
        val after = lf.get(id)
        val changed = after.isEmpty || topology.exists(k => ref(before.element, k) != after.flatMap(f => ref(f.element, k)))
        if (!changed) Nil
        else {
          val endpoints = Seq(Some(before), after).flatten
          val neighbours = endpoints.flatMap(f => links.flatMap(k => ref(f.element, k))).flatMap(n => Seq(bf.get(n), lf.get(n)).flatten)
          val affected = (endpoints ++ neighbours).flatMap(f => ref(f.element, "ParentStory")).distinct.sorted
          affected.flatMap { story =>
            for {
              b <- bs.get(story).toSeq
              c <- cs.get(story).toSeq
              if body(b.element, ignored) != body(c.element, ignored)
            } yield {
              val layoutValue = Some(s"frame=$id layout changed")
              val contentValue = Some(s"Story=$story body edited")
              PartConflict(before.src, Seq(StructuralConflict(s"Self=$id attribute=layout target=$story body=${b.src}/Story[$story]/*", Some(s"frame=$id Story=$story"),
                if (layoutIsLeft) layoutValue else contentValue, if (layoutIsLeft) contentValue else layoutValue)))
            }
          }
        }
      }
    }
    (direction(left, right, true) ++ direction(right, left, false)).distinct
  }
}
