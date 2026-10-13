#!/usr/bin/env python3
"""Independent original plane, transparency and luma captures."""
import argparse,gzip,hashlib,itertools,json,os,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/image-plane.jsonl.gz'
sources=['tools/import-image-plane-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-image-plane-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
counts={'y8':1,'y16':1,'y32':1,'yf':1,'ya8':2,'ya16':2,'rgb8':3,'rgb16':3,'rgbf':3,'rgba8':4,'rgba16':4,'ycbcr8':3,'ycbcrk8':4,'cmyk8':4,'cmyk16':4}
planes={'ya8':['luma','alpha'],'ya16':['luma','alpha'],'rgb8':['red','green','blue'],'rgb16':['red','green','blue'],'rgbf':['red','green','blue'],'rgba8':['red','green','blue','alpha'],'rgba16':['red','green','blue','alpha'],'ycbcr8':['luma','cb','cr'],'cmyk8':['cyan','magenta','yellow','black'],'cmyk16':['cyan','magenta','yellow','black']}
lumas=['y8','y16','y32','yf','ya8','rgb8','rgb16','rgbf','rgba8','ycbcr8']
def canonical_nan(data):
 out=bytearray(data)
 for at in range(0,len(out),4):
  bits=struct.unpack_from('<I',out,at)[0]
  if bits&0x7f800000==0x7f800000 and bits&0x007fffff:struct.pack_into('<I',out,at,0x7fc00000)
 return bytes(out)
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_IMAGE_PLANE_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert {(c['type'],c['selector']) for c in cs if c['op']=='named'}=={(t,s) for t,ss in planes.items() for s in ss}
 assert {c['type'] for c in cs if c['op']=='unsafe'}==set(counts)
 print(f'Verified {len(cs)} original color plane/transparency/luma captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name image-plane-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x504c414e)
float_bits=[(sign<<31)|(exp<<23)|mantissa for sign in [0,1] for exp in range(256) for mantissa in [0,1,0x3fffff,0x400000,0x7ffffe,0x7fffff]]+[rng.getrandbits(32) for _ in range(4096)]
def limit(t):return 2**32 if t in ['yf','rgbf','y32'] else 65536 if t.endswith('16') else 256
def add(op,t,s,w,h,values,kind='values',label=''):
 cases.append(dict(name=f'{len(cases)} {op}/{t}/{s} {label}',op=op,type=t,selector=str(s),width=w,height=h,values=values,kind=kind))
def ordinary(op,t,s):
 n=counts[t];bound=limit(t)
 for w,h in [(0,0),(0,3),(3,0),(0,-5),(-5,0),(1,1),(1,17),(17,1),(7,9),(16,16),(17,19)]:
  for tail in [0,1,7]:add(op,t,s,w,h,[rng.randrange(bound) for _ in range(w*h*n+tail)],label=f'{w}x{h} tail {tail}')
 for i in range(8):
  w=rng.randrange(1,20);h=rng.randrange(1,20)
  add(op,t,s,w,h,[rng.randrange(bound) for _ in range(w*h*n)],label=f'seeded {i}')
 if t in ['yf','rgbf']:
  data=[float_bits[(i+c*257)%len(float_bits)] for i in range(len(float_bits)) for c in range(n)]
  add(op,t,s,len(float_bits),1,data,label='Float sign/exponent/mantissa boundaries and payloads')
 elif t=='y32':
  data=[v for i in range(33) for v in [max(0,2**i-1),min(2**32-1,2**i),min(2**32-1,2**i+1)]]
  add(op,t,s,len(data),1,data,label='full Word32 bit boundaries')
 else:add(op,t,s,16 if bound==256 else 256,16 if bound==256 else 256,[0],kind='range',label='every component value')
for t,ss in planes.items():
 for s in ss:ordinary('named',t,s)
for t,n in counts.items():
 for s in range(n):
  ordinary('unsafe',t,s)
  for w,h in [(-1,-1),(-2,-3)]:add('unsafe',t,s,w,h,[rng.randrange(limit(t)) for _ in range(w*h*n+3)],label='defined linear negative-dimension product')
 for s in [n,n+1,255,2147483647,2**63-1]:
  for w,h in [(0,0),(1,1),(2147483647,2147483647)]:add('unsafe',t,s,w,h,[],label='invalid component before source/size access')
 for s in [-1,-2**63]:add('unsafe',t,s,0,5,[],label='empty output does not read negative index')
for t in lumas:
 for op in ['luma-pixel','luma-image']:
  ordinary(op,t,'')
  if t in ['rgb8','rgba8']:
   for first in range(256):add(op,t,'',256,256,[first],kind='rgb-domain',label=f'full RGB domain {first}')
  if t in ['rgb16']:
   for offset in [1,32768,65535]:add(op,t,'',256,256,[offset],kind='range',label=f'Word16 channel permutation {offset}')
  if t=='ya8':add(op,t,'',256,256,[v for y in range(256) for a in range(256) for v in [y,a]],label='all 65536 YA8 pairs')
  if t in ['rgb8','rgb16','rgba8']:
   edges=[0,1,2,127,128,254,255] if limit(t)==256 else [0,1,255,256,257,32767,32768,32769,65534,65535]
   data=[v for px in itertools.product(edges,repeat=counts[t]) for v in px]
   add(op,t,'',len(data)//counts[t],1,data,label='Cartesian boundaries')
 for w,h in [(-1,-1),(-2,-3)]:
  if t in ['ya8','ycbcr8']:add('luma-image',t,'',w,h,[rng.randrange(limit(t)) for _ in range(w*h*counts[t]+3)],label='defined stride traversal')
  elif t in ['y8','y16','y32','yf']:add('luma-image',t,'',w,h,[rng.randrange(limit(t)) for _ in range(7)],label='scalar identity preserves malformed dimensions/tails')
 if t in ['y8','y16','y32','yf']:add('luma-image',t,'',2147483647,1,[],label='scalar identity avoids allocation')
for t in ['ya8','ya16','rgba8','rgba16']:
 for op in ['drop-image','drop-pixel','alpha','opacity']:
  ordinary(op,t,'')
  if t=='ya8':add(op,t,'',256,256,[v for y in range(256) for a in range(256) for v in [y,a]],label='all 65536 YA8 pairs')
  n=counts[t];edges=[0,1,127,128,254,255] if limit(t)==256 else [0,1,255,256,32767,32768,65534,65535]
  data=[v for px in itertools.product(edges,repeat=n) for v in px]
  add(op,t,'',len(data)//n,1,data,label='Cartesian alpha boundaries')
for s in ['luma','alpha']:add('named','ya8',s,256,256,[0],kind='ya8-upstream',label='original planeSeparationYA8Test')
for start in range(0,len(cases),4):
 batch=cases[start:start+4];requests=['('+','.join(json.dumps(c[k],separators=(',',':')) for k in ['op','type','selector','kind','width','height','values'])+')' for c in batch]
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line)
  if 'exception' not in expected:
   data=bytes.fromhex(expected['hex']);expected['sha256']=hashlib.sha256(data).hexdigest();expected['length']=len(data)
   if c['type']=='rgbf' and c['op'].startswith('luma-'):expected['canonicalNaNSha256']=hashlib.sha256(canonical_nan(data)).hexdigest()
   if c['kind'] in ['range','rgb-domain']:del expected['hex']
  else:assert c['op']=='unsafe' and int(c['selector'])>=counts[c['type']],c
  c['expected']=expected
 if start%256==0:print(f'Captured {start+len(batch)}/{len(cases)} original plane/luma cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} plane/transparency/luma results')
