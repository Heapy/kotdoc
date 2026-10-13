#!/usr/bin/env python3
"""Capture unchanged JuicyPixels PNG decoder outputs, never Kotlin expectations."""
import argparse,gzip,hashlib,itertools,json,os,random,struct,subprocess,zlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/png-decoder.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_PNG_DECODER_REFERENCE'));a=p.parse_args()
sources=['tools/import-png-decoder-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','vendor/JuicyPixels-png-tests-v3.3.8','vendor/zlib-0.7.1.1','tools/haskell-png-decoder-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 corpus={str(p.relative_to(root)) for p in (root/'vendor/JuicyPixels-png-tests-v3.3.8/tests/pngsuite').glob('*.png')}
 assert {c['source'] for c in cs if 'source' in c}==corpus
 for c in cs:
  if 'source' in c:assert hashlib.sha256((root/c['source']).read_bytes()).hexdigest()==c['sourceHash']
 print(f'Verified {len(cs)} original PNG decoder captures, including all {len(corpus)} PNGSuite files');raise SystemExit()
if not a.oracle:p.error('--oracle must name png-decoder-reference')
info=json.loads(subprocess.check_output([a.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0','zlib','0.7.1.1']
observations=['either','palette-tag','palette-data','dynamic-tag','dynamic-data','metadata']
cs=[]
def add(name,data,observe=observations,source=None):
 for mode in observe:
  c=dict(name=name+' / '+mode,observe=mode)
  if source:c.update(source=source,sourceHash=hashlib.sha256(data).hexdigest())
  else:c['hex']=data.hex()
  cs.append(c)
def word(v):return struct.pack('>I',v)
def chunk(t,b):return word(len(b))+t+b+word(zlib.crc32(t+b))
def header(w,h,depth,color,interlace=0,compression=0,filter=0,length=13,crc=0):
 return b'\x89PNG\r\n\x1a\n'+word(length)+b'IHDR'+struct.pack('>IIBBBBB',w,h,depth,color,compression,filter,interlace)+word(crc)
def png(data,w=3,h=2,depth=8,color=2,interlace=0,palette=None,extra=b'',split=None,**kw):
 encoded=zlib.compress(data)
 if split is None:idat=chunk(b'IDAT',encoded)
 else:idat=b''.join(chunk(b'IDAT',encoded[i:j]) for i,j in zip([0]+split,split+[len(encoded)]))
 return header(w,h,depth,color,interlace,**kw)+(chunk(b'PLTE',palette) if palette is not None else b'')+idat+extra+chunk(b'IEND',b'')
for path in sorted((root/'vendor/JuicyPixels-png-tests-v3.3.8/tests/pngsuite').glob('*.png')):
 # These invalid headers make the native unsafe unpacker leave components
 # uninitialized (depth 0), or overrun scanlines/storage (depth 99).
 observe=['either','palette-tag','metadata'] if path.name in ['xd0n2c08.png','xd9n2c08.png'] else observations
 add('upstream '+path.name,path.read_bytes(),observe=observe,source=str(path.relative_to(root)))
palette=bytes((i*71+3)%256 for i in range(768))
adam=[(0,0,8,8),(4,0,8,8),(0,4,4,8),(2,0,4,4),(0,2,2,4),(1,0,2,2),(0,1,1,2)]
def scanlines(w,h,depth,color,interlace,filters,seed):
 rng=random.Random(seed);parts=[];samples={0:1,2:3,3:1,4:2,6:4}[color]
 for x,y,dx,dy in adam if interlace else [(0,0,1,1)]:
  pw=max(0,(w-x+dx-1)//dx);ph=max(0,(h-y+dy-1)//dy);bw=(pw*depth*samples+7)//8
  if bw and ph:
   for row in range(ph):parts.append(bytes([filters[row%len(filters)]])+rng.randbytes(bw))
 return b''.join(parts)
types=[(0,d) for d in [1,2,4,8,16]]+[(3,d) for d in [1,2,4,8]]+[(c,d) for c in [2,4,6] for d in [8,16]]
shapes=[(0,0),(0,9),(9,0),(1,1),(1,9),(9,1),(2,3),(3,2),(5,7),(7,5),(8,8),(9,9),(15,17),(17,15),(31,33)]
for (color,depth),(w,h),interlace,f in itertools.product(types,shapes,[0,1],[0,1,2,3,4,5,255]):
 data=scanlines(w,h,depth,color,interlace,[f],color*20000+depth*1000+w*13+h*7+f)
 add(f'scanlines {w}x{h} color={color} depth={depth} interlace={interlace} filter={f}',png(data,w,h,depth,color,interlace,palette if color==3 else None),observe=['palette-data','dynamic-data'])
for color,depth in types:
 for interlace in [0,1]:
  data=scanlines(19,21,depth,color,interlace,[0,1,2,3,4,255],777)
  for split in [[1],[2],[3,5],[0,2,7]]:add(f'IDAT split {color}/{depth}/{interlace}/{split}',png(data,19,21,depth,color,interlace,palette if color==3 else None,split=split))
raw=scanlines(3,2,8,2,0,[0,4],99)
base=png(raw)
for offset in range(len(base)):add(f'truncated file at {offset}',base[:offset])
for offset in [0,4,7,12,16,29,len(base)-1]:
 b=bytearray(base);b[offset]^=1;add(f'corruption at {offset}',bytes(b),observe=['either','palette-tag','metadata'] if offset==16 else observations)
add('ignore bytes after first IEND',base+b'garbage!')
for value in range(256):
 add(f'color code {value}',png(bytes(256),color=value))
 add(f'interlace code {value}',png(scanlines(3,2,8,2,value,[0,4],99) if value==1 else raw,interlace=value))
 add(f'ignored compression method {value}',png(raw,compression=value))
 add(f'ignored filter method {value}',png(raw,filter=value))
 # Every possible row-filter code, on nonempty images.
 add(f'row filter {value}',png(bytes([value])+bytes(range(9))+bytes([value])+bytes(range(9,18))))
for value in [0,1,12,14,0xffffffff]:add(f'ignored IHDR length {value}',png(raw,length=value))
for value in [0,1,0xffffffff,0x12345678]:add(f'ignored IHDR CRC {value}',png(raw,crc=value))
for color,depth in itertools.product([0,2,3,4,6],range(256)):
 # Empty passes define arbitrary-depth behavior without native uninitialized data.
 add(f'empty invalid depth {color}/{depth}',png(b'',w=0,h=2,color=color,depth=depth,palette=palette if color==3 else None))
 if depth in [0,3,5,6,7]:add(f'nonempty invalid depth {color}/{depth}',png(bytes(200),w=1,h=1,color=color,depth=depth,palette=palette if color==3 else None),observe=['either','palette-tag','dynamic-tag','metadata'] if depth==0 else observations)
for depth in [17,24,32,255]:
 # One pixel, no interlace: original short unpacker initializes all output samples.
 for color in [0,2,3,4,6]:add(f'defined oversized depth {color}/{depth}',png(bytes(range(256)),w=1,h=1,color=color,depth=depth,palette=palette if color==3 else None))
for color,depth in [(3,8),(0,1),(0,2),(0,4),(0,8),(0,16),(2,8),(2,16),(4,8),(6,8)]:
 data=scanlines(7,3,depth,color,0,[0,2,4],71)
 for alpha in [b'',b'\0',bytes(range(16)),bytes(range(256)),bytes(range(256))+b'ignored']:
  for copies in [1,2,3]:add(f'transparency {color}/{depth} length {len(alpha)} count {copies}',png(data,7,3,depth,color,palette=palette if color==3 else None,extra=chunk(b'tRNS',alpha)*copies))
for pal in [None,b'',b'1',b'12',b'123',palette]:
 # Missing/empty/undersized palettes are defined until unsafe indexed expansion.
 add(f'palette validation {None if pal is None else len(pal)}',png(bytes(10),w=1,h=1,color=3,palette=pal),observe=observations if pal and len(pal)%3==0 else ['either','palette-tag','palette-data','metadata'])
for encoded in [b'',b'x',bytes(6),bytes(7),b'garbage!',zlib.compress(b'\0'*64)[:-1],zlib.compress(b'\0'*64)[:-4]+bytes(4),zlib.compress(b'\0'*64)+b'trailing']:
 for color in [2,3]:
  add(f'zlib {color} {encoded.hex()}',header(1,1,8,color)+chunk(b'PLTE',palette)+chunk(b'IDAT',encoded)+chunk(b'IEND',b''))
texts=[b'Title\0title',b'Author\0author',b'Description\0description',b'Copyright\0copyright',b'Software\0software',b'Disclaimer\0disclaimer',b'Warning\0warning',b'Source\0source',b'Comment\0comment',b'Creation Time\0time',b'\0empty keyword',b'Latin\xff\0\x80\xff\x00',b'no terminator']
for t in texts:
 add('text '+t.hex(),png(raw,extra=chunk(b'tEXt',t)))
 if b'\0' in t:
  key,value=t.split(b'\0',1);add('ztext '+t.hex(),png(raw,extra=chunk(b'zTXt',key+b'\0\0'+zlib.compress(value))))
for unit in range(256):add(f'physical unit {unit}',png(raw,extra=chunk(b'pHYs',word(12345)+word(54321)+bytes([unit]))))
for value in [0,1,45455,100000,2147483648,0xffffffff]:add(f'gamma {value}',png(raw,extra=chunk(b'gAMA',word(value))))
for t,n in itertools.product([b'pHYs',b'gAMA'],range(12)):add(f'short {t} {n}',png(raw,extra=chunk(t,bytes(n))+chunk(t,bytes(9))))
for method in [1,2,255]:add(f'ignored zTXt method {method}',png(raw,extra=chunk(b'zTXt',b'Title\0'+bytes([method])+b'garbage!')))
good=zlib.compress(b'good title');bad=good[:-4]+bytes(4)
for content in [b'',b'garbage!',good,bad,good[:-1]]:
 for override in [False,True]:
  extra=chunk(b'zTXt',b'Title\0\0'+content)+(chunk(b'zTXt',b'Title\0\0'+good) if override else b'')
  add(f'zTXt tail {content.hex()} override {override}',png(raw,extra=extra))
for order in itertools.permutations([chunk(b'tEXt',b'Title\0first'),chunk(b'tEXt',b'Title\0last'),chunk(b'zTXt',b'Title\0\0'+good),chunk(b'tEXt',b'Author\0writer')]):
 add('metadata duplicate order '+hashlib.sha256(b''.join(order)).hexdigest()[:12],png(raw,extra=b''.join(order)))
for first,last in itertools.product([b'',b'garbage!',good,bad,good[:-1]],repeat=2):
 for keys in [(b'Title',b'Title'),(b'Title',b'Author'),(b'Author',b'Title')]:
  extra=chunk(b'zTXt',keys[0]+b'\0\0'+first)+chunk(b'zTXt',keys[1]+b'\0\0'+last)+chunk(b'pHYs',word(10000)+word(20000)+b'\1')
  add(f'metadata demand {first.hex()} {last.hex()} {keys}',png(raw,extra=extra),observe=['metadata','metadata-keys','metadata-title','metadata-dpi'])
for start in range(0,len(cs),16):
 batch=cs[start:start+16];requests=[]
 for c in batch:
  data=(root/c['source']).read_bytes().hex() if 'source' in c else c['hex']
  requests.append('('+json.dumps(data)+','+json.dumps(c['observe'])+')')
 lines=subprocess.check_output([a.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line)
  for key in ['error','exception','metadata']:
   if key in expected:expected[key]=''.join(map(chr,expected[key]))
  for key in ['hex','palette']:
   if key in expected:
    b=bytes.fromhex(expected[key]);expected[key+'Length']=len(b);expected[key+'Sha256']=hashlib.sha256(b).hexdigest()
    if len(b)>256:del expected[key]
  c['expected']=expected
 if start%1024==0:print(f'Captured {start+len(batch)}/{len(cs)} PNG observations',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cs),caseHash=digest(cs))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cs)+'\n').encode(),mtime=0))
print(f'Captured {len(cs)} original PNG decoder observations')
