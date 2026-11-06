#!/usr/bin/env python3
"""Evidence-presence gate. It cannot certify the truth of a manual review."""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/test/resources/com/phasmidsoftware/xmldoc/idml/real-world-corpus'
NAMES = {'real-01-text-move': 'accept', 'real-02-ungroup-style': 'accept',
         'real-03-thread-delete-edit': 'conflict', 'real-04-thread-assets': 'accept'}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def verify(path):
    path = Path(path)
    document = json.loads(path.read_text())
    require(document['schemaVersion'] == 1 and document['profile'] == 'strict-text-node-v1', 'unsupported manifest contract')
    require(document['minimumCases'] == 4 and isinstance(document['releaseReady'], bool), 'invalid evidence contract')
    cases = document['cases']
    require(len(cases) == 4 and {c['name'] for c in cases} == set(NAMES), 'fixed four-case inventory required')
    errors = []
    for case in cases:
        name = case['name']
        require(case['expectedStatus'] == NAMES[name] and case['sourceType'] == 'real-indesign-export', 'invalid evidence contract')
        require(case['status'] in ('awaiting-input', 'ready'), 'unknown evidence status')
        if case['status'] != 'ready':
            errors.append(f'{name}: real exported inputs, authorization and InDesign record missing')
            continue
        require(case['authorization']['status'] == 'authorized' and case['authorization']['basis'], 'authorization basis required')
        require(case['indesignVersion'] and all(case['operations'][side] for side in ('left', 'right')), 'invalid evidence contract')
        root = path.parent / name
        required = {'base.idml', 'left.idml', 'right.idml', 'provenance.json', 'application.json',
                    'expected.idml' if case['expectedStatus'] == 'accept' else 'expected-conflicts.json'}
        require(required.issubset(case['artifacts']), 'inputs/oracle/provenance/application artifacts required')

        def artifact(relative, digest):
            rel = Path(relative)
            require(not rel.is_absolute() and '..' not in rel.parts and '.' not in rel.parts, 'unsafe artifact path')
            target = root / rel
            require(target.resolve().is_relative_to(root.resolve()) and not target.is_symlink(), 'escaped or symlink artifact')
            require(re.fullmatch('[0-9a-f]{64}', digest), 'invalid sha256')
            require(hashlib.sha256(target.read_bytes()).hexdigest() == digest, f'{name}/{relative}: hash mismatch')

        for relative, digest in case['artifacts'].items():
            artifact(relative, digest)
        provenance = json.loads((root / 'provenance.json').read_text())
        require(all(provenance.get(k) for k in ('author', 'source', 'authorization', 'baseClosed', 'leftSteps', 'rightSteps', 'identityMap', 'oracleMethod')), 'provenance incomplete')
        application = json.loads((root / 'application.json').read_text())
        require(application.get('version') == case['indesignVersion'] and application.get('reviewer'), 'invalid evidence contract')
        require(application.get('warningsReviewed') is True and isinstance(application.get('warnings'), list), 'invalid evidence contract')
        flags = ('openPassed', 'savePassed', 'closePassed', 'reopenPassed', 'visualPassed', 'threadPassed', 'assetsPassed') if case['expectedStatus'] == 'accept' else ('conflictReviewed', 'oursUnchanged')
        require(all(application.get(k) is True for k in flags), 'application review incomplete')
        evidence = application.get('artifacts', {})
        require(len(evidence) >= (2 if case['expectedStatus'] == 'accept' else 1), 'review artifacts missing')
        for relative, digest in evidence.items():
            require(case['artifacts'].get(relative) == digest, 'review artifact not inventoried')
            artifact(relative, digest)
        if case['expectedStatus'] == 'conflict':
            oracle = json.loads((root / 'expected-conflicts.json').read_text())
            require(isinstance(oracle, list) and oracle and all(set(c) == {'src', 'conflicts'} and c['conflicts'] for c in oracle), 'invalid evidence contract')
            require(all(set(c) == {'slot', 'baseValue', 'leftValue', 'rightValue'} for p in oracle for c in p['conflicts']), 'invalid evidence contract')
    require(not (document['releaseReady'] and errors), 'releaseReady contradicts missing application evidence')
    if not errors and not document['releaseReady']:
        errors.append('manifest releaseReady has not been affirmed after evidence review')
    return dict(contractValid=True, releaseReady=not errors, plannedCases=4,
                suppliedCases=sum(c['status'] == 'ready' for c in cases), blockers=errors)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('manifest', nargs='?', type=Path, default=ROOT / 'manifest.json')
    parser.add_argument('--allow-pending', action='store_true', help='planning contract only; does not approve release')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    try:
        report = verify(args.manifest)
        status = 0 if report['releaseReady'] or args.allow_pending else 1
    except (AssertionError, ValueError, KeyError, OSError, TypeError) as error:
        report = dict(contractValid=False, releaseReady=False, blockers=[str(error)])
        status = 2
    text = json.dumps(report, indent=2) + '\n'
    print(text, end='')
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text)
    return status


if __name__ == '__main__':
    raise SystemExit(main())
