package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.core.{FP, XmlException}
import com.phasmidsoftware.xmldoc.xml.{GenericElement, Zip}

import java.io.File
import scala.util.{Failure, Success, Try}

/**
 * A real, runnable git merge driver for `.idml` files (see `MERGE.md`'s "Eventual integration: a
 * git merge driver"): `merge(base, ours, theirs)` mutates `ours` in place - exactly git's own
 * `%O %A %B` contract, `ours` being the file to leave in whatever state should actually be used.
 *
 * Every part `IdmlPackage.parts` lists (Spreads, Stories, MasterSpreads, Resources, ...) goes
 * through `IdmlPackageMerger`, each part a normal `ThreeWayMerger.merge`. `designmap.xml` itself is
 * handled separately and much more narrowly: it's a far richer element than "a list of part
 * references" (its own `Self`, plenty of other `Self`-bearing children like `Language` resource
 * definitions, and - confirmed by direct inspection - a leading `<?aid ...?>` processing
 * instruction that `scala.xml`'s parser silently drops and `GenericElement` has no way to put back).
 * A full 3-way merge of all of that is out of scope here; instead, `ours`'s own `designmap.xml` is
 * left completely untouched *unless* a part was actually added or removed, in which case its exact
 * text is surgically patched - one `<idPkg:Type src="..." />` line inserted or removed - rather
 * than parsed and rebuilt, so everything else about it (including that processing instruction)
 * survives byte for byte.
 */
object IdmlMergeDriver {

  /**
   * Merges `theirs` into `ours`, both relative to `base`, mutating `ours` in place on success.
   *
   * @param base              the common ancestor `.idml` file.
   * @param ours              "our" independently modified version - mutated in place if the merge
   *                          succeeds; left untouched if it doesn't.
   * @param theirs            the other independently modified version.
   * @param ignoredAttributes forwarded to `IdmlPackageMerger.merge` - see
   *                          `EditDetector.defaultIgnoredAttributes`.
   * @return every part-level conflict found (empty means `ours` was updated and the merge
   *         succeeded), or a `Failure` if the files themselves couldn't be read/written.
   */
  def merge(base: File, ours: File, theirs: File, ignoredAttributes: Set[String] = EditDetector.defaultIgnoredAttributes): Try[Seq[PartConflict]] =
    for {
      basePkg <- IdmlPackage.open(base)
      oursPkg <- IdmlPackage.open(ours)
      theirsPkg <- IdmlPackage.open(theirs)
      result <- IdmlPackageMerger.merge(basePkg, oursPkg, theirsPkg, ignoredAttributes)
      _ <- if (result.conflicts.isEmpty) applyTo(ours, oursPkg, result.partOutcomes) else Success(())
    } yield result.conflicts

  private def applyTo(ours: File, oursPkg: IdmlPackage, outcomes: Seq[PartOutcome]): Try[Unit] = {
    val oursSrcs = oursPkg.parts.map(_.src).toSet
    val toAdd = outcomes.collect { case p: PartContent if !oursSrcs(p.src) => p }
    val toRemove = outcomes.collect { case p: PartRemoved if oursSrcs(p.src) => p }
    for {
      _ <- writeParts(ours, outcomes)
      _ <- if (toAdd.nonEmpty || toRemove.nonEmpty) patchDesignmap(ours, toAdd, toRemove) else Success(())
    } yield ()
  }

  private def writeParts(ours: File, outcomes: Seq[PartOutcome]): Try[Unit] =
    FP.sequence(outcomes.map {
      case PartContent(src, _, content) => Zip.writeEntry(ours, src, xmlDeclaration + GenericElement.toXmlString(content))
      case PartRemoved(src) => Zip.deleteEntry(ours, src)
    }).map(_ => ())

  private val xmlDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

  // Surgically patches designmap.xml's raw text - not a parse/rebuild - so everything about it
  // that isn't the specific idPkg:* lines changing (including the <?aid ...?> processing
  // instruction `GenericElement` can't round-trip) survives untouched.
  private def patchDesignmap(ours: File, toAdd: Seq[PartContent], toRemove: Seq[PartRemoved]): Try[Unit] =
    for {
      original <- Zip.readEntry(ours, "designmap.xml")
      removed <- toRemove.foldLeft(Try(original)) { (acc, part) => acc.flatMap(removeIdPkgLine(_, part.src)) }
      inserted <- toAdd.foldLeft(Try(removed)) { (acc, part) => acc.flatMap(insertIdPkgLine(_, part.partType, part.src)) }
      _ <- Zip.writeEntry(ours, "designmap.xml", inserted)
    } yield ()

  private def removeIdPkgLine(text: String, src: String): Try[String] = {
    val pattern = ("(?m)^.*<idPkg:[A-Za-z]+\\b[^>]*\\bsrc=\"" + java.util.regex.Pattern.quote(src) + "\"[^>]*/>\\s*\\R?").r
    pattern.findFirstIn(text) match {
      case Some(matched) => Success(text.replaceFirst(java.util.regex.Pattern.quote(matched), ""))
      case None => Failure(XmlException(s"IdmlMergeDriver: designmap.xml has no idPkg reference to remove for $src"))
    }
  }

  private def insertIdPkgLine(text: String, partType: String, src: String): Try[String] = {
    val newLine = s"""\t<idPkg:$partType src="$src" />\n"""
    val marker = "</Document>"
    val count = (text.length - text.replace(marker, "").length) / marker.length
    if (count != 1) Failure(XmlException(s"IdmlMergeDriver: designmap.xml has $count occurrences of $marker, expected exactly 1"))
    else Success(text.replace(marker, newLine + marker))
  }

  /**
   * The git merge driver contract: `%O %A %B` as three file paths. Exit `0` (and `ours`/`%A` now
   * holds the merge result) on success; exit `1`, `ours` untouched, on conflict; exit `2` on any
   * other failure (bad arguments, unreadable/corrupt files).
   */
  def main(args: Array[String]): Unit = args match {
    case Array(o, a, b) =>
      merge(new File(o), new File(a), new File(b)) match {
        case Success(Nil) =>
          System.exit(0)
        case Success(conflicts) =>
          System.err.println(s"idml-merge: ${conflicts.size} conflicting part(s):")
          conflicts.foreach { c =>
            System.err.println(s"  ${c.src}:")
            c.conflicts.foreach(sc => System.err.println(s"    ${sc.slot}: ours=${sc.leftValue.getOrElse("?")} theirs=${sc.rightValue.getOrElse("?")}"))
          }
          System.exit(1)
        case Failure(t) =>
          System.err.println(s"idml-merge: failed: ${t.getMessage}")
          System.exit(2)
      }
    case _ =>
      System.err.println("usage: idml-merge <base> <ours> <theirs>")
      System.exit(2)
  }
}
