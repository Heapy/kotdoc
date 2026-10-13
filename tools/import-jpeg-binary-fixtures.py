#!/usr/bin/env python3
"""Capture JPEG Binary instances and ECS parsers directly from pinned Haskell."""
import argparse, gzip, hashlib, json, os, random, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/jpeg-binary.jsonl.gz'
sources=['tools/import-jpeg-binary-fixtures.py']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-jpeg-binary-reference']:
    sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(s in ['dist-newstyle','build'] for s in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases): return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
p=argparse.ArgumentParser(); p.add_argument('--check-source',action='store_true'); p.add_argument('--oracle',default=os.environ.get('KOTDOC_JPEG_BINARY_REFERENCE')); args=p.parse_args()
if args.check_source:
    with gzip.open(target,'rt') as f: manifest=json.loads(next(f)); cases=[json.loads(line) for line in f]
    assert manifest['sources']==hashes
    assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases)
    print(f'Verified {len(cases)} original JPEG binary captures'); raise SystemExit()
if not args.oracle: p.error('--oracle must name jpeg-binary-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']))
assert info==['ghc','9.12','JuicyPixels','3.3.9','binary','0.8.9.3']
cases=[]
def add(op,ns=(),rows=(),name=''):
    cases.append(dict(name=f'{len(cases)} {op} {name}',op=op,values=list(ns),rows=[list(r) for r in rows]))
def trunc(op,data):
    for n in range(len(data)+1): add(op,data[:n],name=f'truncated {n}/{len(data)}')
    add(op,list(data)+[173,255,217],name='trailing bytes')
def u16(n): return [n>>8,n&255]
for op in ['kind','unit','transform','skipMarker']:
    add(op)
    for n in range(256): add(op,[n,173,255])
for n in range(256):
    add('component',[n,n,255-n,173]); add('scanSpec',[n,n,173])
    for op in ['writeApp','writeExtension','writeRestartEnd']: add(op,[n])
    add('writeComponent',[n,n,255-n,n])
    add('writeSpec',[n,n,255-n])
    add('jfif',[74,70,73,70,0,1,n,n,0,n,n,0,n,n,173])
    add('jfif',[74,70,73,70,0,n,3,1,0,72,0,96,0,0])
    add('jfif',[74,70,73,70,0,n,2,2,0,72,0,96,0,0])
    add('adobe',[65,100,111,98,101,0,100,n,255-n,255-n,n,n,173])
    add('quant',[n]+[i%256 for i in range(128)]+[173])
    sizes=[0]*16; sizes[n%16]=n%9
    add('huff',[n]+sizes+list(range(n%9))+[173])
    add('restart',u16(n)+[255,255,173])
for op,data in [('component',[1,34,0]),('scanSpec',[1,17]),('jfif',[74,70,73,70,0,1,2,1,0,72,0,96,3,2]),('adobe',[65,100,111,98,101,0,100,0,0,0,1,1]),('frame',[0,17,8,0,16,0,24,3,1,34,0,2,17,1,3,17,1]),('scan',[0,12,3,1,0,2,17,3,17,0,63,0]),('quant',[0]+list(range(64))),('quant',[16]+list(range(128))),('huff',[0,1]+[0]*15+[42]),('restart',[0,4,255,254]),('skipUntil',[255,216,0,1,2,255,217]),('skipMarker',[255])]: trunc(op,data)
for op,signature in [('jfif',[74,70,73,70,0]),('adobe',[65,100,111,98,101])]:
    for i in range(5):
        data=signature.copy(); data[i]^=1; add(op,data+[0]*30)
for count in [0,1,2,3,4,15,255]:
    for length in [0,1,2,6,8,11,17,255,65535]:
        frame=u16(length)+[8,0,16,0,32,count]+[1,34,0]*count
        scan=u16(length)+[count]+[1,17]*count+[0,63,0]
        add('frame',frame+[173]*16); add('scan',scan+[173]*16)
        add('writeFrame',[length,8,16,32,count],[[1,34,255,0]]*3)
        add('writeScan',[length,count,0,63,255,128],[[1,128,255]]*3)
for precision in [0,1,2,15,16,127,255]:
    for length in [0,1,63,64,65]:
        add('writeQuant',[precision,255],[[[-32768,-1,0,32767][i%4] for i in range(length)]])
for op,body in [('quantList',[0]+list(range(64))),('quantList',[32]+list(range(128))),('huffList',[0,1]+[0]*15+[42])]:
    for repeats in [0,1,2,3]:
        for delta in [-3,-2,-1,0,1,2,3]:
            length=2+len(body)*repeats+delta
            if length>=0: add(op,u16(length)+body*repeats+[173]*5)
    trunc(op,u16(2+len(body))+body)
for n in [0,1,99,100,101,65535]:
    add('adobe',[65,100,111,98,101]+u16(n)+[0]*5)
    for tr in [0,1,2]: add('writeAdobe',[n,65535,32768,tr])
for unit in [0,1,2]:
    for thumb in [0,1,-1,-2**63,2**63-1]: add('writeJfif',[unit,65535,32768,int(thumb!=0),thumb])
for size in [0,1,15,16,17]:
    for codeLength in [0,1,15,16,17]:
        for nonzero in [0,1]: add('writeHuff',[1,255],[[nonzero]*size]+[[1,2,3]]*codeLength)
rng=random.Random(0x4a504742494e)
for n in range(250):
    components=[[rng.randrange(256) for _ in range(4)] for _ in range(rng.randrange(5))]
    add('writeFrame',[rng.randrange(65536),rng.randrange(256),rng.randrange(65536),rng.randrange(65536),rng.randrange(256)],components)
    add('writeScan',[rng.randrange(65536)]+[rng.randrange(256) for _ in range(5)],[c[:3] for c in components])
    sizes=[rng.randrange(5) for _ in range(16)]
    add('huff',[rng.randrange(256)]+sizes+[rng.randrange(256) for _ in range(sum(sizes))])
    add('writeHuff',[n%2,rng.randrange(256)],[sizes]+[[rng.randrange(256) for _ in range(s+1)] for s in sizes])
# Include every two-byte FF successor, all marker offsets and every split point.
for nextByte in range(256):
    for prefix in [[],[42],[42,43,44]]:
        data=prefix+[255,nextByte,17,255,217,173]
        for split in range(len(data)+1):
            rows=[data[:split],[],data[split:]]
            for op in ['ecs','ecsSimple']: add(op,rows=rows)
for data in [[],[255],[42],[42,43,255],[255,0],[255,208],[255,255],[255,217]]:
    for rows in [[data],[[b] for b in data]]:
        for op in ['ecs','ecsSimple']: add(op,rows=rows)
for n in range(300):
    data=[rng.randrange(256) for _ in range(rng.randrange(200))]
    if n%2: data += [255,217,173]
    rows=[]
    while data:
        size=rng.randrange(1,15); rows.append(data[:size]); data=data[size:]
    for op in ['ecs','ecsSimple']: add(op,rows=rows)
for start in range(0,len(cases),250):
    batch=cases[start:start+250]
    requests=['('+','.join([json.dumps(c['op']),str(c['values']),str(c['rows'])])+')' for c in batch]
    result=subprocess.check_output([args.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines(); assert len(result)==len(batch)
    for c,line in zip(batch,result): c['expected']=json.loads(line)
    print(f'Captured {start+len(batch)}/{len(cases)}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
