#!/usr/bin/env python3
"""Independent original packed pixels and aliasing mutable component views."""
import argparse,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/image-packed.jsonl.gz'
sources=['tools/import-image-packed-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-image-packed-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
types={'y8':(1,8,8),'y16':(1,16,16),'y32':(1,32,32),'yf':(1,32,32),'ya8':(2,8,16),'ya16':(2,16,32),'rgba8':(4,8,32),'rgba16':(4,16,64),'cmyk8':(4,8,32),'cmyk16':(4,16,64)}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def generated(t,kind):
 n,bits,packed=types[t]
 if kind=='components':return [v for i in range(2**bits) for c in range(n) for v in [(i*(2*c+1)+c*257)%(2**bits)]]
 if kind=='packed-domain':return list(range(2**packed))
 raise ValueError(kind)
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_IMAGE_PACKED_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 for op in ['pack','unpack','read','write','fill','between']:assert {c['type'] for c in cs if c['op']==op}==set(types),op
 print(f'Verified {len(cs)} original packed-pixel captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name image-packed-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0','memory','little']
cases=[];rng=random.Random(0x5041434b)
float_bits=[(sign<<31)|(exp<<23)|mantissa for sign in [0,1] for exp in range(256) for mantissa in [0,1,0x3fffff,0x400000,0x7ffffe,0x7fffff]]+[rng.getrandbits(32) for _ in range(4096)]
def add(op,t,params=None,storage=None,values=None,kind='values',label=''):
 cases.append(dict(name=f'{len(cases)} {op}/{t} {label}',op=op,type=t,params=params or [],storage=storage or [],values=values or [],kind=kind))
for t,(n,bits,packed) in types.items():
 limit=2**bits
 if t=='yf':add('pack',t,values=float_bits,label='all Float signs/exponents/mantissa boundaries and seeded bits')
 elif bits<=16:add('pack',t,kind='components',label='every component value in every field')
 else:
  edges=sorted({v for i in range(bits+1) for v in [2**i-1,2**i,2**i+1] if 0<=v<limit})
  add('pack',t,values=edges+[rng.getrandbits(bits) for _ in range(4096)],label='Word32 bit boundaries and seeded words')
 if t=='yf':add('unpack',t,values=float_bits,label='raw Float packed identity')
 elif packed<=16:add('unpack',t,kind='packed-domain',label='every packed primitive value')
 else:
  edges=sorted({v for i in range(packed+1) for v in [2**i-1,2**i,2**i+1] if 0<=v<2**packed})
  add('unpack',t,values=edges+[rng.getrandbits(packed) for _ in range(4096)],label='packed Word32/Word64 bit boundaries and seeded words')
 for op in ['pack','unpack']:
  add(op,t,label='empty batch')
  for i in range(16):add(op,t,values=[rng.randrange(limit if op=='pack' else 2**packed) for _ in range((17*n) if op=='pack' else 17)],label=f'seeded batch {i}')
 edges=[0,1,127,128,254,255] if bits==8 else [0,1,255,256,32767,32768,65534,65535] if bits==16 else [0,1,2**31-1,2**31,2**32-1]
 if t!='yf':add('pack',t,values=[v for pixel in itertools.product(edges,repeat=n) for v in pixel],label='component Cartesian boundaries')
 # Observe every component/bit position independently.
 impulses=[]
 for at in range(n):
  for bit in range(bits):pixel=[0]*n;pixel[at]=1<<bit;impulses+=pixel
 add('pack',t,values=impulses,label='each bit at every component position')
 for length in sorted({0,1,n-1,n,n+1,2*n+3,4*n+7}):
  storage=[rng.randrange(limit) for _ in range(length)];pixel=[rng.randrange(limit) for _ in range(n)]
  for offset in range(min(length,6)+1):
   for size in sorted({0,length-offset,min(2*n+1,length-offset)}):
    pars=[-7,3,offset,size,0,0]
    add('fill',t,pars,storage,pixel,label=f'view {offset}/{size} parent {length}')
    for index in range(size):
     if (index//n)*n+n<=size:
      for op in ['read','write']:add(op,t,pars[:4]+[index,0],storage,pixel,label=f'rounded index {index} view {offset}/{size}')
    for start in range(size+1):
     for count in range(min(3,(size-start)//n)+1):add('between',t,pars[:4]+[start,count],storage,pixel,label=f'component start {start}, packed count {count}, view {offset}/{size}')
    for start,count in [(-1,0),(0,-1),(size+1,0),(size,1),(0,size+1),(-2**63,0),(0,-2**63),(2**63-1,0),(0,2**63-1)]:
     add('between',t,pars[:4]+[start,count],storage,pixel,label='defined invalid inner slice')
  for offset,size in [(-1,0),(0,-1),(length+1,0),(length,1),(0,length+1),(-2**63,0),(0,-2**63),(2**63-1,0),(0,2**63-1)]:
   for op in ['fill','read','write','between']:add(op,t,[0,0,offset,size,0,0],storage,pixel,label='invalid outer component view')
 if t=='yf':
  special=[0,0x80000000,1,0x7fffff,0x800000,0x3f800000,0x7f800000,0xff800000,0x7fc00000,0x7f800001,0xffc01234,0xffffffff]
  for value in special:
   for op in ['fill','read','write','between']:add(op,t,[2147483647,-1,1,7,1,3],special,[value],label='raw Float memory copies including NaN payloads')
cache={}
for start in range(0,len(cases),4):
 batch=cases[start:start+4];requests=[]
 for c in batch:
  if c['kind']=='values':values=c['values']
  else:
   key=(c['type'],c['kind'])
   if key not in cache:cache[key]=generated(*key)
   values=cache[key]
  req=[c['op'],c['type'],c['params'],c['storage'],values]
  requests.append('('+','.join(json.dumps(v,separators=(',',':')) for v in req)+')')
 lines=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line);expected['hashes']={};expected['lengths']={}
  for key in ['packedHex','pixelHex','dataHex']:
   if key not in expected:continue
   data=bytes.fromhex(expected[key]);expected['hashes'][key]=hashlib.sha256(data).hexdigest();expected['lengths'][key]=len(data)
   if c['kind']!='values':del expected[key]
  c['expected']=expected
 if start%512==0:print(f'Captured {start+len(batch)}/{len(cases)} original packed-pixel cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} packed-pixel results')
