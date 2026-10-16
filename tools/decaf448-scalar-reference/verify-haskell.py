#!/usr/bin/env python3
"""Replay public scalar operations through actual pinned crypton Haskell FFI linkage."""
import argparse,gzip,json,subprocess,re
from pathlib import Path
D=Path(__file__).resolve().parent;ROOT=D.parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--check',action='store_true');args=p.parse_args()
ops=['multiply','invert','sub','add','setUnsigned','equal','decode','destroy','decodeLong','encode','condSelect','halve']
with gzip.open(ROOT/'core/src/test/resources/crypto-decaf448-scalar/data.jsonl.gz','rt')as f:next(f);rows=[json.loads(x)for x in f]
def raw(words,bits):return list(b''.join(int(x,16).to_bytes(bits//8,'little')for x in words))
selected=[x for x in rows if x['request']['bits']==32 and x['request']['op']in ops and x['request'].get('alias','none')=='none']
requests=[]
for row in selected:
 r=row['request'];requests.append(dict(op=r['op'],a=raw(r.get('a',['0']*14),32),b=raw(r.get('b',['0']*14),32),out=raw(r.get('out',['0']*14),32),word=str(int(r.get('word','0'),16)),bytes=r.get('bytes',[])[:r.get('length',0)]))
result=subprocess.run([args.oracle],input=''.join(json.dumps(x)+'\n'for x in requests),text=True,capture_output=True,check=True)
outputs=[json.loads(x)for x in result.stdout.splitlines()];assert len(outputs)==len(selected)
for row,out in zip(selected,outputs):
 r=row['request'];e=row['expected'];expected=raw(e['out'][1:-2],32)if r['op']!='encode'else e['bytes'][8:64]
 assert out['out']==expected,(r,out,expected)
 assert int(out['value'])%(1<<64)==int(e['value'],16),(r,out['value'],e['value'])
artifact=dict(kind='actual pinned crypton Haskell-linked C API host AArch64 API32 ARCH64; unchanged inversion C has UB',requests=requests,observations=outputs)
path=D/'HaskellObservations.json'
if args.check:assert json.loads(path.read_text())==artifact
else:path.write_text(json.dumps(artifact,separators=(',',':'))+'\n')
print(f'Haskell actual linked scalar ABI PASS {len(outputs)}')

suite=subprocess.run([args.oracle,'--suite','--qc-max-success=100'],text=True,capture_output=True,check=True)
baseline=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite.stdout)
assert baseline==(D/'OriginalPublicSuites.txt').read_text()
print('Original unchanged Ed448/Curve448 suites PASS 29 examples')
