#!/usr/bin/env python3
"""Original generators and literal PrimeSpec partition; source-derived requests only."""
from pathlib import Path
import ast,gzip,json,subprocess,itertools
D=Path(__file__).resolve().parent;R=D.parents[1];base=Path('/tmp/kotdoc-prime-haskell-build/build/aarch64-osx/ghc-9.12.2/kotdoc-prime-reference-0.1.0.0/x')
exe=base/'prime-reference/opt/build/prime-reference/prime-reference'
def capture(name,requests,trace=True):
 result=subprocess.run([str(exe)],input='\n'.join(requests)+'\n',text=True,capture_output=True,check=True);lines=result.stdout.splitlines();assert len(lines)==len(requests)
 out=R/'core/src/test/resources/crypto-number-prime'/name
 with gzip.GzipFile(filename=str(out),mode='wb',mtime=0)as f:
  for req,line in zip(requests,lines):
   value,draws=line.split('|');f.write((json.dumps(dict(request=req,value=value,draws=ast.literal_eval(draws)if trace else None,secret=list(range(32))),separators=(',',':'))+'\n').encode())
 print('Captured',name,len(requests))
requests=subprocess.check_output([str(base/'number-samples/opt/build/number-samples/number-samples')],text=True).splitlines();assert len(requests)==300;assert '\n'.join(requests)+'\n'==(D/'original-property-requests.txt').read_text()
capture('original-properties.jsonl.gz',requests)
requests=[f'demandMR {t} {n}' for t in ['bottom','0','1']for n in ['bottom','-3','1','3','4','5']]+[f'demandFermat {t} {a} {p}'for t in ['bottom','-1','0']for a in ['bottom','2']for p in ['bottom','0','5']]
capture('demand.jsonl.gz',requests,False)
literals=json.loads((D/'OriginalPrimalityLiterals.json').read_text());ns=literals['carmichaels']+literals['strongPseudoprimesBase2']
requests=[f'fixed 30 {n}'for n in range(5,5002,2)]+[f'fixed 30 {n}'for n in ns]+[f'fixedProbably {n}'for n in ns]
capture('original-primality.jsonl.gz',requests)

requests=[f'probablyRounds {r} {n}'for r in [-3,0,1,2,30]for n in [-3,0,1,2,3,4,5,9,2903,2905,2909,2917,561,3215031751]]
requests += [f'findDrawn {r} {n}'for r in [-3,0,1,2,30]for n in [-3,0,1,2,3,4,5,2903,2904,2911]]
requests += [f'findRounds {r} {n} 4 {v}'for r in [0,1,30]for n in [0,2,3,2903,2904]for v in [1,3]]
requests += [f'divides {a} {b}'for a in [-7,-3,-1,0,1,2,3,7]for b in [-21,-7,-1,0,1,7,21]]
requests += [f'findTrace 30 {n} 4 {r}'for n in [-3,0,2,3,4,21,2903,10000]for r in [1,3]]
capture('private.jsonl.gz',requests)
