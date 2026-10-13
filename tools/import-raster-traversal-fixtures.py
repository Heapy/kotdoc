#!/usr/bin/env python3
"""Capture unchanged original imagePixels/imageIPixels using real Applicatives."""
import argparse,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/raster-traversal.jsonl.gz'
counts={'y8':1,'y16':1,'y32':1,'yf':1,'ya8':2,'ya16':2,'rgb8':3,'rgb16':3,'rgbf':3,'rgba8':4,'rgba16':4,'ycbcr8':3,'ycbcrk8':4,'cmyk8':4,'cmyk16':4}
effects=['identity','io','const-list','const-any','const-last','const-bracket','maybe','list']
sources=['tools/import-raster-traversal-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-raster-traversal-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_RASTER_TRAVERSAL_REFERENCE'));a=p.parse_args()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 for op,effect in itertools.product(['pixels','ipixels'],effects):
  assert {(c['source'],c['target']) for c in cs if c['op']==op and c['effect']==effect}==set(itertools.product(counts,repeat=2)),(op,effect)
 print(f'Verified {len(cs)} original Applicative traversal observations');raise SystemExit()
if not a.oracle:p.error('--oracle must name raster-traversal-reference')
info=json.loads(subprocess.check_output([a.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
rng=random.Random(0x54524156);cs=[]
def bound(t):return 2**32 if t in ['y32','yf','rgbf'] else 65536 if t.endswith('16') else 256
def add(op,s,t,effect,mode='normal',observe='data',w=3,h=2,offset=1,length=None,fx=1,fy=1,left=None):
 n=counts[s];length=max(0,w*h*n) if length is None else length
 left=[rng.randrange(bound(s)) for _ in range(offset+length+3)] if left is None else left
 cs.append(dict(name=f'{len(cs)} {op}/{s}/{t}/{effect}/{mode}/{observe} {w}x{h}',op=op,source=s,target=t,effect=effect,mode=mode,observe=observe,params=[w,h,offset,length,fx,fy],left=left))
for op,s,t in itertools.product(['pixels','ipixels'],counts,counts):
 for effect in effects:
  for mode,observe in [('normal','data'),('normal','metadata'),('ignore','bad-data'),('value-fail','metadata'),('effect-fail','metadata')]:
   # List branching stays at <=4 pixels (at most 16 output images).
   add(op,s,t,effect,mode,observe,w=2 if effect=='list' else 3,h=2)
for op,s,effect in itertools.product(['pixels','ipixels'],counts,effects):
 for w,h in [(0,0),(0,3),(3,0),(-2,1),(1,-2),(-2,-3),(2**63-1,0),(1,-2**63),(2**62,4)]:
  # Never materialize uninitialized row output or huge allocated output buffers.
  if op=="ipixels" and w>100000 and h>0 and effect!="identity":continue
  for observe in ['metadata','bad-metadata']:
   add(op,s,s,effect,observe=observe,w=w,h=h,length=max(0,w*h*counts[s]) if abs(w*h)<100 else 0)
  if w>=0 and h>=0 and w*h<100:
   add(op,s,s,effect,w=w,h=h)
 for mode,observe in [('normal','bad-data'),('ignore','bad-metadata'),('effect-fail-at','data'),('effect-fail-at','metadata'),('value-fail','data')]:
  add(op,s,s,effect,mode,observe,w=2 if effect=='list' else 3,h=2)
 if effect=='io':
  for fx,fy in [(0,0),(1,0),(1,1),(2,1),(3,3)]:
   for observe in ['data','metadata','bad-data','bad-metadata']:add(op,s,s,effect,'action-fail',observe,fx=fx,fy=fy)
 if effect=='maybe':
  for mode in ['missing','missing-at']:
   for observe in ['data','metadata','bad-data','bad-metadata']:add(op,s,s,effect,mode,observe)
 if effect in ['const-any','const-last']:
  for fx,fy in [(0,0),(1,1),(2,1)]:add(op,s,s,effect,'effect-fail-at',fx=fx,fy=fy)
 for offset,length in [(-1,0),(0,-1),(8,0),(0,8)]:
  for observe in ['data','metadata']:add(op,s,s,effect,observe=observe,offset=offset,length=length,left=[0]*7,w=1,h=1)
 # Source tails are ignored; view offsets and raw Float payloads pass through callbacks.
 for offset in [0,2,3]:add(op,s,s,effect,offset=offset,length=counts[s]*4+2,w=2,h=2)
# With Int64-min height every source component product is nonpositive. No unsafe
# source reads occur, but odd destination component counts request negative
# allocations when output data is demanded. Exercise every cross-type/effect pair.
for op,s,t,effect,observe in itertools.product(['pixels','ipixels'],counts,counts,effects,['data','metadata']):
 add(op,s,t,effect,observe=observe,w=1,h=-2**63,length=0)
for s,n in counts.items():
 for w,h in [(0,0),(1,1),(3,2),(-2,-3),(3,0),(-1,2)]:
  for mode,observe in itertools.product(['normal','ignore','prefix-fail'],['data','bad-data']):
   length=max(0,w*h*n)
   # Only the first complete pixel has fingerprint zero; later pixels are valid.
   add('foldmap',s,s,'const-last',mode,observe,w=w,h=h,offset=0,length=length,left=[0]*min(n,length)+[1]*(max(0,length-n)))
for start in range(0,len(cs),32):
 batch=cs[start:start+32]
 requests=['('+','.join(json.dumps(v,separators=(',',':')) for v in [c['op'],c['source'],c['target'],c['effect'],c['mode'],c['observe'],c['params'],c['left']])+')' for c in batch]
 lines=subprocess.check_output([a.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):c['expected']=json.loads(line)
 if start%512==0:print(f'Captured {start+len(batch)}/{len(cs)} original traversal cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cs),caseHash=digest(cs))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cs)+'\n').encode(),mtime=0))
print(f'Captured {len(cs)} Applicative traversal results')
