#!/usr/bin/env python3
"""Capture JPEG table behavior directly from the unchanged JuicyPixels module."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/images/jpeg-tables.jsonl.gz'
sources = ['tools/import-jpeg-table-fixtures.py']
for folder in ['vendor/JuicyPixels-3.3.9', 'tools/haskell-jpeg-reference']:
    sources += sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*')
                      if p.is_file() and not any(s in ['dist-newstyle', 'build'] for s in p.parts))
hashes = {p: hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
parser = argparse.ArgumentParser()
parser.add_argument('--check-source', action='store_true')
parser.add_argument('--oracle', default=os.environ.get('KOTDOC_JPEG_TABLE_REFERENCE'))
args = parser.parse_args()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    print(f'Verified {len(cases)} original JPEG table captures')
    raise SystemExit()
if not args.oracle:
    parser.error('--oracle must name the compiled jpeg-tables-reference executable')
info = json.loads(subprocess.check_output([args.oracle, '--reference-info']))
assert info == ['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0','primitive','0.9.1.0'], info
cases = []
def add(name, op, which='custom', rows=None, values=None, quality=0, data=b'', count=0):
    cases.append(dict(name=name, op=op, which=which, rows=rows or [], values=values or [], quality=quality, hex=data.hex(), count=count))
def operations(name, which='custom', rows=None, values=None, pack=True):
    for op in ['tree', 'inverse'] + (['pack'] if pack else []):
        add(name+' '+op, op, which, rows, values)

for which in ['dc-luma','dc-chroma','ac-luma','ac-chroma']:
    add(which+' original table', 'table', which)
    operations(which, which)
    for byte in range(256):
        add(f'{which} byte {byte}', 'decode', which, data=bytes([byte,0,255,0,255,208,165,90]), count=25)
    for size in [0,1,7,8,63,64,255]:
        add(f'{which} EOF {size}', 'decode', which, data=bytes(size), count=100)
for which in ['luma','chroma']:
    add(which+' original quant', 'quant', which)
    for q in list(range(-10,111))+[-2**63,2**31-1,2**32,2**62-1,2**62,2**63-1]:
        add(f'{which} quality {q}', 'scale', which, quality=q)
for size in [0,1,2,7,8,16,32,63,64,65,100,256]:
    add(f'macro block length {size}', 'macro', values=list(range(size)))
for depth in range(1,33):
    for value in [0,1,127,254,255]:
        operations(f'single symbol {value} depth {depth}', rows=[[]]*(depth-1)+[[value]])
for rows in [[],[[]],[[],[]],[[0,1]],[[0,0]],[[255,255]],[[0],[1,2]],[[0],[],[1,2,3,4]],[[0,1,2]],[[0,1],[2]],[[],[0,1,2,3,4]]]:
    operations(f'edge rows {rows}', rows=rows)
    if sum(map(len,rows)) <= 2:
        add(f'edge decode {rows}', 'decode', rows=rows, data=b'\xff\x00\xa5', count=20)
for value in range(256):
    operations(f'root leaf {value}', 'direct', values=[value])
for left,right in [([-2],[-2]),([-2],[17]),([18],[-2]),([17],[18]),([-1,0,1],[-2]),([-2],[-1,2,3]),([-1,0,1],[-1,2,3])]:
    operations(f'branch shape {left} {right}', 'direct', values=[-1]+left+right)
for depth in [15,16,17,31,32,63,64,127,128,254,255,256,257,300]:
    for right in [False,True]:
        tree=[7]
        for _ in range(depth):tree=([-1,-2]+tree) if right else ([-1]+tree+[-2])
        operations(f'deep direct {depth} right {right}', 'direct', values=tree, pack=depth<128)

rng = random.Random(0x4a504547)
for n in range(300):
    slots=1; rows=[]; remaining=rng.randrange(1,150)
    for depth in range(1,17):
        slots*=2
        size=rng.randrange(min(slots,remaining,12)+1)
        rows.append([rng.randrange(256) for _ in range(size)])
        slots-=size; remaining-=size
        if slots==0 or remaining==0:break
    operations(f'seeded canonical {n}', rows=rows)
    add(f'seeded canonical decode {n}', 'decode', rows=rows, data=rng.randbytes(rng.randrange(80)), count=100)
for n in range(300):
    def tree(depth=0):
        if depth==7 or rng.randrange(3)==0:return [rng.randrange(255)] if rng.randrange(3) else [-2]
        return [-1]+tree(depth+1)+tree(depth+1)
    values=tree()
    operations(f'seeded direct {n}', 'direct', values=values)
    add(f'seeded direct decode {n}', 'decode', 'direct', values=values, data=rng.randbytes(40), count=100)
# Packed flags: missing bit takes precedence over the leaf bit; leaf payload is low 8 bits.
for value in [0x8000,0xffff,0xc000,0x4000,0x4001,0x40ff,0x7fff]:
    for byte in [0,128,255]:
        add(f'packed flag {value} {byte}', 'decode', 'packed', values=[value,value], data=bytes([byte,0]), count=12)
# All signed Int16 coefficients, with 64-bit overflow and floor-division boundaries.
for q in [-1,0,1,49,50,99,100,101,2**31-1,2**32,2**62-1,2**62,2**63-1]:
    add(f'all Int16 quality {q}', 'scale', values=list(range(-32768,32768)), quality=q)
for n in range(300):
    add(f'seeded quantisation {n}', 'scale', values=[rng.randrange(-32768,32768) for _ in range(rng.randrange(130))],
        quality=rng.choice([rng.randrange(-100,200),rng.randrange(-2**63,2**63)]))

def request(c):
    return '('+','.join([json.dumps(c['op']),json.dumps(c['which']),str(c['rows']),str(c['values']),str(c['quality']),str(list(bytes.fromhex(c['hex']))),str(c['count'])])+')'
for start in range(0,len(cases),100):
    batch=cases[start:start+100]
    result=subprocess.check_output([args.oracle],input=('\n'.join(request(c) for c in batch)+'\n').encode())
    lines=result.splitlines(); assert len(lines)==len(batch)
    for c,line in zip(batch,lines):c['expected']=json.loads(line)
    print(f'Captured {start+len(batch)}/{len(cases)}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} original JPEG table outcomes')
