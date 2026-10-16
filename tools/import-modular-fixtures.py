#!/usr/bin/env python3
"""Pinned crypton public modular arithmetic and fixed-width C observations."""
import argparse, collections, gzip, hashlib, importlib.util, itertools, json, random, subprocess, sys, tempfile
from pathlib import Path
sys.dont_write_bytecode=True
root=Path(__file__).resolve().parents[1]; folder=root/'tools/haskell-modular-reference'
target=root/'core/src/test/resources/crypto-modular/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
spec=importlib.util.spec_from_file_location('extraction',root/'tools/extract-modular-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
assert (folder/'OriginalPowm.c').read_text()==ex.powm()
assert (folder/'OriginalNumberTests.hs').read_text()==ex.number_tests()
vendor=root/'vendor/crypton-2.0.1';pin=json.loads((vendor/'SOURCE.json').read_text())
assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
assert set(pin['files'])=={str(f.relative_to(vendor)) for f in vendor.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for f,h in pin['files'].items():assert hashlib.sha256((vendor/f).read_bytes()).hexdigest()==h,f
notice=root/'licenses/crypton-BSD-3-Clause.txt';assert notice.read_bytes()==(vendor/'LICENSE').read_bytes()
sources=[Path(__file__).resolve(),root/'tools/extract-modular-reference.py',notice]+[f for f in folder.rglob('*') if f.is_file()]+[f for f in vendor.rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(l) for l in f]
 assert manifest['sources']==hashes()
 assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases) and manifest['groups']==groups(cases)
 assert len(manifest['properties'])==9
 if a.check_source:print('Verified',len(cases),'modular/64-bit limb observations, all 9 arithmetic examples and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 cases=[];rng=random.Random(0xC0DEC2026);mask=(1<<64)-1
 def add(group,op,**kw):cases.append(dict(group=group,request=dict(op=op,**kw)))
 def num(group,op,**kw):add(group,op,**{k:str(v) for k,v in kw.items()})
 sample=subprocess.run([a.oracle,'--samples'],check=True,capture_output=True,text=True,timeout=300)
 observed=set()
 for line in sample.stdout.splitlines():
  request=json.loads(line)
  add('original-NumberSpec-vectors-and-QuickCheck-generator-replays',**request)
  operations=['expSafe','expFast'] if request['kind'].startswith('exponentiation/') else ['inverseSafe','inverse'] if request['kind'].startswith('inverse/') else ['squareRoot','jacobi']
  for op in operations:
   concrete={k:request[k] for k in ['g','e','m']}
   key=(op,tuple(concrete.items()))
   if key not in observed:
    observed.add(key);add('individual-results-for-original-corners-and-generated-large-values',op,**concrete)
 for n,kind,i in itertools.product([1,2,3,4,7,8,9,16,17,32,33],range(11),range(16)):
  choices=[0,1,mask,1<<63,(1<<63)-1,0xaaaaaaaaaaaaaaaa,0x5555555555555555]
  aa=[rng.getrandbits(64) if i>=7 else choices[i] for _ in range(2*n)]
  bb=[rng.getrandbits(64) if i>=7 else choices[(i+2)%7] for _ in range(2*n)]
  mm=[rng.getrandbits(64) for _ in range(n)];mm[0]|=1
  if i==0:mm=[1]+[0]*(n-1)
  if i==1:mm=[mask]*n
  aliases=['none','a','b'] if kind in [0,1,3,8,9] and i<7 else ['none']
  for alias in aliases:add('C-limb-carry-borrow-masks-Montgomery-and-aliasing','limb',n=str(n),kind=str(kind),a=list(map(str,aa)),b=list(map(str,bb)),m=list(map(str,mm)),scalar=str(choices[i%7] if i<7 else rng.getrandbits(64)),alias=alias)
 for n in [0,1,2,3,8,9,17,33]:
  for length in sorted(set([0,1,7,8,9,max(0,8*n-1),8*n,8*n+1,8*n+7])):
   raw=[rng.randrange(256) for _ in range(length)]
   for pad in [False,True]:
    bs=([0]*max(0,length-8*n)+raw[max(0,length-8*n):]) if pad else raw
    add('C-big-endian-width-leading-zeros-overflow-and-partial-write','fromBE',n=str(n),bytes=bs)
   add('C-big-endian-export-padding-and-truncation','toBE',n=str(n),length=str(length),a=[str(rng.getrandbits(64)) for _ in range(n)])
 for value,mod in itertools.product(range(256),[3,65537,(1<<64)-59]):
  mb=mod.to_bytes((mod.bit_length()+7)//8,'big')
  add('all-byte-exponents-same-window-schedule-and-table-address-stream','powRaw',base=[2],exponent=[value],modulus=list(mb))
 for mod in [[],[0],[2],[3],[1],[0,3]]:
  add('C-allocation-failure-status-and-untouched-output','powRaw',base=[2],exponent=[1],modulus=mod,allocationFailure='1')
 for i in range(256):
  size=rng.choice([0,1,7,8,9,15,16,17,31,32,33,64,129,257])
  mod=[rng.randrange(256) for _ in range(size)]
  if mod and i%4:mod[-1]|=1
  base=[rng.randrange(256) for _ in range(rng.choice([0,size,max(0,size-1),((size+7)//8)*8+1]))]
  if i%3==0:base=[0]*max(0,len(base)-size)+base[-size:] if size else base
  expo=[rng.randrange(256) for _ in range(rng.choice([0,1,7,8,9,16,32]))]
  add('C-raw-exponentiation-odd-even-leading-zero-width-and-base-overflow','powRaw',base=base,exponent=expo,modulus=mod)
 gs=[-17,-3,-1,0,1,2,3,4,7,16,19];ms=[-17,-7,-1,0,1,2,3,4,5,7,8,11,16,17,19,23,25,29,31,41,73]
 for op,g,e,m in itertools.product(['expSafe','expFast'],gs,[-3,-1,0,1,2,3,15,16,64],ms):num('GMP-fast-and-secure-dispatch-signed-zero-one-and-nonunit-boundaries',op,g=g,e=e,m=m)
 for op,g,m in itertools.product(['inverse','inverseSafe','inverseCoprimes','inverseFermat','jacobi'],gs,ms):num('inverse-Fermat-fallback-nonunits-and-Jacobi-signs',op,g=g,m=m)
 roots=[-2,-1,0,1,2,3,4,5,6,7,8,10,11,13,17,19,23,29,31,41,73,97,113,193,257,65537,(1<<512)-569]
 for g,m,part in itertools.product(list(range(-20,21))+[1<<1024],roots,['constructor','value']):num('all-square-root-residue-classes-typed-errors-and-lazy-results','squareRoot',g=g,m=m,part=part)
 for m,g,part in itertools.product([9,25,33,49,65],range(20),['constructor','value']):
  if part=='value' and g%m and pow(g%m,(m-1)//2,m)==1:continue # Original Tonelli search can fail to terminate on composites.
  num('composite-Tonelli-validation-versus-deferred-search','squareRoot',g=g,m=m,part=part)
 for op,g,e,m,part in itertools.product(['expSafe','expFast','inverse','inverseSafe','inverseCoprimes','inverseFermat','jacobi','squareRoot'],
                                      ['bottom:g',0,1,3],['bottom:e',-1,0,1],['bottom:m',-1,0,1,2,7,17],['ignore','constructor','value']):
  num('strict-worker-entry-guards-ignored-arguments-and-Maybe-demand',op,g=g,e=e,m=m,part=part)
 properties=sorted({c['request']['kind'] for c in cases if c['request']['op']=='numberProperty'});assert len(properties)==9
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=600);out.seek(0);results=[json.loads(l) for l in out]
assert len(results)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,results)):assert c['expected']==o,(i,c,o)
 print('Original crypton C/GMP verified all',len(cases),'modular observations');raise SystemExit
for c,o in zip(cases,results):
 c['expected']=o
 if c['request']['op']=='numberProperty':assert o in [{'property':True},{'discard':True}],c
manifest=dict(cases=len(cases),sources=hashes(),caseHash=digest(cases),groups=groups(cases),properties=properties)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'modular observations; SHA256',manifest['caseHash'])
