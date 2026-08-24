#!/usr/bin/env python3
"""Generate deterministic three-way IDML merge fixtures from HelloWorld2.idml.

The fixtures are XML-derived test artifacts, not exports produced by automating the
InDesign GUI.  Every branch starts as an exact copy of its case's generated base.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import shutil
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable
from xml.etree import ElementTree as ET


IDPKG = "http://ns.adobe.com/AdobeInDesign/idml/1.0/packaging"
ET.register_namespace("idPkg", IDPKG)

SCRIPT = Path(__file__).resolve()
IDML_MODULE = SCRIPT.parents[1]
SOURCE = IDML_MODULE / "src/test/resources/com/phasmidsoftware/xmldoc/idml/HelloWorld2.idml"
OUTPUT = IDML_MODULE / "src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus"
SPREAD_PATH = "Spreads/Spread_ud1.xml"
STORY1_PATH = "Stories/Story_story1.xml"
STORY2_PATH = "Stories/Story_story2.xml"
OLD_STORIES = {"Stories/Story_u128.xml", "Stories/Story_u110.xml", "Stories/Story_ue6.xml"}

PAGE_ITEM_TAGS = {"Rectangle", "TextFrame", "Group", "Oval", "Polygon", "GraphicLine"}


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def xml_bytes(root: ET.Element) -> bytes:
    ET.indent(root, space="\t")
    body = ET.tostring(root, encoding="utf-8", xml_declaration=True)
    return body.replace(b"<?xml version='1.0' encoding='utf-8'?>", b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>', 1)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


@dataclass
class Package:
    entries: dict[str, bytes]

    def clone(self) -> "Package":
        return Package(dict(self.entries))

    def root(self, path: str) -> ET.Element:
        return ET.fromstring(self.entries[path])

    def put_root(self, path: str, root: ET.Element) -> None:
        self.entries[path] = xml_bytes(root)

    def write(self, path: Path) -> bytes:
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            def write_member(name: str, compress_type: int) -> None:
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = compress_type
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, self.entries[name])

            # IDML requires this entry to be first and uncompressed.
            write_member("mimetype", zipfile.ZIP_STORED)
            for name in sorted(self.entries):
                if name != "mimetype":
                    write_member(name, zipfile.ZIP_DEFLATED)
        return path.read_bytes()


def load_source() -> Package:
    with zipfile.ZipFile(SOURCE) as archive:
        return Package({name: archive.read(name) for name in archive.namelist()})


def find_by_self(root: ET.Element, self_id: str) -> ET.Element:
    for node in root.iter():
        if node.get("Self") == self_id:
            return node
    raise KeyError(f"Self={self_id!r} not found")


def spread_container(package: Package) -> tuple[ET.Element, ET.Element]:
    root = package.root(SPREAD_PATH)
    spread = next(node for node in root if local_name(node.tag) == "Spread")
    return root, spread


def page_item_children(spread: ET.Element) -> list[ET.Element]:
    return [node for node in spread if local_name(node.tag) in PAGE_ITEM_TAGS]


def item_parent(root: ET.Element, self_id: str) -> tuple[ET.Element, ET.Element]:
    for parent in root.iter():
        for child in parent:
            if child.get("Self") == self_id:
                return parent, child
    raise KeyError(f"Self={self_id!r} not found")


def move_before(root: ET.Element, self_id: str, anchor_id: str) -> None:
    parent, node = item_parent(root, self_id)
    anchor_parent, anchor = item_parent(root, anchor_id)
    if parent is not anchor_parent:
        raise ValueError("move_before only supports siblings")
    parent.remove(node)
    parent.insert(list(parent).index(anchor), node)


def move_after(root: ET.Element, self_id: str, anchor_id: str) -> None:
    parent, node = item_parent(root, self_id)
    anchor_parent, anchor = item_parent(root, anchor_id)
    if parent is not anchor_parent:
        raise ValueError("move_after only supports siblings")
    parent.remove(node)
    parent.insert(list(parent).index(anchor) + 1, node)


def remove_item(package: Package, self_id: str) -> None:
    root, _ = spread_container(package)
    parent, node = item_parent(root, self_id)
    parent.remove(node)
    package.put_root(SPREAD_PATH, root)


def modify_item(package: Package, self_id: str, **attributes: str) -> None:
    root, _ = spread_container(package)
    find_by_self(root, self_id).attrib.update(attributes)
    package.put_root(SPREAD_PATH, root)


def reorder(package: Package, action: Callable[[ET.Element], None]) -> None:
    root, _ = spread_container(package)
    action(root)
    package.put_root(SPREAD_PATH, root)


def make_rectangle(template: ET.Element, self_id: str, x: int, y: int) -> ET.Element:
    node = copy.deepcopy(template)
    node.set("Self", self_id)
    node.set("Name", self_id)
    node.set("ContentType", "Unassigned")
    node.set("ItemTransform", f"1 0 0 1 {x} {y}")
    for descendant in list(node.iter()):
        if descendant is not node and descendant.get("Self"):
            descendant.attrib.pop("Self", None)
    return node


def make_text_frame(template: ET.Element, self_id: str, story: str, x: int, y: int) -> ET.Element:
    node = copy.deepcopy(template)
    node.set("Self", self_id)
    node.set("Name", self_id)
    node.set("ParentStory", story)
    node.set("PreviousTextFrame", "n")
    node.set("NextTextFrame", "n")
    node.set("ItemTransform", f"1 0 0 1 {x} {y}")
    for descendant in list(node.iter()):
        if descendant is not node and descendant.get("Self"):
            descendant.attrib.pop("Self", None)
    return node


def make_group(self_id: str, children: Iterable[ET.Element]) -> ET.Element:
    group = ET.Element(
        "Group",
        {
            "Self": self_id,
            "Name": self_id,
            "ItemLayer": "uce",
            "Visible": "true",
            "Locked": "false",
            "ItemTransform": "1 0 0 1 0 0",
            "AppliedObjectStyle": "ObjectStyle/$ID/[None]",
        },
    )
    group.extend(children)
    return group


def make_story(source_story: ET.Element, self_id: str, paragraphs: list[str]) -> ET.Element:
    wrapper = copy.deepcopy(source_story)
    story = next(node for node in wrapper if local_name(node.tag) == "Story")
    story.set("Self", self_id)
    for child in list(story):
        if local_name(child.tag) == "ParagraphStyleRange":
            story.remove(child)
    for text in paragraphs:
        paragraph = ET.SubElement(story, "ParagraphStyleRange", {"AppliedParagraphStyle": "ParagraphStyle/$ID/NormalParagraphStyle"})
        character = ET.SubElement(
            paragraph,
            "CharacterStyleRange",
            {
                "AppliedCharacterStyle": "CharacterStyle/$ID/[No character style]",
                "OTFContextualAlternate": "false",
            },
        )
        ET.SubElement(character, "Content").text = text
    return wrapper


BASE_PARAGRAPHS = [
    "Paragraph one introduces the merge corpus.",
    "The quick brown fox jumps over the lazy dog.",
    "Paragraph three remains unchanged.",
    "Paragraph four closes the story.",
]


def build_base(source: Package, case_name: str) -> Package:
    package = source.clone()

    source_spread = source.root(SPREAD_PATH)
    rectangle_template = find_by_self(source_spread, "u13e")
    text_frame_template = find_by_self(source_spread, "uf8")

    spread_root, spread = spread_container(package)
    for child in list(spread):
        if local_name(child.tag) in PAGE_ITEM_TAGS:
            spread.remove(child)

    r1 = make_rectangle(rectangle_template, "r1", 100, 100)
    r2 = make_rectangle(rectangle_template, "r2", 220, 100)
    r3 = make_rectangle(rectangle_template, "r3", 340, 100)
    r4 = make_rectangle(rectangle_template, "r4", 460, 100)
    g1 = make_group("g1", [r3, r4])
    tf1 = make_text_frame(text_frame_template, "tf1", "story1", 100, 300)
    tf2 = make_text_frame(text_frame_template, "tf2", "story1", 700, 300)
    tf1.set("NextTextFrame", "tf2")
    tf2.set("PreviousTextFrame", "tf1")
    tf_a = make_text_frame(text_frame_template, "tfA", "story2", 100, 520)
    tf_b = make_text_frame(text_frame_template, "tfB", "story2", 700, 520)
    spread.extend([r1, r2, g1, tf1, tf2, tf_a, tf_b])
    if case_name == "05-move-move-divergent":
        # Put r1 between two siblings so both "top" and "bottom" branches
        # encode a genuine move rather than one branch being a no-op.
        move_after(spread_root, "r1", "r2")
    package.put_root(SPREAD_PATH, spread_root)

    source_story = source.root("Stories/Story_ue6.xml")
    package.entries[STORY1_PATH] = xml_bytes(make_story(source_story, "story1", BASE_PARAGRAPHS))
    package.entries[STORY2_PATH] = xml_bytes(make_story(source_story, "story2", ["Secondary story paragraph."]))
    for old_story in OLD_STORIES:
        package.entries.pop(old_story, None)

    designmap = package.root("designmap.xml")
    designmap.set("StoryList", "story1 story2")
    designmap.set("Name", f"ThreeWayCorpus-{case_name}.indd")
    story_refs = [node for node in designmap if local_name(node.tag) == "Story" and node.tag.startswith("{")]
    insert_at = min((list(designmap).index(node) for node in story_refs), default=len(designmap))
    for node in story_refs:
        designmap.remove(node)
    designmap.insert(insert_at, ET.Element(f"{{{IDPKG}}}Story", {"src": STORY1_PATH}))
    designmap.insert(insert_at + 1, ET.Element(f"{{{IDPKG}}}Story", {"src": STORY2_PATH}))
    package.put_root("designmap.xml", designmap)
    return package


def add_rectangle(package: Package, self_id: str, after: str) -> None:
    root, spread = spread_container(package)
    template = find_by_self(root, "r1")
    node = make_rectangle(template, self_id, 580, 100)
    anchor = find_by_self(root, after)
    spread.insert(list(spread).index(anchor) + 1, node)
    package.put_root(SPREAD_PATH, root)


def paragraph_contents(package: Package) -> list[ET.Element]:
    root = package.root(STORY1_PATH)
    return [node for node in root.iter() if local_name(node.tag) == "Content"]


def edit_paragraph(package: Package, number: int, old: str, new: str) -> None:
    root = package.root(STORY1_PATH)
    contents = [node for node in root.iter() if local_name(node.tag) == "Content"]
    node = contents[number - 1]
    if node.text is None or old not in node.text:
        raise ValueError(f"paragraph {number} does not contain {old!r}")
    node.text = node.text.replace(old, new, 1)
    package.put_root(STORY1_PATH, root)


def repair_after_tf2_delete(package: Package) -> None:
    remove_item(package, "tf2")
    modify_item(package, "tf1", NextTextFrame="n")


def insert_thread_frame(package: Package) -> None:
    root, spread = spread_container(package)
    tf1 = find_by_self(root, "tf1")
    tf2 = find_by_self(root, "tf2")
    tf_x = make_text_frame(tf1, "tfX", "story1", 400, 300)
    tf_x.set("PreviousTextFrame", "tf1")
    tf_x.set("NextTextFrame", "tf2")
    tf1.set("NextTextFrame", "tfX")
    tf2.set("PreviousTextFrame", "tfX")
    spread.insert(list(spread).index(tf2), tf_x)
    package.put_root(SPREAD_PATH, root)


def ungroup(package: Package) -> None:
    root, _ = spread_container(package)
    parent, group = item_parent(root, "g1")
    index = list(parent).index(group)
    children = list(group)
    parent.remove(group)
    for offset, child in enumerate(children):
        parent.insert(index + offset, child)
    package.put_root(SPREAD_PATH, root)


def relink_tf1(package: Package, target: str) -> None:
    root, _ = spread_container(package)
    tf1 = find_by_self(root, "tf1")
    old_tf2 = find_by_self(root, "tf2")
    old_tf2.set("PreviousTextFrame", "n")
    tf1.set("NextTextFrame", target)
    target_frame = find_by_self(root, target)
    target_frame.set("PreviousTextFrame", "tf1")
    target_frame.set("ParentStory", "story1")
    package.put_root(SPREAD_PATH, root)


def edit(side: str, kind: str, target: str, description: str) -> dict[str, str]:
    return {"side": side, "type": kind, "target": target, "description": description}


def conflict(kind: str, target: str, left: str, right: str) -> dict[str, object]:
    return {
        "type": kind,
        "target": target,
        "left": left,
        "right": right,
        "resolution": "manual",
    }


@dataclass(frozen=True)
class Case:
    name: str
    requirement: str
    left_summary: str
    right_summary: str
    expected_summary: str
    left: Callable[[Package], None]
    right: Callable[[Package], None]
    accepted: tuple[dict[str, str], ...] = ()
    conflicts: tuple[dict[str, object], ...] = ()
    note: str = ""


NOOP = lambda package: None


CASES = [
    Case(
        "01-single-add", "Single-sided insertion", "Insert r5 after r2.", "No edit.", "Accept the insertion.",
        lambda p: add_rectangle(p, "r5", "r2"), NOOP,
        (edit("left", "insert", "r5", "Insert r5 after r2."),),
    ),
    Case(
        "02-single-delete", "Single-sided deletion", "Delete r2.", "No edit.", "Accept the deletion; repair references if the target participates in a thread.",
        lambda p: remove_item(p, "r2"), NOOP,
        (edit("left", "delete", "r2", "Delete r2."),),
    ),
    Case(
        "03-single-move", "Single-sided move", "Move r1 to the top of the page-item stack.", "No edit.", "Accept the move.",
        lambda p: reorder(p, lambda root: move_after(root, "r1", "tfB")), NOOP,
        (edit("left", "move", "r1", "Move r1 after tfB (top of z-order)."),),
    ),
    Case(
        "04-both-add-diff-pos", "Both sides insert at different positions", "Insert rL after r1.", "Insert rR after r2.", "Accept both insertions in deterministic order.",
        lambda p: add_rectangle(p, "rL", "r1"), lambda p: add_rectangle(p, "rR", "r2"),
        (
            edit("left", "insert", "rL", "Insert rL after r1."),
            edit("right", "insert", "rR", "Insert rR after r2."),
        ),
        note="The merged sibling order must be deterministic and preserve both anchors.",
    ),
    Case(
        "05-move-move-divergent", "Both sides move the same node", "Move r1 to the top.", "Move r1 to the bottom.", "Report move-move.",
        lambda p: reorder(p, lambda root: move_after(root, "r1", "tfB")),
        lambda p: reorder(p, lambda root: move_before(root, "r1", "r2")),
        conflicts=(conflict("move-move", "r1", "top of page-item stack", "bottom of page-item stack"),),
    ),
    Case(
        "06-delete-vs-modify", "One side deletes; the other modifies", "Delete r2.", "Change r2 fill to red.", "Report delete-modify.",
        lambda p: remove_item(p, "r2"), lambda p: modify_item(p, "r2", FillColor="Color/Red"),
        conflicts=(conflict("delete-modify", "r2", "deleted", "FillColor=Color/Red"),),
    ),
    Case(
        "06b-delete-thread-vs-content", "Thread deletion versus story content", "Delete tf2 and repair tf1.NextTextFrame.", "Edit story1 paragraph 2.", "Report a structural/content conflict.",
        repair_after_tf2_delete, lambda p: edit_paragraph(p, 2, "brown", "crimson"),
        conflicts=(conflict("delete-thread-vs-content", "tf2/story1", "tf2 deleted; thread terminates at tf1", "story1 paragraph 2 changes brown to crimson"),),
        note="This is a policy case: a frame deletion changes story flow even though story text is stored separately.",
    ),
    Case(
        "07-same-story-diff-para", "Different paragraphs in the same Story", "Edit paragraph 2.", "Edit paragraph 4.", "Accept both edits.",
        lambda p: edit_paragraph(p, 2, "brown", "auburn"), lambda p: edit_paragraph(p, 4, "closes", "concludes"),
        (
            edit("left", "text-update", "story1/P2", "Change brown to auburn."),
            edit("right", "text-update", "story1/P4", "Change closes to concludes."),
        ),
    ),
    Case(
        "08-same-para-overlap", "Overlapping edits in one paragraph", "Replace quick with swift.", "Replace quick brown with slow red.", "Report text-overlap.",
        lambda p: edit_paragraph(p, 2, "quick", "swift"), lambda p: edit_paragraph(p, 2, "quick brown", "slow red"),
        conflicts=(conflict("text-overlap", "story1/P2@The quick brown", "quick -> swift", "quick brown -> slow red"),),
    ),
    Case(
        "08b-same-para-disjoint", "Disjoint edits in one paragraph", "Replace quick with swift.", "Replace lazy with sleepy.", "Accept at token granularity; conflict at paragraph-atom granularity.",
        lambda p: edit_paragraph(p, 2, "quick", "swift"), lambda p: edit_paragraph(p, 2, "lazy", "sleepy"),
        (
            edit("left", "text-update", "story1/P2@quick", "Change quick to swift."),
            edit("right", "text-update", "story1/P2@lazy", "Change lazy to sleepy."),
        ),
        note="The expected policy is token-aware. A paragraph-atomic implementation may instead report text-overlap.",
    ),
    Case(
        "09-zorder", "Z-order changes", "Move r1 one step forward (after r2).", "Move r2 one step backward (before r1).", "Accept both; report a conflict only if constraints contradict.",
        lambda p: reorder(p, lambda root: move_after(root, "r1", "r2")), lambda p: reorder(p, lambda root: move_before(root, "r2", "r1")),
        (
            edit("left", "z-order", "r1", "Constrain r1 after r2."),
            edit("right", "z-order", "r2", "Constrain r2 before r1."),
        ),
        note="Both branches encode the compatible order r2, r1.",
    ),
    Case(
        "10-group-ungroup", "Group/ungroup and child movement", "Ungroup g1.", "Move child r3 inside g1.", "Report structural-parent-change.",
        ungroup, lambda p: modify_item(p, "r3", ItemTransform="1 0 0 1 380 140"),
        conflicts=(conflict("structural-parent-change", "g1/r3", "g1 removed; r3 reparented to Spread", "r3 moved while parent remains g1"),),
    ),
    Case(
        "11-thread-insert", "Cross-node reference insertion", "Insert tfX between tf1 and tf2.", "No edit.", "Accept and update reciprocal references.",
        insert_thread_frame, NOOP,
        (edit("left", "reference-insert", "tfX", "Set tf1.Next=tfX, tfX.Previous=tf1, tfX.Next=tf2, and tf2.Previous=tfX."),),
    ),
    Case(
        "12-next-divergent", "Divergent NextTextFrame references", "Set tf1.NextTextFrame=tfA.", "Set tf1.NextTextFrame=tfB.", "Report reference-divergence.",
        lambda p: relink_tf1(p, "tfA"), lambda p: relink_tf1(p, "tfB"),
        conflicts=(conflict("reference-divergence", "tf1@NextTextFrame", "tfA", "tfB"),),
    ),
    Case(
        "13-parentstory-vs-content", "ParentStory reference versus story content", "Set tf2.ParentStory=story2 and detach it from tf1.", "Edit story1 content that previously flowed into tf2.", "Report reference-divergence.",
        lambda p: (modify_item(p, "tf1", NextTextFrame="n"), modify_item(p, "tf2", ParentStory="story2", PreviousTextFrame="n")),
        lambda p: edit_paragraph(p, 2, "jumps", "leaps"),
        conflicts=(conflict("reference-divergence", "tf2@ParentStory/story1", "tf2.ParentStory=story2", "story1 P2 changes jumps to leaps"),),
    ),
]


def operation_readme(case: Case) -> str:
    expectation = "accept" if not case.conflicts else "conflict"
    note = f"\n\nCase note: {case.note}" if case.note else ""
    return f"""# {case.name}

