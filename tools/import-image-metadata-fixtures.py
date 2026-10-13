#!/usr/bin/env python3
"""Independent JuicyPixels typed metadata, TIFF/JFIF and JPEG integration captures."""
import argparse,gzip,hashlib,json,os,random,struct,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/image-metadata.jsonl.gz'
sources=['tools/import-image-metadata-fixtures.py','core/src/test/resources/images/jpeg-encoder.jsonl.gz','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-image-metadata-reference','vendor/JuicyPixels-jpeg-tests-v3.3.8']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(x in ['dist-newstyle','build'] for x in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_IMAGE_METADATA_REFERENCE'));args=p.parse_args()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 print(f'Verified {len(cs)} original image metadata captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name image-metadata-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0']
cases=[];rng=random.Random(0x4d455441)
def add(op,left=None,right=None,fields=None,ns=None,name=''):
 cases.append(dict(name=f'{len(cases)} {op} {name}',op=op,left=left or [],right=right or [],fields=fields or [],ns=ns or []))
def entry(key,ns=None,arg='',kind=''):return [key,arg,kind,ns or []]
def string(key,text):return entry(key,list(map(ord,text)))
def exif(tag,kind,ns):return entry('Exif',ns,str(tag),kind)
def field(tag,typ=4,count=1,offset=0,kind='none',ns=None):return [tag,typ,count,offset,kind,ns or []]
def bits(x):return struct.unpack('>Q',struct.pack('>d',x))[0]
textkeys=['Title','Description','Author','Copyright','Software','Comment','Disclaimer','Source','Warning']
wordkeys=['DpiX','DpiY','Width','Height']
tags=[262,259,256,257,282,283,296,278,279,273,258,320,322,323,324,325,277,315,269,305,284,274,339,332,255,266,529,530,531,532,286,287,338,270,317,33432,271,272,306,34853,37384,37385,512,513,514,515,517,518,519,520,521,34665]
variants=[('none',[]),('short',[65535]),('long',[2**32-1]),('shorts',[1,65535]),('longs',[1,2**32-1]),('string',[0,65,127,255]),('undefined',[1,2,3,4]),('rational',[300,7]),('signed',[-300,7]),('ifd',[271,4,37384,5])]
entries=[string(k,'A\0Ж😀\\\n') for k in textkeys]+[entry(k,[2**64-1]) for k in wordkeys]
entries += [entry('Gamma',[bits(v)]) for v in [0.0,-0.0,1.0,-1.0,1e23,float('inf'),float('-inf'),float('nan')]]
entries += [entry('Format',arg=str(n)) for n in range(7)]+[entry('ColorSpace',list(range(256)),arg=n) for n in ['SRGB','ICCProfile','WindowsBitmapColorSpace']]
entries += [entry('Unknown',[-2**63],'x','int'),entry('Unknown',[bits(-0.0)],'x','double'),entry('Unknown',[97,0,0x1f600],'x','string')]
entries += [exif(t,k,ns) for t in [271,-272,65000,-65001] for k,ns in variants]
for e in entries:add('store',[e],[e],name='all typed constructors')
add('store',entries,entries[::-1],name='all keys replacement/order')
unknowns=[entry('Unknown',[bits(v)],'x','double') for v in [0.0,-0.0,1.0,-1.0,float('inf'),float('-inf'),float('nan')]]+[entry('Unknown',[-2**63],'x','int'),entry('Unknown',[0],'x','int'),entry('Unknown',[97,0,0x1f600],'x','string')]
add('values',unknowns+unknowns,name='Value Show/Eq including NaN and signed zero')
colors=[entry('ColorSpace',ns,arg=kind) for kind in ['SRGB','ICCProfile','WindowsBitmapColorSpace'] for ns in [[],[0],[0,1,255]]]
add('colors',colors+colors,name='ColorSpace Show/Eq')
for _ in range(500):add('store',rng.choices(entries,k=rng.randrange(30)),rng.choices(entries,k=rng.randrange(25)),name='seeded union/delete/lookup/folds')
values=[0,1,2,72,93,254,255,256,65535,65536,2**32-1,2**32,2**63-1,2**63,2**64-1,-1,-2**63]+[rng.randrange(2**64) for _ in range(100)]
for n in values:
 for f in range(7):add('helpers',ns=[n,n+1,n+2,n+3,f],name='Word overflow/conversions and constructors')
for unit in range(3):
 for v in list(range(0,65536,257))+[65535]:add('jfif',ns=[unit,v,65535-v],name='unit and Word16 range')
for t in tags+[-t-1 for t in tags]+[0,65535]:
 for kind,ns in variants:add('extract',fields=[field(t,4,1,65535,kind,ns)],name='tag constructor and Exif data')
for unit in [0,1,2,3,4]:
 for typ in [1,3,4]:
  for a,b in [(0,1),(300,7),(2**32-1,1),(1,0)]:
   fs=[field(296,typ,offset=unit),field(282,5,kind='rational',ns=[a,b]),field(283,5,kind='rational',ns=[a,b])]
   add('extract',fields=fs,name='DPI unit/division');add('extract',fields=fs+[field(296,3,offset=2)],name='first duplicate resolution')
for _ in range(300):
 fs=[field(rng.choice(tags+[-65001]),rng.randrange(1,13),rng.randrange(4),rng.randrange(2**32),*rng.choice(variants)) for _ in range(rng.randrange(20))]
 add('extract',fields=fs,name='mixed fields/right folds')
for kind,ns in variants:
 for t in [271,-272,65000,-65001]:add('encode',[exif(t,kind,ns)],name='Exif construction')
for kind in ['shorts','longs','string','undefined']:
 for n in range(17):
  for v in [0,1,127,255,65535]:add('encode',[exif(-65001,kind,[v]*n)],name='inline/extended boundary')
for key in textkeys:
 for text in ['', '\0','abcd','Ж😀\U0010ffff', ''.join(map(chr,range(256)))]:add('encode',[string(key,text)],name='Char8 and recognized text keys')
valid=[e for e in entries if not(e[0]=='Exif' and e[2] in ['none','ifd'])]
for _ in range(200):
 es=rng.choices(valid,k=rng.randrange(20));add('encode',es,name='stable numeric tag sorting');add('jpeg',es,name='complete metadata encoding')
# Original metadataWriteTest construction, in its actual insertion order.
original=[entry('DpiX',[93]),entry('DpiY',[93])]+[string(k,v) for k,v in [('Comment','Test of comment'),('Description',"let's see the results"),('Copyright','meh'),('Title','A metadata test'),('Software','JuicyPixels test suite'),('Author',"It's a me")]]
add('originalWrite',original,name='original metadataWriteTest JPEG');add('roundtrip',original,name='original metadataWriteTest JPEG')
for den in [0,1,7]:
 es=[entry('DpiX',[72]),entry('DpiY',[93]),exif(296,'short',[2]),exif(282,'rational',[300,den]),exif(283,'rational',[400,den])]
 add('jpeg',es,name='conflicting JFIF and Exif DPI');add('roundtrip',es,name='Exif priority/lazy errors');add('pixelOnly',es,name='plain decode leaves metadata lazy')
add('offset')
# Raw TIFF frames preserve correct inline shorts and expose metadata laziness even
# where the original metadata writer's inline-field quirks lose those values.
with gzip.open(root/'core/src/test/resources/images/jpeg-encoder.jsonl.gz','rt') as f:
 next(f);base=bytes(next(json.loads(l) for l in f if '\"color\":\"YCbCr8\"' in l and '\"op\":\"encode\"' in l)['expected']['encoded'])
def w(n):return struct.pack('>H',n)
def d(n):return struct.pack('>I',n)
def segment(n,payload):return bytes([255,n])+w(len(payload)+2)+payload
jfif=segment(224,b'JFIF\0'+bytes([1,2,1])+w(72)+w(93)+bytes(2))
for unit in [1,2,3]:
 for den in [0,1,7]:
  fs=w(256)+w(4)+d(1)+d(888)+w(257)+w(4)+d(1)+d(999)+w(296)+w(3)+d(1)+w(unit)+w(0)+w(282)+w(5)+d(1)+d(74)+w(283)+w(5)+d(1)+d(82)
  ex=segment(225,b'Exif\0\0MM'+w(42)+d(8)+w(5)+fs+d(0)+d(300)+d(den)+d(400)+d(den))
  for suffix in [b'',segment(225,b'bad'),segment(224,b'JFIF\0'+bytes([1,2,1])+w(11)+w(12)+bytes(2))]:
   raw=base[:2]+jfif+ex+suffix+base[2:]
   add('decode',ns=list(raw),name='raw Exif priority, dimension override and lazy failure')
   add('decodePixels',ns=list(raw),name='metadata not forced by pixel decode')
def hs(v):
 if isinstance(v,list):return '['+','.join(hs(x) for x in v)+']'
 if isinstance(v,str):return json.dumps(v,ensure_ascii=True)
 return str(v)
for start in range(0,len(cases),30):
 batch=cases[start:start+30];lines=[]
 for c in batch:
  left='['+','.join('('+','.join(hs(x) for x in e)+')' for e in c['left'])+']'
  right='['+','.join('('+','.join(hs(x) for x in e)+')' for e in c['right'])+']'
  fields='['+','.join('('+','.join(hs(x) for x in f)+')' for f in c['fields'])+']'
  lines.append('('+','.join([hs(c['op']),left,right,fields,hs(c['ns'])])+')')
 out=subprocess.check_output([args.oracle],input=('\n'.join(lines)+'\n').encode()).splitlines();assert len(out)==len(batch)
 for c,line in zip(batch,out):c['expected']=json.loads(line)
 if start%600==0:print(f'Captured {start+len(batch)}/{len(cases)} synthetic',flush=True)
with tempfile.TemporaryDirectory() as tmp:
 for file in sorted((root/'vendor/JuicyPixels-jpeg-tests-v3.3.8/tests/jpeg').glob('*.jpg')):
  out=Path(tmp)/'encoded.jpg';result=json.loads(subprocess.check_output([args.oracle,'--file',str(file),str(out)]))
  if 'metadata' in result:
   encoded=out.read_bytes();result.update(encodedHash=hashlib.sha256(encoded).hexdigest(),encodedLength=len(encoded))
  cases.append(dict(name=f'{len(cases)} original metadata {file.name}',source=str(file.relative_to(root)),expected=result))
  print(f'Captured {file.name}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} image metadata results')
