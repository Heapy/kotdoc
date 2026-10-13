#!/usr/bin/env python3
"""Capture original HDR helpers with platform-specific system Float powf results."""
import argparse,gzip,hashlib,itertools,json,os,platform,struct,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_HDR_REFERENCE'));p.add_argument('--platform',choices=['macos-aarch64','linux-x86_64'],required=True);p.add_argument('--output-dir',type=Path);a=p.parse_args()
target=(a.output_dir or root/'core/src/test/resources/images')/f'hdr-{a.platform}.jsonl.gz'
sources=['tools/import-hdr-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-hdr-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def canonical(data):
 out=bytearray(data)
 for i in range(0,len(out),4):
  v=struct.unpack_from('<I',out,i)[0]
  if v&0x7f800000==0x7f800000 and v&0x007fffff:struct.pack_into('<I',out,i,0x7fc00000)
 return bytes(out)
def bits(f):return struct.unpack('<I',struct.pack('<f',f))[0]
special=[0,0x80000000,1,0x80000001,0x7fffff,0x800000,0x3f000000,0xbf000000,0x3f800000,0xbf800000,0x40000000,0xc0000000,0x7f7fffff,0xff7fffff,0x7f800000,0xff800000,0x7f800001,0x7fc00000,0xffc01234]
patterns=[(s<<31)|(e<<23)|m for s in [0,1] for e in range(256) for m in [0,1,0x3fffff,0x400000,0x7ffffe,0x7fffff]]
def generated(kind):
 if kind=='patterns':return [patterns[(i+c*257)%len(patterns)] for i in range(len(patterns)) for c in range(3)]
 if kind=='lcg':
  state=0x484452;out=[]
  for i in range(100002):
   state=(state*1664525+1013904223)&0xffffffff;out.append((state&0x7fffffff)%0x7f800000)
  return out
 if kind=='cube':return list(itertools.chain.from_iterable(itertools.product(special,repeat=3)))
 raise ValueError(kind)
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs) and m['platform']==a.platform
 assert {c['op'] for c in cs}=={'gamma','tone','tone-pixels'}
 print(f'Verified {len(cs)} original HDR captures for {a.platform}');raise SystemExit()
if not a.oracle:p.error('--oracle must name hdr-reference')
actual=('macos-aarch64' if sys.platform=='darwin' and platform.machine() in ['arm64','aarch64'] else 'linux-x86_64' if sys.platform.startswith('linux') and platform.machine() in ['x86_64','amd64'] else '')
assert actual==a.platform,(actual,a.platform)
info=json.loads(subprocess.check_output([a.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cs=[]
def add(op,param,mode='normal',observe='data',w=3,h=2,offset=0,length=None,values=None,kind='values'):
 if values is None:values=[bits(v) for v in [1,2,3,4,5,6,-1,-2,-3,0.1,0.2,0.3,0,0,0,17,31,7,99,-8]]
 length=len(values)-offset if length is None else length
 cs.append(dict(name=f'{len(cs)} {op}/{mode}/{observe}/{kind} {w}x{h} param {param:08x}',op=op,mode=mode,observe=observe,params=[w,h,param,offset,length],values=values,kind=kind))
params=sorted(set(special+[bits(v) for v in [1.1,1.5,2.2,3,0.3,17]]))
for op,param,kind in itertools.product(['gamma','tone','tone-pixels'],params,['patterns','cube']):
 values=generated(kind);add(op,param,w=len(values)//3,h=1,length=len(values),values=[],kind=kind)
for param in [bits(v) for v in [0.5,1,1.1,1.5,2,2.2,3,0.3,17]]:
 for op in ['gamma','tone-pixels']:add(op,param,w=33334,h=1,length=100002,values=[],kind='lcg')
for op,param in itertools.product(['gamma','tone'],params):
 for mode,observe in itertools.product(['normal','bad-image','bad-data','bad-parameter'],['metadata','data']):add(op,param,mode,observe)
 for w,h in [(0,0),(0,3),(3,0),(-2,1),(1,-2),(-2,-3),(2**63-1,0),(1,-2**63)]:
  for observe in ['metadata','data']:
   if op=='gamma' and w<0 and h<0 and w*h>0 and observe=='data':continue
   # Gamma negative-height with odd 3-component count has negative allocation.
   add(op,param,observe=observe,w=w,h=h,length=0 if w*h==0 else 20)
 for offset,length,w,h in [(1,18,3,2),(2,19,3,2),(3,17,2,2),(20,0,3,2),(0,20,-2,-3),(0,20,0,0)]:
  if op=='gamma' and (w<0 and h<0 or w>0 and h>0 and length<w*h*3):continue
  add(op,param,offset=offset,length=length,w=w,h=h)
 for offset,length in [(-1,0),(0,-1),(21,0),(0,21)]:
  for observe in ['metadata','data']:add(op,param,observe=observe,offset=offset,length=length)
 for values in itertools.product([0,0x80000000,0x7f800000,0xff800000,0x7fc00000,bits(1)],repeat=2):
  # Two pixels with opposite max/NaN/zero order, plus a nonpixel tail.
  add(op,param,w=2,h=1,length=7,values=[values[0]]*3+[values[1]]*3+[bits(7)])
cache={}
for start in range(0,len(cs),4):
 batch=cs[start:start+4];req=[]
 for c in batch:
  if c['kind']=='values':values=c['values']
  else:
   if c['kind'] not in cache:cache[c['kind']]=generated(c['kind'])
   values=cache[c['kind']]
  req.append('('+','.join(json.dumps(v,separators=(',',':')) for v in [c['op'],c['mode'],c['observe'],c['params'],values])+')')
 lines=subprocess.check_output([a.oracle],input=('\n'.join(req)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line)
  if 'hex' in expected:
   data=bytes.fromhex(expected['hex']);expected['sha256']=hashlib.sha256(data).hexdigest();expected['length']=len(data);expected['canonicalSha256']=hashlib.sha256(canonical(data)).hexdigest()
   if c['kind']!='values':del expected['hex']
  c['expected']=expected
 if start%512==0:print(f'Captured {start+len(batch)}/{len(cs)} {a.platform} HDR cases',flush=True)
manifest=dict(sources=hashes,oracle=info,platform=a.platform,system=[sys.platform,platform.machine(),platform.release(),platform.libc_ver()],cases=len(cs),caseHash=digest(cs))
target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cs)+'\n').encode(),mtime=0));print(f'Captured {len(cs)} HDR results for {a.platform}')
