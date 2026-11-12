import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1])
classpath = ':'.join(re.findall(r'Attributed\((.+)\)', Path(sys.argv[2]).read_text()))
accepted = {'01','02','03','04','07','09','10','11'}
results=[]
for folder in sorted(root.iterdir()):
    if not folder.is_dir() or not folder.name[:2].isdigit():
        continue
    output=folder/'merged.idml'
    shutil.copyfile(folder/'left.idml',output)
    before=hashlib.sha256(output.read_bytes()).hexdigest()
    command=['java','-cp',classpath,'com.phasmidsoftware.xmldoc.idml.IdmlMergeDriver',str(folder/'base.idml'),str(output),str(folder/'right.idml')]
    try:
        run=subprocess.run(command,capture_output=True,text=True,timeout=120)
        code=run.returncode
        (folder/'merge.log').write_text(run.stdout+run.stderr)
    except subprocess.TimeoutExpired as e:
        code=124
        (folder/'merge.log').write_text('Merge timed out after 120 seconds\n'+str(e.stderr))
    expected=0 if folder.name.split('-')[0] in accepted else 1
    result=dict(case=folder.name,expectedExit=expected,actualExit=code,statusMatches=expected==code,oursUnchanged=before==hashlib.sha256(output.read_bytes()).hexdigest())
    results.append(result)
    print(result,flush=True)
    (root/'merge-results.json').write_text(json.dumps(results,indent=2)+'\n')
