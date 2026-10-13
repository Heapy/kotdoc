#!/usr/bin/env python3
"""Capture the original JPEG Common/FastIdct modules; never use Kotlin results."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/images/jpeg-blocks.jsonl.gz'
sources = ['tools/import-jpeg-block-fixtures.py']
for folder in ['vendor/JuicyPixels-3.3.9', 'tools/haskell-jpeg-block-reference']:
    sources += sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*')
                      if p.is_file() and not any(s in ['dist-newstyle', 'build'] for s in p.parts))
hashes = {p: hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
parser = argparse.ArgumentParser()
parser.add_argument('--check-source', action='store_true')
parser.add_argument('--oracle', default=os.environ.get('KOTDOC_JPEG_BLOCK_REFERENCE'))
args = parser.parse_args()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    print(f'Verified {len(cases)} original JPEG block captures')
    raise SystemExit()
if not args.oracle:
    parser.error('--oracle must name the compiled jpeg-block-reference executable')
info = json.loads(subprocess.check_output([args.oracle, '--reference-info']))
assert info == ['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0','primitive','0.9.1.0'], info
cases = []
def add(name, op, block=None, quant=None, destination=None, options=None, data=b''):
    cases.append(dict(name=name, op=op, block=block if block is not None else [0]*64,
                      quant=quant if quant is not None else [1]*64,
                      destination=destination if destination is not None else [-17]*64,
                      options=options if options is not None else [0], hex=data.hex()))
add('empty allocation', 'empty', block=[], destination=[])
add('block dimensions signed arithmetic', 'blockSize', options=list(range(-130,131))+[-2**63,-2**63+1,2**63-9,2**63-8,2**63-7,2**63-2,2**63-1])
for width in range(-2,11):
    for height in range(-2,11):add(f'raster {width} {height}', 'raster', options=[width,height])
values=[-32768,-16384,-8192,-4096,-2048,-1024,-257,-256,-129,-128,-9,-8,-1,0,1,7,8,127,128,255,256,1024,2047,4096,8191,16384,32767]
for position in range(64):
    for value in values:
        block=[0]*64; block[position]=value
        for op in ['fast','idct']:
            add(f'impulse {position} {value} {op}',op,block)
for value in values:
    for op in ['fast','idct','macro']:
        add(f'uniform {value} {op}',op,[value]*64)
# Exhaustive signed Int16 level-shift overflow, grouped into blocks.
for start in range(-32768,32768,64):
    add(f'level shift Int16 {start}', 'shift', list(range(start,start+64)))
for op in ['zig','forward','forwardInt','forwardByte','forwardValues']:
    for alias in [0,1]:
        for length in [64,65,128]:
            add(f'permutation {op} alias {alias} length {length}',op,
                list(range(length)),destination=[-19]*length,options=[alias])
rng=random.Random(0x4a504742)
for n in range(500):
    length=rng.choice([64,65,80])
    block=[rng.randrange(-32768,32768) for _ in range(length)]
    quant=[rng.randrange(-32768,32768) for _ in range(length)]
    destination=[rng.randrange(-32768,32768) for _ in range(length)]
    for op in ['fast','idct','dequant','macro']:
        add(f'seeded block {n} {op}',op,block,quant,destination,options=[n%2])
    if n<100:
        for op in ['zig','forward']:
            add(f'seeded permutation {n} {op}',op,block,destination=destination,options=[n%2])
        wide=[rng.randrange(-2**31,2**31) for _ in range(length)]
        for op in ['forwardInt','forwardByte','forwardValues']:
            add(f'seeded wide permutation {n} {op}',op,wide,destination=[0]*length,options=[n%2])
for n in range(30):
    add(f'unpacker parameter {n}', 'parameter', block=[rng.randrange(65536) for _ in range(n)],
        quant=[rng.randrange(65536) for _ in range(30-n)],
        options=[rng.choice([-2**63, 2**63-1, rng.randrange(-100,100)]) for _ in range(15)])
patterns=[bytes(100),bytes.fromhex('80ff00ffd07faa55')*13,bytes(range(100))]
for bits in range(1,256):
    for offset in [0,1,7]:
        for pattern,data in enumerate(patterns):
            add(f'signed field {bits} offset {offset} pattern {pattern}','decodeInt',options=[bits,offset,3],data=data)
for symbol in range(256):
    for pattern,data in enumerate(patterns):
        add(f'DC category {symbol} pattern {pattern}','dc',options=[symbol,pattern,3],data=data)
    add(f'AC nibbles {symbol}','ac',options=[symbol,7,3],data=patterns[1])
for kind in ['dcDefault','acDefault']:
    for which in [0,1]:
        for byte in range(256):
            add(f'{kind} {which} byte {byte}',kind,options=[which,0,15],data=bytes([byte])+patterns[1])
for op in ['restart','unpackInt']:
    for bits in [0,1,7,8,15,16,31,32,33,63,64,127,255]:
        for offset in [0,1,7]:add(f'{op} {bits} offset {offset}',op,options=[bits,offset,3],data=patterns[1])
# Pixel duplication, edge clipping, component placement and signed pixel clamping.
for components in [1,2,3,4]:
    for component in sorted({0,components-1}):
        for width,height in [(0,0),(1,1),(7,9),(8,8),(9,7),(15,17),(16,16),(17,15),(32,24)]:
            for wc,hc in [(0,1),(1,0),(-1,2),(2,-1),(1,1),(1,2),(2,1),(2,2),(3,2),(4,4)]:
                for x,y in [(0,0),(1,0),(0,1),(2,2)]:
                    block=[values[i%len(values)] for i in range(64)]
                    add(f'unpack {components} {component} {width}x{height} {wc}x{hc} {x},{y}','unpack',block,
                        options=[components,wc,hc,component,x,y,width,height,173])

def request(c):
    return '('+','.join([json.dumps(c['op']),str(c['block']),str(c['quant']),str(c['destination']),str(c['options']),str(list(bytes.fromhex(c['hex'])))])+')'
for start in range(0,len(cases),100):
    batch=cases[start:start+100]
    result=subprocess.check_output([args.oracle],input=('\n'.join(request(c) for c in batch)+'\n').encode())
    lines=result.splitlines(); assert len(lines)==len(batch)
    for c,line in zip(batch,lines):
        c['expected']=json.loads(line)
        assert 'error' not in c['expected'], (c['name'],c['expected'])
    print(f'Captured {start+len(batch)}/{len(cases)}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} original JPEG block outcomes')
