#!/usr/bin/env python3
"""Independent exact Float DCT captures, including every Int16 constant level."""
import argparse,gzip,hashlib,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/jpeg-dct.jsonl.gz'
sources=['tools/import-jpeg-dct-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-jpeg-dct-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_JPEG_DCT_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 print(f'Verified {len(cs)} original Float JPEG DCT captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name jpeg-dct-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x44435446)
def add(values,name,op='block',work=None):cases.append(dict(name=f'{len(cases)} {name}',op=op,work=work if work is not None else [0x12345678]*64,values=values))
for start in range(-32768,32768,256):add(list(range(start,start+256)),f'all constant levels {start}',op='constants')
for start in range(-32768,32768,64):add(list(range(start,start+64)),f'full Int16 ramp {start}')
for at in range(64):
 for value in [-32768,-16384,-256,-1,1,127,255,16384,32767]:
  block=[0]*64;block[at]=value;add(block,f'impulse {at}/{value}')
for a,b in [(-32768,32767),(-1,1),(0,255),(-32768,0)]:
 for shift in range(8):add([a if ((i//8+i%8+shift)%8)<4 else b for i in range(64)],'alternating extrema')
for n in range(1024):add([rng.randrange(-32768,32768) for _ in range(64)],'seeded full Int16 block')
for n in range(256):add([rng.randrange(256) for _ in range(64)],'seeded byte block')
for n in range(32):add([rng.randrange(-32768,32768) for _ in range(64+n)],'nonzero workspace and preserved tails',work=[rng.randrange(-2**31,2**31) for _ in range(64+n)])
for start in range(0,len(cases),16):
 batch=cases[start:start+16];requests=['('+','.join(json.dumps(c[k],separators=(',',':')) for k in ['op','work','values'])+')' for c in batch]
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  c['expected']=json.loads(line);assert 'exception' not in c['expected'],c
 if start<256 or start%256==0:print(f'Captured {start+len(batch)}/{len(cases)} Float DCT cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} Float DCT results')
