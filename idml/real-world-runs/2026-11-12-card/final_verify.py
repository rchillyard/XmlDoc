"""Run with uv run --with pillow python final_verify.py."""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops
from verify import read, check_case

root = Path(__file__).parent
merges = json.loads((root / 'merge-results.json').read_text())
conflict_evidence = {
    '05': ['successor after'], '06': ['deleted vs updated'],
    '06b': ['layout', 'body edited'], '08': ['The swift', 'The slow red'],
    '08b': ['The swift', 'sleepy'], '10b': ['parent of'],
    '12': ['NextTextFrame'], '13': ['layout', 'body edited'],
}
results = []
for run in merges:
    folder = root / run['case']
    coverage = check_case(folder)
    result = dict(case=run['case'], coveragePassed=coverage['covered'], statusPassed=run['statusMatches'])
    base = read(folder / 'base.idml')
    result['identityInventory'] = {}
    for side in ['left', 'right']:
        branch = read(folder / (side + '.idml'))
        result['identityInventory'][side] = dict(
            retained=sorted(base['nodes'].keys() & branch['nodes'].keys()),
            removed=sorted(base['nodes'].keys() - branch['nodes'].keys()),
            added=sorted(branch['nodes'].keys() - base['nodes'].keys()),
        )
    if run['expectedExit'] == 1:
        result['oursUnchanged'] = (folder / 'left.idml').read_bytes() == (folder / 'merged.idml').read_bytes()
        log = (folder / 'merge.log').read_text()
        result['conflictEvidencePassed'] = all(s in log for s in conflict_evidence[run['case'].split('-')[0]])
        result['passed'] = all(result[k] for k in ['coveragePassed','statusPassed','oursUnchanged','conflictEvidencePassed'])
    else:
        lines = (folder / 'application.log').read_text().splitlines()
        result['applicationLifecyclePassed'] = all(s + ' PASS' in lines for s in ['OPEN','SAVE','CLOSE','REOPEN','RENDER'])
        result['noOverflowOrApplicationError'] = not any(s.startswith(('ERROR','overflow=')) for s in lines)
        result['pages'] = []
        for filename in ['merged-page2.png', 'merged-page22.png']:
            actual = Image.open(folder / filename).convert('RGB')
            expected = Image.open(folder / filename.replace('merged-', 'expected-')).convert('RGB')
            result['pages'].append(dict(file=filename, pixelEqual=actual.size == expected.size and ImageChops.difference(actual, expected).getbbox() is None))
        result['uiScreenshotPresent'] = (folder / 'ui.png').is_file()
        result['passed'] = all(result[k] for k in ['coveragePassed','statusPassed','applicationLifecyclePassed','noOverflowOrApplicationError','uiScreenshotPresent']) and all(p['pixelEqual'] for p in result['pages'])
    results.append(result)
report = dict(cases=len(results), passed=sum(r['passed'] for r in results), results=results)
(root / 'final-results.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({k: v for k, v in report.items() if k != 'results'}))
for result in results:
    print(result['case'], 'PASS' if result['passed'] else 'FAIL')
raise SystemExit(0 if report['cases'] == report['passed'] == 16 else 1)