## Purpose

{case.requirement}. This fixture is expected to **{expectation}**: {case.expected_summary}

## Baseline document

The case uses the normalized corpus page: `r1`, `r2`, and group `g1` (children `r3` and `r4`), plus text frames `tf1`, `tf2`, `tfA`, and `tfB`. `tf1` and `tf2` initially form a thread over the four-paragraph `story1`.

## InDesign-equivalent operations

1. Close the common base document.
2. Reopen the base and perform the left operation: {case.left_summary}
3. Save/export that branch as `left.idml`, then close it.
4. Reopen the unchanged base and perform the right operation: {case.right_summary}
5. Save/export that branch as `right.idml`, then close it.

## Fixture construction record

The checked-in files are deterministic XML-derived fixtures. `base.idml` is built from `HelloWorld2.idml`; `left.idml` and `right.idml` begin as exact in-memory copies of that case base before the operations above are expressed in the relevant IDML XML parts. No InDesign GUI automation was executed. The package-level hashes and expected edits/conflicts are recorded in `expected.json`.{note}
"""


def global_readme() -> str:
    rows = "\n".join(
        f"| `{case.name}` | {case.requirement} | {case.left_summary} | {case.right_summary} | {case.expected_summary} |"
        for case in CASES
    )
    return f"""# Reliable Three-Way IDML Merge Corpus

