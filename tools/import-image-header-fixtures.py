#!/usr/bin/env python3
# Binary builders and original cases translated from Tests.ImageSize:
# Copyright © 2025 John MacFarlane, GPL-2.0-or-later.
"""Capture original Pandoc image.format/image.size; Kotlin header code is never used.

PNG/JPEG failure cases use synthetic headers without decodable pixel streams.
The complete decoder fallback remains a separate dependency to port.
"""
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
target = root / 'core/src/test/resources/images/headers.jsonl.gz'
sources = ['tools/import-image-header-fixtures.py',
           'upstream/pandoc/src/Text/Pandoc/ImageSize.hs',
           'upstream/pandoc/test/Tests/ImageSize.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Image.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/ImageSize.hs',
           'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-image.lua']
hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources}
p = argparse.ArgumentParser(); p.add_argument('--check-source', action='store_true'); args = p.parse_args()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); assert manifest['sources'] == hashes
        cases = [json.loads(line) for line in f]
    assert len(cases) == manifest['cases']
    assert hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest() == manifest['caseHash']
    for c in cases:
        if 'source' in c:
            assert (root / c['source']).read_bytes().hex() == c['hex']
    print(f'Verified {len(cases)} independent image header/type captures')
    raise SystemExit()
assert subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0] == 'pandoc 3.12.1'
def be16(n): return struct.pack('>H', n & 0xffff)
def be32(n): return struct.pack('>I', n & 0xffffffff)
def le32(n): return struct.pack('<I', n & 0xffffffff)
def be64(n): return struct.pack('>Q', n)
def box(t, b, kind='normal'):
    return (be32(0) + t if kind == 'zero' else be32(1) + t + be64(len(b)+16) if kind == 'large' else be32(len(b)+8) + t) + b
def emf(frame, device, mm): return le32(1) + bytes(20) + b''.join(map(le32, frame)) + b' EMF' + bytes(28) + b''.join(map(le32, device+mm))
def pngchunk(t, b): return be32(len(b)) + t + b + bytes(4)
def png(chunks=(), w=640, h=480): return b'\x89PNG\r\n\x1a\n' + pngchunk(b'IHDR', be32(w)+be32(h)+bytes([8,3,0,0,0])) + b''.join(chunks)
def phys(unit, x, y): return pngchunk(b'pHYs', be32(x)+be32(y)+bytes([unit]))
def seg(m, b): return bytes([255,m]) + be16(len(b)+2) + b
def jpeg(segments, w=640, h=480, sof=0xc0): return b'\xff\xd8' + b''.join(segments) + seg(sof, bytes([8])+be16(h)+be16(w)+bytes([3]))
def jfif(unit, x, y): return seg(0xe0, b'JFIF\0'+bytes([1,2,unit])+be16(x)+be16(y)+bytes(2))
def exif(little=False, unit=2, x=300, y=200, den=1, duplicate=False):
    w16 = (lambda n: struct.pack('<H', n & 65535)) if little else be16
    w32 = le32 if little else be32
    def entry(tag, kind, value): return w16(tag)+w16(kind)+w32(1)+value
    count = 4 if duplicate else 3
    start = 8+2+count*12+4
    entries = [entry(0x011a,5,w32(start)), entry(0x011b,5,w32(start+8)), entry(0x0128,3,w16(unit)+w16(0))]
    if duplicate: entries.append(entry(0x011a,5,w32(0xffffffff)))
    return seg(0xe1, b'Exif\0\0'+(b'II' if little else b'MM')+w16(42)+w32(8)+w16(count)+b''.join(entries)+w32(0)+w32(x)+w32(den)+w32(y)+w32(den))
