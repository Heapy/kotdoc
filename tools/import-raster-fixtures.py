#!/usr/bin/env python3
"""Capture original JuicyPixels raster operations, with independent supplied callbacks."""
import argparse,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/raster.jsonl.gz'
counts={'y8':1,'y16':1,'y32':1,'yf':1,'ya8':2,'ya16':2,'rgb8':3,'rgb16':3,'rgbf':3,'rgba8':4,'rgba16':4,'ycbcr8':3,'ycbcrk8':4,'cmyk8':4,'cmyk16':4}
sources=['tools/import-raster-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-raster-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_RASTER_REFERENCE'));a=p.parse_args()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 for op in ['generate','with','generate-fold','map','mapxy','fold','foldm','foldmap','zip','eq','slice','thaw','freeze','base','pixel','read','write']:
  assert {c['source'] for c in cs if c['op']==op}==set(counts),op
 for op in ['map','mapxy']:
  assert {(c['source'],c['target']) for c in cs if c['op']==op}==set(itertools.product(counts,repeat=2)),op
 print(f'Verified {len(cs)} original raster observations');raise SystemExit()
if not a.oracle:p.error('--oracle must name raster-reference')
info=json.loads(subprocess.check_output([a.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
rng=random.Random(0x52415354);cs=[]
def bound(t):return 2**32 if t in ['y32','yf','rgbf'] else 65536 if t.endswith('16') else 256
def data(t,n):return [rng.randrange(bound(t)) for _ in range(n)]
def add(op,s,t=None,mode='normal',observe='data',w=3,h=2,offset=1,length=None,left=None,right=None,third=None,seed=7,w2=None,h2=None):
 n=counts[s];length=w*h*n if length is None else length
 if left is None:left=data(s,max(0,offset+length+3))
 cs.append(dict(name=f'{len(cs)} {op}/{s}/{t or s}/{mode}/{observe} {w}x{h} [{offset},{length}]',op=op,source=s,target=t or s,mode=mode,observe=observe,params=[w,h,offset,length,seed,w if w2 is None else w2,h if h2 is None else h2],left=left,right=right or [],third=third or []))
for s,n in counts.items():
 for t in counts:
  for op in ['map','mapxy']:
   for w,h in [(1,1),(3,2),(9,7),(0,3),(-3,0)]:add(op,s,t,w=w,h=h)
   add(op,s,t,mode='ignore',observe='bad-data')
   add(op,s,t,mode='fail',observe='metadata')
 for op in ['generate','with','generate-fold']:
  for w,h in [(0,0),(0,3),(3,0),(1,1),(3,2),(9,7),(17,1),(-2,1),(1,-2),(2**63-1,0),(1,-2**63)]:
   # Pure row generators return uninitialized memory for both-negative dimensions;
   # unsafe withImage overflow is deliberately outside native defined coverage.
   if (w,h)==(1,-2**63) and (op=='with' or n%2):continue
   add(op,s,w=w,h=h,length=0)
   add(op,s,w=w,h=h,length=0,observe='metadata')
  if op!='generate-fold':
   add(op,s,mode='fail');add(op,s,mode='fail',observe='metadata')
 for op in ['fold','foldm','foldmap']:
  modes=['normal','ignore','fail'] if op!='foldmap' else ['normal','ignore','any','bracket']
  for mode in modes:
   for w,h in [(3,2),(1,7),(7,1),(0,3),(3,0),(-2,-3),(-2,1),(1,-2),(2**63-1,0)]:
    add(op,s,mode=mode,w=w,h=h,length=max(0,w*h*n))
   for w,h in [(3,2),(0,3),(3,0),(-2,-3)]:add(op,s,mode=mode,observe='bad-data',w=w,h=h,length=max(0,w*h*n))
  if op=='foldm':
   for size in [0,1,n-1,3*n+1]:add(op,s,length=size)
 for size in [0,1,n-1,n,n+1,11*n+3]:
  for offset in [0,1,3]:
   for op in ['slice','freeze','thaw']:
    add(op,s,offset=offset,length=size,w=2**63-1,h=-2**63)
 for offset,length in [(-1,0),(0,-1),(12,0),(11,1),(0,12),(-2**63,0),(0,2**63-1)]:
  for op in ['slice','freeze','thaw']:add(op,s,offset=offset,length=length,left=data(s,11))
 for length in [0,1,n,n+1,6*n+3]:
  for rb,tb in [(0,0),(length,length),(length+3,length+1),(length+1,length+3)]:
   add('zip',s,length=length,right=data(s,rb),third=data(s,tb))
 add('zip',s,observe='bad-data',w2=4)
 add('zip',s,observe='metadata',w2=4)
 add('zip',s,observe='metadata')
 for observe in ['data','bad-data']:
  for w2,h2 in [(3,2),(4,2),(3,3)]:
   left=data(s,6*n+4);add('eq',s,observe=observe,left=left,right=left[1:1+6*n],w2=w2,h2=h2)
 for v in ([0,0x80000000,0x7f800000,0xff800000,0x7f800001,0x7fc00000,0xffc01234] if s.endswith('f') else [0,1,bound(s)-1]):
  for z in [v,0]:add('eq',s,w=1,h=1,offset=0,length=n,left=[v]*n,right=[z]*n)
for s,n in counts.items():
 edges=[-2**63,-2147483649,-1,0,1,2147483648,2**63-1]
 for w,x,y in itertools.product(edges,repeat=3):add('base',s,w=w,h=-2**63,w2=x,h2=y,length=0,observe='bad-data')
 for w,x,y in [(2,0,0),(2,1,0),(2,2,0),(2,-1,1),(-2,2,1),(2**63-1,-(2**63-1),1),(2**63-1,2,2),(2**63-1,1,-2**63)]:
  for length in sorted({0,1,n-1,n,n+1,3*n+1}):
   for op in ['pixel','read','write']:add(op,s,w=w,h=-2**63,w2=x,h2=y,offset=3,length=length,right=data(s,n))
 if s.endswith('f'):
  for v in [0,0x80000000,0x7f800001,0x7fc00000,0xffc01234]:add('eq',s,mode='self',w=1,h=1,offset=0,length=n,left=[v]*n)
for start in range(0,len(cs),32):
 batch=cs[start:start+32]
 requests=['('+','.join(json.dumps(v,separators=(',',':')) for v in [c['op'],c['source'],c['target'],c['mode'],c['observe'],c['params'],c['left'],c['right'],c['third']])+')' for c in batch]
 lines=subprocess.check_output([a.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):c['expected']=json.loads(line)
 if start%512==0:print(f'Captured {start+len(batch)}/{len(cs)} original raster cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cs),caseHash=digest(cs))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cs)+'\n').encode(),mtime=0))
print(f'Captured {len(cs)} raster results')
