#!/usr/bin/env python3
"""Independent full decodeJpeg captures, including original upstream JPEG files."""
import argparse,gzip,hashlib,json,os,random,struct,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/jpeg-decoder.jsonl.gz'
sources=['tools/import-jpeg-decoder-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-jpeg-decoder-reference','vendor/JuicyPixels-jpeg-tests-v3.3.8']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_JPEG_DECODER_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 print(f'Verified {len(cs)} original complete JPEG decoding captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name jpeg-decoder-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']))
assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[]
def add(data,name):cases.append(dict(name=f'{len(cases)} {name}',hex=data.hex()))
def word(n):return struct.pack('>H',n)
def seg(code,data):return bytes([255,code])+word(len(data)+2)+data
def sof(code,w,h,components):return seg(code,bytes([8])+word(h)+word(w)+bytes([len(components)])+b''.join(bytes(c) for c in components))
def sos(ids,start=0,end=63,high=0,low=0,table=0):return seg(218,bytes([len(ids)])+b''.join(bytes([i,table]) for i in ids)+bytes([start,end,high*16+low]))
def huff(dc=0,ac=0,dest=0):
 return seg(196,bytes([dest,1])+bytes(15)+bytes([dc])+bytes([16+dest,1])+bytes(15)+bytes([ac]))
def quant(q=8,dest=0):return seg(219,bytes([dest])+bytes([q])*64)
def jpeg(w=8,h=8,ids=(1,2,3),sampling=None,kind=192,dc=0,ac=0,q=8,data=b'\0',adobe=None,table=0,restart=None,separate=False):
 if sampling is None:sampling=[17]*len(ids)
 components=list(zip(ids,sampling,[min(table,3)]*len(ids)))
 prefix=b'\xff\xd8'
 if adobe is not None:prefix+=seg(238,b'Adobe'+word(100)+word(0)+word(0)+bytes([adobe]))
 prefix+=quant(q,min(table,3))+huff(dc,ac,min(table,3))+sof(kind,w,h,components)
 if restart is not None:prefix+=seg(221,word(restart))
 body=b''
 groups=[[i] for i in ids] if separate else [ids]
 for group in groups:body+=sos(group,0,0 if kind==194 else 63,table=table*17)+data
 if kind==194:
  for i in ids:body+=sos([i],1,63,table=table*17)+data
 return prefix+body+b'\xff\xd9'
for kind in [192,193,194]:
 for ids in [(1,),(1,2),(1,2,3),(0,1,2),tuple(b'RGB'),tuple(b'YCc'),tuple(b'CMYK'),tuple(b'YCcK'),tuple(b'YCcA'),tuple(b'RGBA'),(1,2,3,4),(1,2,3,4,5)]:
  for adobe in [None,0,1,2]:
   add(jpeg(ids=ids,kind=kind,adobe=adobe,dc=5,q=255,data=b'\xaa\x55'),'color selection')
for kind in [192,193,194]:
 for layout in [[17],[17,17,17],[33,17,17],[18,17,17],[34,17,17],[50,17,17],[17]*4]:
  for w,h in [(1,1),(7,9),(8,8),(9,17),(17,25),(33,19),(49,33)]:
   for separate in [False,True]:
    for restart in [None,0,1,2,7]:
     add(jpeg(w,h,ids=tuple(range(1,len(layout)+1)),sampling=layout,kind=kind,dc=4,ac=1,data=b'\xaa\x55\xff\0\xff\xd0\x80'*8,restart=restart,separate=separate),'MCU sampling/restart')
for table in range(16):
 for kind in [192,194]:add(jpeg(kind=kind,table=table,dc=7),'clamped table selectors')
# Parser failures, missing/duplicate/unsupported frame kinds and lazy errors.
for data in [b'',b'\xff',b'\xff\xd8',b'\xff\xd8\xff\xd9',b'\xff\xd8\xff\xda\0\x02',b'bad']:
 add(data,'invalid prefix/kind')
for kind in [192,194,195,197,198,199,201,202,203,205,206,207,208]:
 base=jpeg(kind=kind)
 if kind in [192,194]:
  add(base[:-2]+sof(kind,8,8,[(1,17,0)])+b'\xff\xd9','duplicate DCT header')
 else:add(base,'unsupported SOF')
