#!/usr/bin/env python3
"""Pinned basic-number and BE/LE public/raw integer serialization observations."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess,sys,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-number-serial-reference';target=root/'core/src/test/resources/crypto-number-serial/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
d=root/'vendor/crypton-2.0.1';pin=json.loads((d/'SOURCE.json').read_text());assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
assert set(pin['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for path,h in pin['files'].items():assert hashlib.sha256((d/path).read_bytes()).hexdigest()==h,path
original=(d/'tests/NumberSpec.hs').read_text();assert (folder/'OriginalNumberSerialTests.hs').read_text()==original.replace('module NumberSpec (spec) where','module OriginalNumberSerialTests where')
notice=root/'licenses/crypton-BSD-3-Clause.txt';assert notice.read_bytes()==(d/'LICENSE').read_bytes()
bignum=root/'vendor/ghc-bignum-1.3-encode';gpin=json.loads((bignum/'SOURCE.json').read_text());assert gpin['ref']=='ghc-9.12.2-release' and gpin['version']=='1.3'
for path,h in gpin['files'].items():assert hashlib.sha256((bignum/path).read_bytes()).hexdigest()==h,path
sources=[Path(__file__).resolve(),notice,bignum/'SOURCE.json']+[bignum/p for p in gpin['files']]+[f for f in folder.rglob('*') if f.is_file()]+[f for f in d.rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if args.check_source or args.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(l) for l in f]
 assert manifest['sources']==hashes()
 assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases) and manifest['groups']==groups(cases)
 if args.check_source:print('Verified',len(cases),'basic-number/serialization observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert args.oracle,'--oracle required'
 cases=[];rng=random.Random(0xBEC0DE2026)
 def add(group,op,**kw):cases.append(dict(group=group,request=dict(op=op,**{k:str(v) if isinstance(v,int) else v for k,v in kw.items()})))
 basics=['numBits','numBytes','log2','sqrti','asPowerOf2AndOdd']
 for n,op in itertools.product(range(-1024,1025),basics):add('dense-signed-basic-values-and-exact-sqrt-bounds',op,a=n)
 for bits in list(range(1,130))+[255,256,257,511,512,513,1023,1024,1025,2048,4096]:
  for delta,sign,op in itertools.product([-1,0,1],[-1,1],basics):add('bit-byte-limb-and-wide-magnitude-boundaries',op,a=sign*((1<<bits)+delta))
 for a,b in itertools.product(range(-32,33),repeat=2):add('dense-signed-exact-gmp-bezout-coefficients','gcde',a=a,b=b)
 for i in range(256):
  bits=[1,8,32,64,65,128,256,512,1024,2048,4096][i%11];a=rng.getrandbits(bits);b=rng.getrandbits(bits)
  for sa,sb in itertools.product([-1,1],repeat=2):add('large-signed-gmp-bezout-coefficient-selection','gcde',a=sa*a,b=sb*b)
 for n in list(range(2049))+[10**i for i in range(1,81)]:
  for delta in [-1,0,1]:add('perfect-square-neighbours-and-original-bound-selection','sqrti',a=n*n+delta)
 for a,part in itertools.product([-257,-1,0,1,257],['constructor','first','second','third']):add('legacy-gcde-zero-divisor-component-demand','gcde',a=a,b=0,part=part)
 for a,b,part in itertools.product(['bottom:a','-1','0','1'],['bottom:b','-1','0','1'],['ignore','constructor','first','second','third','value']):add('lazy-gcde-entry-and-error-precedence','gcde',a=a,b=b,part=part)
 for op,part in itertools.product(basics,['ignore','constructor','first','second','value']):add('basic-operation-bottom-and-tuple-demand',op,a='bottom:a',part=part)
 lists=[[],['bottom:a'],['0'],['1'],['2','3','bottom:a'],['2','bottom:a','3'],['bottom:a','1'],['-2','4','6'],['-2','-3','bottom:a']]
 for values,tail,part in itertools.product(lists,['','bottom'],['ignore','value']):add('are-even-list-element-and-spine-short-circuit','areEven',values=values,tail=tail,part=part)
 numbers=list(range(513))+[2**bits+delta for bits in [8,16,31,32,63,64,65,127,128,255,256,319,320,321,512,1024,2048,4096] for delta in [-1,0,1]]
 for endian,n in itertools.product(['be','le'],numbers):
  add('exact-minimal-byte-encodings','i2osp',a=n,endian=endian)
  if n:add('negative-unpadded-allocation-length-only','i2osp',a=-n,endian=endian,part='length')
  size=(n.bit_length()+7)//8
  for length in sorted({-1,0,1,size-1,size,size+1,40}):
   add('fixed-width-maybe-bytes-and-error-boundaries','i2ospOf',a=n,length=length,endian=endian)
   add('fixed-width-unsafe-error-boundaries','i2ospOf_',a=n,length=length,endian=endian)
   for padded in [False,True]:
    content=[(17+31*i)%256 for i in range(max(0,length)+4)]
    add('guarded-raw-partial-padding-and-return-counts','rawWriteOf' if padded else 'rawWrite',a=n,length=length,endian=endian,bytes=content)
 for endian,n,length,op in itertools.product(['be','le'],[-257,-1],[-1,0,1,2,8],['i2ospOf','i2ospOf_','rawWrite','rawWriteOf']):
  add('negative-integers-rejected-without-buffer-writes',op,a=n,length=length,endian=endian,bytes=[165]*12)
 for endian,n,length,op,part in itertools.product(['be','le'],['bottom:a','-1','0','256'],['bottom:length','-1','0','1','2'],['i2ospOf','i2ospOf_','rawWrite','rawWriteOf'],['ignore','constructor','value']):
  add('fixed-width-strict-size-and-argument-error-precedence',op,a=n,length=length,endian=endian,part=part,bytes=[165]*8)
 for endian in ['be','le']:
  arrays=[[],[0],[255],[0,1],[1,0],[0,255,0]]+[[rng.randrange(256) for _ in range(n)] for n in [7,8,9,15,16,17,32,33,40,128,512]]
  for bs in arrays:
   add('byte-imports-leading-zero-and-byte-order','os2ip',bytes=bs,endian=endian)
   for n in sorted({-1,0,1,len(bs)//2,len(bs)}):
    if n<=len(bs):add('guarded-raw-prefix-import-and-nonpositive-size','rawRead',bytes=bs,endian=endian,length=n)
 def query(arg):return [json.loads(l) for l in subprocess.run([args.oracle,arg],check=True,capture_output=True,text=True,timeout=120).stdout.splitlines()]
 vectors=query('--vectors');assert len(vectors)==5
 for v in vectors:
  add('all-original-serialization-known-answer-outputs','i2ospOf_',a=v['a'],length=v['length'],endian='be')
  add('all-original-serialization-known-answer-inputs','os2ip',bytes=v['bytes'],endian='be')
 samples=query('--samples');assert len(samples)==1408
 for request in samples:
  cases.append(dict(group='all-original-basic-and-marshalling-property-expressions',request=request))
  n=int(request['a']);kind=request['kind']
  if kind=='num-bits':
   for value in [(1<<n)-1,1<<n,(1<<(n+1))-1]:add('original-generated-widths-exact-bit-and-byte-results','numBits',a=value);add('original-generated-widths-exact-bit-and-byte-results','numBytes',a=value)
  elif kind=='as-power-of-2-and-odd':add('original-generated-exact-factorization','asPowerOf2AndOdd',a=n)
  elif 'marshalling' in kind:
   for endian in ['be','le']:add('original-generated-exact-byte-results','i2osp',a=n,endian=endian)
assert args.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([args.oracle],stdin=inp,stdout=out,check=True,timeout=600);out.seek(0);results=[json.loads(l) for l in out]
assert len(results)==len(cases)
if args.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,results)):assert c['expected']==o,(i,c,o)
 print('Original GHC verified all',len(cases),'basic-number/serialization observations');raise SystemExit
for c,o in zip(cases,results):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'number observations; SHA256',manifest['caseHash'])
