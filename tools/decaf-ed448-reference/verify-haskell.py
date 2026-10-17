#!/usr/bin/env python3
"""Actual pinned crypton AArch64 ABI and unchanged original public suite."""
import argparse,gzip,json,re,subprocess
from pathlib import Path
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--corpus',type=Path,required=True);p.add_argument('--check',action='store_true');a=p.parse_args()
with gzip.open(a.corpus,'rt')as f:next(f);rows=[json.loads(line)for line in f]
rows=[r for r in rows if r['request']['vectorMasks32']and r['request']['op']not in ['clamp','hashInitWithDom','signInternal']]
run=subprocess.run([a.oracle],input=''.join(json.dumps(r['request'])+'\n'for r in rows),text=True,capture_output=True,check=True);observations=[json.loads(v)for v in run.stdout.splitlines()];assert len(rows)==len(observations)
for index,(row,out)in enumerate(zip(rows,observations)):assert row['expected']==out,(index,row['request'],row['expected'],out)
artifact=dict(kind='actual original Haskell-linked AArch64 field64/scalar32/NEON raw Ed448 exports plus two header aliases',requests=[r['request']for r in rows],observations=observations);path=D/'HaskellObservations.json'
if a.check:assert json.loads(path.read_text())==artifact
else:path.write_text(json.dumps(artifact,separators=(',',':'))+'\n')
suite=subprocess.run([a.oracle,'--suite','--qc-max-success','100'],text=True,capture_output=True,check=True);assert '25 examples, 0 failures'in suite.stdout,suite.stdout;baseline=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite.stdout);path=D/'OriginalSuite.txt'
if a.check:assert path.read_text()==baseline
else:path.write_text(baseline)
print('PASS actual Haskell ABI',len(rows),'and original Ed448Spec25 (supporting public baseline; Kotlin public gate pending)')
