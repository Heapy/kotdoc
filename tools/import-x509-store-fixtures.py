#!/usr/bin/env python3
"""Independent crypton-x509-store 1.9.0 Memory/File/CertificateStore observations."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
folder=root/'tools/haskell-x509-store-reference';target=root/'core/src/test/resources/x509-store/data.jsonl.gz'
sources=[Path(__file__).resolve()]
for name,sha,notice in [
 ('crypton-x509-store-1.9.0','de9193403c11d1fb090083382646be6f6cf68ed984f475d1a0e142f77efb8766','crypton-x509-store-BSD-3-Clause.txt'),
 ('containers-0.7','cf6902d4d27158f0b8643673a230a75c98169d388295f9b641686c83ce15e324','Containers-BSD-3-Clause.txt')]:
 d=root/'vendor'/name;m=json.loads((d/'SOURCE.json').read_text());assert m['sha256']==sha
 assert m['url']=='https://hackage-content.haskell.org/package/'+name+'.tar.gz'
 assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
 for f,h in m['files'].items():assert hashlib.sha256((d/f).read_bytes()).hexdigest()==h
 assert (root/'licenses'/notice).read_bytes()==(d/'LICENSE').read_bytes()
 sources += list(f for f in d.rglob('*') if f.is_file())+[root/'licenses'/notice]
for corpus in ['x509-signed','certificate-binary']:
 f=root/f'core/src/test/resources/{corpus}/data.jsonl.gz';sources.append(f)
 with gzip.open(f,'rt') as z:m=json.loads(next(z));sources += [root/s for s in m['sources']]
 if corpus=='x509-signed':
  with gzip.open(f,'rt') as z:next(z);signed=[json.loads(l) for l in z]
sources += [f for name in ['haskell-x509-store-reference','haskell-x509-crl-reference'] for f in (root/'tools'/name).rglob('*') if f.is_file()]
original=(root/'vendor/crypton-x509-store-1.9.0/Tests/Tests.hs').read_text()
assert (folder/'OriginalStoreFixtures.hs').read_text()==original[:original.index('memoryKeyTests ::')].replace('module Main (main) where','module OriginalStoreFixtures where')
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as z:m=json.loads(next(z));cases=[json.loads(l) for l in z]
 assert m['sources']==hashes() and m['caseHash']==digest(cases) and m['cases']==len(cases) and m['groups']==groups(cases)
 assert sum(c['group'].startswith('original-memory-') for c in cases)==32
 assert all(c['expected']==(True if c['request']['api']=='sameKeys' else 1) for c in cases if c['group'].startswith('original-memory-'))
 if a.check_source:print('Verified',len(cases),'X509 store observations and',len(m['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 cases=[json.loads(l) for l in subprocess.check_output([a.oracle,'--vectors']).splitlines()];assert len(cases)==32
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def pem(label,raw):
  import base64
  b=base64.b64encode(bytes(raw));return list(b'-----BEGIN '+label.encode()+b'-----\n'+b+b'\n-----END '+label.encode()+b'-----\n')
 keys=[c['request']['bytes'] for c in cases if c['request']['api']=='keys']
 certs=[c['request']['bytes'] for c in cases if c['request']['api']=='signed']
 import base64
 def unpem(b):return list(base64.b64decode(bytes(b).splitlines()[1:-1][0] if len(bytes(b).splitlines())==3 else b''.join(bytes(b).splitlines()[1:-1])))
 rawKeys=[unpem(b) for b in keys];rawCerts=[unpem(b) for b in certs]
 labels=['PRIVATE KEY','RSA PRIVATE KEY','DSA PRIVATE KEY','EC PRIVATE KEY','X25519 PRIVATE KEY','X448 PRIVATE KEY','ED25519 PRIVATE KEY','ED448 PRIVATE KEY','CERTIFICATE','UNKNOWN','']
 for raw,label in itertools.product(rawKeys+rawCerts+[[],[48,0],[2,0],[4,0],[255]],labels):
  b=pem(label,raw)
  for api in ['keys','signed','pemToKey']:add('all-eight-families-and-every-PEM-label',api,bytes=b,part='all',count=100)
 for raw,api in [(b,'keys') for b in rawKeys]+[(b,'signed') for b in rawCerts]:
  label='PRIVATE KEY' if api=='keys' else 'CERTIFICATE'
  for n in range(len(raw)+1):add('every-original-DER-truncation',api,bytes=pem(label,raw[:n]),part='all',count=100)
  for at in range(len(raw)):
   b=raw.copy();b[at]^=255
   add('every-original-DER-byte-mutation',api,bytes=pem(label,b),part='all',count=100)
 for b in [[],[0,255],list(b'-----BEGIN X-----\n'),list(b'-----BEGIN X-----\n!\n-----END X-----\n')]+keys+certs:
  for other in [[],keys[0],keys[2],keys[4],certs[0],list(b'-----BEGIN broken')]:
   for api in ['keys','signed','pemToKey']:
    for count in [0,1,2,100]:add('multi-block-reversal-invalid-blocks-and-whole-PEM-failure',api,bytes=b+other,part='all',count=count)
 def dn(n):return [[['2','5','4','3'],1,list(str(n).encode())]]
 def make(certs,tail=False):return dict(op='make',certs=certs,tailError=tail)
 empty=dict(op='empty')
 def append(l,r):return dict(op='append',left=l,right=r)
 models=[empty,make([]),make([[dn(1),'1']]),make([[dn(1),'1'],[dn(1),'2']]),make([[dn(2),'2'],[dn(1),'1']]),make([[None,'1']]),make([None]),make([],True),make([[dn(1),'1']],True)]
 models += [append(l,r) for l,r in itertools.product(models[:7],repeat=2)]
 models += [dict(op='concat',stores=ls,tailError=t) for ls,t in itertools.product([[],[empty],[empty,empty],[models[2],models[3]],[models[3],models[2]],['error'],[models[2],'error']],[False,True])]
 rng=random.Random(0x58355354)
 for n in [3,4,5,8,16,32,64,128]:
  for _ in range(10):
   cs=[[dn(rng.randrange(n)),str(i)] for i in range(n)]
   models.append(make(cs))
 for model,part,count,query in itertools.product(models,['empty','find','findTag','spine','list'],[0,1,2,200],[[],None,dn(1),dn(2),dn(500)]):
  add('subject-map-order-duplicates-append-priority-and-demand','store',store=model,part=part,count=count,dn=query)
 # DN constructors and certificate payloads remain lazy even inside strict map keys.
 partialDns=[[],[[None,1,None]],[[['2','5','4','3'],1,None]],[[['2','5','4','3'],2,None]],[[['0'],1,None]],[[['3'],1,None]],dn(1)+[[None,1,None]]]
 for d in partialDns:
  c=[d,None];normal=[dn(1),'7']
  for model,part,count,query in itertools.product([make([c]),make([c,normal]),make([normal,c]),append(make([normal]),make([c]))],['empty','find','findTag','spine','list'],[0,1,2],partialDns+[dn(1),dn(2)]):
   add('deferred-DN-components-and-certificate-fields','store',store=model,part=part,count=count,dn=query)
 names=['root.pem','a.crt','no-extension','.hidden','0123abcd.0','ABCDEF01.9','0123abcd.10','0123abcg.0','0123abcd.x','0123abcd.\u0661','0123abcd.\uff11','0123abc.0','0123abcde.0']
 for name,b,part in itertools.product(names,[certs[0],pem('UNKNOWN',rawCerts[0]),[],list(b'-----BEGIN broken')],['store','certificates','signed','keys']):
  add('real-file-and-directory-name-filtering','file',files=[[name,b],['subdir',None]],path='.' if part=='store' else name,part=part)
 for b in certs+keys+[[],list(b'-----BEGIN broken'),certs[0]+keys[0],certs[0]+certs[1]]:
  for part in ['store','certificates','signed','keys']:add('real-single-file-PEM-and-key-errors','file',files=[['bundle',b]],path='bundle',part=part)
 for path in ['missing','subdir']:
  for part in ['store','certificates','signed','keys']:add('missing-file-directory-and-IO-error-boundaries','file',files=[['subdir',None]],path=path,part=part)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=300);out.seek(0);outputs=[json.loads(l) for l in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 vectors=[json.loads(l) for l in subprocess.check_output([a.oracle,'--vectors']).splitlines()]
 assert vectors==[c for c in cases if c['group'].startswith('original-memory-')]
 print('Original X509 store verified',len(cases),'observations and all 32 original assertions');raise SystemExit
for c,o in zip(cases,outputs):
 if 'expected' in c:assert c['expected']==o
 c['expected']=o
m=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cases)+'\n').encode())
print('Generated',len(cases),'X509 store observations; SHA256',m['caseHash'])
