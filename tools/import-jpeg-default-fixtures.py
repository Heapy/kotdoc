#!/usr/bin/env python3
"""Missing-DHT captures; bounded observations accompany the source-proven zero loop."""
import argparse,gzip,hashlib,json,os,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/jpeg-defaults.jsonl.gz'
sources=['tools/import-jpeg-default-fixtures.py']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-jpeg-decoder-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_JPEG_DECODER_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 print(f'Verified {len(cs)} original JPEG default-table observations');raise SystemExit()
if not args.oracle:p.error('--oracle must name jpeg-decoder-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
def w(n):return struct.pack('>H',n)
def seg(n,p):return bytes([255,n])+w(len(p)+2)+p
cases=[]
def add(kind,selector,missing,width=8,height=8,late=False):
 tables=b''
 for typ,label in [(0,'dc'),(16,'ac')]:
  if missing not in [label,'both']:tables+=seg(196,bytes([typ+min(3,selector),1])+bytes(15)+b'\0')
 header=seg(kind,bytes([8])+w(height)+w(width)+bytes([1,1,17,0]))
 def scan(start,end):return seg(218,bytes([1,1,selector*17,start,end,0]))+b'\0'
 scans=scan(0,0 if kind==194 else 63)+(scan(1,63) if kind==194 else b'')
 raw=b'\xff\xd8'+(b'' if late else tables)+header+scans+(tables if late else b'')+b'\xff\xd9'
 c=dict(name=f'{len(cases)} kind={kind} selector={selector} missing={missing} {width}x{height} late={late}',hex=raw.hex());cases.append(c)
 try:
  c['expected']=json.loads(subprocess.check_output([args.oracle],input=(raw.hex()+'\n').encode(),timeout=0.5))
  assert width==0 or height==0 or (missing=='none' and not late),c
 except subprocess.TimeoutExpired:
  assert width>0 and height>0 and (missing!='none' or late),c
  c['expected']=dict(observation='did-not-return',deadlineMillis=500)
for kind in [192,193,194]:
 for selector in [0,1,2,3,15]:
  for missing in ['none','dc','ac','both']:
   for dims in [(8,8),(0,8),(8,0)]:add(kind,selector,missing,*dims)
  add(kind,selector,'none',late=True)
  print(f'Captured {len(cases)} default-table cases',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases),proof='emptyDecoderState -> prepareHuffmanTable -> singleton 0; zero entropy and EOF bits traverse index 0 forever in huffmanPackedDecode; no native out-of-bounds reads are used')
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} JPEG default-table observations')
