#!/usr/bin/env python3
"""Capture original progressive JPEG pixel unpacking with explicit scan inputs."""
import argparse,copy,gzip,hashlib,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/jpeg-progressive.jsonl.gz'
sources=['tools/import-jpeg-progressive-fixtures.py']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-jpeg-progressive-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_JPEG_PROGRESSIVE_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 print(f'Verified {len(cs)} original progressive JPEG captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name jpeg-progressive-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']))
assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[]
def leaf(value):return [0x4000|value]*2
def branch(a,b):return [0x4000|a,2,0x4000|b,0x4000|b]
def params(component=0,restart=-1,w=1,h=1,start=0,end=0,low=0,high=0,reader=0,order=0,block=0,dc=1,ac=1):
 return [leaf(dc) if isinstance(dc,int) else dc,leaf(ac) if isinstance(ac,int) else ac,[component,restart,w,h,1,1,start,end,low,high,reader,order,block,0,0]]
def add(name,scans=(),dims=(8,8,1,1),components=((1,1,1,0),),quants=None):
 cases.append(dict(name=f'{len(cases)} {name}',dimensions=list(dims),components=[list(c) for c in components],quants=quants if quants is not None else [[8]*64 for _ in range(4)],scans=copy.deepcopy(scans)))
patterns=[[],[0],[255,0],[85],[170],list(range(32)),[255,208,255,0,128,255,209,1],list(range(255,-1,-1))]
# All four coefficient modes, Int16 narrowing, EOF and marker/stuffing transitions.
for dc in list(range(17))+[31,32,63,64,127,255]:
 for low in [0,1,2,7,14,15,16,31,32,63]:
  for bs in patterns[:5]:
   add('DC first/refine',[[[params(dc=dc,low=low)],bs],[[params(reader=1,low=max(0,low-1),high=1)],bs]],dims=(17,9,1,1))
# Every run/size code; a one-coefficient range allows runs past its end but
# never beyond the original allocation. Larger dense ranges use run=0 only.
for ac in range(256):
 for low in [0,1,3,7,15]:
  for bs in patterns[:4]:
   add('AC first/refine symbol',[[[params(start=1,end=1,low=low,ac=ac)],bs],[[params(start=1,end=20,low=max(0,low-1),high=1,reader=1,ac=ac)],bs]],dims=(8,8,1,1))
for start,end in [(1,63),(1,1),(16,16),(32,47),(63,63),(20,19)]:
 for ac in [0,1,2,7,15,16,32,112,224,240,branch(1,0),branch(0,2)]:
  # Nonzero run + size is absent here, so writes cannot exceed index 63.
  for bs in patterns:
   scans=[[[params(start=start,end=end,low=2,ac=ac)],bs],[[params(reader=1,start=start,end=end,low=1,high=2,ac=ac)],bs],[[params(reader=2,start=start,end=end,low=0,high=1,ac=ac)],bs]]
   add('spectral ranges and EOB continuation',scans,dims=(25,17,1,1))
# Component order, per-row work reuse, clipped right/bottom blocks, and restart
# state across scans. Include nonstandard sampling factors used by the original.
layouts=[[(1,1)],[(2,1)],[(1,2)],[(2,2)],[(1,1)]*2,[(1,1)]*3,[(2,2),(1,1),(1,1)],[(2,1),(1,1),(1,1)],[(1,2),(1,1),(1,1)],[(3,2),(1,1),(1,1)],[(1,1)]*4]
for layout in layouts:
 maxiW=max(w for w,h in layout);maxiH=max(h for w,h in layout)
 components=[[i+1,w,h,i%4] for i,(w,h) in enumerate(layout)]
 for width,height in [(0,0),(0,9),(9,0),(1,1),(7,9),(8,8),(9,17),(17,25),(33,19)]:
  for restart in [-1,0,1,2,3,7,2**63-1]:
   scans=[]
   for mode in range(4):
    ps=[]
    for i,(w,h) in enumerate(layout):
     for y in range(h):
      for x in range(w):
       ps.append(params(i,restart,w,h,start=0 if mode<2 else 1,end=0 if mode<2 else 63,low=1 if mode in [0,2] else 0,high=mode%2,reader=mode,order=1,block=y*w+x,dc=branch(1,3),ac=branch(0,1)))
    scans.append([ps,patterns[(width+height+mode)%len(patterns)]*4])
   add('sampling/restarts/interleaved',scans,[width,height,maxiW,maxiH],components)
   # Separate scans use the solo (visible blocks only) line index vector.
   solo=[]
   for i,(w,h) in enumerate(layout):
    for mode in range(4):
     ps=[params(i,restart,w,h,start=0 if mode<2 else 1,end=0 if mode<2 else 63,low=1 if mode in [0,2] else 0,high=mode%2,reader=len(solo),order=0,block=y*w+x,dc=branch(1,3),ac=branch(0,1)) for y in range(h) for x in range(w)]
     solo.append([ps,patterns[(width+height+i+mode)%len(patterns)]*4])
   add('sampling/restarts/noninterleaved',solo,[width,height,maxiW,maxiH],components)
