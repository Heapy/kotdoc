#!/usr/bin/env python3
"""Capture original JuicyPixels full frame assembly, not pixel decoding."""
import argparse,gzip,hashlib,json,os,random,struct,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/jpeg-frames.jsonl.gz'
sources=['tools/import-jpeg-frame-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-jpeg-frame-reference','vendor/JuicyPixels-jpeg-tests-v3.3.8']:
    sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(s in ['dist-newstyle','build'] for s in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_JPEG_FRAME_REFERENCE'));args=p.parse_args()
if args.check_source:
    with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(line) for line in f]
    assert manifest['sources']==hashes
    assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases)
    print(f'Verified {len(cases)} original JPEG frame captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name jpeg-frame-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']))
assert info==['ghc','9.12','JuicyPixels','3.3.9','binary','0.8.9.3']
cases=[]
def add(op,data=b'',ns=(),parts=(),name=''):
    cases.append(dict(name=f'{len(cases)} {op} {name}',op=op,values=list(ns),hex=data.hex(),parts=parts))
ops=['strict','semi','strictEncode','semiEncode','frames','first','kinds','firstInImage','take']
def image(data,name=''):
    for op in ops:add(op,data,[1],name=name)
def word(n):return struct.pack('>H',n&65535)
def segment(code,payload):return bytes([255,code])+word(len(payload)+2)+payload
sof=word(11)+bytes([8])+word(3)+word(5)+bytes([1,1,0x11,0])
sos=word(8)+bytes([1,1,0,0,63,0])
header=b'\xff\xc0'+sof
scan=b'\xff\xda'+sos
q=word(67)+bytes([0])+bytes(range(64))
h=word(20)+bytes([0,1])+bytes(15)+bytes([17])
bad_h=word(22)+bytes([0,3])+bytes(15)+bytes([1,2,3])
jfif=b'JFIF\0'+bytes([1,2,1])+word(72)+word(144)+bytes([0,0])
adobe=b'Adobe'+word(100)+word(0)+word(0)+bytes([1])
tiff=b'II'+struct.pack('<HIHI',42,8,0,0)
frames=[header,scan+b'\x01\x02\xff\x00\x03',b'\xff\xdb'+q,b'\xff\xc4'+h,b'\xff\xdd'+word(4)+word(42),segment(0xe0,jfif),segment(0xe1,b'Exif\0\0'+tiff),segment(0xee,adobe),segment(0xe2,bytes(range(256))),segment(0xf3,b'extension')]
for data in [b'',b'\xff',b'\xff\xd8',b'\xff\xd8\xff',b'\xff\xd8\xff\xd9',b'\xff\xd8junk\xff\xd9tail']:
    image(data,'start/end boundary')
for f in frames:
    whole=b'\xff\xd8'+f+b'\xff\xd9tail'
    image(whole,'single complete frame')
    for n in range(len(whole)+1):
        for op in ['strict','semi','first','kinds']:add(op,whole[:n],name=f'frame truncation {n}/{len(whole)}')
    add('frame',f[1:]+b'\xff\xd9tail')
# Every marker byte with multiple payload classes; unknown markers fail before payload.
for code in range(256):
    for payload in [b'',sof,word(2),sos+b'abc',q,h]:
        add('frame',bytes([code])+payload+b'\xff\xd9')
# Negative getByteString sizes, short/surplus payload, optional metadata parse failures.
for code in list(range(0xe0,0xf0))+list(range(0xf0,0xfe)):
    for size in [0,1,2,3,4,16,65535]:
        image(b'\xff\xd8'+bytes([255,code])+word(size)+b'abc\xff\xd9',f'APP/extension {code} size {size}')
for code,payload in [(0xe0,jfif),(0xe1,b'Exif\0\0'+tiff),(0xee,adobe)]:
    for i in range(len(payload)):
        corrupted=payload[:i]+bytes([payload[i]^255])+payload[i+1:]
        image(b'\xff\xd8'+segment(code,corrupted)+header+b'\xff\xd9','optional metadata corrupt byte')
