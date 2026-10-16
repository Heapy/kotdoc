#!/usr/bin/env python3
"""Compare actual pinned Haskell-linked AArch64 C ABI; not a production dependency."""
import argparse,gzip,json,subprocess,re
from pathlib import Path
D=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--corpus',required=True,type=Path);p.add_argument('--check',action='store_true');a=p.parse_args()
ops=['scalarmul','doubleScalarmul','dualScalarmul','precompute','precomputedScalarmul','directScalarmul','x448','generateKey','derivePublicKey','precomputeWnafs','baseDoubleScalarmulNonSecret','precomputedDestroy']
with gzip.open(a.corpus,'rt')as f:next(f);rows=[json.loads(line)for line in f]
rows=[row for row in rows if row['request']['vectorMasks32']and row['request']['op']in ops and row['request']['alias']=='none']
def raw(values,width):return list(b''.join(int(v,16).to_bytes(width,'little')for v in values))
requests=[]
for row in rows:
 r=row['request'];v=dict(op=r['op'])
 for k,count,width in [('a',32,8),('b',32,8),('out',32,8),('out2',32,8),('s1',14,4),('s2',14,4),('table',1920,8)]:v[k]=raw(r.get(k,['0000000051515151']*count),width)
 for k in ['bytes','baseBytes','scalarBytes']:v[k]=r.get(k,[165]*56)
 for k in ['allow','shortCircuit']:v[k]=str(int(r.get(k,'0'),16)&0xffffffff)
 requests.append(v)
process=subprocess.run([a.oracle],input=''.join(json.dumps(v)+'\n'for v in requests),text=True,capture_output=True,check=True);outputs=[json.loads(line)for line in process.stdout.splitlines()];assert len(outputs)==len(rows)
for row,out in zip(rows,outputs):
 e=row['expected'];r=row['request']
 for k in ['out','out2']:assert out[k]==raw(e[k][4:-4],8),(r,k)
 if 'table'in e:assert out['table']==raw(e['table'][4:-4],8),(r,'table')
 assert out['bytes']==e['bytes'][1:-1],r
 assert int(out['value'])%(1<<64)==int(e['value'],16),r
artifact=dict(kind='actual pinned Haskell crypton AArch64 NEON field64/API32 FFI exports',requests=requests,observations=outputs)
path=D/'HaskellObservations.json'
if a.check:assert json.loads(path.read_text())==artifact
else:path.write_text(json.dumps(artifact,separators=(',',':'))+'\n')
print('PASS Haskell linked ABI',len(outputs))
suite=subprocess.run([a.oracle,'--suite','--qc-max-success=100'],text=True,capture_output=True,check=True);baseline=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite.stdout)
assert baseline==(D.parent/'decaf448-points-reference/OriginalPublicSuites.txt').read_text();(D/'OriginalPublicSuites.txt').write_text(baseline)
print('PASS original unchanged Ed448/Curve448 supporting suite29; public/signature Kotlin port remains pending')
