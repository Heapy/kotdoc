#!/usr/bin/env python3
"""Capture the original Pandoc/JuicyPixels metadata path, without Kotlin code.

Fixtures include upstream GIF files, all parser branches, truncations and seeded
mutations. Full original error aggregates are retained; this stage compares the
GIF decoder's component, not the still-unported generic decoder dispatcher.
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
target = root / 'core/src/test/resources/images/gif-metadata.jsonl.gz'
sources = ['tools/import-gif-metadata-fixtures.py',
           'upstream/pandoc/src/Text/Pandoc/ImageSize.hs',
           'upstream/pandoc/test/Tests/ImageSize.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Image.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/ImageSize.hs',
           'upstream/pandoc/test/bodybg.gif']
for folder in ['vendor/JuicyPixels-3.3.9', 'vendor/JuicyPixels-tests-v3.3.8']:
    sources += sorted(str(p.relative_to(root)) for p in (root / folder).rglob('*') if p.is_file())
hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
p = argparse.ArgumentParser()
p.add_argument('--check-source', action='store_true')
args = p.parse_args()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f))
        cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes
    assert len(cases) == manifest['cases'] and digest(cases) == manifest['caseHash']
    for c in cases:
        if 'source' in c:
            assert hashlib.sha256((root / c['source']).read_bytes()).hexdigest() == c['sourceHash']
    print(f'Verified {len(cases)} independent GIF metadata captures')
    raise SystemExit()

assert subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0] == 'pandoc 3.12.1'
def word(n): return struct.pack('<H', n)
def palette(flags): return bytes(3 * (1 << ((flags & 7) + 1))) if flags & 128 else b''
def header(w=320, h=240, flags=0, background=0, aspect=0, version=b'GIF89a'):
    return version + word(w) + word(h) + bytes([flags, background, aspect]) + palette(flags)
def blocks(data):
    return b''.join(bytes([len(data[i:i+255])]) + data[i:i+255] for i in range(0,len(data),255)) + b'\0'
def frame(w=1, h=1, x=0, y=0, flags=0, lzw=2, data=b''):
    return b',' + word(x) + word(y) + word(w) + word(h) + bytes([flags]) + palette(flags) + bytes([lzw]) + blocks(data)
def control(size=4, flags=0, delay=0, index=0, end=0):
    return b'!\xf9' + bytes([size, flags]) + word(delay) + bytes([index, end])
def extension(label, data=b'hello', prefix=b'header'):
    return b'!' + bytes([label]) + (bytes([len(prefix)])+prefix if label in [1,255] else b'') + blocks(data)
cases = []
def add(name, data, dpi=96, source=None):
    c = dict(name=name, hex=data.hex(), dpi=dpi)
    if source:
        c.update(source=source, sourceHash=hashlib.sha256(data).hexdigest())
    cases.append(c)

for source in ['upstream/pandoc/test/bodybg.gif'] + sorted(str(p.relative_to(root)) for p in (root/'vendor/JuicyPixels-tests-v3.3.8/tests/gif').glob('*.gif')):
    add('original file '+source, (root/source).read_bytes(), source=source)
seeds = [header()+frame()+b';', header(flags=0x87)+frame(flags=0x87)+b';',
         header()+extension(1)+extension(255)+extension(254)+frame(data=b'abcd')+b';',
         header()+control(flags=1)+frame()+control(flags=255)+frame()+b';',
         header()+control()+b';', header()+b';']
for i, seed in enumerate(seeds):
    add(f'parser seed {i}',seed)
    for n in range(4, len(seed)):
        add(f'truncated seed {i} at {n}',seed[:n])
for version in [b'GIF87a', b'GIF89a', b'GIF88a', b'GIF89b', b'GIF8\0\xff', b'GIF8\n\r']:
    add('signature '+version.hex(),header(version=version)+frame()+b';')
for w,h in [(0,0),(0,65535),(65535,0),(65535,65535),(1,2),(256,257)]:
    for fw,fh in [(0,0),(1,2),(65535,65535)]:
        for dpi in [0,1,72,96,300,-1]:
            add(f'dimensions {w} {h} frame {fw} {fh} dpi {dpi}',header(w,h)+frame(fw,fh,x=65535,y=65535)+b';',dpi)
for n in range(256):
    add(f'global flags {n}',header(flags=n,background=255,aspect=n)+frame()+b';')
    add(f'local flags {n}',header()+frame(flags=n)+b';')
    add(f'LZW root {n}',header()+frame(lzw=n,data=b'\xff\x00\xff')+b';')
    add(f'control flags {n}',header()+control(flags=n,index=255,delay=65535)+frame()+b';')
    add(f'control ignored size {n}',header()+control(size=n)+frame()+b';')
    add(f'control ignored terminator {n}',header()+control(end=n)+frame()+b';')
    add(f'block marker {n}',header()+bytes([n])+frame()+b';')
    if n != 249:
        add(f'extension label {n}',header()+extension(n)+frame()+b';')
for length in [0,1,2,254,255,256,510,511,65536]:
    data=bytes(i%256 for i in range(length))
    for label in [1,254,255,0]:
        add(f'subblocks {label} {length}',header()+extension(label,data)+frame()+b';')
    add(f'pixel subblocks {length}',header()+frame(data=data)+b';')
for middle in [control(), control(flags=1)+control(), extension(254), extension(255), control()+extension(1)]:
    for ending in [b';',frame()+b';',frame()+b'\x99',frame()+b';junk']:
        add('association '+str(len(cases)),header()+middle+ending)
for trailing in [b'',b'garbage',b'\0',header()+frame()+b';']:
    add('trailer '+trailing.hex(),header()+frame()+b';'+trailing)
for tail in [frame()[:-1],frame()+b'\x01',frame(flags=0x87)[:-50],control()[:-1],extension(1)[:-1]]:
    add('later malformed '+str(len(cases)),header()+frame()+tail)
rng = random.Random(0x474946)
for n in range(1000):
    data=bytearray(rng.choice(seeds))
    for _ in range(rng.randrange(1,5)):
        data[rng.randrange(4,len(data))]=rng.randrange(256)
    if n%3==0: data=data[:rng.randrange(4,len(data)+1)]
    if n%7==0: data+=rng.randbytes(rng.randrange(30))
    add(f'seeded mutation {n}',bytes(data))

with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory)
    request=temp/'request.json'; request.write_text(json.dumps(cases))
    script=temp/'gif.lua'; script.write_text('''
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_GIF_REQUEST')))
 local cases=pandoc.json.decode(f:read('*a'),false);f:close()
 local rows={}
 for _,c in ipairs(cases) do
  local bytes=c.hex:gsub('..',function(p) return string.char(tonumber(p,16)) end)
  local ok,r=pcall(pandoc.image.size,bytes,{dpi=c.dpi})
  local row={}
  if ok then row.size={tostring(r.width),tostring(r.height),tostring(r.dpi_horz),tostring(r.dpi_vert)}
  else row.error=tostring(r) end
  rows[#rows+1]=row
 end
 return pandoc.json.encode(rows)
end
''')
    results=json.loads(subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',env={**os.environ,'KOTDOC_GIF_REQUEST':str(request)}))
assert len(results)==len(cases)
for c,r in zip(cases,results):
    c.update(r)
    if 'error' in r:
        # Preserve the whole aggregate. Extract only the exact GIF contribution
        # between decoder labels (signatures may themselves contain newlines).
        c['gifError']=r['error'].split('\nGIF ',1)[1].rsplit('\nHDR ',1)[0]
    if 'source' in c: del c['hex']
manifest=dict(sources=hashes,oracle='pandoc 3.12.1',cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} GIF cases: {sum("size" in c for c in cases)} sizes, {sum("error" in c for c in cases)} decoder errors')
