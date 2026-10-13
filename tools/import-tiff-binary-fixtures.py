#!/usr/bin/env python3
"""Capture full original TIFF/Exif typed parsing and serialization independently."""
import argparse,gzip,hashlib,json,os,random,re,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/tiff-binary.jsonl.gz'
prior=root/'core/src/test/resources/images/tiff-metadata.jsonl.gz'
sources=['tools/import-tiff-binary-fixtures.py',str(prior.relative_to(root)),'vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','tools/haskell-tiff-binary-reference','vendor/JuicyPixels-tiff-tests-v3.3.8','vendor/JuicyPixels-exif-tests-v3.3.8']:
    sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and not any(s in ['dist-newstyle','build'] for s in p.parts))
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases): return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_TIFF_BINARY_REFERENCE'));args=p.parse_args()
if args.check_source:
    with gzip.open(target,'rt') as f: manifest=json.loads(next(f));cases=[json.loads(line) for line in f]
    assert manifest['sources']==hashes
    assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases)
    print(f'Verified {len(cases)} original TIFF binary captures');raise SystemExit()
if not args.oracle:p.error('--oracle must name tiff-binary-reference')
info=json.loads(subprocess.check_output([args.oracle,'--reference-info']))
assert info==['ghc','9.12','JuicyPixels','3.3.9','binary','0.8.9.3']
cases=[]
def add(op,en=0,ns=(),data=b'',rows=(),name=''):
    cases.append(dict(name=f'{len(cases)} {op} {name}',op=op,endian=en,values=list(ns),hex=data.hex(),rows=rows))
def field(tag=65000,typ=4,count=1,offset=0,kind='none',values=()): return [tag,typ,count,offset,kind,list(values)]
def trunc(op,en,data):
    for n in range(len(data)+1):add(op,en,data=data[:n],name=f'truncated {n}/{len(data)}')
for start in range(0,65536,256):
    ns=list(range(start,start+256));add('tag',ns=ns+[-n-1 for n in ns])
tag_source=(root/'vendor/JuicyPixels-3.3.9/src/Codec/Picture/Metadata/Exif.hs').read_text()
known=[int(n) for n in re.findall(r'^\s+(\d+) -> Tag',tag_source,re.M)]
add('tag',ns=known[::-1]+[-n-1 for n in known]+[0,65535],name='all constructors versus numeric order')
for op in ['planar','compression','sample','color','predictor']:
    for n in list(range(20))+[32772,32773,32774,65535,2**31-1,2**31,2**32-1]:add(op,ns=[n],data=b'\xad\xff')
for n in range(3):add('extra',ns=[n])
for depth in [0,1,10,100,5000,20000]:add('deepEq',ns=[depth])
for en in [0,1]:
    endian='<' if en==0 else '>'
    def pack(fmt,*ns):return struct.pack(endian+fmt,*ns)
    for op,data in [('endian',b'II'),('endian',b'MM'),('header',(b'II' if en==0 else b'MM')+pack('HI',42,8)),('word16',bytes.fromhex('1234')),('word32',bytes.fromhex('12345678')),('int32',bytes.fromhex('fedcba98')),('field',pack('HHII',65000,3,2,0xffff0001)),('directory',pack('H',1)+pack('HHII',65000,4,1,0xffffffff)+pack('I',0xffffffff))]:trunc(op,en,data)
    for n in [0,1,7,8,9,65535,2**31,2**32-1]:add('writeHeader',en,[n])
    for code in list(range(20))+[255,65535]:add('type',en,data=pack('H',code)+b'\xad')
    for magic in [0,41,42,43,65535]:add('header',en,data=(b'II' if en==0 else b'MM')+pack('HI',magic,8))
    for typ in range(1,13):
        for count in [0,1,2,3,4,5,16]:
            for offset in [0,1,7,8,15,16,31,32,127,128,2**32-1]:
                for pos in [0,8,16]:
                    add('extended',en,[pos,128],bytes(range(128)),[[field(typ=typ,count=count,offset=offset)]])
    for maxi in [-1,0,1,7,8,15,16,127,128,129,2**32,2**32+1]:
        add('extended',en,[8,maxi],bytes(range(128)),[[field(typ=2,count=8,offset=16)]])
    # All ExifData constructors, independent of TIFF field type/count, exercise the writer.
    variants=[('none',[]),('long',[2**32-1]),('short',[65535]),('rational',[1,0]),('rational',[2**32-1,17]),('signed',[-2**31,2**31-1]),('ifd',[256,1,34665,2,65000,3])]
    for size in [0,1,2,3,4,5,16,255,256]:
        for kind in ['string','undefined','shorts','longs']:
            variants.append((kind,[i%256 if kind in ['string','undefined'] else [0,1,65535,32768][i%4] if kind=='shorts' else [0,1,2**31,2**32-1][i%4] for i in range(size)]))
    for kind,values in variants:
        f=field(65000,7,123,456,kind,values)
        for op in ['writeField','writeExtended','writeDirectory','writeExif']:add(op,en,rows=[[f]])
        for offset in [0,8,2**32-17]:add('writeDocument',en,[offset],b'\xad\x01',[[f]])
    # Declared field counts and inline values are not rewritten from extended data.
    mixed=[field(37385,3,1,0,'short',[0]),field(272,2,10,0,'string',list(b'SM-G955F\0')),field(34665,4,1,0),field(282,5,1,123,'rational',[300,1]),field(256,4,1,4032,'long',[4032]),field(65000,7,3,0,'undefined',[255,0,128]),field(-34666,4,1,12)]
    for order in [mixed,mixed[::-1]]:
        add('writeExif',en,rows=[order]);add('writeDirectory',en,rows=[order])
        for dirs in [[],[[]],[order],[order,[]],[order,order[::-1]]]:add('writeDocument',en,[8],rows=dirs)
    # Explicit circular/backward Exif redirects terminate at the shared forward cursor.
    for targetOffset in [0,8,26,44,62,2**32-1]:
        data=(b'II' if en==0 else b'MM')+pack('HIH',42,8,1)+pack('HHII',34665,4,1,targetOffset)+pack('I',0)+pack('H',1)+pack('HHII',34665,4,1,8)+pack('I',0)
        add('document',en,data=data)
        add('parseExif',en,data=b'Exif\0\0'+data)
