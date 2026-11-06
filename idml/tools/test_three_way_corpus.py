#!/usr/bin/env python3
"""Contract and corruption tests, without invoking an IDML merger.

Run: python3 -m unittest discover -s idml/tools -p 'test_three_way_corpus.py'
"""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import generate_three_way_corpus as corpus


class CorpusContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = corpus.OUTPUT

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="idml-corpus-contract-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "corpus"
        shutil.copytree(self.original, self.root)
        corpus.OUTPUT = self.root
        self.addCleanup(setattr, corpus, "OUTPUT", self.original)

    def document(self, name="08b-same-para-disjoint"):
        return self.root / name / "expected.json"

    def mutate(self, change, name="08b-same-para-disjoint"):
        path = self.document(name)
        d = json.loads(path.read_text())
        change(d)
        path.write_text(json.dumps(d))

    def snapshot(self):
        return {str(p.relative_to(self.root)): (hashlib.sha256(p.read_bytes()).hexdigest(), p.stat().st_mtime_ns)
                for p in self.root.rglob("*") if p.is_file()}

    def verify(self):
        with contextlib.redirect_stdout(io.StringIO()):
            corpus.verify()

    def test_read_only_success_and_repeated_verification(self):
        before = self.snapshot()
        self.verify()
        self.verify()
        self.assertEqual(before, self.snapshot())

    def test_cli_verify_is_read_only_and_rejects_write_flag_combination(self):
        before = self.snapshot()
        for flag in ([], ["--verify"], ["--verify-only"]):
            run = subprocess.run(["python3", str(corpus.SCRIPT), "--output", str(self.root), *flag],
                                 capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stderr)
        self.assertEqual(before, self.snapshot())
        run = subprocess.run(["python3", str(corpus.SCRIPT), "--output", str(self.root), "--generate", "--verify"],
                             capture_output=True, text=True)
        self.assertNotEqual(0, run.returncode)
        self.assertEqual(before, self.snapshot())

    def test_missing_base_is_rejected_without_repairing_files(self):
        (self.root / "01-single-add/base.idml").unlink()
        before = self.snapshot()
        with self.assertRaises(FileNotFoundError):
            self.verify()
        self.assertEqual(before, self.snapshot())

    def test_missing_and_unexpected_case_are_rejected(self):
        shutil.rmtree(self.root / "01-single-add")
        with self.assertRaisesRegex(AssertionError, "directories mismatch"):
            self.verify()
        (self.root / "unknown").mkdir()
        with self.assertRaisesRegex(AssertionError, "directories mismatch"):
            self.verify()

    def test_unknown_schema_and_profile_are_rejected(self):
        path = self.document()
        original = path.read_text()
        for field, value in (("schemaVersion", 1), ("schemaVersion", 99), ("schemaVersion", True),
                             ("policyProfile", "token-aware"), ("policyProfile", None)):
            with self.subTest(field=field, value=value):
                path.write_text(original)
                self.mutate(lambda d: d.update({field: value}))
                with self.assertRaises(AssertionError):
                    self.verify()

    def test_malformed_json_and_duplicate_keys_are_rejected(self):
        path = self.document()
        path.write_text("{")
        with self.assertRaises(json.JSONDecodeError):
            self.verify()
        path.write_text('{"schemaVersion": 2, "schemaVersion": 1}')
        with self.assertRaisesRegex(ValueError, "duplicate JSON key"):
            self.verify()

    def test_wrong_hash_missing_field_and_wrong_status_are_rejected(self):
        path = self.document()
        original = path.read_text()
        changes = [lambda d: d["artifacts"]["base.idml"].update(sha256="0" * 64),
                   lambda d: d.pop("identity"),
                   lambda d: d["oracle"]["assertions"][0].pop("base"),
                   lambda d: d.update(expectedStatus="accept"),
                   lambda d: d["invariants"].update(commonBase=False),
                   lambda d: d["oracle"]["assertions"][0].update(right="incorrect")]
        for i, change in enumerate(changes):
            with self.subTest(change=i):
                path.write_text(original)
                self.mutate(change)
                with self.assertRaises(AssertionError):
                    self.verify()

    def test_tampered_snapshot_fails_even_if_its_digest_is_resealed(self):
        name = "07-same-story-diff-para"
        path = self.root / name / ("expected/" + corpus.STORY1_PATH)
        data = path.read_bytes().replace(b"auburn", b"wrong")
        path.write_bytes(data)
        self.mutate(lambda d: d["oracle"]["entries"][corpus.STORY1_PATH].update(
            sha256=corpus.sha256(data), bytes=len(data)), name)
        with self.assertRaisesRegex(AssertionError, "contract mismatch"):
            self.verify()

    def test_changed_input_fails_even_if_artifact_hash_is_resealed(self):
        name = "01-single-add"
        path = self.root / name / "right.idml"
        package = corpus.read_package(path)
        corpus.modify_item(package, "r1", Name="changed")
        data = package.write(path)
        self.mutate(lambda d: d["artifacts"]["right.idml"].update(
            sha256=corpus.sha256(data), bytes=len(data)), name)
        with self.assertRaisesRegex(AssertionError, "common-base recipe"):
            self.verify()

    def test_missing_snapshot_and_unlisted_file_are_rejected(self):
        path = self.root / "01-single-add/expected" / corpus.SPREAD_PATH
        data = path.read_bytes()
        path.unlink()
        with self.assertRaises(FileNotFoundError):
            self.verify()
        path.write_bytes(data)
        (self.root / "01-single-add/extra.xml").write_text("<extra/>")
        with self.assertRaisesRegex(AssertionError, "required files"):
            self.verify()

    def test_regeneration_reproduces_all_bytes_including_oracles_and_docs(self):
        before = self.snapshot()
        with contextlib.redirect_stdout(io.StringIO()):
            corpus.generate()
        self.verify()
        self.assertEqual({k: v[0] for k, v in before.items()},
                         {k: v[0] for k, v in self.snapshot().items()})

    def test_reviewed_target_text_and_conflict_baseline(self):
        expected = corpus.ET.parse(self.root / "07-same-story-diff-para/expected" / corpus.STORY1_PATH)
        text = [n.text for n in expected.iter() if corpus.local_name(n.tag) == "Content"]
        self.assertEqual([corpus.BASE_PARAGRAPHS[0], "The quick auburn fox jumps over the lazy dog.",
                          corpus.BASE_PARAGRAPHS[2], "Paragraph four concludes the story."], text)
        conflict = json.loads(self.document("06-delete-vs-modify").read_text())["oracle"]["assertions"][0]
        self.assertIsNone(conflict["base"]["FillColor"])
        self.assertEqual({"exists": False}, conflict["left"])


if __name__ == "__main__":
    unittest.main()
