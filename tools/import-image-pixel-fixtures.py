#!/usr/bin/env python3
"""Independent original Pixel and mutable-image observations for every pixel type."""
import argparse,gzip,hashlib,itertools,json,os,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/image-pixel.jsonl.gz'
sources=['tools/import-image-pixel-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-image-pixel-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
counts={'y8':1,'y16':1,'y32':1,'yf':1,'ya8':2,'ya16':2,'rgb8':3,'rgb16':3,'rgbf':3,'rgba8':4,'rgba16':4,'ycbcr8':3,'ycbcrk8':4,'cmyk8':4,'cmyk16':4}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def limit(t):return 2**32 if t.endswith('f') or t=='y32' else 65536 if t.endswith('16') else 256
float_bits=[(sign<<31)|(exp<<23)|mantissa for sign in [0,1] for exp in range(256) for mantissa in [0,1,0x3fffff,0x400000,0x7ffffe,0x7fffff]]
word_bits=sorted({v for n in range(33) for v in [2**n-1,2**n,2**n+1] if 0<=v<2**32})
def generated(t,kind):
 stride=counts[t];n=65536 if kind in ['pairs8','range16'] else 256 if kind=='range8' else len(float_bits) if kind=='float-bits' else len(word_bits)
 left=[];right=[]
 for i in range(n):
  for c in range(stride):
   if kind=='pairs8':a=(i//256+c*17)%256;b=(i%256+c*73)%256
   elif kind=='range8':a=(i+c*17)%256;b=(255-i+c*73)%256
   elif kind=='range16':a=(i*(2*c+1)+c*257)%65536;b=(65535-i*(2*c+1)+c*73)%65536
   elif kind=='float-bits':a=float_bits[(i+c*257)%n];b=float_bits[(n-1-i+c*131)%n]
   else:a=word_bits[i];b=2**32-1-a
   left.append(a);right.append(b)
 return left,right
def canonical(data):
 out=bytearray(data)
 for at in range(0,len(out),4):
  value=struct.unpack_from('<I',out,at)[0]
  if value&0x7f800000==0x7f800000 and value&0x007fffff:struct.pack_into('<I',out,at,0x7fc00000)
 return bytes(out)
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_IMAGE_PIXEL_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 for op in ['mix','mix-alpha','map','meta','pixel','read','write','unsafe-pixel','unsafe-read','unsafe-write','base','mutable-base','freeze','thaw','unsafe-freeze','unsafe-thaw','new','create']:
  assert {c['type'] for c in cs if c['op']==op}==set(counts),op
 print(f'Verified {len(cs)} original Pixel/mutable-image captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name image-pixel-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x50495845)
def add(op,t,mode='',params=None,left=None,right=None,kind='values',label=''):
 cases.append(dict(name=f'{len(cases)} {op}/{t}/{mode} {label}',op=op,type=t,mode=mode,params=params or [],left=left or [],right=right or [],kind=kind))
for t,n in counts.items():
 bound=limit(t);range_kind='float-bits' if t.endswith('f') else 'word-bits' if t=='y32' else 'range16' if t.endswith('16') else 'range8'
 pure=[('meta','')]+[('mix',m) for m in ['left','right','sum','difference','weighted','index','fail1']]+[('mix-alpha',m) for m in ['index/tagged','sum/difference','left/right','right/left','weighted/fail','index/fail','fail1/tagged']]+[('map',m) for m in ['identity','negate','offset','scale','fail']]
 for op,mode in pure:
  kind='pairs8' if op.startswith('mix') and bound==256 else range_kind
  add(op,t,mode,kind=kind,label='complete component domains/ranges')
  add(op,t,mode,label='empty batch')
  for i in range(3):add(op,t,mode,left=[rng.randrange(bound) for _ in range(n*17)],right=[rng.randrange(bound) for _ in range(n*17)],label=f'seeded pixels {i}')
 # All safe operations check only flattened vector indices, including partial writes.
 for length in sorted({0,1,n-1,n,n+1,n+2,3*n+2}):
  data=[rng.randrange(bound) for _ in range(length)];replacement=[rng.randrange(bound) for _ in range(n)]
  for x,y in itertools.product([-2,-1,0,1,2,3],[-1,0,1]):
   for op in ['pixel','read','write']:add(op,t,params=[2,2,x,y,0],left=data,right=replacement,label=f'flat coordinate {x}/{y} vector {length}')
  if length>=n:
   for at in range(length-n+1):
    for op in ['unsafe-pixel','unsafe-read','unsafe-write']:add(op,t,params=[-7,-3,0,0,at],left=data,right=replacement,label=f'in-bounds component offset {at}')
 for w,h,x,y in [(2,-7,3,-1),(-2,3,2,1),(0,0,0,0),(1,1,-2**63,0),(1,1,2**63-1,1),(2,3,2**63-1,-2**63)]:
  for op in ['pixel','read','write']:add(op,t,params=[w,h,x,y,0],left=[rng.randrange(bound) for _ in range(n*4)],right=[rng.randrange(bound) for _ in range(n)],label='nonstandard/wrapping coordinates')
 edges=[-2**63,-2147483648,-1,0,1,2147483647,2**63-1] if t in ['y32','ya8','rgbf','rgba16'] else [-1,0,1,2147483647]
 for w,x,y in itertools.product(edges,repeat=3):
  for op in ['base','mutable-base']:add(op,t,params=[w,-17,x,y,0],label='Int64 boundary product')
 for i in range(16):
  pars=[rng.randrange(-2**63,2**63),0,rng.randrange(-2**63,2**63),rng.randrange(-2**63,2**63),0]
  for op in ['base','mutable-base']:add(op,t,params=pars,label='seeded Int64 arithmetic')
 for w,h in [(0,0),(0,5),(5,0),(1,1),(2,3),(-2,-3),(-7,1),(2147483647,1)]:
  for length in sorted({0,1,n-1,n,n+1,5*n+7}):
   data=[rng.randrange(bound) for _ in range(length)];replacement=[rng.randrange(bound) for _ in range(n)]
   for op in ['freeze','thaw','unsafe-freeze','unsafe-thaw']:
    mode='mutate' if length>=n and op!='unsafe-freeze' else 'view'
    add(op,t,mode,params=[w,h,0,0,max(0,length-n)],left=data,right=replacement,label=f'copy/alias vector {length} dimensions {w}x{h}')
 for w,h in [(0,0),(0,5),(5,0),(1,1),(2,3),(7,9),(17,1),(-1,1),(1,-2)]:
  for op in ['create','new']:
   length=max(0,w*h*n)
   add(op,t,params=[w,h,0,0,0],left=[rng.randrange(bound) for _ in range(length)],right=[rng.randrange(bound) for _ in range(n)],label=f'allocation/background {w}x{h}')
 add('new',t,params=[-2,-3,0,0,0],left=[rng.randrange(bound) for _ in range(6*n)],right=[0]*n,label='negative dimensions positive allocation, fully initialized')
cache={}
for start in range(0,len(cases),4):
 batch=cases[start:start+4];requests=[]
 for c in batch:
  if c['kind']=='values':left,right=c['left'],c['right']
  else:
   key=(c['type'],c['kind'])
   if key not in cache:cache[key]=generated(*key)
   left,right=cache[key]
  req=[c['op'],c['type'],c['mode'],c['params'],left,right]
  requests.append('('+','.join(json.dumps(v,separators=(',',':')) for v in req)+')')
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line);expected['hashes']={};expected['lengths']={}
  for key in ['hex','pixelHex','dataHex','sourceHex','imageHex']:
   if key not in expected:continue
   data=bytes.fromhex(expected[key]);expected['hashes'][key]=hashlib.sha256(data).hexdigest();expected['lengths'][key]=len(data)
   color=c['mode'].split('/')[0]
   arithmetic=c['type'].endswith('f') and ((c['op'] in ['mix','mix-alpha'] and color in ['sum','difference','weighted']) or (c['op']=='map' and color in ['offset','scale']))
   if arithmetic and key=='hex':expected['canonicalNaNSha256']=hashlib.sha256(canonical(data)).hexdigest()
   if c['kind']!='values':del expected[key]
  c['expected']=expected
 if start%512==0:print(f'Captured {start+len(batch)}/{len(cases)} original Pixel/mutable cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} Pixel/mutable-image results')
