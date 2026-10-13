#!/usr/bin/env python3
"""Capture original Pandoc/JuicyPixels TIFF metadata, never using Kotlin output."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import random
import struct
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
target = root/'core/src/test/resources/images/tiff-metadata.jsonl.gz'
sources = ['tools/import-tiff-metadata-fixtures.py',
 'upstream/pandoc/src/Text/Pandoc/ImageSize.hs',
 'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Image.hs',
 'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/ImageSize.hs',
 'vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','vendor/JuicyPixels-tiff-tests-v3.3.8']:
 sources += sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file())
hashes = {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):
 return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:
  manifest=json.loads(next(f)); cases=[json.loads(line) for line in f]
 assert manifest['sources']==hashes
 assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases)
 for c in cases:
  if 'source' in c: assert hashlib.sha256((root/c['source']).read_bytes()).hexdigest()==c['sourceHash']
 print(f'Verified {len(cases)} independent TIFF metadata captures')
 raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'

def entry(tag,typ,value=0,count=None,raw=None):
 return dict(tag=tag,type=typ,value=value,count=count,raw=raw)
def base():
 return [entry(256,4,320),entry(257,4,240),entry(258,3,8),entry(259,3,1),entry(262,3,1),
         entry(273,4,0xffffffff),entry(277,3,1),entry(278,4,240),entry(279,4,0xffffffff)]
def replace(entries,*changes):
 tags={e['tag'] for e in changes}
 return [e for e in entries if e['tag'] not in tags]+list(changes)
def tiff(entries=None,endian='<',next_ifd=0):
 entries=base() if entries is None else entries
 body=bytearray(); rows=[]; start=8+2+12*len(entries)+4
 for e in entries:
  tag,typ,value=e['tag'],e['type'],e['value']; count=e['count']
  if e['raw'] is not None:
   raw=e['raw'];count=1 if count is None else count
  else:
   if typ in [5,10]:
    encoded=struct.pack(endian+('ii' if typ==10 else 'II'),*value);n=1
   elif isinstance(value,bytes): encoded=value;n=len(value)
   else:
    values=value if isinstance(value,list) else [value];n=len(values)
    encoded=struct.pack(endian+{1:'B',3:'H',4:'I',6:'b',8:'h',9:'i',11:'f',12:'d'}.get(typ,'I')*n,*values)
   count=n if count is None else count
   if len(encoded)<=4: raw=struct.unpack(endian+'I',encoded.ljust(4,b'\0'))[0]
   else: raw=start+len(body);body+=encoded
  rows.append(struct.pack(endian+'HHII',tag,typ,count,raw))
 return (b'II' if endian=='<' else b'MM')+struct.pack(endian+'HIH',42,8,len(entries))+b''.join(rows)+struct.pack(endian+'I',next_ifd)+body

cases=[]
def add(name,data,source=None,exception=False):
 c=dict(name=name,hex=data.hex())
 if source:c.update(source=source,sourceHash=hashlib.sha256(data).hexdigest())
 if exception:c['expectException']='divide by zero'
 cases.append(c)
for p in sorted((root/'vendor/JuicyPixels-tiff-tests-v3.3.8/tests/tiff').rglob('*')):
 if p.suffix.lower() in ['.tif','.tiff']:
  add('original '+str(p.relative_to(root)),p.read_bytes(),str(p.relative_to(root)))

seeds=[]
for end in ['<','>']:
 seeds += [tiff(endian=end), tiff(replace(base(),entry(258,3,[8,8,8]),entry(262,3,2)),end),
           tiff(base()+[entry(282,5,(300,2)),entry(283,5,(601,3)),entry(296,3,2)],end),
           tiff(base()+[entry(305,2,b'long software name\0'),entry(65000,7,b'unknown bytes')],end)]
 for tag in [256,257,262,277,278,258,259,279,273]:
  add(f'missing {tag} {end}',tiff([e for e in base() if e['tag']!=tag],end))
 for left in range(10):add(f'first {left} required tags {end}',tiff(base()[:left],end))
 for tag,values in [(262,range(12)),(284,range(5)),(339,range(7)),(259,[0,1,2,3,4,5,6,7,32773,65535]),(317,range(5))]:
  for value in values:
   add(f'field {tag} value {value} {end}',tiff(replace(base(),entry(tag,3,value)),end))
 for color in [0,1,2,3,4,5,6,8]:
  for depth in [1,2,4,6,8,10,12,14,16,24,32,64]:
   for components in [1,2,3,4]:
    for fmt in [[],[1],[2],[3],[4],[1,3]]:
     es=replace(base(),entry(262,3,color),entry(258,3,[depth]*components),entry(339,3,fmt),entry(277,3,components))
     if color==3: es+=[entry(320,3,[0,0,0,65535,65535,65535])]
     add(f'color {color} depth {depth} components {components} format {fmt} {end}',tiff(es,end))
 for w,h in [(0,0),(0,1),(1,0),(0xffffffff,0xffffffff)]:
  add(f'dimensions {w} {h} {end}',tiff(replace(base(),entry(256,4,w),entry(257,4,h),entry(278,4,1)),end))
 for tag in [256,257]:
  for typ in range(1,13):
   for count in [0,1,2]:
    add(f'dimension raw type {tag} {typ} {count} {end}',tiff(replace(base(),entry(tag,typ,count=count,raw=0x12345678)),end))
 for rows in [0,1,0xffffffff]:
  add(f'rows {rows} {end}',tiff(replace(base(),entry(278,4,rows)),end))
 for palette in [[],[0],[0,1],[0,1,2],[0,1,2,3]]:
  add(f'palette shape {palette} {end}',tiff(replace(base(),entry(262,3,3))+[entry(320,3,palette)],end))
 for planar in [0,1,2]:
  for subsampling in [[],[1],[2,2],[0,0],[65535,65535]]:
   add(f'YCbCr {planar} {subsampling} {end}',tiff(replace(base(),entry(262,3,6),entry(258,3,[8,8,8]))+[entry(284,3,planar),entry(530,3,subsampling)],end))
 for unit_type in [3,4]:
  for unit in [0,1,2,3,4]:
   for x,y in [((0,1),(0,1)),((301,2),(599,3)),((0xffffffff,1),(1,0xffffffff))]:
    add(f'dpi {unit_type} {unit} {x} {y} {end}',tiff(base()+[entry(282,5,x),entry(283,5,y),entry(296,unit_type,unit)],end))
 for tag in [282,283]:
  for typ in [1,3,4,5,10]:
   value=(100,1) if typ in [5,10] else 100
   add(f'dpi representation {tag} {typ} {end}',tiff(base()+[entry(tag,typ,value),entry(296,3,2)],end))
 for units in [(2,3),(3,2),(0,2)]:
  for ratios in [((900,2),(300,1)),((300,1),(900,2))]:
   add(f'duplicate dpi {units} {ratios} {end}',tiff(base()+[entry(282,5,r) for r in ratios]+[entry(296,3,u) for u in units],end))
 # Equal offsets are stable and the shared cursor makes the second rational
 # absent after consuming the first. Reverse file order to change the axis.
 for tags in [(282,283),(283,282)]:
  start=8+2+12*12+4
  b=tiff(base()+[entry(t,5,count=1,raw=start) for t in tags]+[entry(296,3,2)],end)+struct.pack('<II',300,1)
  add(f'shared rational offset {tags} {end}',b)
 for tag in [258,273,279,339,530,65000]:
  for typ in range(1,13):
   for count in [0,1,2,3,5]:
    raw= b'\x80\x22\x00\xff\x39'[:count] if typ in [2,7] else ((-2,-3) if typ==10 else (2,3)) if typ in [5,10] else [1]*count
    add(f'extended {tag} type {typ} count {count} {end}',tiff(replace(base(),entry(tag,typ,raw,count=count)),end))
 for type_id in [0,13,255,65535]:
  add(f'invalid type {type_id} {end}',tiff(base()+[entry(65000,type_id,raw=0)],end))
 for offset in [0,1,7,8,10,31,100,121,122,0xffffffff]:
  add(f'extended overlap {offset} {end}',tiff(base()+[entry(65000,2,count=8,raw=offset)],end))
 for tag in [256,257,262,258,296]:
  for values in [(1,2),(2,1),(65535,1)]:
   for typ in [3,4]:
    add(f'duplicate {tag} {values} {typ} {end}',tiff(base()+[entry(tag,typ,v) for v in values],end))
 for next_ifd in [0,1,8,0xffffffff]:
  add(f'ignored next IFD {next_ifd} {end}',tiff(endian=end,next_ifd=next_ifd))
 # Header offsets smaller than eight are not rewinds in Data.Binary.Get.skip.
 for offset in [0,1,7,9,10,0xffffffff]:
  b=tiff(endian=end);b=b[:4]+struct.pack(end+'I',offset)+b[8:]
  add(f'header offset {offset} {end}',b)
 # Exif subdirectories share the forward cursor. A later field may overlap them.
 for depth in [1,2,10,100,5000]:
  start=8+2+12*10+4
  b=tiff(base()+[entry(34665,4,raw=start)],end)
  for i in range(depth):
   rows=[entry(34665,4,raw=start+(i+1)*18)] if i+1<depth else [entry(65000,3,4)]
   b+=tiff(rows,end)[8:]
  add(f'nested Exif {depth} {end}',b)
 for typ in [2,3,4,5,7,10]:
  for malformed in [False,True]:
   start=8+2+12*10+4
   count=1 if typ in [5,10] else 8
   payload=struct.pack('<II',300,1) if count==1 else bytes(range(count*({3:2,4:4}.get(typ,1))))
   b=tiff(base()+[entry(34665,4,raw=start)],end)+tiff([entry(65000,typ,count=count,raw=start+18)],end)[8:]+payload
   if malformed:b=b[:-1]
   add(f'subIFD extended type {typ} truncated {malformed} {end}',b)
for i,b in enumerate(seeds):
 add(f'parser seed {i}',b)
 for n in range(4,len(b)): add(f'truncated seed {i} at {n}',b[:n])
# Bytes and signed rational Show used in optional-tag errors.
for byte in range(256):
 add(f'error string byte {byte}',tiff(base()+[entry(339,2,bytes([byte])+b'9')]))
for tag in [282,283]:
 for unit in [1,2,3]:
  add(f'division by zero {tag} unit {unit}',tiff(base()+[entry(tag,5,(300,0)),entry(296,3,unit)]),exception=unit!=1)
# Permute fields and mutate offsets/types without giant count allocations in the oracle.
rng=random.Random(0x54494646)
for i in range(1000):
 es=base();rng.shuffle(es)
 for _ in range(rng.randrange(1,4)):
  ix=rng.randrange(len(es));e=es[ix].copy()
  if i%2:e['raw']=rng.choice([0,1,2,7,8,16,255,65535,0xffffffff])
  else:e['type']=rng.randrange(14);e['raw']=rng.randrange(256)
  es[ix]=e
 b=tiff(es,rng.choice(['<','>']))
 if i%3==0:b=b[:rng.randrange(4,len(b)+1)]
 # Avoid the original nonterminating rowPerStrip=height=0 recursion.
 if any(e['tag']==278 and e['raw']==0 for e in es) and any(e['tag']==257 and e['raw']==0 for e in es):continue
 add(f'seeded mutation {i}',b)

with tempfile.TemporaryDirectory() as directory:
 temp=Path(directory);request=temp/'request.json';script=temp/'tiff.lua'
 script.write_text('''
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_TIFF_REQUEST')))
 local cases=pandoc.json.decode(f:read('*a'),false);f:close()
 local rows={}
 for _,c in ipairs(cases) do
  local bytes=c.hex:gsub('..',function(p) return string.char(tonumber(p,16)) end)
  local ok,r=pcall(pandoc.image.size,bytes)
  local row={}
  if ok then row.size={tostring(r.width),tostring(r.height),tostring(r.dpi_horz),tostring(r.dpi_vert)}
  else row.error=tostring(r) end
  rows[#rows+1]=row
 end
 return pandoc.json.encode(rows)
end
''')
 def run(rows):
  request.write_text(json.dumps(rows))
  return subprocess.run(['pandoc','-f','native','-t',str(script)],input=b'[]',capture_output=True,env={**os.environ,'KOTDOC_TIFF_REQUEST':str(request)},timeout=60)
 normal=[c for c in cases if 'expectException' not in c]
 # Batches keep oracle memory bounded; progress also identifies a failing group.
 for start in range(0,len(normal),250):
  batch=normal[start:start+250];result=run(batch)
  if result.returncode: raise RuntimeError((start,result.stderr.decode()))
  rows=json.loads(result.stdout);assert len(rows)==len(batch)
  for c,r in zip(batch,rows):
   c.update(r)
   if 'error' in r:c['tiffError']=r['error'].split('\nTiff ',1)[1].rsplit('\nTGA ',1)[0]
  print(f'Captured {min(start+250,len(normal))}/{len(normal)} TIFF outcomes',flush=True)
 for c in cases:
  if 'expectException' in c:
   r=run([c]);assert r.returncode!=0 and c['expectException'] in r.stderr.decode()
   c['exception']=r.stderr.decode();del c['expectException']
  if 'source' in c:del c['hex']
manifest=dict(sources=hashes,oracle='pandoc 3.12.1',cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} TIFF cases: {sum("size" in c for c in cases)} sizes, {sum("error" in c for c in cases)} decoder errors, {sum("exception" in c for c in cases)} exceptions')
