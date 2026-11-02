package com.phasmidsoftware.xmldoc.merge

import com.phasmidsoftware.xmldoc.core.XmlException
import com.phasmidsoftware.xmldoc.xml.{GenericCData, GenericContent, GenericElement, GenericText}

import scala.util.{Failure, Success, Try}

/**
 * Reconstructs a `GenericElement` tree from a `RelationSet`, by walking it from a given root
 * label along `Pcs` links - the step Lindholm's paper describes only in passing ("Tm can be
 * reconstructed by traversing from ⊥0 level by level along the PCS relations in Δ", section 6),
 * but that `PcsMerger` deliberately stops short of, since it's only meaningful once the relation
 * set is actually consistent.
 *
 * Elements, text and CDATA are all independent PCS nodes, so reconstruction preserves their exact
 * order even for mixed content such as `<p>Hello <b>world</b>!</p>`.
 */
object PcsTreeBuilder {

  /**
   * Reconstructs the tree rooted at `result.rootLabel` from `result.relations` - refusing outright
   * if `result.conflicts` is non-empty, rather than relying on `build(relations, rootLabel)`'s own
   * cycle/missing-content detection to catch the problem. That detection isn't a complete
   * substitute for checking `conflicts`: a "unique parent" conflict (two sides disagreeing about
   * which parent a node belongs to, e.g. Kaining's `10-group-ungroup`) can leave both parents'
   * chains individually well-formed - one side's edit simply wins by construction, silently, with
   * no cycle or missing content to trip over - which is exactly why `PcsMerger` reports it as a
   * conflict in the first place rather than resolving it.
   *
   * @param result a `PcsMerger.merge` result.
   * @return the rebuilt tree, or a `Failure` if `result.conflicts` is non-empty, or (should that
   *         somehow not catch it) `result.relations` still turns out inconsistent.
   */
  def build(result: StructuralMergeResult): Try[GenericElement] =
    if (result.conflicts.nonEmpty)
      Failure(XmlException(s"PcsTreeBuilder: refusing to build from ${result.conflicts.size} unresolved conflict(s): ${result.conflicts.map(_.slot).mkString(", ")}"))
    else build(result.relations, result.rootLabel)

  /**
   * Reconstructs the tree rooted at `rootLabel` from `relations`.
   *
   * @param relations a relation set - consistent, or this fails.
   * @param rootLabel the label of the node to treat as the root.
   * @return the rebuilt tree, or a `Failure` if `relations` has no content for some referenced
   *         label, or its `Pcs` chain for some parent is broken or cyclic (both symptoms of an
   *         unresolved conflict having been reconstructed from anyway).
   */
  def build(relations: RelationSet, rootLabel: String): Try[GenericElement] = {
    val contentByLabel = relations.content.map(c => c.label -> c).toMap
    val successorOf = relations.pcs.map(p => (p.parent, p.predecessor) -> p.successor).toMap

    def childLabels(parent: String): Try[Seq[String]] = {
      def loop(current: Sibling, seen: Set[Sibling]): Try[Seq[String]] =
        successorOf.get(parent -> current) match {
          case None =>
            Failure(XmlException(s"PcsTreeBuilder: no successor recorded after ${render(current)} under $parent - the relation set is incomplete"))
          case Some(ListEnd) => Success(Nil)
          case Some(ListStart) =>
            Failure(XmlException(s"PcsTreeBuilder: $parent has a relation whose successor is ⊣ (list-start), which can only ever be a predecessor"))
          case Some(next@SiblingNode(label)) =>
            if (seen(next))
              Failure(XmlException(s"PcsTreeBuilder: cyclic child chain under $parent (revisited $label) - the relation set is inconsistent"))
            else
              loop(next, seen + next).map(label +: _)
        }

      loop(ListStart, Set(ListStart))
    }

    def buildNode(label: String, ancestors: Set[String]): Try[GenericContent] =
      if (ancestors(label))
        Failure(XmlException(s"PcsTreeBuilder: cyclic parent chain - $label is its own ancestor"))
      else contentByLabel.get(label) match {
        case None => Failure(XmlException(s"PcsTreeBuilder: no content recorded for $label"))
        case Some(content) if content.kind == TextNode =>
          content.text match {
            case Some(value) => childLabels(label).flatMap {
              case Nil => Success(GenericText(value))
              case _ => Failure(XmlException(s"PcsTreeBuilder: text node $label cannot have children"))
            }
            case None => Failure(XmlException(s"PcsTreeBuilder: text node $label has no text payload"))
          }
        case Some(content) if content.kind == CDataNode =>
          content.text match {
            case Some(value) => childLabels(label).flatMap {
              case Nil => Success(GenericCData(value))
              case _ => Failure(XmlException(s"PcsTreeBuilder: CDATA node $label cannot have children"))
            }
            case None => Failure(XmlException(s"PcsTreeBuilder: CDATA node $label has no text payload"))
          }
        case Some(content) =>
          for {
            labels <- childLabels(label)
            children <- labels.foldLeft[Try[Vector[GenericContent]]](Success(Vector.empty)) { (acc, childLabel) =>
              for {
                built <- acc
                child <- buildNode(childLabel, ancestors + label)
              } yield built :+ child
            }
          } yield {
            // `text`/`textIsCData` are kept as a compatibility path for relation sets created by
            // the earlier leaf-folding representation. New `Pcs.relations` never puts text here.
            val legacyText = content.text.map(t => if (content.textIsCData) GenericCData(t) else GenericText(t)).toSeq
            GenericElement(content.tag, content.attributes, children ++ legacyText)
          }
      }

    buildNode(rootLabel, Set.empty).flatMap {
      case root: GenericElement => Success(root)
      case _ => Failure(XmlException(s"PcsTreeBuilder: root $rootLabel is not an element"))
    }
  }

  private def render(s: Sibling): String = s match {
    case SiblingNode(label) => label
    case ListStart => "⊣"
    case ListEnd => "⊢"
  }
}
