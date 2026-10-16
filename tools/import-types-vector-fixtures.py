#!/usr/bin/env python3
"""Capture unchanged QuickCheck vector values and native thunk demand stages."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DEST=ROOT/'core/src/test/resources/pandoc-types';PROJECT='tools/haskell-types-vector-reference'
FILES=['tools/import-types-vector-fixtures.py',*[PROJECT+'/'+n for n in ['TypesVectorReference.hs','GHC-9.12.2-Control-Monad.iface.txt','kotdoc-types-vector-reference.cabal','cabal.project','cabal.project.freeze','README.md']],*[ 'vendor/quickcheck-2.16.0.0/'+n+'.hs' for n in ['Gen','Random']], 'vendor/splitmix-0.1.3.2/SplitMix.hs', 'core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalQuickCheck.kt','core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesVectorTest.kt','licenses/QuickCheck-BSD-3-Clause.txt']
def sha(b):return hashlib.sha256(b).hexdigest()
def hashes():return {f:sha((ROOT/f).read_bytes()) for f in FILES}
def validate(values,demand):
 rows=[json.loads(x) for x in values.splitlines()];d=[json.loads(x) for x in demand.splitlines()]
 assert len(rows)==180 and len(d)==10
 assert {(r['seed'],r['size'],r['count']) for r in rows}=={(s,n,c) for s in [-2**63,2**63-1,-1,0,1,611] for n in [0,3,30] for c in [-1,0,1,2,3,31,32,33,1024,100000]}
 for r in rows:
  assert r['length']==max(0,r['count']) and r['complete']==(r['count']<=1024)
  assert len(r['positions'])==len(r['expected'])
  assert len(r['all'])==(max(0,r['count']) if r['complete'] else 0)
 assert [(r['name'],r['value'],r['calls']) for r in d]==[
  ('length-before-elements',100000,0),('last-element',202,1),('same-last-element',202,1),('first-element',213,2),
  ('failure-spine-length',100000,0),('first-failure',None,1),('same-first-failure',None,1),
  ('nullable-length',100000,0),('nullable-head',7,1),('same-nullable-head',7,1)]
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');a=p.parse_args();m=DEST/'vector-manifest.json';names=['vector','vector-demand']
 if a.check_source:
  saved=json.loads(m.read_text());assert saved['sources']==hashes();raws=[gzip.decompress((DEST/(n+'.jsonl.gz')).read_bytes()) for n in names];assert saved['payloads']=={n:sha(r) for n,r in zip(names,raws)};validate(*raws);print('Original vector180values/10native demand stages: source/payload verified');return
 if not a.oracle:p.error('--oracle required')
 raws=[subprocess.check_output([a.oracle]),subprocess.check_output([a.oracle,'demand'])];validate(*raws)
 for n,r in zip(names,raws):(DEST/(n+'.jsonl.gz')).write_bytes(gzip.compress(r,mtime=0))
 m.write_text(json.dumps({'sources':hashes(),'payloads':{n:sha(r) for n,r in zip(names,raws)},'valueCases':180,'nativeDemandStages':10,'kotlinCases':182},indent=2)+'\n');print('Captured original vector180+10')
if __name__=='__main__':main()
