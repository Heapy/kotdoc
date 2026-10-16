#!/usr/bin/env python3
"""Pinned PEM/Base64 and raw ASN.1 original modules, incremental traces and tests."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile,base64
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-certificate-binary-reference';target=root/'core/src/test/resources/certificate-binary/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
pins={'base64-1.0': '7942239f1804945fd6d319a953f26c53b64518076cd294141fda983f2ff1b2b6', 'crypton-pem-0.3.0': '1fadab8cba74c6acd74873f06415b4a2a7c84b16b43425217a7beb0ec5830540', 'crypton-asn1-types-0.4.1': '61efc63877a0d3ddb992a793839d9427c1597e095a6e1f63c04e05fd3e4e302d', 'crypton-asn1-encoding-0.10.0': '49813a0ee253f628cb8102dd6857905609b52e490c73c554d7cdf0cbce2926d2', 'crypton-asn1-parse-0.10.0': '35c0f278d8fcb720b4ad89c2a7f256f7fb098b9f71a0aeb493a4c3c0fe7728d9', 'crypton-x509-1.9.2': '7da68d3b512dbb42261f37f16337741677f40409d33fe6b3b508e49f861c1a4f', 'crypton-x509-store-1.9.0': 'de9193403c11d1fb090083382646be6f6cf68ed984f475d1a0e142f77efb8766', 'crypton-x509-validation-1.9.1': '8fcb83401114c7f694d84c543e8f7f96cea60c543a30a770903741d336eece3d'}
sources=['tools/import-certificate-binary-fixtures.py','licenses/crypton-asn1-Get-NOTICE.txt']
for package,archive_hash in pins.items():
 d=root/'vendor'/package;m=json.loads((d/'SOURCE.json').read_text())
 assert m['package']+'-'+m['version']==package and m['sha256']==archive_hash and m['url']=='https://hackage-content.haskell.org/package/'+package+'.tar.gz'
 assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
 for name,h in m['files'].items():assert hashlib.sha256((d/name).read_bytes()).hexdigest()==h,(package,name)
 notice=root/'licenses'/(m['package']+'-BSD-3-Clause.txt');assert notice.read_bytes()==(d/'LICENSE').read_bytes()
 sources += [str(f.relative_to(root)) for f in d.rglob('*') if f.is_file()]+[str(notice.relative_to(root))]
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
get_source=(root/'vendor/crypton-asn1-encoding-0.10.0/src/Data/ASN1/Get.hs').read_text();notice=get_source[get_source.index('Original code is pulled'):get_source.index('\nThe original code')]
assert (root/'licenses/crypton-asn1-Get-NOTICE.txt').read_bytes()==(notice+'\n\n').encode()+(root/'vendor/crypton-asn1-encoding-0.10.0/LICENSE').read_bytes()
# Raw original modules compile directly from the retained package trees.
cabal=(folder/'kotdoc-certificate-binary-reference.cabal').read_text()
for package in ['base64-1.0','crypton-pem-0.3.0','crypton-asn1-types-0.4.1','crypton-asn1-encoding-0.10.0']:assert '../../vendor/'+package+'/src' in cabal
assert '../../vendor/crypton-pem-0.3.0/test' in cabal
assert 'cpp-options: -DMIN_VERSION_base64(a,b,c)=1' in cabal
assert set(folder.rglob('*.hs'))=={folder/'CertificateBinaryReference.hs'}
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 if a.check_source:print('Verified',len(cs),'original PEM/Base64/raw-ASN1 observations and',len(m['sources']),'source hashes');raise SystemExit
else:
 cs=[];rng=random.Random(0x58353039)
 def add(group,api,**v):cs.append(dict(group=group,request=dict(api=api,**v)))
 def bs(s):return list(s.encode('latin1'))
 def section(name='CERTIFICATE',content=(),headers=()):return dict(name=list(map(ord,name)),content=list(content),headers=[[list(map(ord,k)),list(v)] for k,v in headers])
 def pem(name,lines,end=None,tail='\n'):return bs('-----BEGIN '+name+'-----\n'+'\n'.join(lines)+'\n-----END '+(name if end is None else end)+'-----'+tail)
 for s in ['', 'f','fo','foo','foob','fooba','foobar','Zg==','Zm8=','Zm9v','Zm9vYg==','Zm9vYmE=','Zm9vYmFy','ZE==','ZK==','ZA==','ZmC=','ZmD=','ZmA=','Zm9vYE==','Zm9vYK==','Zm9vYA==','Zm9vYmC=','Zm9vYmD=','Zm9vYmA=','=A==','P===','%D==','P%==','PD%=','PA=%','PDw%','%Dw/PDw/','P%w/PDw/','PD%/PDw/','PDw%PDw/','PD=/PDw/','PDw=PDw/','=eAoeAo=','e=AoeAo=','eA=oeAo=','eAo=eAo=','eAoe=Ao=','eAoeA=o=']:
  add('upstream-standard-base64-RFC-padding-offset-and-canonicity', 'base64',bytes=bs(s))
 for n,position,prefix in itertools.product(range(256),range(4),['','QUJD','QUJDQUJD']):
  b=bs('QUJD');b[position]=n;add('every-byte-base64-table-and-error-priority','base64',bytes=bs(prefix)+b)
 for x,y in itertools.product([0,9,10,13,32,37,61,65,90,97,122,255],repeat=2):
  for length in [4,8]:
   b=[65]*length;b[0]=x;b[-2]=y;add('conflicting-padding-invalid-character-priority','base64',bytes=b)
 for n in list(range(130))+[255,256,257,511,512,513,4096]:
  b=[rng.randrange(256) for _ in range(n)];add('standard-base64-encode-size-boundaries','base64',bytes=b);add('standard-base64-roundtrip-input','base64',bytes=list(base64.b64encode(bytes(b))))
 for n in range(2):
  sec=section('abc' if n==0 else 'xxx',[0]*64 if n==0 else [3]*12)
  add('upstream-PEM-two-verbatim-writer-units','pemWrite',sections=[sec])
 source=(root/'vendor/crypton-pem-0.3.0/test/Main.hs').read_text()
 lines=re.search(r'  content = BC.pack \$ unlines\n(.*?)\n    \]',source,re.S).group(1)
 multiple=[json.loads(line.split('"',1)[0] and '"'+line.split('"',1)[1]) for line in lines.splitlines() if '"' in line]
 add('upstream-PEM-multiple-sections-verbatim','pemParse',chunks=[bs('\n'.join(multiple)+'\n')])
 add('upstream-PEM-unmatching-names','pemParse',chunks=[pem('marker',['AAAA'],end='marker2')])
 for name,n in itertools.product(['','A','CERTIFICATE','a b','A-B','a\r','\x00','ÿ','Русский','😀','\ud800'],[0,1,2,3,47,48,49,96,97,256]):
  add('PEM-name-Char8-modulo-and-48-byte-line-boundaries','pemWrite',sections=[section(name,[rng.randrange(256) for _ in range(n)])])
 for headers in [[],[('Key',bs('value'))],[('Proc-Type',bs('4,ENCRYPTED')),('DEK-Info',bs('DES-EDE3-CBC,123'))],[('',[])],[('x\nBEGIN',list(range(256)))],[('😀',[0,255])]]:
  add('PEM-header-writer-and-original-parser-rejection','pemWrite',sections=[section('CERTIFICATE',range(64),headers)])
 malformed=['','junk','-----BEGIN A-----','-----BEGIN A-----\n','-----BEGIN A-----\n\n','-----BEGIN A----\n',' -----BEGIN A-----\nAAAA\n-----END A-----\n','-----BEGIN A------\nAAAA\n-----END A-----\n','-----BEGIN A----- \nAAAA\n-----END A-----\n','-----BEGIN A-----\n-----END A-----','-----BEGIN -----\n-----END -----','-----BEGIN A-----\nAAAA\n-----END A----','-----BEGIN A-----\nAAAA\n-----END A-----x']
 malformed += ['-----BEGIN A-----\n'+s+'\n-----END A-----\n' for s in ['','A','AA','AAA','AAAA','AA==','AAA=','ZE==','AAAA\r','AAAA\r\r','AAAA ','AAAA\t',' AA=','AAAA\nAA==','AAAA\n','AA==\nAA==','Key:value','-----BEGIN B-----','-----END B-----']]
 for s,crlf in itertools.product(malformed,[False,True]):
  b=bs(s.replace('\n','\r\n') if crlf else s)
  for chunks in [[b],[[x] for x in b],[b[:1],[],b[1:]]]:add('PEM-delimiters-linewise-base64-CR-and-chunk-boundaries','pemParse',chunks=chunks)
 for i in range(1000):
  ss=[section(''.join(rng.choices('ABCDEFGHIJKLMNOPQRSTUVWXYZ',k=rng.randrange(1,31))),[rng.randrange(256) for _ in range(rng.randrange(1,101))]) for _ in range(rng.randrange(8))]
  add('upstream-PEM-marshall-property-domain','pemWrite',sections=ss)
 # All byte values as short primitive headers, long tag/length malformed cases,
 # EOC precedence, indefinite nesting, and construction boundaries.
 inputs=[[],[0],[0,0],[0,1,255],[0x20,0x80,0,0],[0x30,0x80,2,1,1,0,0],[0x30,0],[0x30,1,2,0],[0x30,2,2,1],[0x30,2,2,0],[0x30,3,2,0,0],[0x30,0x80,0x30,0x80,0,0,0,0],[0x30,0x80,0x30,0,0,0],[0x04,0x80],[0x1f,0x80],[0x1f,0x81,0x80,0x01,0],[0x1f,0x81],[0x1f,0,0],[4,0x88]+[255]*8+[0,0],[4,0x88]+[128]+[0]*7+[1],[0x30,0x88]+[255]*8]
 inputs += [[b,n]+[65]*n for b,n in itertools.product(range(256),[0,1,2])]
 inputs += [[0x1f]+[255]*n+[0,0] for n in [1,2,8,9,10,64,128]]
 inputs += [[4,128+n]+[255]*n+[1,0] for n in [1,2,4,8,9,16,127]]
 for b in inputs:
  chunks=[[b],[[x] for x in b],[[],b[:1],[],b[1:],[]]]
  for chunked in chunks:add('raw-ASN1-headers-EOC-truncation-overflow-and-incremental-state','raw',chunks=chunked)
 def header(cl,tag,constructed,length):return dict(class_=cl,tag=tag,constructed=constructed,length=length)
 def h(cl,tag,constructed,length):return {'class':cl,'tag':tag,'constructed':constructed,'length':length}
 for cl,tag,constructed,length in itertools.product(range(4),[-2**63,-1,0,1,30,31,127,128,255,256,16384,2**63-1],[False,True],[[0,-1],[0,0],[0,127],[0,128],[1,0,0],[1,7,128],[1,1,2**63-1],[1,0,-1],[2]]):
  add('ASN1-header-writer-machine-Int-and-length-policy','putHeader',header=h(cl,tag,constructed,length))
 raw_events=[[],['begin'],['end'],['begin','end'],[dict(primitive=[0,255])],[dict(header=h(0,16,True,[2])),'begin','end'],[dict(header=h(0,16,True,[0,0])),'begin','end','end']]
 for evs in raw_events:add('raw-ASN1-writer-unvalidated-events-and-indefinite-end','writeRaw',events=evs)
 for i in range(500):
  cl=rng.randrange(4);tag=rng.randrange(1,10001);size=rng.randrange(256);payload=[rng.randrange(256) for _ in range(size)];length=[0,size] if size<128 else [1,1,size]
  add('upstream-ASN1-event-marshalling-property-domain','writeRaw',events=[dict(header=h(cl,tag,False,length)),dict(primitive=payload)])
 for i in range(1000):
  n=rng.randrange(3);size=rng.randrange(0x80,0x1001) if n==1 else rng.randrange(0x7a)
  length=[0,size] if n==0 else [1,(size.bit_length()+7)//8,size] if n==1 else [2]
  add('upstream-ASN1-header-marshalling-property','headerRoundtrip',header=h(rng.randrange(4),rng.randrange(1,10001),bool(rng.randrange(2)),length))
  size=rng.randrange(0x1001);length=[0,size] if size<128 else [1,(size.bit_length()+7)//8,size]
  add('upstream-ASN1-event-marshalling-property','eventRoundtrip',events=[dict(header=h(rng.randrange(4),rng.randrange(1,10001),False,length)),dict(primitive=[rng.randrange(256) for _ in range(size)])])
 for pos in [0,1,2**64-1,2**64-2]:
  for b in [[31,128],[31,129,0,0],[4,0x82,1,0,65],[48,128],[31],[4,0x81]]:
   for chunks in [[b],[[x] for x in b]+[[]],[[],b[:2],b[2:],[]]]:add('incremental-header-Get-position-wrap-and-EOF','getHeader',position=pos,chunks=chunks)
 programs= [dict(op='word'),dict(op='empty'),dict(op='zero'),dict(op='fail',message='x')]
 programs += [dict(op=op,count=n) for op,n in itertools.product(['bytes','copy'],[-2**63,-1,0,1,2,4,17,2**63-1])]
 programs += [dict(op='choice',first=dict(op='sequence',programs=[dict(op='bytes',count=n),dict(op='fail',message='first')]),second=dict(op='sequence',programs=[dict(op='word'),dict(op='bytes',count=2)])) for n in [0,1,2,4,17]]
 programs += [dict(op='map',program=dict(op='choice',first=dict(op='fail',message='a'),second=dict(op='pure',value=[1,2])))]
 for program,pos,chunks in itertools.product(programs,[0,2**64-2],[[[]],[[0,1,2,3]],[[0],[1],[2],[3],[]],[[],[0,1],[],[2,3]],[[0,1],[]]]):add('original-Get-monad-choice-buffering-failure-and-position', 'get',program=program,position=pos,chunks=chunks)
 for i in [0,1,-1,127,128,255,256,-128,-129,-255,-256,2**63-1,-2**63]+[rng.randrange(-(1<<512),1<<512) for _ in range(200)]:
  add('ASN1-arbitrary-Integer-twos-complement-and-variable-groups','integer',integer=str(i),bytes=[rng.randrange(256) for _ in range(rng.randrange(129))])
 for kind,bs0 in [('pem',bs('-----BEGIN X----\n')),('pem',bs('-----BEGIN X-----\nA\n')),('pem',bs('-----BEGIN X-----\nAAAA\n-----END X-----\n')),('pem',bs('-----BEGIN X-----\n')),('raw',[4,128]),('raw',[48,1,2,0]),('raw',[4,1,1]),('raw',[31,128])]:
  for prefix in [[],[[]]]:
   add('lazy-chunk-demand-stops-at-original-parser-error','lazyDemand',kind=kind,chunks=prefix+[bs0,'tail demanded'])
 for i in range(1000):
  b=[rng.randrange(256) for _ in range(rng.randrange(65))];cuts=sorted([0,len(b)]+[rng.randrange(len(b)+1) for _ in range(4)]);chunks=[b[x:y] for x,y in zip(cuts,cuts[1:])];add('random-raw-ASN1-byte-stream-and-chunk-errors','raw',chunks=chunks)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cs).encode());inp.seek(0);subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=180);out.seek(0);observed=[json.loads(line) for line in out]
assert len(cs)==len(observed),(len(cs),len(observed))
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cs,observed)):assert c['expected']==o,(i,c,o)
 print('Independent oracle agrees on all',len(cs),'certificate-binary observations');raise SystemExit
for c,o in zip(cs,observed):c['expected']=o
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for x in [m]+cs:gz.write((json.dumps(x,separators=(',',':'))+'\n').encode())
print('Wrote',len(cs),'original observations;',target.stat().st_size,'bytes;',m['caseHash'])
