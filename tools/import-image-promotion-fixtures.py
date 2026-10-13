#!/usr/bin/env python3
"""Independent original 21 ColorConvertible instances and identity sharing."""
import argparse,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/image-promotion.jsonl.gz'
sources=['tools/import-image-promotion-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-image-promotion-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
ops=['y8-ya8','y8-yf','y8-y16','y8-rgb8','y8-rgb16','y8-rgba8','y16-ya16','y16-rgb16','y16-rgba16','yf-rgbf','ya8-rgb8','ya8-rgb16','ya8-rgba8','ya16-rgb16','ya16-rgba16','rgb16-rgba16','rgb8-rgba8','rgb8-rgbf','rgb8-rgb16','rgb8-rgba16','rgba8-rgba16']
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_IMAGE_PROMOTION_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert set(ops)=={c['op'] for c in cs if not c['op'].startswith('identity')}
 assert all(c['expected']['shared'] for c in cs if c['op'].startswith('identity'))
 print(f'Verified {len(cs)} original image promotion/identity captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name image-promotion-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x50524f4d)
def add(op,kind,path,w,h,values,label):cases.append(dict(name=f'{len(cases)} {label}',op=op,kind=kind,path=path,width=w,height=h,values=values))
float_bits=[(sign<<31)|(exponent<<23)|mantissa for sign in [0,1] for exponent in range(256) for mantissa in [0,1,0x3fffff,0x400000,0x7ffffe,0x7fffff]]+[rng.getrandbits(32) for _ in range(8192)]
components={'y8':1,'y16':1,'yf':1,'ya8':2,'ya16':2,'rgb8':3,'rgb16':3,'rgba8':4}
for path in ['pixel','image']:
 for op in ops:
  source,dest=op.split('-');count=components[source];limit=2**32 if source=='yf' else 65536 if source.endswith('16') else 256
  if source=='yf':add(op,'values',path,len(float_bits),1,float_bits,f'{op} {path} IEEE signs/exponents/mantissas and seeded bits')
  elif source=='rgb8':
   for first in range(256):add(op,'ramp',path,256,256,[first],f'{op} {path} full RGB8 domain {first}')
  elif source=='y8':add(op,'ramp',path,16,16,[0],f'{op} {path} all Word8 values')
  elif source in ['ya16','rgb16','rgba8']:
   for offset in [0,1,32768,65535]:add(op,'ramp',path,256,256,[offset],f'{op} {path} complete component ranges {offset}')
  else:add(op,'ramp',path,256,256,[0],f'{op} {path} all scalar values or YA8 pairs')
  for w,h in [(0,0),(0,3),(3,0),(0,-5),(-5,0),(1,1),(1,17),(17,1),(7,9),(16,16),(17,19)]:
   for tail in [0,1,7]:add(op,'values',path,w,h,[rng.randrange(limit) for _ in range(w*h*count+tail)],f'{op} {path} {w}x{h} tail {tail}')
  for n in range(16):
   w=rng.randrange(1,24);h=rng.randrange(1,24)
   add(op,'values',path,w,h,[rng.randrange(limit) for _ in range(w*h*count)],f'{op} {path} seeded {n}')
  if source!='yf':
   edges=[0,1,2,127,128,254,255] if limit==256 else [0,1,2,255,256,257,32767,32768,32769,65534,65535]
   data=[v for pixel in itertools.product(edges,repeat=count) for v in pixel]
   add(op,'values',path,len(data)//count,1,data,f'{op} {path} boundary Cartesian product')
for path in ['promote-image','convert-image']:
 for op,limit in [('identity8',256),('identity16',65536),('identityf',2**32)]:
  for w,h in [(0,0),(1,1),(3,2),(-1,4),(-3,-2),(2147483647,1)]:
   for n in [0,1,2,3,7,64]:
    values=[rng.randrange(limit) for _ in range(n)]
    add(op,'identity',path,w,h,values,f'{op} {path} original sharing {w}x{h} vector {n}')
  if op=='identityf':add(op,'identity',path,1,1,float_bits,f'{op} {path} entire Float vector preserved')
for path in ['promote-pixel','convert-pixel']:
 for n in range(256):add('identity-pixel','identity',path,1,1,[n,(n*73)%256,255-n],f'{path} RGB8 object sharing {n}')
for start in range(0,len(cases),4):
 batch=cases[start:start+4];requests=['('+','.join(json.dumps(c[k],separators=(',',':')) for k in ['op','kind','path','width','height','values'])+')' for c in batch]
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line);assert 'exception' not in expected,c
  if c['kind']=='identity':assert expected['shared'],c
  data=bytes.fromhex(expected['hex']);expected['sha256']=hashlib.sha256(data).hexdigest();expected['length']=len(data)
  if c['kind']=='ramp':del expected['hex']
  c['expected']=expected
 if start%128==0:print(f'Captured {start+len(batch)}/{len(cases)} original promotion cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} promotion and identity results')
