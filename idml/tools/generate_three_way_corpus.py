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
import io
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

    def to_bytes(self) -> bytes:
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
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
        return buffer.getvalue()

    def write(self, path: Path) -> bytes:
        path.parent.mkdir(parents=True, exist_ok=True)
        data = self.to_bytes()
        path.write_bytes(data)
        return data


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
    if case_name == "10b-divergent-parent":
        # A real alternate parent exists in the common base, with an unchanged child.
        # It is inserted immediately after g1, before tf1, on all three versions.
        g2 = make_group("g2", [make_rectangle(rectangle_template, "r6", 580, 140)])
        spread.insert(list(spread).index(g1) + 1, g2)
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


def move_r3_to_g2(package: Package) -> None:
    root, _ = spread_container(package)
    parent, r3 = item_parent(root, "r3")
    parent.remove(r3)
    find_by_self(root, "g2").append(r3)  # after r6; r4 remains inside g1
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
        "08b-same-para-disjoint", "Disjoint edits in one paragraph", "Replace quick with swift.", "Replace lazy with sleepy.", "Report text-node update/update under strict-text-node-v1.",
        lambda p: edit_paragraph(p, 2, "quick", "swift"), lambda p: edit_paragraph(p, 2, "lazy", "sleepy"),
        conflicts=(conflict("text-node-update-update", "story1/P2", "quick -> swift", "lazy -> sleepy"),),
        note="Migrated from schemaVersion 1 token-aware accept to strict-text-node-v1 conflict by MR01; token merge is deferred.",
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
        "10-group-ungroup", "Reparenting with an independent content update", "Ungroup g1.", "Change r3 ItemTransform while it remains inside g1.", "Accept both reparenting and the r3 attribute update (generic XML only).",
        ungroup, lambda p: modify_item(p, "r3", ItemTransform="1 0 0 1 380 140"),
        accepted=(edit("left", "ungroup", "g1/r3/r4", "Remove g1; promote r3 and r4 to Spread in their original order."),
                  edit("right", "attribute-update", "r3@ItemTransform", "Keep ItemTransform=1 0 0 1 380 140 on r3.")),
        note="Schema 3 corrects the former conflict expectation using paper §2.1, §3.1 rules 4/7 and §6. No local-coordinate conflict policy is enabled; visual placement is not proven by XML merge success.",
    ),
    Case(
        "10b-divergent-parent", "Two genuine edits assign different new parents", "Ungroup g1; promote r3 and r4 to Spread.", "Move r3 from g1 into g2, after r6.", "Report Position/Position: r3 has two incompatible new parents.",
        ungroup, move_r3_to_g2,
        conflicts=(conflict("position-position", "r3@parent", "ud1", "g2"),),
        note="g2 and r6 exist in the common base. Required parent conflict is base=g1, left=ud1, right=g2; any additional PCS contradictions must be derived, not suppressed to force one low-level report.",
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


PROFILE = "strict-text-node-v1"
SCHEMA_VERSION = 3
# Reviewed target recipes. These do not call a merger or derive the oracle from its output.
# Single-sided targets are expressed independently from the Case.left/right callbacks.
REFERENCES = {
    "01": (["paper"], "§3.1 rule 5, p4"),
    "02": (["paper", "idml"], "§3.1 rule 6, p4; reference integrity is IDML policy"),
    "03": (["paper"], "§3.1 rule 7, pp4-5"),
    "04": (["paper"], "§3.1 rule 5; §5, p6"),
    "05": (["paper"], "§6.3 Position/Position, p8"),
    "06": (["paper", "project"], "§6.3 optional Delete/Edit, p8; project strict mode"),
    "06b": (["idml"], "MR01 Story-level dependency rule; §9 is motivation, not proof"),
    "07": (["paper"], "§3.1 rule 4, p4; §4 matching, p5"),
    "08": (["paper", "project"], "§2.3 text nodes, p3; §6.3 Update/Update, p8"),
    "08b": (["project"], "MR01 strict-text-node-v1; no token merge guarantee in §7, p9"),
    "09": (["paper"], "§3.1 rule 7; §5 relation consistency, p6"),
    "10": (["paper"], "§2.1, p2; §3.1 rules 4/7, pp4-5; §6, p6: preserve reparenting and content edits"),
    "10b": (["paper"], "§5 unique parent, p6; §6.3 Position/Position, p8: two incompatible parent edits"),
    "11": (["paper", "idml"], "§3.1 rule 5, p4; reciprocal thread references are IDML policy"),
    "12": (["paper", "idml"], "§6.3 Update/Update, p8; thread integrity is separate"),
    "13": (["idml"], "MR01 Story-level dependency rule; no character-to-frame layout inference"),
}


def provenance(case: Case) -> dict:
    layers, reference = REFERENCES[case.name.split("-", 1)[0]]
    return {"layers": layers, "reference": reference,
            "construction": "xml-derived", "oracle": "reviewed-target-recipe-no-merger"}


def targets(case: Case) -> list[dict]:
    # Structured targets remain resolvable independently of human-readable descriptions.
    key = case.name.split("-", 1)[0]
    target = {
        "01": ["r5"], "02": ["r2"], "03": ["r1"], "04": ["rL", "rR"],
        "05": ["r1"], "06": ["r2"], "06b": ["tf2", "story1"],
        "07": ["story1"], "08": ["story1"], "08b": ["story1"], "09": ["r1", "r2"],
        "10": ["g1", "r3", "r4"], "10b": ["g1", "g2", "r3", "r4", "r6"], "11": ["tf1", "tfX", "tf2"], "12": ["tf1"],
        "13": ["tf2", "story1", "story2"],
    }[key]
    return [{"part": STORY1_PATH if t == "story1" else STORY2_PATH if t == "story2" else SPREAD_PATH,
             "self": t} for t in target]


def identity_contract(case: Case) -> dict:
    spread_ids = ["g1", "r1", "r2", "r3", "r4", "tf1", "tf2", "tfA", "tfB", "ud1", "ud6"]
    if case.name == "10b-divergent-parent":
        spread_ids = sorted(spread_ids + ["g2", "r6"])
    return {"strategy": "stable-Self-plus-explicit-paragraph-ordinal",
            "baseSelfByPart": {SPREAD_PATH: spread_ids,
                               STORY1_PATH: ["story1"], STORY2_PATH: ["story2"]},
            "selflessMatches": [{"part": STORY1_PATH, "selector": f"Story[@Self='story1']/ParagraphStyleRange[{i}]",
                                  "baseOrdinal": i, "leftOrdinal": i, "rightOrdinal": i} for i in range(1, 5)],
            "note": "Paragraph order is unchanged in these 16 cases; this is not a general position matching rule."}


def expected_package(case: Case, base: Package) -> Package:
    result = base.clone()
    key = case.name.split("-", 1)[0]
    if case.conflicts:
        raise ValueError("conflict cases have no successful output oracle")
    if key == "01":
        add_rectangle(result, "r5", "r2")
    elif key == "02":
        remove_item(result, "r2")
    elif key in {"03", "04", "09", "11"}:
        if key == "04":
            add_rectangle(result, "rL", "r1")
            add_rectangle(result, "rR", "r2")
        if key == "11":
            insert_thread_frame(result)
        order = {"03": ["r2", "g1", "tf1", "tf2", "tfA", "tfB", "r1"],
                 "04": ["r1", "rL", "r2", "rR", "g1", "tf1", "tf2", "tfA", "tfB"],
                 "09": ["r2", "r1", "g1", "tf1", "tf2", "tfA", "tfB"],
                 "11": ["r1", "r2", "g1", "tf1", "tfX", "tf2", "tfA", "tfB"]}[key]
        root, spread = spread_container(result)
        items = {n.get("Self"): n for n in page_item_children(spread)}
        for node in list(items.values()):
            spread.remove(node)
        spread.extend(items[n] for n in order)
        result.put_root(SPREAD_PATH, root)
    elif key == "10":
        root, spread = spread_container(result)
        group = find_by_self(root, "g1")
        children = {node.get("Self"): copy.deepcopy(node) for node in group}
        children["r3"].set("ItemTransform", "1 0 0 1 380 140")
        items = {node.get("Self"): node for node in page_item_children(spread) if node is not group}
        items.update(children)
        for node in list(page_item_children(spread)):
            spread.remove(node)
        spread.extend(items[name] for name in ["r1", "r2", "r3", "r4", "tf1", "tf2", "tfA", "tfB"])
        result.put_root(SPREAD_PATH, root)
    elif key == "07":
        root = result.root(STORY1_PATH)
        contents = [n for n in root.iter() if local_name(n.tag) == "Content"]
        contents[1].text = "The quick auburn fox jumps over the lazy dog."
        contents[3].text = "Paragraph four concludes the story."
        result.put_root(STORY1_PATH, root)
    else:
        raise ValueError(f"missing reviewed oracle recipe: {case.name}")
    return result


def conflict_assertions(case: Case) -> list[dict]:
    key = case.name.split("-", 1)[0]
    if not case.conflicts:
        return []
    # Explicit business objects. MR09 maps generic slots; MR11 maps domain reports.
    rows = {
        "05": (SPREAD_PATH, "r1", "page-item-sibling-context", ["r2", "g1"], ["tfB", None], [None, "r2"]),
        "06": (SPREAD_PATH, "r2", "existence/FillColor", {"exists": True, "FillColor": None},
               {"exists": False}, {"exists": True, "FillColor": "Color/Red"}),
        "06b": (SPREAD_PATH, "tf2/story1", "layout/content-dependency",
                {"frame": {"Self": "tf2", "ParentStory": "story1", "PreviousTextFrame": "tf1", "NextTextFrame": "n"}, "storyParagraphs": BASE_PARAGRAPHS},
                {"frame": None, "tf1.NextTextFrame": "n", "storyParagraphs": BASE_PARAGRAPHS},
                {"frame": {"Self": "tf2", "ParentStory": "story1", "PreviousTextFrame": "tf1", "NextTextFrame": "n"},
                 "storyParagraphs": [BASE_PARAGRAPHS[0], "The quick crimson fox jumps over the lazy dog.", *BASE_PARAGRAPHS[2:]]}),
        "08": (STORY1_PATH, "story1/P2/Content", "text", BASE_PARAGRAPHS[1],
               "The swift brown fox jumps over the lazy dog.", "The slow red fox jumps over the lazy dog."),
        "08b": (STORY1_PATH, "story1/P2/Content", "text", BASE_PARAGRAPHS[1],
                "The swift brown fox jumps over the lazy dog.", "The quick brown fox jumps over the sleepy dog."),
        "10b": (SPREAD_PATH, "r3", "parent", "g1", "ud1", "g2"),
        "12": (SPREAD_PATH, "tf1", "NextTextFrame", "tf2", "tfA", "tfB"),
        "13": (SPREAD_PATH, "tf2/story1", "ParentStory/content-dependency",
               {"ParentStory": "story1", "PreviousTextFrame": "tf1", "tf1.NextTextFrame": "tf2", "storyParagraphs": BASE_PARAGRAPHS},
               {"ParentStory": "story2", "PreviousTextFrame": "n", "tf1.NextTextFrame": "n", "storyParagraphs": BASE_PARAGRAPHS},
               {"ParentStory": "story1", "PreviousTextFrame": "tf1", "tf1.NextTextFrame": "tf2",
                "storyParagraphs": [BASE_PARAGRAPHS[0], "The quick brown fox leaps over the lazy dog.", *BASE_PARAGRAPHS[2:]]}),
    }
    part, target, field, base, left, right = rows[key]
    return [{"level": "domain" if key in {"06b", "13"} else "generic",
             "part": part, "target": target, "field": field,
             "base": base, "left": left, "right": right,
             "type": case.conflicts[0]["type"], "relatedParts": [STORY1_PATH] if key in {"06b", "13"} else []}]


# Filled from the independently constructed target during generation, never from a merger.
ORACLES: dict[str, dict] = {}


def oracle_contract(case: Case) -> dict:
    return ORACLES[case.name]


def build_oracle(case: Case, base: Package, case_dir: Path | None = None) -> dict:
    if case.conflicts:
        return {"kind": "conflicts", "assertions": conflict_assertions(case), "entries": {}}
    expected = expected_package(case, base)
    entries = {}
    for name, data in sorted(expected.entries.items()):
        changed = data != base.entries.get(name)
        if changed and not name.endswith(".xml"):
            raise AssertionError(f"unexpected binary edit in target recipe: {name}")
        record = {"sha256": sha256(data), "bytes": len(data),
                  "source": "snapshot" if changed else "base"}
        if changed:
            record["path"] = "expected/" + name
            if case_dir is not None:
                target = case_dir / record["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
        entries[name] = record
    return {"kind": "package", "assertions": [], "entries": entries}


def operation_readme(case: Case) -> str:
    expectation = "accept" if not case.conflicts else "conflict"
    note = f"\n\nCase note: {case.note}" if case.note else ""
    baseline_extra = (" For 10b only, g2 (containing r6) is inserted immediately after g1 and before tf1 in the common base. "
                      "Left removes g1 and promotes r3/r4 at its position; right leaves r4 in g1 and appends r3 after r6 in g2.") if case.name == "10b-divergent-parent" else ""
    snapshots = "\n".join("- `" + record["path"] + "`" for record in ORACLES[case.name]["entries"].values()
                          if record["source"] == "snapshot") or "None: conflict cases have no successful XML output."
    return f"""# {case.name}

## Purpose

{case.requirement}. This fixture is expected to **{expectation}**: {case.expected_summary}

## Contract and evidence

Profile: `{PROFILE}`. Schema: `{SCHEMA_VERSION}`. Sources: {", ".join(provenance(case)["layers"])}.
Basis: {provenance(case)["reference"]}.
Structured targets, identity matching and exact business conflict objects (including base/left/right) are in `expected.json`.
Successful cases compare the complete package entry inventory: changed XML uses the listed `expected/` snapshots; unchanged entries use the exact base bytes. No expected output is obtained from a merger. Domain reports are not claimed to exist in the current implementation.

## Baseline document

The case uses the normalized corpus page: `r1`, `r2`, and group `g1` (children `r3` and `r4`), plus text frames `tf1`, `tf2`, `tfA`, and `tfB`. `tf1` and `tf2` initially form a thread over the four-paragraph `story1`.{baseline_extra}

## Expected XML snapshots

{snapshots}

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

This directory maps all 16 merge scenarios (the original 15 cases plus `10b-divergent-parent`) to concrete `base / left / right / expected` fixtures. The wording below is the English version of the source table.

## Authoritative specification

The target contract is [IDML XML Merge Test Specification](../../../../../../../../docs/xml-merge-test-spec.md). This corpus validates test data, not current merger conformance. Run Scala contract checks with `sbt "idml/testOnly *CorpusContractSpec"`; run verifier corruption tests with `python3 -m unittest discover -s idml/tools -p 'test_three_way_corpus.py'`.

## Reliability invariant

For every case, both branches are created from an exact copy of the same closed-state `base.idml`. Existing `Self` values are never regenerated. A branch may remove a node or add a new unique node, but all unchanged nodes retain their base identity. This avoids the live-session `Self` drift observed in the old `HelloWorld2A/B/C` samples.

The fixtures are generated from the XML in `../HelloWorld2.idml`; they model the listed InDesign operations directly in IDML and are not represented as hand-exported GUI artifacts. From the repository root, run `python3 idml/tools/generate_three_way_corpus.py --verify` for **read-only** verification. Use `--generate` explicitly to rebuild, or `--generate --output /tmp/idml-corpus` for isolated regeneration. `--verify-only` remains a read-only alias; no arguments also verifies. Generation and verification flags cannot be combined.

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
  expected/  # changed XML snapshots, success cases only
```

`expected.json` uses schemaVersion 3 and the only enabled profile `strict-text-node-v1`. Schemas 1 and 2 are rejected with an explicit migration error. Schema 3 corrects case 10 to accept (no input bytes changed) and adds the genuine parent-conflict case 10b. The fixed status inventory is 8 accept and 8 conflict. `08b` was migrated from historical token-aware accept to text-node conflict as frozen in MR01. Each document records provenance, structured targets, base identities, explicit selfless matches, hashes, migration and the executable oracle. `oracle.entries` is the complete successful ZIP entry inventory; snapshot paths resolve relative to the case directory. `oracle.assertions` contains exact business conflict objects; generic slot mapping is MR09, compatible edits and divergent-parent checks are MR06/MR07, and cross-part domain implementation is MR11/MR12. Unknown profiles, incomplete fields and inconsistent status are rejected.

`expected.json` declares accepted edits and conflicts independently. A case with no conflicts has status `accept`; a case with at least one conflict has status `conflict`. The per-case README records reproducible InDesign-equivalent steps and the actual XML-derived construction method.

## Merge policy clarified by the table

- Independent inserts and edits to different paragraphs are accepted.
- Compatible z-order constraints are accepted and must produce deterministic order.
- Divergent moves, delete/modify pairs, overlapping text edits, incompatible structural reparenting, and incompatible references are reported as conflicts.
- Different edits to the same text node conflict, even at disjoint word ranges. Token-aware merging is deferred; a runner must not accept either result interchangeably.
- `10` accepts reparenting plus the independent ItemTransform update, per paper §2.1 and §3.1 rules 4/7. Its old forced conflict rule has been withdrawn. XML success does not prove the intended visual position in InDesign.
- `10b` requires the genuine parent conflict for r3: base=g1, left=ud1 (Spread), right=g2. Other low-level relation contradictions are not ruled out or silently discarded.
- `06b` and `13` use MR01 Story-level dependency rules, not inferred character-to-frame layout.
- Thread changes must update both ends of `PreviousTextFrame` / `NextTextFrame` references.
"""


def expected_document(case: Case, blobs: dict[str, bytes]) -> dict[str, object]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyProfile": PROFILE,
        "provenance": provenance(case),
        "targets": targets(case),
        "identity": identity_contract(case),
        "oracle": oracle_contract(case),
        "migration": {"fromSchemaVersion": None if case.name == "10b-divergent-parent" else 2,
                      "date": "2026-11-06",
                      "reason": "Schema 3 paper-based correction: case10 accepts reparenting plus content update; add case10b genuine divergent-parent conflict; withdraw forced local-coordinate policy. New case10b has no prior schema; preserve strict-text-node-v1 (including 08b conflict)."},
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
        ORACLES[case.name] = build_oracle(case, base, case_dir)
        write_text(case_dir / "expected.json", json.dumps(expected_document(case, blobs), indent=2, ensure_ascii=False))
        write_text(case_dir / "README.md", operation_readme(case))

    write_text(OUTPUT / "README.md", global_readme())


def validate_thread(package: Package) -> None:
    root = package.root(SPREAD_PATH)
    frames = {node.get("Self"): node for node in root.iter() if local_name(node.tag) == "TextFrame"}
    story_ids = {node.get("Self") for name in (STORY1_PATH, STORY2_PATH)
                 for node in package.root(name).iter() if local_name(node.tag) == "Story"}
    for frame_id, frame in frames.items():
        if frame.get("ParentStory") not in story_ids:
            raise AssertionError(f"{frame_id}: missing ParentStory")
        visited = set()
        current = frame_id
        while current != "n":
            if current in visited:
                raise AssertionError(f"{frame_id}: cyclic thread")
            visited.add(current)
            if current not in frames:
                raise AssertionError(f"{frame_id}: missing thread target {current}")
            current = frames[current].get("NextTextFrame", "n")
        previous = frame.get("PreviousTextFrame", "n")
        following = frame.get("NextTextFrame", "n")
        if previous != "n" and (previous not in frames or frames[previous].get("NextTextFrame") != frame_id):
            raise AssertionError(f"{frame_id}: non-reciprocal PreviousTextFrame={previous}")
        if following != "n" and (following not in frames or frames[following].get("PreviousTextFrame") != frame_id):
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


def unique_json(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def validate_document(document: dict, case: Case, blobs: dict[str, bytes], base: Package) -> None:
    if type(document.get("schemaVersion")) is not int or document["schemaVersion"] != SCHEMA_VERSION:
        raise AssertionError(f"{case.name}: unsupported schemaVersion; migrate schemas 1/2 explicitly to 3")
    if document.get("policyProfile") != PROFILE:
        raise AssertionError(f"{case.name}: unknown or missing policyProfile; required {PROFILE}")
    ORACLES[case.name] = build_oracle(case, base)
    wanted = expected_document(case, blobs)
    # Exact shape/content checking rejects missing fields, extra fields and wrong types as
    # well as status changes. JSON serialization distinguishes booleans from numeric 0/1.
    if json.dumps(document, sort_keys=True) != json.dumps(wanted, sort_keys=True):
        keys = sorted(k for k in document.keys() | wanted.keys() if
                      json.dumps(document.get(k), sort_keys=True) != json.dumps(wanted.get(k), sort_keys=True))
        raise AssertionError(f"{case.name}: contract mismatch in {keys}")


def verify() -> None:
    if not OUTPUT.exists():
        raise AssertionError(f"corpus does not exist: {OUTPUT}")
    actual_case_names = {path.name for path in OUTPUT.iterdir() if path.is_dir()}
    if actual_case_names != {case.name for case in CASES}:
        raise AssertionError(f"case directories mismatch: {sorted(actual_case_names)}")
    if (OUTPUT / "README.md").read_text(encoding="utf-8") != global_readme().rstrip() + "\n":
        raise AssertionError("corpus README missing or stale")
    source = load_source()
    for case in CASES:
        case_dir = OUTPUT / case.name
        if any(p.is_symlink() for p in case_dir.rglob("*")):
            raise AssertionError(f"{case.name}: symlinks are not permitted")
        document = json.loads((case_dir / "expected.json").read_text(encoding="utf-8"), object_pairs_hook=unique_json)
        base = build_base(source, case.name)
        left, right = base.clone(), base.clone()
        case.left(left)
        case.right(right)
        canonical = {"base.idml": base, "left.idml": left, "right.idml": right}
        blobs = {name: (case_dir / name).read_bytes() for name in canonical}
        validate_document(document, case, blobs, base)
        # The independently reproducible construction checks common base and all unchanged
        # identities/content, not merely the set of identity strings or self-declared hashes.
        for name, package in canonical.items():
            if blobs[name] != package.to_bytes():
                raise AssertionError(f"{case.name}/{name}: input differs from common-base recipe")
            actual = read_package(case_dir / name)
            validate_manifest(actual)
            validate_thread(actual)
            for part, data in actual.entries.items():
                if part.endswith(".xml"):
                    ids = [n.get("Self") for n in ET.fromstring(data).iter() if n.get("Self")]
                    if len(ids) != len(set(ids)):
                        raise AssertionError(f"{case.name}/{name}/{part}: duplicate Self")
        oracle = document["oracle"]
        required = {"base.idml", "left.idml", "right.idml", "expected.json", "README.md"}
        if not case.conflicts:
            wanted_package = expected_package(case, base)
            validate_manifest(wanted_package)
            validate_thread(wanted_package)
            for entry, record in oracle["entries"].items():
                if record["source"] == "snapshot":
                    required.add(record["path"])
                    data = (case_dir / record["path"]).read_bytes()
                    if data != wanted_package.entries[entry]:
                        raise AssertionError(f"{case.name}/{entry}: snapshot differs from reviewed target recipe")
                    ET.fromstring(data)
                elif wanted_package.entries[entry] != base.entries[entry]:
                    raise AssertionError(f"{case.name}/{entry}: incorrect base source")
        actual_files = {str(p.relative_to(case_dir)) for p in case_dir.rglob("*") if p.is_file()}
        if actual_files != required:
            raise AssertionError(f"{case.name}: required files differ: {sorted(actual_files ^ required)}")
        if (case_dir / "README.md").read_text(encoding="utf-8") != operation_readme(case).rstrip() + "\n":
            raise AssertionError(f"{case.name}: README missing or stale")
    print(f"verified {len(CASES)} cases, schema {SCHEMA_VERSION}, profile {PROFILE} in {OUTPUT} (read-only)")


def main() -> int:
    global OUTPUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="verify existing files without rewriting them (default)")
    parser.add_argument("--generate", action="store_true", help="explicitly rebuild fixtures and expected snapshots")
    parser.add_argument("--output", type=Path, default=OUTPUT, help="corpus root (also supports isolated regeneration)")
    parser.add_argument("--verify-only", action="store_true", help="verify existing files without rewriting them")
    args = parser.parse_args()
    if args.generate and (args.verify or args.verify_only):
        parser.error("--generate cannot be combined with read-only verification")
    OUTPUT = args.output.resolve()
    try:
        if args.generate:
            generate()
        verify()
    except Exception as error:  # pragma: no cover - command-line diagnostics
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
