#!/usr/bin/env python3
"""Actual pinned Haskell linkage, host AArch64 field64/scalar API32."""
import argparse,gzip,json,subprocess,re
from pathlib import Path
D=Path(__file__).resolve().parent;ROOT=D.parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--check',action='store_true');args=p.parse_args()
ops=['deisogenize','encode','decode','sub','add','double','negate','equal','valid','torque','pscale','select','encodeEd','decodeEd','edPublicToX','encodeX','destroy']
with gzip.open(ROOT/'core/src/test/resources/crypto-decaf448-points/data.jsonl.gz','rt')as f:next(f);rows=[json.loads(x)for x in f]
def raw(words):return list(b''.join(int(x,16).to_bytes(8,'little')for x in words))
selected=[x for x in rows if x['request']['bits']==64 and x['request']['scalarBits']==32 and x['request'].get('vectorMasks32',False) and x['request']['op']in ops and x['request'].get('alias','none')=='none']
requests=[]
for row in selected:
 r=row['request'];requests.append(dict(op=r['op'],a=raw(r.get('a',['0']*32)),b=raw(r.get('b',['0']*32)),out=raw(r.get('out',['0']*32)),bytes=r.get('bytes',[0]*128),**{k:str(int(r.get(k,'0'),16))for k in ['mask','mask2','mask3']}))
result=subprocess.run([args.oracle],input=''.join(json.dumps(x)+'\n'for x in requests),text=True,capture_output=True,check=True)
outputs=[json.loads(x)for x in result.stdout.splitlines()];assert len(outputs)==len(selected)
for row,out in zip(selected,outputs):
 r=row['request'];e=row['expected'];assert out['out']==raw(e['out'][4:-4]),(r,out)
 assert out['bytes']==e['bytes'][1:-1],(r,out)
 assert int(out['value'])%(1<<64)==int(e['value'],16),(r,out)
artifact=dict(kind='actual pinned crypton Haskell linked C geometry API; AArch64 field64/scalarAPI32',requests=requests,observations=outputs)
path=D/'HaskellObservations.json'
if args.check:assert json.loads(path.read_text())==artifact
else:path.write_text(json.dumps(artifact,separators=(',',':'))+'\n')
print(f'Haskell linked geometry ABI PASS {len(outputs)}')
suite=subprocess.run([args.oracle,'--suite','--qc-max-success=100'],text=True,capture_output=True,check=True)
baseline=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite.stdout)
assert baseline==(D/'OriginalPublicSuites.txt').read_text()
print('Original unchanged Ed448/Curve448 suites PASS29 (native supporting baseline only)')
