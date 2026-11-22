import copy
import json
import tempfile
import unittest
from pathlib import Path
from verify_real_world_manifest import ROOT, verify


class RealWorldEvidenceGateTest(unittest.TestCase):
    def setUp(self):
        self.document = json.loads((ROOT / 'manifest.json').read_text())
        self.document['releaseReady'] = False
        for case in self.document['cases']:
            case.update(status='awaiting-input', authorization=dict(status='missing', basis=None),
                        indesignVersion=None, operations=dict(left=None, right=None), artifacts={})

    def check_document(self, document):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'manifest.json'
            path.write_text(json.dumps(document))
            return verify(path)

    def test_missing_real_evidence_is_explicitly_not_release_ready(self):
        report = self.check_document(self.document)
        self.assertTrue(report['contractValid'])
        self.assertFalse(report['releaseReady'])
        self.assertEqual(report['suppliedCases'], 0)
        self.assertEqual(len(report['blockers']), 4)

    def test_cannot_assert_ready_without_evidence(self):
        self.document['releaseReady'] = True
        with self.assertRaises(AssertionError):
            self.check_document(self.document)

    def test_no_empty_or_duplicate_case_inventory(self):
        for cases in ([], [self.document['cases'][0]] * 4):
            d = copy.deepcopy(self.document)
            d['cases'] = cases
            with self.assertRaises(AssertionError):
                self.check_document(d)

    def test_ready_case_requires_authorization_and_independent_oracle(self):
        c = self.document['cases'][0]
        c['status'] = 'ready'
        with self.assertRaises(AssertionError):
            self.check_document(self.document)
        c['authorization'] = dict(status='authorized', basis='original work')
        c['indesignVersion'] = 'recorded version'
        c['operations'] = dict(left=['edit'], right=['move'])
        with self.assertRaisesRegex(AssertionError, 'artifacts required'):
            self.check_document(self.document)

    def test_artifact_paths_cannot_escape_case_directory(self):
        c = self.document['cases'][0]
        c.update(status='ready', authorization=dict(status='authorized', basis='original work'),
                 indesignVersion='recorded version', operations=dict(left=['edit'], right=['move']))
        c['artifacts'] = {'../outside.idml': '0' * 64, **{p: '0' * 64 for p in
            ('base.idml', 'left.idml', 'right.idml', 'expected.idml', 'provenance.json', 'application.json')}}
        with self.assertRaisesRegex(AssertionError, 'unsafe artifact path'):
            self.check_document(self.document)