def webp(b): return b'RIFF'+bytes(4)+b'WEBP'+b
def lossless(w=100,h=200): return webp(b'VP8L'+bytes(4)+b'\x2f'+le32((w-1)|((h-1)<<14)))
def lossy(w=320,h=240): return webp(b'VP8 '+bytes(4)+bytes([0,0,0,0x9d,1,0x2a])+struct.pack('<HH',w,h))
def extended(w=1000,h=500): return webp(b'VP8X'+bytes(8)+le32(w-1)[:3]+le32(h-1)[:3])
def ftyp(brand=b'avif', kind='normal'): return box(b'ftyp', brand+bytes(4), kind)
def ispe(w=640,h=480): return box(b'ispe',bytes(4)+be32(w)+be32(h))
def meta(w=640,h=480): return bytes(4)+box(b'iprp',box(b'ipco',ispe(w,h)))
def tkhd(version=0,w=640,h=480): return box(b'tkhd',bytes([version])+bytes(3)+bytes(84 if version==1 else 72)+be32(w<<16)+be32(h<<16))
def av01(w=640,h=480): return box(b'av01',bytes(24)+be16(w)+be16(h))
cases=[]
def add(name, data, scanner=None, dpi=96, expected=None, source=None, original=False):
    c={'name':name,'hex':data.hex(),'dpi':str(dpi),'scanner':scanner,'original':original}
    if expected is not None: c['assertSize']=list(map(str,expected))
    if source: c['source']=source
    cases.append(c)
# All 19 original imageType examples, independently translated binary builders.
original_types=[('png',b'\x89PNG\r\n\x1a\n'),('gif',b'GIF89a'),('tiff little',b'II*\0'),('tiff big',b'MM\0*'),
 ('jpeg app',b'\xff\xd8\xff\xe0rest'),('jpeg bare',b'\xff\xd8\xff\xdbrest'),('pdf',b'%PDF-1.5'),('eps',b'%!PS-Adobe-3.0 EPSF-3.0'),
 ('svg',b'<svg xmlns="http://www.w3.org/2000/svg" ></svg>'),('svg xml',b'<?xml version="1.0"?>\n<svg xmlns="http://www.w3.org/2000/svg" ></svg>'),
 ('SVG xml',b'<?xml version="1.0"?>\n<SVG></SVG>'),('xml not svg',b'<?xml version="1.0"?>\n<html><p>svg</p></html>'),
 ('svg bom',b'\xef\xbb\xbf<svg xmlns="http://www.w3.org/2000/svg" ></svg>'),('emf',emf([0,0,1,1],[1,1],[1,1])),('webp',lossless()),
 ('avif',ftyp()+box(b'meta',meta())),('avis',ftyp(b'avis')+box(b'moov',box(b'trak',tkhd()))),('isom',ftyp(b'isom')),('garbage',b'garbage!')]
for name,data in original_types: add('original type '+name,data,original=True)
for name,chunks,dx,dy in [('no pHYs',[],72,72),('pHYs',[phys(1,3937,3937)],99,99),('unknown unit',[phys(0,4,3)],72,72),('after text',[pngchunk(b'tEXt',b'k\0v'),phys(1,3937,3937)],99,99)]:
    add('original png '+name,png(chunks),'png',expected=[640,480,dx,dy],original=True)
for name,segments,dx,dy in [('jfif',[jfif(1,96,96)],96,96),('dpcm',[jfif(2,100,100)],254,254),('ratio',[jfif(0,1,1)],72,72),('exif',[exif()],300,200),('exif overrides',[jfif(1,96,96),exif()],300,200),('bare',[seg(0xdb,bytes(65))],72,72),('zero',[jfif(1,0,0)],72,72)]:
    add('original jpeg '+name,jpeg(segments),'jpeg',expected=[640,480,dx,dy],original=True)
for name,frame,mm,expect in [('origin',[0,0,10000,5000],[320,240],[320,160,81,81]),('offset',[2000,1000,12000,6000],[320,240],[320,160,81,81]),('zero device',[0,0,10000,5000],[0,240],None)]:
    add('original emf '+name,emf(frame,[1024,768],mm),'emf',expected=expect,original=True)
for name,data,expected in [('lossless',lossless(),[100,200,96,96]),('lossy',lossy(),[320,240,96,96]),('extended',extended(),[1000,500,96,96])]:
    add('original webp '+name,data,'webp',expected=expected,original=True)
