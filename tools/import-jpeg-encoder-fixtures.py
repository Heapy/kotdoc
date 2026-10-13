#!/usr/bin/env python3
"""Capture the original fixed-point DCT and complete JPEG encoder independently."""
import argparse,gzip,hashlib,json,os,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/jpeg-encoder.jsonl.gz'
sources=['tools/import-jpeg-encoder-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-jpeg-encoder-reference','vendor/JuicyPixels-jpeg-tests-v3.3.8']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_JPEG_ENCODER_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 print(f'Verified {len(cs)} original JPEG encoding captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name jpeg-encoder-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']))
assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x454e434f)
def add(name,op='encode',color='Y8',dims=None,values=None,dpi=None,exif=None):
 cases.append(dict(name=f'{len(cases)} {name}',op=op,color=color,dims=dims or [],values=values or [],dpi=dpi or [],exif=exif or []))
for value in range(256):add(f'constant DCT {value}',op='dct',values=[value]*64)
for offset in range(-32768,32768,64):add(f'full Int16 domain DCT {offset}',op='dct',values=list(range(offset,offset+64)))
for at in range(64):
 for value in [-32768,-1,1,127,255,32767]:
  block=[0]*64;block[at]=value;add(f'DCT impulse {at}/{value}',op='dct',values=block)
for n in range(256):add('seeded full Int16 DCT',op='dct',values=[rng.randrange(-32768,32768) for _ in range(64)])
colors={'Y8':1,'YCbCr8':3,'RGB8':3,'CMYK8':4}
for color,count in colors.items():
 for q in range(256):
  add(f'all Word8 qualities {color}/{q}',color=color,dims=[17,19,q],values=[(x*31+y*57+c*83+x*y)%256 for y in range(19) for x in range(17) for c in range(count)])
 for w,h in [(1,1),(1,17),(17,1),(7,9),(8,8),(9,7),(15,16),(16,15),(16,16),(17,17),(31,33),(33,31)]:
  for q in [0,1,49,50,75,99,100,255]:
   for pattern in ['black','mid','white','checker','random']:
    values=[{'black':0,'mid':128,'white':255,'checker':255*((x+y+c)%2)}.get(pattern,0) if pattern!='random' else rng.randrange(256) for y in range(h) for x in range(w) for c in range(count)]
    add(f'edge/pattern {color}/{w}x{h}/{q}/{pattern}',color=color,dims=[w,h,q],values=values)
 for w,h in [(0,0),(0,17),(17,0),(-1,0),(0,-1),(65536,0),(0,65537)]:
  add('empty traversal/header narrowing',color=color,dims=[w,h,50])
 for dpi in [[],[72],[0,0],[72,93],[65535,65536],[2**64-1,2**63]]:
  for length in [0,1,3,4,5,255,256]:
   add('JFIF/Exif frame integration',color=color,dims=[9,7,50],values=[(i*73)%256 for i in range(9*7*count)],dpi=dpi,exif=[(i*97)%256 for i in range(length)])
for start in range(0,len(cases),25):
 batch=cases[start:start+25]
 requests=['('+','.join(json.dumps(c[k],separators=(',',':')) for k in ['op','color','dims','values','dpi','exif'])+')' for c in batch]
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  c['expected']=json.loads(line);assert 'exception' not in c['expected'],c
 if start%500==0:print(f'Captured {start+len(batch)}/{len(cases)} synthetic',flush=True)
with tempfile.TemporaryDirectory() as tmp:
 for file in sorted((root/'vendor/JuicyPixels-jpeg-tests-v3.3.8/tests/jpeg').glob('*.jpg')):
  for quality in [50,90,100]:
   out=Path(tmp)/'encoded.jpg'
   result=json.loads(subprocess.check_output([args.oracle,'--file',str(quality),str(file),str(out)]));assert not result,result
   encoded=out.read_bytes()
   cases.append(dict(name=f'{len(cases)} original {file.name}/{quality}',source=str(file.relative_to(root)),quality=quality,expected=dict(encodedHash=hashlib.sha256(encoded).hexdigest(),encodedLength=len(encoded))))
  print(f'Captured original {file.name}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} complete JPEG encoding/DCT results')