for kind in [192,194]:
 base=jpeg(kind=kind)
 for n in range(len(base)+1):add(base[:n],f'complete file truncation {kind} {n}')
 for empty in [sof(kind,0,0,[]),sof(kind,0,0,[(1,17,0)])]:add(b'\xff\xd8'+empty+b'\xff\xd9','empty image')
 # Oversubscribed replaced tables stay lazy. Once a parameter is observed,
 # its two strict tree fields are forced, including refinement-only scans.
 bad=seg(196,bytes([0,3])+bytes(15)+bytes([0,1,2]))
 for pos in ['before','after']:
  add(base[:2]+bad+base[2:] if pos=='before' else base[:-2]+bad+b'\xff\xd9','replaced/unused invalid table')
 add(b'\xff\xd8'+bad+sof(kind,8,8,[(1,17,0)])+sos([1],0,0,1,0)+b'\x55\xff\xd9','selected malformed tree')
 for ids in [tuple(b'YCc'),tuple(b'RGBA'),(1,2,3,4,5)]:
  add(b'\xff\xd8'+bad+sof(kind,8,8,[(i,17,0) for i in ids])+sos(ids,0,0,1,0)+b'\x55\xff\xd9','invalid color after selected bad tree')

# Noncanonical baseline run/size (r,0) reaches decodeInt(0). A second EOB
# leaf stops these streams before any original unchecked write escapes 64 entries.
for run in range(1,15):
 table=seg(196,bytes([0,1])+bytes(15)+bytes([0])+bytes([16,2])+bytes(15)+bytes([run*16,0]))
 for payload in [b'\x10',b'\x30']:
  for q in [1,8,255]:
   add(b'\xff\xd8'+quant(q)+table+sof(192,8,8,[(1,17,0)])+sos([1])+payload+b'\xff\xd9','zero-width baseline AC coefficient')
for kind in [192,194]:
 for dest in range(4,16):
  base=jpeg(kind=kind)
  add(base[:2]+quant(8,dest)+base[2:],'out-of-range quantization update')
  for dc in [0,16]:
   bad=seg(196,bytes([dc+dest,3])+bytes(15)+bytes([0,1,2]))
   add(base[:2]+bad+base[2:],'ignored out-of-range Huffman destination')
 add(b'\xff\xd8'+sof(kind,8,8,[(1,17,0)])+sos([2])+b'\0\xff\xd9','unknown component selector')
 add(b'\xff\xd8'+sos([1])+b'\0'+sof(kind,8,8,[(1,17,0)])+b'\xff\xd9','scan before frame')
# Stable random entropy with complete valid scan/component setup.
rng=random.Random(0x4445434f)
for n in range(200):
 count=rng.choice([1,3,4]);payload=bytes(rng.randrange(255) for _ in range(rng.randrange(1,64)))
 add(jpeg(w=rng.randrange(1,40),h=rng.randrange(1,30),ids=tuple(range(1,count+1)),kind=rng.choice([192,193,194]),dc=rng.randrange(16),ac=rng.choice([0,1,2,3]),q=rng.randrange(1,256),data=payload,adobe=rng.choice([None,0,1,2])),'seeded complete image')
for start in range(0,len(cases),25):
 batch=cases[start:start+25]
 lines=subprocess.check_output([args.oracle],input=('\n'.join(c['hex'] for c in batch)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):c['expected']=json.loads(line)
 if start%250==0:print(f'Captured {start+len(batch)}/{len(cases)} synthetic',flush=True)
with tempfile.TemporaryDirectory() as tmp:
 for file in sorted((root/'vendor/JuicyPixels-jpeg-tests-v3.3.8/tests/jpeg').glob('*.jpg')):
  c=dict(name=f'{len(cases)} original {file.name}',source=str(file.relative_to(root)));cases.append(c)
  out=Path(tmp)/'pixels'
  result=json.loads(subprocess.check_output([args.oracle,'--file',str(file),str(out)]))
  if 'color' in result:
   pixels=out.read_bytes();result['pixelHash']=hashlib.sha256(pixels).hexdigest();result['pixelLength']=len(pixels)
  c['expected']=result
  print(f'Captured {file.name}: {result}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} complete JPEG decode results')