for name,data in [('ispe',ftyp()+box(b'meta',meta())),('tkhd',ftyp(b'avis')+box(b'moov',box(b'trak',tkhd()))),('large',ftyp()+box(b'meta',meta(),'large')),('zero',ftyp()+box(b'meta',meta(),'zero')),('unknown',ftyp()+box(b'free',b'junk')+box(b'meta',meta()))]:
    add('original avif '+name,data,'avif',expected=[640,480,96,96],original=True)
for source,scanner,expected in [('lalune.jpg','jpeg',[250,250,120,120]),('fb2/test-small.png','png',[48,32,71,71]),('bodybg.gif',None,None)]:
    path='upstream/pandoc/test/'+source; add('original file '+source,(root/path).read_bytes(),scanner,expected=expected,source=path,original=True)
# Detection uses exact byte prefixes, including deliberately permissive SVG matching.
for prefix in [b'',b' ',b'\xef\xbb\xbf',b'\xef\xbb\xbf'*2]:
    for body in [b'<svg',b'<SVG',b'<svgother',b'<?xm<SVGother',b'<?xml><!-- <svg -->',b'<?xml><Svg>',b'%!PS EPSF',b'%!PS  EPSF',b'%!PS\tEPSF',b'RIFF1234WEBP',b'\x89PNG',b'GIF8']:
        add('prefix '+str(len(cases)),prefix+body)
for marker in range(256): add(f'jpeg signature {marker}',bytes([255,216,255,marker]))
# Complete truncation of every original synthetic binary header, including type changes.
seeds=[c.copy() for c in cases if c['scanner'] and not c.get('source')]
for c in seeds:
    data=bytes.fromhex(c['hex'])
    for n in range(len(data)):
        add(f'truncated {c["name"]} {n}',data[:n],c['scanner'])
# Branches and integer limits from the Haskell source; expected values from Lua.
for unit in [0,1,2,255]:
    for x,y in [(0,0),(1,3937),(0xffffffff,0xffffffff),(10000,20000)]:
        for chunks in [[phys(unit,x,y)],[pngchunk(b'IDAT',b''),phys(unit,x,y)],[phys(unit,x,y),phys(1,9999,9999)]]:
            add('png density '+str(len(cases)),png(chunks,0xffffffff,0),'png')
for little in [False,True]:
    for unit in [0,1,2,3,65535]:
        for den in [0,1,2,7]:
            for duplicate in [False,True]:
                add('exif '+str(len(cases)),jpeg([jfif(1,96,100),exif(little,unit,0xffffffff,601,den,duplicate)]),'jpeg')
for sof in range(0xc0,0xd0):
    add(f'jpeg SOF {sof}',jpeg([jfif(1,100,200),b'\xff\xff\xd0',b'\xff\x01'],65535,0,sof),'jpeg')
for segments in [[jfif(1,90,80),jfif(1,30,40)],[jfif(1,90,80),jfif(0,1,1)],[exif(),seg(0xe1,b'Exif\0\0broken')],[exif(),exif(True,3,100,200)],[seg(0xe0,b'bad'),jfif(1,90,80)]]:
    add('density precedence '+str(len(cases)),jpeg(segments),'jpeg')
for frame in [[-2147483648,-2147483648,2147483647,2147483647],[1,1,0,0],[-2,-3,-1,-1]]:
    for device in [[0,0],[0xffffffff,0xffffffff],[1,2]]:
        for mm in [[1,1],[0xffffffff,0xffffffff],[0,1]]:
            add('emf signed '+str(len(cases)),emf(frame,device,mm),'emf')
for dpi in [-9223372036854775808,-1,0,1,72,300,9223372036854775807]:
    for data in [lossless(16384,16384),lossy(65535,65535),extended(16777216,16777216)]:
        add('webp dpi '+str(len(cases)),data,'webp',dpi)
    add('avif dpi '+str(len(cases)),ftyp()+ispe(0xffffffff,0),'avif',dpi)
