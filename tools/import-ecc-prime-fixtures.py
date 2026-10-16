#!/usr/bin/env python3
"""Independent complete prime ECC C engine, tables, access and allocation observations."""
import argparse,collections,gzip,hashlib,importlib.util,itertools,json,random,subprocess,sys,tempfile
from pathlib import Path
sys.dont_write_bytecode=True
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-ecc-prime-reference';target=root/'core/src/test/resources/crypto-ecc-prime/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
spec=importlib.util.spec_from_file_location('extraction',root/'tools/extract-ecc-prime-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
assert (folder/'OriginalPrimeEcc.c').read_text()==ex.ecc()
assert (folder/'OriginalEccTests.hs').read_text()==ex.tests()
d=root/'vendor/crypton-2.0.1';pin=json.loads((d/'SOURCE.json').read_text());assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
assert set(pin['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for path,h in pin['files'].items():assert hashlib.sha256((d/path).read_bytes()).hexdigest()==h,path
notice=root/'licenses/crypton-BSD-3-Clause.txt';assert notice.read_bytes()==(d/'LICENSE').read_bytes()
sources=[Path(__file__).resolve(),root/'tools/extract-ecc-prime-reference.py',notice]+[f for f in folder.rglob('*') if f.is_file()]+[f for f in d.rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(l) for l in f]
 assert manifest['sources']==hashes()
 assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases) and manifest['groups']==groups(cases)
 if a.check_source:print('Verified',len(cases),'prime ECC engine observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 def query(arg):return [json.loads(l) for l in subprocess.run([a.oracle,arg],check=True,capture_output=True,text=True,timeout=120).stdout.splitlines()]
 curves=query('--curves');assert len(curves)==15;byname={c['name']:c for c in curves};weights=query('--weights');assert len(weights)==2
 cases=[];rng=random.Random(0xECC2026)
 def add(group,request):cases.append(dict(group=group,request=request))
 def raw(c,k,op='mul',other=False,pad=0,width=None,**kw):
  p=int(c['p']);length=(p.bit_length()+7)//8+pad;n=int(c['n']);width=max((n.bit_length()+7)//8,(k.bit_length()+7)//8) if width is None else width
  req=dict(op=op,curve=c['name'],p=list(p.to_bytes(length,'big')),a=list(int(c['a']).to_bytes(length,'big')),b=list(int(c['b']).to_bytes(length,'big')),x=list(int(c['ox'] if other else c['gx']).to_bytes(length,'big')),y=list(int(c['oy'] if other else c['gy']).to_bytes(length,'big')),k=list(k.to_bytes(width,'big')))
  req.update(kw);return req
 for c in curves:
  n=int(c['n'])
  for k in [0,1,2,3,7,15,16,17,n-1,n,n+1,2*n+3,n*n,n*n*n]:
   for op in ['mul','table']:add('all-fifteen-prime-curves-base-edges-wide-scalars-and-exact-tables',raw(c,k,op,mathCheck=True))
   add('all-prime-curves-nonbase-points',raw(c,k,other=True,mathCheck=True))
 for w in weights:
  c=byname[w['name']]
  for s in w['scalars']:
   add('original-weight-nonbase-double-and-add-expectations',raw(c,int(s),other=True,mathCheck=True))
  for x,y in w['pairs']:
   for k in [int(x),int(y),int(x)+int(y)]:
    for op in ['mul','table']:add('original-weight-pair-operands-and-sums',raw(c,k,op,mathCheck=True))
 selected=[byname[n] for n in ['SEC_p112r2','SEC_p256k1','SEC_p384r1']]
 for c,k,op in itertools.product(selected,range(256),['mul','table']):add('all-byte-scalars-complete-formula-and-table-address-schedule',raw(c,k,op,width=1,mathCheck=True))
 for c,pad,k,op in itertools.product(selected,[1,7,8,9],[0,1,17,int(selected[0]['n'])+1],['mul','table']):add('field-leading-zero-bytes-changing-limb-width',raw(c,k,op,pad=pad,mathCheck=True))
 for c,k,op,alias in itertools.product(selected,[0,1,17],['mul','table'],['point','same']):add('unaligned-byte-spans-and-aliased-output-buffers',raw(c,k,op,alias=alias,mathCheck=alias=='point'))
 for c,k,fail in itertools.product(selected,[0,1,17],range(1,5)):
  add('each-allocation-failure-and-unreached-infinity-inversion',raw(c,k,fail=str(fail)))
  add('each-table-build-allocation-failure',raw(c,k,'table',buildFail=str(fail)))
  add('each-table-multiplication-allocation-failure',raw(c,k,'table',fail=str(fail)))
 for c in selected:
  req=raw(c,17,'table');size=(len(req['k'])*2*16*3*((len(req['p'])+7)//8)*8)
  for mutations in [[[0,1]],[[size//2,255]],[[size-1,128]],[[0,1],[size-1,1]]]:add('borrowed-precomputed-table-mutations-and-read-only-multiplication',raw(c,17,'table',mutations=mutations))
  add('borrowed-zero-projective-table-infinity',raw(c,17,'table',tableOverride='zero'))
 for p,k,op in itertools.product([0,1,2,3,4,5,7,9,11,15,17],range(4),['mul','table']):
  size=1 if p else 0
  req=dict(op=op,p=[p] if p else [],a=[0]*size,b=[1]*size,x=[1]*size,y=[2]*size,k=[] if k==0 else [k])
  add('bounded-unchecked-buffers-and-unsupported-field-widths',req)
 sizes=[0,1,7,8,9,16,66,65536,2**29,2**30,2**32-9,2**32-8,2**32-7,2**32-1]
 for plen,klen in itertools.product(sizes,sizes):add('all-uint32-table-size-guard-and-wrap-boundaries',dict(op='size',plen=str(plen),klen=str(klen)))
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=600);out.seek(0);results=[json.loads(l) for l in out]
assert len(results)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,results)):assert c['expected']==o,(i,c,o)
 print('Original prime ECC C verified all',len(cases),'observations');raise SystemExit
for c,o in zip(cases,results):
 assert 'fatal' not in o,c
 c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases),primeCurves=[c['name'] for c in curves])
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'prime ECC observations; SHA256',manifest['caseHash'])
