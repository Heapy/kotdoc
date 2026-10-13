#!/usr/bin/env python3
"""Capture unmodified JuicyPixels BitWriter with GHC, independently of Kotlin."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess

root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/bitstream.jsonl.gz'
sources=['tools/import-image-bitstream-fixtures.py']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-reference']:
    sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(part in ['dist-newstyle','build'] for part in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_BITSTREAM_REFERENCE'));args=p.parse_args()
if args.check_source:
    with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(line) for line in f]
    assert manifest['sources']==hashes
    assert len(cases)==manifest['cases'] and digest(cases)==manifest['caseHash']
    print(f'Verified {len(cases)} original JuicyPixels bitstream captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name the compiled tools/haskell-reference bitstream-reference executable')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']))
assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0','primitive','0.9.1.0'],info
cases=[]
def op(name,n=0,bits=0,data=b''):return [name,n,bits,data.hex()]
def add(name,mode,data,ops):cases.append(dict(name=name,mode=mode,hex=data.hex(),ops=ops))
for mode in ['lsb','msb','jpeg']:
    for byte in range(256):
        for n in [0,1,7,8,9,16,31,32]:
            add(f'{mode} byte {byte} width {n}',mode,bytes([byte]),[op(mode,n),op(mode,32),op(mode,8)])
    for offset in range(8):
        for n in range(33):
            add(f'{mode} offset {offset} width {n}',mode,bytes.fromhex('123456789abcdef0ff00ffd080f00f'),[op(mode,offset),op('save'),op(mode,n),op(mode,32),op('restore'),op(mode,n)])
    for data in [b'',b'\xff',b'\xff\x00',b'\xff\xd0',b'\xff\xff',bytes(20),bytes(range(256))]:
        add(f'{mode} stream {data.hex()}',mode,data,[op(mode,n) for n in range(33)])
for mode in ['lsb','msb','jpeg']:
    for n in range(33,257):
        data=bytes((i*37+128)%256 for i in range(64))
        add(f'{mode} wide field {n}',mode,data,[op(mode,n%8),op(mode,n),op(mode,32)])
for marker in range(256):
    data=b'\xff'+bytes([marker])+b'\xa5\xff\0\x5a'
    for offset in [0,1,3,7,8,9]:
        add(f'JPEG marker {marker} align offset {offset}','jpeg',data,[op('jpeg',offset),op('align'),op('jpeg',8),op('bit'),op('jpeg',23)])
for initial in ['default','empty','lsb','msb','jpeg']:
    for setter in ['setLsb','setMsb','setJpeg']:
        for data in [b'',b'\x80',b'\xff',b'\xff\x01',b'\xff\x00\x01']:
            mode={'setLsb':'lsb','setMsb':'msb','setJpeg':'jpeg'}[setter]
            add(f'reset {initial} {setter} {data.hex()}',initial,b'\xf0',[op(setter,data=data),op(mode,3),op(mode,32),op(mode,1)])
for data in [b'',b'\xff\xd0',b'\xff\xd0\xff',b'\xff\xd0'*10000+b'\xa5',b'\xff\xd0'*10000]:
    add(f'JPEG marker chain {len(data)} {data[-4:].hex()}','jpeg',data,[op('bit')]*16+[op('jpeg',32),op('align'),op('jpeg',8)])
rng=random.Random(0x424954)
for n in range(1000):
    mode=rng.choice(['lsb','msb','jpeg']);data=rng.randbytes(rng.randrange(65));ops=[]
    for _ in range(rng.randrange(1,70)):
        if mode=='jpeg' and rng.randrange(5)==0:ops.append(op(rng.choice(['align','bit'])))
        else:ops.append(op(mode,rng.randrange(33)))
    add(f'seeded reader {n}',mode,data,ops)
for mode in ['jpeg','gif']:
    finish='finishJpeg' if mode=='jpeg' else 'finishGif'
    for bits in range(33):
        for value in [0,1,0x80,0xff,0xffff,0x80000000,0xffffffff,0x12345678]:
            for prefix in [0,1,7]:
                add(f'{mode} writer prefix {prefix} bits {bits} value {value}','writer',b'',[op(mode,5,prefix),op(mode,value,bits),op(finish)])
    for bits in range(1,9):
        add(f'{mode} finalize state {bits}','writer',b'',[op(mode,0xff,bits),op(finish),op(finish),op(mode,3,2),op(finish),op(mode,0xff,8),op(finish)])
    # Original writer flushes a 256 KiB buffer. Check both sides and stuffing
    # that straddles the boundary; use compact repeat input for these cases.
    for count in ([32767,32768,32769,65537] if mode=='jpeg' else [65535,65536,65537,131073]):
        c=dict(name=f'{mode} output buffer boundary {count}',mode='writer',hex='',ops=[op(mode,0xffffffff,32),op(finish)],prefix=[op(mode,1,8)],repeat=count)
        cases.append(c)
for n in range(1000):
    ops=[]
    for _ in range(rng.randrange(1,70)):
        if rng.randrange(10)==0:ops.append(op(rng.choice(['finishJpeg','finishGif'])))
        else:ops.append(op(rng.choice(['jpeg','gif']),rng.getrandbits(32),rng.randrange(33)))
    ops.append(op(rng.choice(['finishJpeg','finishGif'])))
    add(f'seeded mixed writer {n}','writer',b'',ops)

def request(c):
    ops=c['ops']
    if 'repeat' in c:ops=c['prefix']+[ops[0]]*c['repeat']+ops[1:]
    tuples=[f'({json.dumps(name)},{n},{bits},{list(bytes.fromhex(data))})' for name,n,bits,data in ops]
    return f'({json.dumps(c["mode"])},{list(bytes.fromhex(c["hex"]))},[{",".join(tuples)}])'
for start in range(0,len(cases),200):
    batch=cases[start:start+200]
    result=subprocess.check_output([args.oracle],input=('\n'.join(request(c) for c in batch)+'\n').encode())
    rows=result.splitlines();assert len(rows)==len(batch)
    for c,line in zip(batch,rows):
        result=json.loads(line)
        if c['mode']=='writer':
            # Store exact output bytes for ordinary cases; only large buffer
            # boundary cases use independent length+SHA256 to keep fixtures small.
            if 'repeat' in c:c['outputHashes']=[dict(length=len(bs),sha256=hashlib.sha256(bytes(bs)).hexdigest()) for bs in result]
            else:c['outputs']=[bytes(bs).hex() for bs in result]
        else:c['values']=result
    print(f'Captured {start+len(batch)}/{len(cases)}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} original bitstream outcomes')
