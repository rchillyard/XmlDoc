package com.phasmidsoftware.xmldoc.idml

import com.phasmidsoftware.xmldoc.core.{FP, XmlException}
import com.phasmidsoftware.xmldoc.xml.{GenericContent, GenericElement, Zip}

import java.io.File
import java.nio.file.{Files, Path, StandardCopyOption}
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
    mergeWithWrites(base, ours, theirs, ignoredAttributes, DefaultWrites)

  /** Per-invocation, narrow write boundary for failure tests; no shared mutable hooks. */
  private[idml] trait Writes {
    def part(file: File, outcome: PartOutcome): Try[Unit] = outcome match {
      case PartContent(src, _, content) => Zip.writeEntry(file, src, xmlDeclaration + GenericElement.toXmlString(content))
      case PartRemoved(src) => Zip.deleteEntry(file, src)
    }
    def manifest(file: File, added: Seq[PartContent], removed: Seq[PartRemoved]): Try[Unit] =
      patchDesignmap(file, added, removed)
    def replace(staged: Path, target: Path): Try[Unit] = Try {
      Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      ()
    }
  }
  private object DefaultWrites extends Writes

  private[idml] def mergeWithWrites(base: File, ours: File, theirs: File,
                                   ignoredAttributes: Set[String], writes: Writes): Try[Seq[PartConflict]] =
    for {
      basePkg <- IdmlPackage.open(base)
      oursPkg <- IdmlPackage.open(ours)
      theirsPkg <- IdmlPackage.open(theirs)
      result <- IdmlPackageMerger.merge(basePkg, oursPkg, theirsPkg, ignoredAttributes)
      _ <- if (result.conflicts.isEmpty) applyAtomically(ours, oursPkg, result.partOutcomes, ignoredAttributes, writes) else Success(())
    } yield result.conflicts

  private def applyAtomically(ours: File, oursPkg: IdmlPackage, outcomes: Seq[PartOutcome], ignored: Set[String], writes: Writes): Try[Unit] = Try {
    val target = ours.toPath.toAbsolutePath
    val staged = Files.createTempFile(target.getParent, ".idml-merge-", ".idml")
    var committed = false
    try {
      Files.copy(target, staged, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
      val oursSrcs = oursPkg.parts.map(_.src).toSet
      val toAdd = outcomes.collect { case p: PartContent if !oursSrcs(p.src) => p }
      val toRemove = outcomes.collect { case p: PartRemoved if oursSrcs(p.src) => p }
      outcomes.foreach {
        // Keep untouched entries' original bytes, including XML serialization and PIs.
        case p: PartContent if oursSrcs(p.src) && sameXml(oursPkg.loadPart(p.src).get, p.content, ignored) => ()
        case p => writes.part(staged.toFile, p).get
      }
      if (toAdd.nonEmpty || toRemove.nonEmpty) writes.manifest(staged.toFile, toAdd, toRemove).get
      validateStaged(staged.toFile, oursPkg, outcomes, ignored).get
      // No destructive fallback when the filesystem cannot perform atomic replacement.
      writes.replace(staged, target).get
      committed = true
    } finally {
      if (!committed) Files.deleteIfExists(staged)
    }
  }

  private def validateStaged(file: File, original: IdmlPackage, outcomes: Seq[PartOutcome], ignored: Set[String]): Try[Unit] =
    for {
      pkg <- IdmlPackage.open(file)
      loaded <- FP.sequence(pkg.parts.map(p => pkg.loadPart(p.src).map(p.src -> _)))
      _ <- Try {
        val expected = outcomes.foldLeft(original.parts.map(_.src).toSet) {
          case (srcs, p: PartContent) => srcs + p.src
          case (srcs, p: PartRemoved) => srcs - p.src
        }
        require(pkg.parts.map(_.src).distinct.size == pkg.parts.size, "staged designmap.xml: duplicate src")
        require(loaded.map(_._1).toSet == expected, "staged designmap.xml: part inventory mismatch")
        val entries = Zip.entries(file).get.toSet
        val view = loaded.toMap
        outcomes.foreach {
          case PartRemoved(src) => require(!entries(src), s"staged ZIP: removed part $src still exists")
          case PartContent(src, _, e) => require(view.get(src).exists(sameXml(_, e, ignored)), s"staged XML: $src differs from candidate")
        }
        IdmlReferenceValidator.requireIdentities(view)
        require(IdmlReferenceValidator.validate(view).isEmpty, "staged ZIP: invalid frame/Story references")
      }
    } yield ()

  private def sameXml(a: GenericContent, b: GenericContent, ignored: Set[String]): Boolean = (a, b) match {
    case (x: GenericElement, y: GenericElement) =>
      x.tag == y.tag && x.attributes.filterNot(kv => ignored(kv._1)).toMap == y.attributes.filterNot(kv => ignored(kv._1)).toMap &&
        x.children.size == y.children.size && x.children.zip(y.children).forall { case (c, d) => sameXml(c, d, ignored) }
    case _ => a == b
  }

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
    // Match only the requested element, never another reference on the same line.
    val pattern = ("<idPkg:[A-Za-z]+\\b[^>]*\\bsrc\\s*=\\s*([\"'])" + java.util.regex.Pattern.quote(scala.xml.Utility.escape(src)) + "\\1[^>]*/>").r
    val matches = pattern.findAllMatchIn(text).toSeq
    if (matches.size != 1) Failure(XmlException(s"IdmlMergeDriver: designmap.xml has ${matches.size} idPkg references to remove for $src"))
    else {
      val m = matches.head
      Success(text.substring(0, m.start) + text.substring(m.end))
    }
  }

  private def insertIdPkgLine(text: String, partType: String, src: String): Try[String] = {
    val escapedSrc = scala.xml.Utility.escape(src).replace("\"", "&quot;")
    val newLine = s"""\t<idPkg:$partType src="$escapedSrc" />\n"""
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