for version in [0,1,2,255]: add('tkhd version '+str(version),ftyp(b'avis')+tkhd(version,65535,1),'avif')
for data in [av01(),box(b'stsd',bytes(8)+av01()),box(b'stsd',bytes(8)+box(b'other',b'junk')+av01()),box(b'stsd',bytes(8)+be32(0)+b'none'+av01())]:
    add('avif sample entry '+str(len(cases)),ftyp()+data,'avif')
for kind in ['normal','large','zero']:
    for container in [b'moov',b'trak',b'mdia',b'minf',b'stbl',b'meta',b'dinf',b'ipco',b'iprp']:
        body=(bytes(4) if container==b'meta' else b'')+ispe()
        add('avif container '+str(len(cases)),ftyp()+box(container,body,kind),'avif')
for length in [0,1,2,7,8,9,15,16,19,20,21,0xffffffff]:
    for name in [b'ispe',b'free',b'meta']:
        add('avif declared size '+str(len(cases)),ftyp()+be32(length)+name+bytes(4)+be32(640)+be32(480)+ispe(2,3),'avif')
for length in [0,8,15,16,20,0x7fffffff,0x80000000,0xffffffffffffffff]:
    add('avif largesize '+str(length),ftyp()+be32(1)+b'meta'+be64(length)+meta(),'avif')
# Exercise the unusual upstream range behavior: exiting an inner container
# switches back to unbounded scanning rather than restoring the parent bound.
for data in [box(b'iprp',box(b'ipco',b''))+ispe(),box(b'iprp',box(b'free',b''))+ispe(),box(b'iprp',box(b'free',b'')+b'abc')+ispe(),box(b'iprp',box(b'ispe',bytes(4)))+be32(17)+be32(19)]:
    add('avif range '+str(len(cases)),ftyp()+data,'avif')
rng=random.Random(0x494d414745)
for i in range(1200):
    c=rng.choice(seeds);data=bytearray.fromhex(c['hex'])
    # Mutate payloads while retaining signatures, then truncate/append randomly.
    for _ in range(rng.randrange(1,5)):
        off=rng.randrange(12,len(data));data[off]=rng.randrange(256)
    if i%3==0:data=data[:rng.randrange(12,len(data)+1)]
    if i%7==0:data+=rng.randbytes(rng.randrange(16))
    add(f'seeded mutation {i}',bytes(data),c['scanner'])
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory);request=temp/'request.json';request.write_text(json.dumps(cases))
    script=temp/'images.lua';script.write_text('''
function Writer(doc, opts)
 local f=assert(io.open(os.getenv('KOTDOC_IMAGE_REQUEST')))
 local cases=pandoc.json.decode(f:read('*a'),false);f:close()
 local result={}
 for _,c in ipairs(cases) do
  local bytes=c.hex:gsub('..',function(pair) return string.char(tonumber(pair,16)) end)
  local kind=pandoc.image.format(bytes)
  local row={format=kind or false}
  if c.scanner and kind==c.scanner then
   local ok,value=pcall(pandoc.image.size,bytes,{dpi=tonumber(c.dpi)})
   if ok then row.size={tostring(value.width),tostring(value.height),tostring(value.dpi_horz),tostring(value.dpi_vert)}
   else row.error=tostring(value) end
  end
  result[#result+1]=row
 end
 return pandoc.json.encode(result)
end
''')
    result=json.loads(subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',env={**os.environ,'KOTDOC_IMAGE_REQUEST':str(request)}))
assert len(result)==len(cases)
for c,r in zip(cases,result):
    c.update(r)
    if 'assertSize' in c: assert c.pop('assertSize')==c['size'],c['name']
manifest={'sources':hashes,'oracle':'pandoc 3.12.1','cases':len(cases),'caseHash':hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()}
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} image type results, {sum("size" in c for c in cases)} sizes, {sum("error" in c for c in cases)} failures')