This directory maps all 15 requested merge scenarios (13 main numbers plus the `06b` and `08b` variants) to concrete `base / left / right / expected` fixtures. The wording below is the English version of the source table.

## Reliability invariant

For every case, both branches are created from an exact copy of the same closed-state `base.idml`. Existing `Self` values are never regenerated. A branch may remove a node or add a new unique node, but all unchanged nodes retain their base identity. This avoids the live-session `Self` drift observed in the old `HelloWorld2A/B/C` samples.

The fixtures are generated from the XML in `../HelloWorld2.idml`; they model the listed InDesign operations directly in IDML and are not represented as hand-exported GUI artifacts. From the repository root, run `python3 idml/tools/generate_three_way_corpus.py --verify` to rebuild and verify the corpus.

## Case mapping

| Directory | Requirement | Left branch | Right branch | Expected merge result |
|---|---|---|---|---|
{rows}

## Directory contract

Each case contains:

```text
case-name/
  base.idml
  left.idml
  right.idml
  expected.json
  README.md
```

`expected.json` declares accepted edits and conflicts independently. A case with no conflicts has status `accept`; a case with at least one conflict has status `conflict`. The per-case README records reproducible InDesign-equivalent steps and the actual XML-derived construction method.

## Merge policy clarified by the table

- Independent inserts and edits to different paragraphs are accepted.
- Compatible z-order constraints are accepted and must produce deterministic order.
- Divergent moves, delete/modify pairs, overlapping text edits, structural reparenting, and incompatible references are reported as conflicts.
- Disjoint edits in one paragraph are accepted by a token-aware merger; a paragraph-atomic merger may report a conflict instead.
- Thread changes must update both ends of `PreviousTextFrame` / `NextTextFrame` references.
"""


def expected_document(case: Case, blobs: dict[str, bytes]) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "case": case.name,
        "requirement": case.requirement,
        "expectedStatus": "conflict" if case.conflicts else "accept",
        "acceptedEdits": list(case.accepted),
        "conflicts": list(case.conflicts),
        "invariants": {
            "commonBase": True,
            "unchangedSelfValuesStable": True,
            "reciprocalThreadReferencesRequired": True,
        },
        "artifacts": {name: {"sha256": sha256(data), "bytes": len(data)} for name, data in blobs.items()},
        "note": case.note or None,
    }


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def generate() -> None:
    source = load_source()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    expected_names = {case.name for case in CASES}
    for child in OUTPUT.iterdir():
        if child.is_dir() and child.name not in expected_names:
            raise RuntimeError(f"refusing to remove unexpected directory: {child}")

    for case in CASES:
        case_dir = OUTPUT / case.name
        if case_dir.exists():
            shutil.rmtree(case_dir)
        case_dir.mkdir(parents=True)

        base = build_base(source, case.name)
        left = base.clone()
        right = base.clone()
        case.left(left)
        case.right(right)

        blobs = {
            "base.idml": base.write(case_dir / "base.idml"),
            "left.idml": left.write(case_dir / "left.idml"),
            "right.idml": right.write(case_dir / "right.idml"),
        }
        write_text(case_dir / "expected.json", json.dumps(expected_document(case, blobs), indent=2, ensure_ascii=False))
        write_text(case_dir / "README.md", operation_readme(case))

    write_text(OUTPUT / "README.md", global_readme())


def self_ids(package: Package) -> set[str]:
    ids: set[str] = set()
    for name, data in package.entries.items():
        if not name.endswith(".xml"):
            continue
        root = ET.fromstring(data)
        ids.update(node.get("Self") for node in root.iter() if node.get("Self"))
    return ids


EXPECTED_ID_DELTAS: dict[str, dict[str, tuple[set[str], set[str]]]] = {
    "01-single-add": {"left.idml": ({"r5"}, set())},
    "02-single-delete": {"left.idml": (set(), {"r2"})},
    "04-both-add-diff-pos": {
        "left.idml": ({"rL"}, set()),
        "right.idml": ({"rR"}, set()),
    },
    "06-delete-vs-modify": {"left.idml": (set(), {"r2"})},
    "06b-delete-thread-vs-content": {"left.idml": (set(), {"tf2"})},
    "10-group-ungroup": {"left.idml": (set(), {"g1"})},
    "11-thread-insert": {"left.idml": ({"tfX"}, set())},
}


EXPECTED_CHANGED_PARTS: dict[str, dict[str, set[str]]] = {
    "01-single-add": {"left.idml": {SPREAD_PATH}},
    "02-single-delete": {"left.idml": {SPREAD_PATH}},
    "03-single-move": {"left.idml": {SPREAD_PATH}},
    "04-both-add-diff-pos": {"left.idml": {SPREAD_PATH}, "right.idml": {SPREAD_PATH}},
    "05-move-move-divergent": {"left.idml": {SPREAD_PATH}, "right.idml": {SPREAD_PATH}},
    "06-delete-vs-modify": {"left.idml": {SPREAD_PATH}, "right.idml": {SPREAD_PATH}},
    "06b-delete-thread-vs-content": {"left.idml": {SPREAD_PATH}, "right.idml": {STORY1_PATH}},
    "07-same-story-diff-para": {"left.idml": {STORY1_PATH}, "right.idml": {STORY1_PATH}},
    "08-same-para-overlap": {"left.idml": {STORY1_PATH}, "right.idml": {STORY1_PATH}},
    "08b-same-para-disjoint": {"left.idml": {STORY1_PATH}, "right.idml": {STORY1_PATH}},
    "09-zorder": {"left.idml": {SPREAD_PATH}, "right.idml": {SPREAD_PATH}},
    "10-group-ungroup": {"left.idml": {SPREAD_PATH}, "right.idml": {SPREAD_PATH}},
    "11-thread-insert": {"left.idml": {SPREAD_PATH}},
    "12-next-divergent": {"left.idml": {SPREAD_PATH}, "right.idml": {SPREAD_PATH}},
    "13-parentstory-vs-content": {"left.idml": {SPREAD_PATH}, "right.idml": {STORY1_PATH}},
}


def validate_thread(package: Package) -> None:
    root = package.root(SPREAD_PATH)
    frames = {node.get("Self"): node for node in root.iter() if local_name(node.tag) == "TextFrame"}
    for frame_id, frame in frames.items():
        previous = frame.get("PreviousTextFrame", "n")
        following = frame.get("NextTextFrame", "n")
        if previous != "n" and previous in frames and frames[previous].get("NextTextFrame") != frame_id:
            raise AssertionError(f"{frame_id}: non-reciprocal PreviousTextFrame={previous}")
        if following != "n" and following in frames and frames[following].get("PreviousTextFrame") != frame_id:
            raise AssertionError(f"{frame_id}: non-reciprocal NextTextFrame={following}")


def validate_manifest(package: Package) -> None:
    designmap = package.root("designmap.xml")
    story_ids = designmap.get("StoryList", "").split()
    story_refs: list[str] = []
    for child in designmap:
        src = child.get("src")
        if src:
            if src not in package.entries:
                raise AssertionError(f"designmap reference is missing from package: {src}")
            if local_name(child.tag) == "Story":
                story_refs.append(src)
    referenced_story_ids = [find_by_self(package.root(src), story_id).get("Self") for story_id, src in zip(story_ids, story_refs)]
    if story_ids != referenced_story_ids or len(story_ids) != len(story_refs):
        raise AssertionError(f"StoryList {story_ids} does not match Story references {story_refs}")


def read_package(path: Path) -> Package:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if not names or names[0] != "mimetype":
            raise AssertionError(f"{path}: mimetype is not first")
        if archive.getinfo("mimetype").compress_type != zipfile.ZIP_STORED:
            raise AssertionError(f"{path}: mimetype is compressed")
        bad = archive.testzip()
        if bad:
            raise AssertionError(f"{path}: corrupt member {bad}")
        return Package({name: archive.read(name) for name in names})


def verify() -> None:
    if not OUTPUT.exists():
        raise AssertionError(f"corpus does not exist: {OUTPUT}")
    actual_case_names = {path.name for path in OUTPUT.iterdir() if path.is_dir()}
    expected_case_names = {case.name for case in CASES}
    if actual_case_names != expected_case_names:
        raise AssertionError(
            f"case directories={sorted(actual_case_names)}; expected={sorted(expected_case_names)}"
        )
    if not (OUTPUT / "README.md").is_file():
        raise AssertionError("corpus README.md is missing")
    for case in CASES:
        case_dir = OUTPUT / case.name
        actual_files = {path.name for path in case_dir.iterdir() if path.is_file()}
        expected_files = {"base.idml", "left.idml", "right.idml", "expected.json", "README.md"}
        if actual_files != expected_files:
            raise AssertionError(f"{case.name}: files={sorted(actual_files)}; expected={sorted(expected_files)}")
        expected = json.loads((case_dir / "expected.json").read_text(encoding="utf-8"))
        if expected["case"] != case.name:
            raise AssertionError(f"{case.name}: expected.json case mismatch")
        if expected["expectedStatus"] != ("conflict" if case.conflicts else "accept"):
            raise AssertionError(f"{case.name}: status mismatch")

        packages = {name: read_package(case_dir / name) for name in ("base.idml", "left.idml", "right.idml")}
        base_ids = self_ids(packages["base.idml"])
        validate_manifest(packages["base.idml"])
        for branch_name in ("left.idml", "right.idml"):
            branch = packages[branch_name]
            branch_ids = self_ids(branch)
            added = branch_ids - base_ids
            removed = base_ids - branch_ids
            expected_added, expected_removed = EXPECTED_ID_DELTAS.get(case.name, {}).get(branch_name, (set(), set()))
            if added != expected_added or removed != expected_removed:
                raise AssertionError(
                    f"{case.name}/{branch_name}: Self delta added={sorted(added)}, removed={sorted(removed)}; "
                    f"expected added={sorted(expected_added)}, removed={sorted(expected_removed)}"
                )
            changed_parts = {
                name
                for name in set(packages["base.idml"].entries) | set(branch.entries)
                if packages["base.idml"].entries.get(name) != branch.entries.get(name)
            }
            expected_parts = EXPECTED_CHANGED_PARTS.get(case.name, {}).get(branch_name, set())
            if changed_parts != expected_parts:
                raise AssertionError(
                    f"{case.name}/{branch_name}: changed parts={sorted(changed_parts)}; "
                    f"expected={sorted(expected_parts)}"
                )
            validate_manifest(branch)
            validate_thread(branch)
        validate_thread(packages["base.idml"])

        for artifact, details in expected["artifacts"].items():
            data = (case_dir / artifact).read_bytes()
            if sha256(data) != details["sha256"] or len(data) != details["bytes"]:
                raise AssertionError(f"{case.name}/{artifact}: recorded digest mismatch")

    print(f"verified {len(CASES)} cases in {OUTPUT}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="verify after generating")
    parser.add_argument("--verify-only", action="store_true", help="verify existing files without rewriting them")
    args = parser.parse_args()
    try:
        if not args.verify_only:
            generate()
        if args.verify or args.verify_only:
            verify()
    except Exception as error:  # pragma: no cover - command-line diagnostics
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
