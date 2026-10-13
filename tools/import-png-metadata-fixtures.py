#!/usr/bin/env python3
"""Capture Pandoc 3.12.1's JuicyPixels PNG fallback (never Kotlin output).

IHDR length is set below 13: this bypasses Pandoc's header-only fast path;
JuicyPixels explicitly ignores this field. Original files stay byte-for-byte
intact in vendor/, and each transformation is recorded in the fixture.
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
import zlib

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/images/png-metadata.jsonl.gz'
sources = ['tools/import-png-metadata-fixtures.py',
           'upstream/pandoc/src/Text/Pandoc/ImageSize.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Image.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/ImageSize.hs',
           'vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9', 'vendor/JuicyPixels-png-tests-v3.3.8', 'vendor/zlib-0.7.1.1']:
    sources += sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file())
hashes = {p: hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');args=p.parse_args()
if args.check_source:
    with gzip.open(target,'rt') as f:
        manifest=json.loads(next(f));cases=[json.loads(line) for line in f]
    assert manifest['sources']==hashes
    assert len(cases)==manifest['cases'] and digest(cases)==manifest['caseHash']
    for c in cases:
        if 'source' in c: assert hashlib.sha256((root/c['source']).read_bytes()).hexdigest()==c['sourceHash']
    print(f'Verified {len(cases)} independent PNG fallback captures');raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
def word(n): return struct.pack('>I',n)
def chunk(t,data,crc=None): return word(len(data))+t+data+word(zlib.crc32(t+data) if crc is None else crc)
def header(w=2,h=3,depth=8,color=2,compression=0,filter=0,interlace=0,length=0,crc=0):
    return b'\x89PNG\r\n\x1a\n'+word(length)+b'IHDR'+struct.pack('>IIBBBBB',w,h,depth,color,compression,filter,interlace)+word(crc)
def png(*,data=b'garbage!',extra=b'',palette=None,**kwargs):
    return header(**kwargs)+(chunk(b'PLTE',palette) if palette is not None else b'')+chunk(b'IDAT',data)+extra+chunk(b'IEND',b'')
cases=[]
def add(name,data,source=None,**metadata):
    c=dict(name=name,hex=data.hex(),**metadata)
    if source: c.update(source=source,sourceHash=hashlib.sha256((root/source).read_bytes()).hexdigest(),forceFallback=True)
    cases.append(c)
for path in sorted((root/'vendor/JuicyPixels-png-tests-v3.3.8/tests/pngsuite').glob('*.png')):
    original=path.read_bytes();data=bytearray(original)
    # Force the PNG type gate as well for original signature-invalid test files.
    # Remaining signature bytes retain their original invalid values.
    data[:4]=b'\x89PNG';data[8:12]=bytes(4)
    add('upstream fallback '+path.name,bytes(data),str(path.relative_to(root)))
for depth in range(256):
    for color in [0,2,4,6]:
        if color==0 and depth in [1,2,4]: continue
        add(f'lazy depth {depth} color {color}',png(depth=depth,color=color))
for n in range(256):
    add(f'color {n}',png(color=n))
    add(f'interlace {n}',png(interlace=n))
    add(f'compression {n}',png(compression=n))
    add(f'filter method {n}',png(filter=n))
    add(f'physical unit {n}',png(extra=chunk(b'pHYs',word(12345)+word(54321)+bytes([n]))))
for length in list(range(13))+[0xffffffff]:
    add(f'ignored IHDR length {length}',png(length=length))
for crc in [0,1,0xffffffff,0x12345678]: add(f'ignored IHDR CRC {crc}',png(crc=crc))
for w,h in [(0,0),(0,1),(1,0),(1,1),(0xffffffff,0xfffffffe)]:
    add(f'unsigned dimensions {w} {h}',png(w=w,h=h))
for data in [b'',b'x',bytes(6),bytes(7),b'garbage!',zlib.compress(b''),zlib.compress(b'\0'*100)]:
    for color in [0,2,3,4,6]:
        add(f'IDAT length {len(data)} color {color} data {data.hex()}',png(color=color,palette=bytes(6),data=data), unsafeScanlines=(color==3 and data==zlib.compress(b'')))
for depth in [0,1,2,3,4,5,7,8,9,16]:
    for w,h in [(0,0),(0,7),(7,0),(1,1)]:
        for interlace in [0,1]:
            add(f'indexed depth {depth} {w}x{h} interlace {interlace}',png(w=w,h=h,depth=depth,color=3,interlace=interlace,palette=bytes(6),data=zlib.compress(bytes(4096))))
for depth in [1,2,4,8,16]:
    for palette in [None,b'',b'x',bytes(2),bytes(3),bytes(4),bytes(6),bytes(768),bytes(771)]:
        add(f'palette {depth} {None if palette is None else len(palette)}',png(w=1,h=1,depth=depth,color=3,palette=palette,data=zlib.compress(bytes(4096))))
for color,depth in [(0,1),(0,2),(0,4),(3,8)]:
    for interlace in [0,1]:
        for row_filter in range(256):
            # Supply complete safe scanlines, including all Adam7 passes.
            add(f'row filter {row_filter} color {color} depth {depth} interlace {interlace}',png(w=1,h=1,color=color,depth=depth,interlace=interlace,palette=bytes(6),data=zlib.compress(bytes([row_filter,0]))))
for n in range(20):
    add(f'physical length {n}',png(extra=chunk(b'pHYs',(word(12345)+word(54321)+b'\1'+bytes(20))[:n])))
for x,y in [(0,0),(1,39),(40,10000),(0xffffffff,0xfffffffe)]:
    add(f'physical values {x} {y}',png(extra=chunk(b'pHYs',word(x)+word(y)+b'\1')))
for first in [b'',bytes(9),word(10000)+word(20000)+b'\1']:
    add(f'first physical wins {first.hex()}',png(extra=chunk(b'pHYs',first)+chunk(b'pHYs',word(30000)+word(40000)+b'\1')))
for t in [b'PLTE',b'IDAT',b'IEND',b'pHYs',b'zTXt',b'tEXt',b'gAMA',b'abcd',bytes(4)]:
    for crc in [0,1,0xffffffff]: add(f'chunk CRC {t.hex()} {crc}',png(extra=chunk(t,b'hello',crc)))
    for data in [b'',b'x',b'Title\0hello',bytes(9)]:
        if t in [b'zTXt']: continue
        add(f'chunk payload {t.hex()} {data.hex()}',png(extra=chunk(t,data)))
for data in [b'',b'x',b'garbage!',zlib.compress(b''),zlib.compress(b'hello'),zlib.compress(b'hello')[:-1],zlib.compress(b'hello')[:-1]+b'!',zlib.compress(b'x'*100000)[:-4]]:
    for key in [b'Title',b'Unknown',b'',b'DpiX']:
        for method in [0,1,255]:
            add(f'compressed text {key!r} {method} {data.hex()}',png(extra=chunk(b'zTXt',key+b'\0'+bytes([method])+data)))
for data in [b'',b'Title',b'Title\0',b'\0',b'hello\0\1',b'hello\0\0']:
    add(f'malformed text {data.hex()}',png(extra=chunk(b'zTXt',data)))
for trailing in [b'',b'garbage',chunk(b'IDAT',b'garbage!',0),header()]:
    add(f'ignored trailer {trailing.hex()}',png()+trailing)
for segments in [[b'garbage!'],[b'gar',b'bage!'],[b'',b'gar',b'',b'bage!',b'']]:
    add('split IDAT '+str(len(segments)),header()+b''.join(chunk(b'IDAT',b) for b in segments)+chunk(b'IEND',b''))
# Full IDAT forcing versus lazy textual payload forcing, including dictionary,
# checksum failures, ignored trailing bytes and concatenated zlib members.
compressed=zlib.compress(bytes(100))
dictionary=zlib.compressobj(zdict=b'hello world')
with_dictionary=dictionary.compress(b'hello world')+dictionary.flush()
streams=[compressed,compressed[:-1],compressed[:-4],compressed[:-1]+b'!',
         compressed+b'junk',compressed+zlib.compress(b'next member'),with_dictionary]
for i,data in enumerate(streams):
    for color,depth in [(0,1),(0,8),(2,8),(3,8),(3,16)]:
        add(f'stream forcing {i} {color} {depth}',png(w=1,h=1,color=color,depth=depth,palette=bytes(6),data=data))
    add(f'text stream forcing {i}',png(extra=chunk(b'zTXt',b'Title\0\0'+data)))
for payload in [b'',b'x',b'xy']:
    packed=zlib.compress(payload)
    for n in range(1,5):
        add(f'short text truncated {len(payload)} {n}',png(extra=chunk(b'zTXt',b'Title\0\0'+packed[:-n])))
    add(f'short text checksum {len(payload)}',png(extra=chunk(b'zTXt',b'Title\0\0'+packed[:-1]+b'!')))
for w,h in [(0,0),(0,1),(1,0),(1,1)]:
    for depth in [0,1,2,3,4,8,16]:
        add(f'empty pass forcing {w} {h} {depth}',png(w=w,h=h,color=3,depth=depth,palette=bytes(6)))
for first,second in [(b'x',bytes(6)),(bytes(6),b'x'),(b'',bytes(6))]:
    for depth in [8,16]:
        add(f'palette precedence {len(first)} {len(second)} {depth}',
            header(w=1,h=1,color=3,depth=depth)+chunk(b'PLTE',first)+chunk(b'PLTE',second)
            +chunk(b'IDAT',compressed)+chunk(b'IEND',b''))
for t in [b'tRNS',b'gAMA',b'tEXt',b'zTXt']:
    for n in [0,1,2,3]:
        data=(b'Title\0\0'+zlib.compress(b'hello')) if t==b'zTXt' else bytes(5)
        add(f'repeated ancillary {t!r} {n}',png(color=3,palette=bytes(6),data=compressed,extra=chunk(t,data)*n))
seeds=[png(),png(extra=chunk(b'pHYs',word(10000)+word(20000)+b'\1')),png(color=3,palette=bytes(6),data=zlib.compress(bytes(100))),png(extra=chunk(b'zTXt',b'Title\0\0'+zlib.compress(b'hello')))]
for i,seed in enumerate(seeds):
    for n in range(4,len(seed)): add(f'truncated {i} {n}',seed[:n])
rng=random.Random(0x504e47)
for n in range(1000):
    data=bytearray(seeds[0])
    # RGB avoids undefined pixel reads. Mutate structural bytes and CRCs,
    # preserving the type gate and the header-scanner bypass.
    for _ in range(rng.randrange(1,5)):
        pos=rng.choice(list(range(4,8))+list(range(12,len(data))))
        data[pos]=rng.randrange(256)
    if n%3==0:data=data[:rng.randrange(4,len(data)+1)]
    add(f'seeded mutation {n}',bytes(data))

with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory);request=temp/'request.json';script=temp/'png.lua'
    script.write_text('''
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_PNG_REQUEST')))
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
    def capture(batch):
        request.write_text(json.dumps(batch))
        proc=subprocess.run(['pandoc','-f','native','-t',str(script)],input=b'[]',capture_output=True,env={**os.environ,'KOTDOC_PNG_REQUEST':str(request)},timeout=60)
        if proc.returncode:
            if len(batch)>1:
                mid=len(batch)//2;return capture(batch[:mid])+capture(batch[mid:])
            err=proc.stderr.decode()
            if batch[0].get('unsafeScanlines') and proc.returncode in [-11,-6]:
                return [dict(undefined='unsafe scanline read', oracleSignal=-proc.returncode)]
            if 'Codec.Compression.Zlib:' in err:
                return [dict(exception='zlib',exceptionMessage=err.split('Codec.Compression.Zlib:',1)[1].splitlines()[0].strip())]
            if 'Impossible bit depth' in err: return [dict(exception='bitDepth',exceptionMessage='Impossible bit depth')]
            raise RuntimeError((batch[0]['name'],proc.returncode,err))
        return json.loads(proc.stdout)
    results=[]
    for i in range(0,len(cases),250):
        results+=capture(cases[i:i+250]);print(f'Captured {len(results)}/{len(cases)}',flush=True)
for c,r in zip(cases,results,strict=True):
    c.update(r)
    if 'error' in r:
        assert '\nPNG ' in r['error'],c
        c['pngError']=r['error'].split('\nPNG ',1)[1].rsplit('\nBitmap ',1)[0]
    if 'source' in c: del c['hex']
manifest=dict(sources=hashes,oracle='pandoc 3.12.1',cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} PNG cases: {sum("size" in c for c in cases)} sizes, {sum("error" in c for c in cases)} errors, {sum("exception" in c for c in cases)} exceptions')
