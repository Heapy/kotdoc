#!/usr/bin/env python3
"""Original RGB8/YCbCr8 conversion captures across both complete 24-bit domains."""
import argparse,gzip,hashlib,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/image-color.jsonl.gz'
sources=['tools/import-image-color-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-image-color-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_IMAGE_COLOR_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert sum(c['mode'].startswith('exhaustive') for c in cs)==1024
 print(f'Verified {len(cs)} original image color captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name image-color-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x59434243)
def add(mode,direction,w,h,values,label):cases.append(dict(name=f'{len(cases)} {label}',mode=mode,direction=direction,width=w,height=h,values=values))
for direction in ['rgb','ycc']:
 for mode in ['exhaustive-image','exhaustive-pixel']:
  for first in range(256):add(mode,direction,256,256,[first],f'{direction} {mode} channel {first}')
 for mode in ['image','pixel']:
  for w,h in [(0,0),(0,5),(5,0),(1,1),(1,17),(17,1),(2,3),(7,9),(8,8),(15,17),(16,16),(17,19)]:
   for tail in [0,1,7]:add(mode,direction,w,h,[rng.randrange(256) for _ in range(w*h*3+tail)],f'{direction} {mode} {w}x{h} tail {tail}')
  for n in range(64):
   w=rng.randrange(1,20);h=rng.randrange(1,20)
   add(mode,direction,w,h,[rng.randrange(256) for _ in range(w*h*3)],f'{direction} {mode} random {n}')
 # Both negative dimensions have a positive product in the optimized conversion.
 for w,h in [(-1,-1),(-2,-3),(0,-5),(-5,0)]:
  add('image',direction,w,h,[rng.randrange(256) for _ in range(w*h*3)],f'{direction} signed dimensions {w}x{h}')
for value,name in [(255,'white'),(0,'black')]:add('jpeg','rgb',16,16,[value]*(16*16*3),f'original toJpg {name}')
add('jpeg','rgb',128,128,[v for y in range(128) for x in range(128) for v in [x,y,255]],'original toJpg test gradient')
for n in range(32):
 w=rng.randrange(1,34);h=rng.randrange(1,34)
 add('jpeg','rgb',w,h,[rng.randrange(256) for _ in range(w*h*3)],f'RGB conversion and JPEG odd edges {n}')
for start in range(0,len(cases),4):
 batch=cases[start:start+4];requests=['('+','.join(json.dumps(c[k],separators=(',',':')) for k in ['mode','direction','width','height','values'])+')' for c in batch]
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line);assert 'exception' not in expected,c
  data=bytes.fromhex(expected['hex'])
  expected['sha256']=hashlib.sha256(data).hexdigest();expected['length']=len(data)
  if c['mode'].startswith('exhaustive'):del expected['hex']
  c['expected']=expected
 if start%64==0:print(f'Captured {start+len(batch)}/{len(cases)} original color cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} image color results')
