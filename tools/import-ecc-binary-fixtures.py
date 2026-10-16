#!/usr/bin/env python3
"""Independent pinned binary-field C engine/helpers and affine Haskell observations."""
import argparse,collections,gzip,hashlib,importlib.util,itertools,json,random,subprocess,sys
from pathlib import Path
sys.dont_write_bytecode=True
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-ecc-binary-reference';target=root/'core/src/test/resources/crypto-ecc-binary/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--library');p.add_argument('--math-oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
def module(name,path):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
ex=module('extraction',root/'tools/extract-ecc-binary-reference.py')
assert (folder/'OriginalBinaryEcc.c').read_text()==ex.source()
assert (folder/'OriginalBinaryTests.hs').read_text()==ex.tests()
d=root/'vendor/crypton-2.0.1';pin=json.loads((d/'SOURCE.json').read_text());assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
assert set(pin['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for path,h in pin['files'].items():assert hashlib.sha256((d/path).read_bytes()).hexdigest()==h,path
notice=root/'licenses/crypton-BSD-3-Clause.txt';assert notice.read_bytes()==(d/'LICENSE').read_bytes()
sources=[Path(__file__).resolve(),root/'tools/extract-ecc-binary-reference.py',notice]+[f for f in folder.rglob('*') if f.is_file()]+[f for f in d.rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if args.check_source or args.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(l) for l in f]
 assert manifest['sources']==hashes()
 assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases) and manifest['groups']==groups(cases)
 if args.check_source:print('Verified',len(cases),'binary ECC observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert args.math_oracle and args.library
 def query(arg):return [json.loads(l) for l in subprocess.run([args.math_oracle,arg],check=True,capture_output=True,text=True,timeout=120).stdout.splitlines()]
 curves=query('--curves');assert len(curves)==18;byname={c['name']:c for c in curves};edges=query('--edges');assert len(edges)==4
 cases=[];rng=random.Random(0xF2ECC2026)
 def add(group,request):cases.append(dict(group=group,request=request))
 def raw(c,k,other=False,pad=0,width=None,**kw):
  fx=int(c['fx']);length=(fx.bit_length()-1+7)//8+pad;n=int(c['n']);width=max((n.bit_length()+7)//8,(k.bit_length()+7)//8) if width is None else width
  req=dict(op='mul',curve=c['name'],fx=list(fx.to_bytes((fx.bit_length()+7)//8,'big')),b=list(int(c['b']).to_bytes(length,'big')),x=list(int(c['ox'] if other else c['gx']).to_bytes(length,'big')),y=list(int(c['oy'] if other else c['gy']).to_bytes(length,'big')),k=list(k.to_bytes(width,'big')))
  req.update(kw);return req
 for c in curves:
  n=int(c['n'])
  for k,other in itertools.product([0,1,2,3,7,15,16,17,n-1,n,n+1,2*n+3,n*n,n*n*n],[False,True]):add('all-eighteen-binary-curves-base-nonbase-wide-scalars',raw(c,k,other,mathCheck=True))
  for k in [0,1,2,3,2*n]:
   r=raw(c,k);r['x']=[0]*len(r['x']);r['y']=list(int(c['twoY']).to_bytes(len(r['y']),'big'));add('all-binary-order-two-points-native-unsupported',r)
 for e in edges:
  for k in e['scalars']:add('original-binary-edge-scalar-expressions',raw(byname[e['name']],int(k),mathCheck=True))
 selected=[byname[e['name']] for e in edges]
 for c,k in itertools.product(selected,range(256)):add('all-byte-scalars-exact-ladder-and-cswap-address-schedule',raw(c,k,width=1,mathCheck=True))
 for c,pad,k in itertools.product(selected,[1,7,8,9],[0,1,17,255]):add('leading-zero-field-widths-and-unaligned-byte-spans',raw(c,k,pad=pad,mathCheck=True))
 for c,k,alias in itertools.product(selected,[0,1,17,int(selected[0]['n'])-1],['point','same']):add('output-aliases-and-guard-preservation',raw(c,k,alias=alias,mathCheck=alias=='point'))
 for c,k,fail in itertools.product(selected,[0,1,17],range(1,4)):add('each-allocation-failure-free-order-and-secret-workspace-wipe',raw(c,k,fail=str(fail)))
 polys=[0,1,2,3,4,5,7,9,11,15,17,255,511,1023,1<<64,(1<<64)|3,(1<<65)|7,(1<<128)|3]
 for fx,flen,k in itertools.product(polys,[0,1,7,8,9,16],[None,0,1,17]):
  for x in ([0,1,(1<<(flen*8))-1] if flen else [0]):
   add('bounded-unchecked-polynomials-width-guards-and-coordinate-overflow',dict(op='mul',fx=list(fx.to_bytes((fx.bit_length()+7)//8,'big')),b=([1]+[0]*(flen-1) if flen else []),x=list(x.to_bytes(flen,'big')),y=[2]*flen,k=[] if k is None else [k]))
 def helper(kind,n=1,m=4,terms=(0,1),av=None,bv=None,tv=None,arg=0):
  def words(v):return list(map(str,v if v is not None else [rng.getrandbits(64) for _ in range(2*n)]))
  return dict(op='helper',kind=kind,n=n,m=m,terms=list(terms),a=words(av),b=words(bv),t=words(tv),out=words(None),arg=str(arg))
 for bits,kind in [(32,0),(64,1)]:
  for i,j in itertools.product(range(bits),repeat=2):add('every-single-bit-pair-carryless-'+str(bits),helper(kind,av=[1<<i,0],bv=[1<<j,0]))
 corners=[0,1,2,3,0xffffffff,1<<32,1<<63,(1<<64)-1,0xaaaaaaaaaaaaaaaa,0x5555555555555555]
 for kind,x,y in itertools.product([0,1],corners,corners):add('dense-lane-boundaries-and-overflow-free-carryless-products',helper(kind,av=[x,0],bv=[y,0]))
 for value in range(65536):add('all-sixteen-bit-inputs-of-bit-spreading',helper(2,arg=value))
 for value in corners+[1<<i for i in range(64)]:add('full-word-bit-spreading-boundaries',helper(2,arg=value))
 for n in list(range(1,11))+[17,33]:
  for shape in range(5):
   vals=[[0]*(2*n),[(1<<64)-1]*(2*n),[0xaaaaaaaaaaaaaaaa]*(2*n),[1<<(i%64) for i in range(2*n)],[rng.getrandbits(64) for _ in range(2*n)]]
   for kind in [3,4,5,9,10]:add('generic-and-host-dispatched-polynomial-and-xor-primitives',helper(kind,n,av=vals[shape],bv=vals[(shape+1)%5]))
   for bit in [0,1,2,(1<<64)-1]:add('masked-exchange-full-word-values-and-exact-buffer-schedule',helper(12,n,av=vals[shape],bv=vals[(shape+1)%5],arg=bit))
 for m in [1,2,3,4,7,8,31,32,63,64,65,113,127,128,129,163,193,233,239,283,409,571,1024,2048]:
  n=(m+64)//64
  termsets={tuple(sorted({0})),tuple(sorted({0,min(1,m-1)})),tuple(sorted({0,m-1})),tuple(sorted({0,m//2,m-1}))}
  for terms in sorted(termsets):
   for shape in range(3):
    av=[0]*(2*n) if shape==0 else [(1<<64)-1]*(2*n) if shape==1 else [rng.getrandbits(64) for _ in range(2*n)]
    for kind in [6,7,8]+([11] if m<=571 else []):add('destructive-reduction-field-operations-and-fixed-fermat-inversion',helper(kind,n,m,terms,av=av))
 for n,kind,alias in itertools.product([1,2,3,5,9,17],[6,7,8,9,11],[1,2]):
  req=helper(kind,n,m=64*n-1,terms=(0,1));req['alias']=alias;add('supported-field-output-aliases-and-destructive-scratch',req)
assert args.library and args.math_oracle
native=module('native_reference',folder/'binary_reference.py').Reference(args.library)
results=[native.observe(c['request']) for c in cases]
math_indices=[i for i,c in enumerate(cases) if c['request'].get('mathCheck')]
requests=''.join(json.dumps(cases[i]['request'],separators=(',',':'))+'\n' for i in math_indices)
run=subprocess.run([args.math_oracle],input=requests,capture_output=True,text=True,check=True,timeout=600)
mathematical=[json.loads(l) for l in run.stdout.splitlines()];assert len(mathematical)==len(math_indices)
for i,point in zip(math_indices,mathematical):
 result=results[i];req=cases[i]['request'];size=len(req['b'])
 actual=['infinity'] if result['ret']==1 else [str(int.from_bytes(bytes(result[k][3:3+size]),'big')) for k in ['x','y']]
 assert result['ret'] in [0,1] and actual==point,(i,req,result,point)
 results[i]=dict(native=result,math=point)
if args.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,results)):assert c['expected']==o,(i,c,o)
 print('Original binary ECC C and affine Haskell verified all',len(cases),'observations');raise SystemExit
for c,o in zip(cases,results):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases),binaryCurves=[c['name'] for c in curves])
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'binary ECC observations; SHA256',manifest['caseHash'])
