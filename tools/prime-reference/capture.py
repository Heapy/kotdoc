#!/usr/bin/env python3
"""Capture original-process and separately fixed-secret observed Prime results; never imports Kotlin."""
import ast,gzip,json,subprocess,sys
from pathlib import Path
D=Path(__file__).resolve().parent;R=D.parents[1]
exe=Path('/tmp/kotdoc-prime-haskell-build/build/aarch64-osx/ghc-9.12.2/kotdoc-prime-reference-0.1.0.0/x/prime-reference/opt/build/prime-reference/prime-reference')
requests=['table','compat 100']+[f'rounds {n}' for n in [-1,0,1,249,250,251,299,300,349,350,399,400,449,450,549,550,649,650,849,850,1299,1300,2147483647]]
requests += [f'{op} {n}' for op in ['naive','fixedProbably'] for n in range(-5,5002)]
requests += [f'coprime {a} {b}' for a in [-300,-3,-1,0,1,2,3,11,35,100,999] for b in [-300,-3,-1,0,1,2,3,11,35,100,999]]
requests += [f'fermat {t} {a} {n}' for t in [-3,-1,0,1,2,5] for a in [-3,0,1,2,3,15] for n in [-3,0,1,2,3,4,5,9,15,561,1105,2909]]
requests += [f'{op} {t} {n}' for op in ['mr','fixed'] for t in [-2,0,1,2,30] for n in [-3,0,1,2,3,4,5,6,9,15,561,1105,1729,2465,2821,6601,2047,3277,2909,2917,1000000007,1000000009,3215031751,2**512-569,(2**256-189)*(2**256-357)]]
requests += [f'fixedFind {n}' for n in list(range(-10,101))+[2897,2903,2904,2909,2911,10000,1000000,2**127-10]]
requests += [f'findWith {n} {m} {r}' for n in [-10,1,2,3,15,2897,2903,10000] for m,r in [(4,1),(4,3),(6,1),(6,5)]]
requests += [f'{op} {bits} {seed}' for op in ['generate','safe'] for bits in ([-1,0,1,4,5,6,7,8,9,15,16,17,31,32,33,63,64,65,127,128,129,200] if op=='safe' else [-1,0,1,4,5,6,7,8,9,15,16,17,31,32,33,63,64,65,127,128,129,249,250,300,350,400,450,550,650,850,1300]) for seed in [0,1,127,255]]
requests=list(dict.fromkeys(requests))
p=subprocess.run([str(exe)],input='\n'.join(requests+['secret'])+'\n',text=True,capture_output=True,check=True)
lines=p.stdout.splitlines();secret=ast.literal_eval(lines.pop().split('|')[0]);assert len(secret)==32;assert len(lines)==len(requests),(len(lines),len(requests),p.stderr)
rows=[]
for req,line in zip(requests,lines):
 value,trace=line.split('|',1);rows.append(dict(request=req,value=value,draws=None if req.startswith('mr ') else ast.literal_eval(trace),secret=secret if req.startswith('mr ') else list(range(32))))
out=R/'core/src/test/resources/crypto-number-prime';out.mkdir(parents=True,exist_ok=True)
with gzip.GzipFile(filename=str(out/'original.jsonl.gz'),mode='wb',mtime=0) as f:
 for row in rows:f.write((json.dumps(row,separators=(',',':'))+'\n').encode())
print('Captured',len(rows),'original/observed rows')
