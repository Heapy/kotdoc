#!/usr/bin/env python3
"""Independent original six CMYK/YCbCrK color-conversion instances."""
import argparse,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/image-cmyk.jsonl.gz'
sources=['tools/import-image-cmyk-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-image-cmyk-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_IMAGE_CMYK_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 print(f'Verified {len(cs)} original CMYK and YCbCrK captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name image-cmyk-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x434d594b)
def add(op,kind,path,w,h,values,label):cases.append(dict(name=f'{len(cases)} {label}',op=op,kind=kind,path=path,width=w,height=h,values=values))
for path in ['pixel','image']:
 for op in ['rgb8','ycck-rgb','ycck-cmyk']:
  for first in range(256):add(op,'sweep',path,256,256,[first],f'{op} {path} full three-channel domain {first}')
 add('cmyk8','sweep',path,256,256,[0],f'cmyk8 {path} every component/K pair')
 for offset in [0,1,32768,65535]:add('rgb16','sweep',path,256,256,[offset],f'rgb16 {path} full component range {offset}')
 for k in sorted({v for n in range(17) for v in [2**n-1,2**n,2**n+1] if 0<=v<=65535}):
  add('cmyk16','sweep',path,256,256,[k],f'cmyk16 {path} full component range K={k}')
 for op in ['rgb8','cmyk8','rgb16','cmyk16','ycck-rgb','ycck-cmyk']:
  count=3 if op.startswith('rgb') else 4;limit=65536 if op.endswith('16') else 256
  for w,h in [(0,0),(0,3),(3,0),(0,-5),(-5,0),(1,1),(1,17),(17,1),(7,9),(16,16),(17,19)]:
   for tail in [0,1,7]:add(op,'values',path,w,h,[rng.randrange(limit) for _ in range(w*h*count+tail)],f'{op} {path} {w}x{h} tail {tail}')
  for n in range(32):
   w=rng.randrange(1,24);h=rng.randrange(1,24)
   add(op,'values',path,w,h,[rng.randrange(limit) for _ in range(w*h*count)],f'{op} {path} seeded {n}')
  edges=[0,1,2,127,128,254,255] if limit==256 else [0,1,2,255,256,257,32767,32768,32769,65534,65535]
  data=[v for pixel in itertools.product(edges,repeat=count) for v in pixel]
  add(op,'values',path,len(data)//count,1,data,f'{op} {path} boundary Cartesian product')
 for op in ['ycck-rgb','ycck-cmyk']:
  for y,cb,cr in itertools.product([0,1,127,128,254,255],repeat=3):
   add(op,'values',path,256,1,[v for k in range(256) for v in [y,cb,cr,k]],f'{op} {path} all K at {y}/{cb}/{cr}')
for start in range(0,len(cases),4):
 batch=cases[start:start+4];requests=['('+','.join(json.dumps(c[k],separators=(',',':')) for k in ['op','kind','path','width','height','values'])+')' for c in batch]
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line);assert 'exception' not in expected,c
  data=bytes.fromhex(expected['hex']);expected['sha256']=hashlib.sha256(data).hexdigest();expected['length']=len(data)
  if c['kind']=='sweep':del expected['hex']
  c['expected']=expected
 if start%128==0:print(f'Captured {start+len(batch)}/{len(cases)} original CMYK cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} CMYK and YCbCrK results')