# Quantization tables and table selector clamping, including signed overflow.
for q in [0,1,2,16,255,32767,-32768,-1]:
 for selector in [0,1,2,3,4,255]:
  quants=[[((q+i+32768)%65536)-32768]*64 for i in range(4)]
  add('quantization',[[[params(dc=7)],patterns[7]],[[params(reader=1,start=1,end=63,ac=1)],patterns[7]]],components=[[1,1,1,selector]],quants=quants)
# Scan-wide selection is taken from the first parameter. Reader indices can be
# shared/reordered and restart interval comes only from the first scan.
for mode in range(4):
 for restart in [-1,0,1,2]:
  p0=params(start=0 if mode<2 else 1,end=1,high=mode%2,restart=restart)
  p1=params(component=1,start=1,end=1,high=1-mode%2,reader=1,restart=17)
  scans=[[[p0,p1],patterns[7]],[[params(component=1,reader=0,start=1,end=1,high=1,restart=99)],patterns[6]]]
  add('first parameter selects strategy',scans,dims=(17,17,1,1),components=[[1,1,1,0],[2,1,1,0]])
# Original checked failures; avoid unchecked vector memory accesses.
add('empty scans')
add('no components',components=[])
add('empty scan parameters',[[[],[]]])
for field,value in [(10,-1),(10,1),(11,-1),(11,2),(0,1),(12,-1)]:
 ps=params();ps[2][field]=value;add('checked invalid parameter',[[[ps],[]]])
add('missing quantization table',[[[params()],[]]],quants=[])
# Zero dimensions/sampling expose which derived sizes the lazy original forces.
for width,height in [(0,0),(0,8),(8,0),(8,8)]:
 for mw,mh in [(0,0),(0,1),(1,0),(1,1)]:
  for comps in [[],[[1,0,0,0]],[[1,1,1,0]]]:
   add('zero sampling/dimensions',dims=(width,height,mw,mh),components=comps)
# Deterministic multi-scan mixtures with nonzero coefficients and refinement.
rng=random.Random(0x50524f47)
for n in range(400):
 count=rng.randrange(1,5);scans=[]
 for mode in range(rng.randrange(1,9)):
  start,end=(0,0) if mode==0 or rng.randrange(3)==0 else (rng.randrange(1,32),rng.randrange(32,64))
  high=0 if mode==0 else rng.randrange(3)
  ps=[params(component=i,start=start,end=end,low=rng.randrange(5),high=high,reader=mode,restart=rng.choice([-1,1,2,4]),dc=branch(0,rng.randrange(1,12)),ac=branch(0,rng.choice([1,2,3,16,32,240]))) for i in range(count)]
  scans.append([ps,[rng.randrange(256) for _ in range(rng.randrange(64))]])
 add('seeded scans',scans,dims=(rng.randrange(1,34),rng.randrange(1,26),1,1),components=[[i+1,1,1,i] for i in range(count)],quants=[[rng.randrange(1,65) for _ in range(64)] for _ in range(4)])
def request(c):
 scans='['+','.join('(['+','.join('('+','.join(map(str,p))+')' for p in ps)+'],'+str(bs)+')' for ps,bs in c['scans'])+']'
 return '('+','.join([str(c['dimensions']),str(c['components']),str(c['quants']),scans])+')'
for start in range(0,len(cases),25):
 batch=cases[start:start+25]
 result=subprocess.check_output([args.oracle],input=('\n'.join(map(request,batch))+'\n').encode()).splitlines();assert len(result)==len(batch)
 for c,line in zip(batch,result):c['expected']=json.loads(line)
 if start%500==0:print(f'Captured {start+len(batch)}/{len(cases)}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} original progressive results')