# Reuse the independently generated input corpus, capturing fresh full typed outcomes.
with gzip.open(prior,'rt') as f:
    next(f)
    for line in f:
        c=json.loads(line)
        add('document',data=bytes.fromhex(c.get('hex','')),name=c['name'])
        if 'source' in c: cases[-1].update(source=c['source'],sourceHash=c['sourceHash'])
        elif len(c['hex'])<=32000 and (len(cases)%37==0 or any(word in c['name'] for word in ['nested Exif','subIFD','parser seed'])):
            add('parseExif',data=b'Exif\0\0'+bytes.fromhex(c['hex']),name=c['name'])
# Verbatim APP1 payload from the original metadataReadTest photograph.
photo=(root/'vendor/JuicyPixels-exif-tests-v3.3.8/tests/jpeg/10x8-samsung-s8.jpg').read_bytes()
assert photo[:2]==b'\xff\xd8'
pos=2;app1=[]
while pos<len(photo):
    assert photo[pos]==255
    code=photo[pos+1];pos+=2
    if code in [0xda,0xd9]:break
    length=int.from_bytes(photo[pos:pos+2],'big');payload=photo[pos+2:pos+length];pos+=length
    if code==0xe1:app1.append(payload)
assert app1
for payload in app1:
    add('parseExif',data=payload,name='original Samsung metadataReadTest APP1')
    add('document',data=payload[6:],name='original Samsung TIFF fields')
    for n in range(len(payload)+1):add('parseExif',data=payload[:n],name=f'Samsung APP1 truncation {n}')
# APP1 failure swallowing, signature boundaries and a valid empty directory.
empty=b'II'+struct.pack('<HIHI',42,8,0,0)
for n in range(6+len(empty)+1):add('parseExif',data=(b'Exif\0\0'+empty)[:n])
for sig in [b'exif\0\0',b'Exif\0\1',b'Exif\0\0',b'Exif00']:add('parseExif',data=sig+empty)
rng=random.Random(0x54494646)
for n in range(250):
    rows=[]
    for d in range(rng.randrange(1,4)):
        fs=[]
        for i in range(rng.randrange(10)):
            kind,values=rng.choice(variants)
            fs.append(field(rng.choice([256,257,272,282,33432,34665,34853,37385,65000]),rng.randrange(1,13),rng.randrange(2**32),rng.randrange(2**32),kind,values))
        rows.append(fs)
    add('writeDocument',n%2,[rng.randrange(2**32)],bytes(rng.randrange(256) for _ in range(n%7)),rows)
    add('writeExif',n%2,rows=[sum(rows,[])])

def request(c):
    def f(row):return '('+','.join([str(x) for x in row[:4]]+[json.dumps(row[4]),str(row[5])])+')'
    rows='['+','.join('['+','.join(map(f,row))+']' for row in c['rows'])+']'
    return '('+','.join([json.dumps(c['op']),str(c['endian']),str(c['values']),json.dumps((root/c['source']).read_bytes().hex() if 'source' in c else c['hex']),rows])+')'
# Small batches bound original Haskell parsing of large pixel-containing TIFF inputs.
for start in range(0,len(cases),25):
    batch=cases[start:start+25]
    result=subprocess.check_output([args.oracle],input=('\n'.join(map(request,batch))+'\n').encode()).splitlines();assert len(result)==len(batch)
    for c,line in zip(batch,result):
        c['expected']=json.loads(line)
        assert 'exception' not in c['expected'],(c['name'],c['expected'])
    if start%250==0:print(f'Captured {start+len(batch)}/{len(cases)}',flush=True)
manifest=dict(sources=hashes,oracle=info,cases=len(cases),caseHash=digest(cases))
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} original TIFF/Exif results')