# Eager prefix errors versus discarded post-SOS failures, empty ECS, stuffing/restarts.
tails=[b'',b'\xff',b'\xff\xd9',b'\xff\xd9junk',b'\xff\xdb\0\x02\xff\xd9',b'\xff\xe2\0\x03a\xff\x00',b'\xff\xc4'+bad_h+b'\xff\xd9',b'\xff\xda'+sos+b'next\xff\xd9',b'\xff\xe2\0\x03a\xff\xda'+sos+b'next\xff\xd9']
for ecs in [b'',b'a',b'abc',b'\xff\0',b'\xff\xd0x',b'abc\xff\xff']:
    for tail in tails:image(b'\xff\xd8'+header+scan+ecs+tail,'scan continuation')
for prefix in [header,b'',b'\xff\xc4'+bad_h,header+b'\xff\xc4'+bad_h]:
    for tail in [b'\xff\xd9',scan+b'abc\xff\xd9',b'\xff\x00']:
        data=b'\xff\xd8'+prefix+tail;image(data,'lazy Huffman or prefix failure')
        for n in [-1,0,1,2,3,20]:add('take',data,[n])
# Serializers exercise every constructor, including writer-only out-of-domain APP codes.
parts=[['app',2,[],list(range(256))],['extension',3,[],[255,0,128]],['adobe',0,[100,65535,17,2],[]],['jfif',42,[2,72,144],[]],['exif',0,[],list(tiff)],['quant',0,[],list(q)],['huff',0,[0,32768,65535],list(h)],['scans',192,list(sof),[]],['blob',0,list(sos),[0,255,0,1]],['restart',65535,[],[]]]
for part in parts:add('write',parts=[part])
add('write',parts=parts);add('write',parts=[])
for code in range(256):
    for kind in ['app','extension']:add('write',parts=[[kind,code,[],[0,1,127,128,255]]])
for size in [0,1,2,3,255,256,65534,65535,65536]:
    add('writeOnly',parts=[['app',15,[],[i%256 for i in range(size)]]],name=f'payload length {size}')
for vals in [list(range(64)),[-2**63,2**63-1,-32768,32767,0,-1,99999,-99999]*8,list(range(80))]:
    for op in ['printMutable','printPure']:add(op,ns=vals)
rng=random.Random(0x4a504547)
for n in range(250):
    fs=rng.choices(frames,k=rng.randrange(1,8))
    data=b'\xff\xd8'+b''.join(fs)+b'\xff\xd9'
    if n%2:
        i=rng.randrange(len(data));data=data[:i]+bytes([rng.randrange(256)])+data[i+1:]
    image(data,'seeded frame composition')
def request(c):
    parts='['+','.join('('+','.join([json.dumps(f[0]),str(f[1]),str(f[2]),str(f[3])])+')' for f in c['parts'])+']'
    return '('+','.join([json.dumps(c['op']),str(c['values']),json.dumps(c['hex']),parts])+')'
for start in range(0,len(cases),50):
    batch=cases[start:start+50]
    lines=subprocess.check_output([args.oracle],input=('\n'.join(map(request,batch))+'\n').encode()).splitlines()
    assert len(lines)==len(batch)
    for c,line in zip(batch,lines):c['expected']=json.loads(line)
    if start%1000==0:print(f'Captured {start+len(batch)}/{len(cases)} synthetic',flush=True)
# Hash full original outputs, retaining input photographs verbatim and bounded fixture size.
with tempfile.TemporaryDirectory() as tmp:
    for source in sorted((root/'vendor/JuicyPixels-jpeg-tests-v3.3.8/tests/jpeg').glob('*.jpg')):
        for op in ['strict','semi','first','kinds','firstInImage','take']:
            add(op,ns=[1],name=source.name);c=cases[-1];c['source']=str(source.relative_to(root));c['sourceHash']=hashes[c['source']]
            prefix=Path(tmp)/'output'
            result=json.loads(subprocess.check_output([args.oracle,'--file',op,str(source),str(prefix)]))
            for flag,suffix,key in [('hasShow','.show','show'),('hasEncoded','.bin','encoded')]:
                if result.pop(flag,False):
                    raw=Path(str(prefix)+suffix).read_bytes();result[key+'Hash']=hashlib.sha256(raw).hexdigest();result[key+'Length']=len(raw)
            c['expected']=result
        print(f'Captured original {source.name}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} original JPEG frame results')
