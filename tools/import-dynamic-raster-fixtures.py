#!/usr/bin/env python3
"""Original dynamic-image dispatch, palette conversion and identity observations."""
import argparse,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/dynamic-raster.jsonl.gz'
counts={'y8':1,'y16':1,'y32':1,'yf':1,'ya8':2,'ya16':2,'rgb8':3,'rgb16':3,'rgbf':3,'rgba8':4,'rgba16':4,'ycbcr8':3,'ycbcrk8':4,'cmyk8':4,'cmyk16':4}
dynamic=set(counts)-{'ycbcrk8'};paletted={'y8','rgb8','rgba8','rgb16'}
sources=['tools/import-dynamic-raster-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-dynamic-raster-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_DYNAMIC_RASTER_REFERENCE'));a=p.parse_args()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 for op in ['map','transform','eq','tag-eq','fallback','force']:assert {c['type'] for c in cs if c['op']==op}==dynamic,op
 assert {c['type'] for c in cs if c['op']=='expand'}==paletted
 assert {c['type'] for c in cs if c['op']=='palette-image'}==set(counts)
 print(f'Verified {len(cs)} original dynamic/palette captures');raise SystemExit()
if not a.oracle:p.error('--oracle must name dynamic-raster-reference')
info=json.loads(subprocess.check_output([a.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cs=[];rng=random.Random(0x44594e41)
def bound(t):return 2**32 if t in ['yf','rgbf','y32'] else 65536 if t.endswith('16') else 256
def data(t,n):return [rng.randrange(bound(t)) for _ in range(n)]
def add(op,t,mode='normal',observe='data',w=3,h=2,offset=1,length=None,size=256,po=2,pl=None,left=None,right=None):
 n=counts[t];length=6*n+2 if length is None else length;pl=256*n+1 if pl is None else pl
 cs.append(dict(name=f'{len(cs)} {op}/{t}/{mode}/{observe} {w}x{h} palette {size}',op=op,type=t,mode=mode,observe=observe,params=[w,h,offset,length,size,po,pl],left=data(t,max(0,offset+length+3)) if left is None else left,right=data(t,max(0,po+pl+3)) if right is None else right))
for t,n in counts.items():
 for size,po,length in itertools.product([-2**63,-1,0,1,17,256,2**63-1],[0,1,3],[0,1,n,n+1,7*n+2]):
  for observe in ['data','metadata']:add('palette-image',t,observe=observe,size=size,po=po,pl=length)
 for mode in ['bad-palette','bad-palette-data']:
  for observe in ['data','metadata']:add('palette-image',t,mode,observe)
 for po,pl in [(-1,0),(0,-1),(8,0),(0,8)]:add('palette-image',t,po=po,pl=pl,right=[0]*7)
 if t not in dynamic:continue
 for mode in ['normal','bad-image','bad-data','fail','empty','swap','map']:
  for observe in ['tag','metadata','data']:
   add('transform',t,mode,observe)
 for mode,observe in itertools.product(['normal','bad-image','bad-data'],['constant','metadata','data']):add('map',t,mode,observe)
 for w,h in [(0,3),(3,0),(-2,-3),(-1,2),(2**63-1,-2**63)]:
  for op in ['map','transform','fallback']:
   add(op,t,observe='metadata',w=w,h=h)
   if op!='map':add(op,t,w=w,h=h)
 for mode,observe in itertools.product(['normal','bad-image','bad-data'],['tag','metadata','data']):add('fallback',t,mode,observe)
 for mode in ['normal','self','bad-image','bad-data']:
  add('eq',t,mode,right=[0]*(6*n+2));add('tag-eq',t,mode)
 for v in ([0,0x80000000,1,0x7f800000,0x7f800001,0x7fc00000,0xffc01234] if t.endswith('f') else [0,1,bound(t)-1]):
  add('eq',t,'self',w=1,h=1,offset=0,length=n,left=[v]*n)
  for z in [v,0]:add('eq',t,w=1,h=1,offset=0,length=n,left=[v]*n,right=[z]*n)
for t in sorted(paletted):
 n=counts[t]
 for size,po in itertools.product([-2**63,-1,0,1,17,255,256,2**63-1],[0,1,3]):
  add('expand',t,w=256,h=1,offset=2,length=256,size=size,po=po,left=[99,99]+list(range(256))+[88],pl=256*n+3)
 for mode,observe in itertools.product(['normal','bad-image','bad-data','bad-palette','bad-palette-data','both-data'],['tag','metadata','data']):
  add('expand',t,mode,observe,offset=1,length=6,left=[99,0,255,1,254,127,128,88])
 for w,h in [(0,3),(3,0),(-1,2),(-2,-3),(1,-2**63),(2**63-1,0)]:
  for mode in ['bad-data','bad-palette','both-data']:
   add('expand',t,mode,'metadata',w=w,h=h,offset=0,length=0,left=[])
   if w*h<=0:add('expand',t,mode,'data',w=w,h=h,offset=0,length=0,left=[])
for t in sorted(dynamic):
 for mode,(w,h) in itertools.product(['normal','bad-image','bad-data'],[(3,2),(0,0),(-2,-3),(2**63-1,-2**63)]):add('force',t,mode,w=w,h=h)
patterns=[(sign<<31)|(exp<<23)|m for sign in [0,1] for exp in range(256) for m in [0,1,0x3fffff,0x400000,0x7ffffe,0x7fffff]]
for t in ['yf','rgbf']:
 for size in [-1,0,256]:add('palette-image',t,size=size,po=1,pl=len(patterns),right=[123]+patterns+[456])
for start in range(0,len(cs),32):
 batch=cs[start:start+32]
 req=['('+','.join(json.dumps(v,separators=(',',':')) for v in [c['op'],c['type'],c['mode'],c['observe'],c['params'],c['left'],c['right']])+')' for c in batch]
 lines=subprocess.check_output([a.oracle],input=('\n'.join(req)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):c['expected']=json.loads(line)
 if start%512==0:print(f'Captured {start+len(batch)}/{len(cs)} dynamic/palette cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cs),caseHash=digest(cs))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cs)+'\n').encode(),mtime=0));print(f'Captured {len(cs)} dynamic/palette results')
