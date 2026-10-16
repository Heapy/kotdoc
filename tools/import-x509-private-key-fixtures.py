#!/usr/bin/env python3
"""Observe unchanged X509 private keys and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-private-key/data.jsonl.gz'
sources=[Path(__file__).resolve()]
for name,archive,notice in [
 ('crypton-2.0.1','9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914','crypton-BSD-3-Clause.txt'),
 ('crypton-asn1-parse-0.10.0','35c0f278d8fcb720b4ad89c2a7f256f7fb098b9f71a0aeb493a4c3c0fe7728d9','crypton-asn1-parse-BSD-3-Clause.txt'),
 ('crypton-x509-1.9.2','7da68d3b512dbb42261f37f16337741677f40409d33fe6b3b508e49f861c1a4f','crypton-x509-BSD-3-Clause.txt'),
 ('crypton-asn1-types-0.4.1','61efc63877a0d3ddb992a793839d9427c1597e095a6e1f63c04e05fd3e4e302d','crypton-asn1-types-BSD-3-Clause.txt'),
 ('crypton-asn1-encoding-0.10.0','49813a0ee253f628cb8102dd6857905609b52e490c73c554d7cdf0cbce2926d2','crypton-asn1-encoding-BSD-3-Clause.txt'),
 ('time-hourglass-0.3.0','9c7c7c74dda5033160328606cb3e8724201abadf9a140271c73c89a2612b9d22','time-hourglass-BSD-3-Clause.txt')]:
 d=root/'vendor'/name;m=json.loads((d/'SOURCE.json').read_text());assert m['sha256']==archive
 assert m['package']+'-'+m['version']==name and m['url']=='https://hackage-content.haskell.org/package/'+name+'.tar.gz'
 assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
 for f,h in m['files'].items():assert hashlib.sha256((d/f).read_bytes()).hexdigest()==h,(name,f)
 assert (root/'licenses'/notice).read_bytes()==(d/'LICENSE').read_bytes()
 sources += [root/'licenses'/notice]+[f for f in d.rglob('*') if f.is_file()]
sources += [f for f in (root/'tools/haskell-x509-private-key-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
imports='-- Unchanged original private-key generators/properties, crypton-x509 1.9.2 Tests/Tests.hs.\nmodule OriginalPrivateKeyTests where\nimport Test.QuickCheck\nimport Control.Monad\nimport qualified Data.ByteString as B\nimport Crypto.Error(throwCryptoError)\nimport qualified Crypto.PubKey.RSA as RSA\nimport qualified Crypto.PubKey.DSA as DSA\nimport qualified Crypto.PubKey.Curve25519 as X25519\nimport qualified Crypto.PubKey.Curve448 as X448\nimport qualified Crypto.PubKey.Ed25519 as Ed25519\nimport qualified Crypto.PubKey.Ed448 as Ed448\nimport Data.ASN1.Types\nimport Data.List(isInfixOf)\nimport Crypto.Debug(DebugShow(..))\nimport qualified Crypto.PubKey.ECC.Types as ECC\nimport Data.X509.PublicKey(SerializedPoint(..))\nimport Data.X509.PrivateKey\n\n'
parts=[original[original.index('instance Arbitrary RSA.PublicKey'):original.index('instance Arbitrary DSA.PublicKey')],original[original.index('instance Arbitrary RSA.PrivateKey'):original.index('instance Arbitrary HashALG')],original[original.index('arbitraryBS r1 r2'):original.index('instance Arbitrary ASN1StringEncoding')],original[original.index('property_unmarshall_marshall_id\n'):original.index('property_extension_id ::')]]
assert (root/'tools/haskell-x509-private-key-reference/OriginalPrivateKeyTests.hs').read_text()==imports+''.join(parts)
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['group'].startswith('original-') for c in cases)==2000
 if a.check_source:print('Verified',len(cases),'X509 private-key observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 def query(rs):return [json.loads(x) for x in subprocess.check_output([a.oracle],input=''.join(json.dumps(r)+'\n' for r in rs).encode(),timeout=180).splitlines()]
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()];assert len(cases)==2000
 rng=random.Random(0x58353039505249)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def o(xs):return list(map(str,xs))
 st=['start',['sequence']];en=['end',['sequence']]
 def ctx(cl,tag):return ['container',cl,tag]
 rsa=o([1,2,840,113549,1,1,1]);dsa=o([1,2,840,10040,4,1]);ec=o([1,2,840,10045,2,1]);curve=o([1,2,840,10045,3,1,7])
 numbers=[-2**256,-65536,-256,-129,-128,-1,0,1,127,128,255,256,257,65536,2**256]
 keys=[['rsa',sz,str(n),'65537']+[str(x) for x in [n,n+1,n+2,n+3,n+4,n+5]] for sz,n in itertools.product([-1,0,1,64],numbers)]
 keys += [['dsa',str(p),str(g),str(q),str(x)] for p,g,q,x in itertools.product([-17,0,1,17],[-2,0,1,2],[-1,0,1],[-2,0,1,3])]
 keys += [['ecNamed',c,str(d)] for c,d in itertools.product(range(33),[0,1,127,128,256,2**256])]
 keys += [['ecPrime',str(d),'1','2','17',b,'19','1',str(seed)] for d,b,seed in itertools.product([0,1,2**256],[[],[4,1,2],list(range(256))],[0,1,2**128])]
 for typ,size in [('x25519',32),('x448',56),('ed25519',32),('ed448',57)]:
  keys += [[typ,[byte]*size] for byte in range(256)]
  keys += [[typ,[rng.randrange(256) for _ in range(n)]] for n in [0,1,size-1,size+1,128]]
 for k in keys:
  for api in ['value','algorithm','show','debug']:add('private-record-Eq-redacted-Show-and-explicit-debug',api,value=k,other=k)
  for part in ['values','bytes']:add('private-key-ASN1-and-DER-encoding','encode',value=k,values=[],tailError=False,part=part,count=9999)
  add('private-key-roundtrip-values-and-remainders','roundtrip',value=k,part='all',count=2)
 selected=[['rsa',1,'127','3','5','7','11','13','17','19'],['dsa','17','2','3','5'],['ecNamed',12,'1'],['ecPrime','1','1','2','17',[],'19','1','0']]+[[ty,[1]*sz] for ty,sz in [('x25519',32),('x448',56),('ed25519',32),('ed448',57)]]
 for x,y in itertools.product(selected,repeat=2):add('private-key-derived-Eq-constructor-order','value',value=x,other=y)
 broken=[['rsa',1,'127','3']+[None]*6,['dsa','17','2','3',None],['ecNamed',12,None],['ecPrime',None,'1','2','17',[],'19','1','0']]+[[ty,None] for ty in ['x25519','x448','ed25519','ed448']]
 for k in broken:
  for api in ['algorithm','show','debug']:add('secret-redaction-versus-explicit-demand',api,value=k)
 for k,n,part in itertools.product(selected+broken,range(28),['spine','kinds','values','shown','bytes']):add('private-writer-prefix-field-and-failing-tail-demand','encode',value=k,part=part,count=n,values=[['null']],tailError=True)
 for c,d in itertools.product(range(33),[-1,0,1,2**512]):add('EC-record-Show-debug-and-curve-field','ecShow',value=['ecNamed',c,str(d)])
 for n in [0,1,127,128,255,256,257,65535,65536,2**512,2**4096]:add('defined-positive-i2osp-including-zero','i2osp',number=str(n))
 for b,e,m in itertools.product([-17,-2,-1,0,1,2,17],[-3,-1,0,1,3,64],[-17,-2,-1,0,1,2,17]):add('used-modular-power-signed-zero-inverse-boundaries','pow',base=str(b),exponent=str(e),modulus=str(m))
 canonical=query([dict(api='encode',value=k,part='values',count=9999,values=[],tailError=False) for k in selected])
 tokens=[['null'],['integer','0'],['integer','1'],['integer','-1'],['oid',[]],['oid',rsa],['oid',ec],['octets',[]],['octets',[4,0]],['bits','0',[]],['boolean',True],['string',1,[]],st,en,['start',['set']],['end',['set']],['start',ctx(0,0)],['end',ctx(3,9)]]
 def dec(group,vs,tailError=False):
  for part in ['tag','remaining','algorithm','shown','all']:add(group,'from',values=vs,tailError=tailError,part=part,count=2)
 for vs in canonical:
  for n in range(len(vs)+1):dec('every-private-key-truncation',vs[:n])
  for i,t in itertools.product(range(len(vs)),tokens):dec('every-private-key-position-mutation',vs[:i]+[t]+vs[i+1:])
  dec('decoded-key-with-failing-outer-tail',vs,True)
  for i in range(len(vs)):
   for t in [['oidError'],['integerError'],['octetsError'],['bitsError']]:dec('parser-alternative-and-field-demand',vs[:i]+[t]+vs[i+1:])
 for _ in range(200):dec('random-malformed-private-key-stream',rng.choices(tokens,k=rng.randrange(30)))
 def der(vs):
  result=query([dict(api='derValues',values=vs,tailError=False)])[0]
  assert isinstance(result,list),(vs,result)
  return result
 # Real unchanged BER/DER encoding creates all wrapper payloads independently.
 for kind,alg in [(0,rsa),(1,dsa),(2,ec),(3,ec)]:
  inner=canonical[kind] if kind!=1 else [['integer','5']]
  data=der(inner+[['null']])
  params=([['null']] if kind==0 else [st,['integer','17'],['integer','3'],['integer','2'],en] if kind==1 else [['oid',curve]])
  for version in [-1,0,1,2]:
   vs=[st,['integer',str(version)],st,['oid',alg]]+params+[en,['octets',data],en,['null']]
   dec('PKCS8-RSA-DSA-EC-wrappers-and-discarded-inner-remainder',vs)
 wrappers=[]
 for kind,alg in [(0,rsa),(1,dsa),(2,ec),(3,ec)]:
  inner=canonical[kind] if kind!=1 else [['integer','5']]
  params=([['null']] if kind==0 else [st,['integer','17'],['integer','3'],['integer','2'],en] if kind==1 else [['oid',curve]])
  wrapper=[st,['integer','0'],st,['oid',alg]]+params+[en,['octets',der(inner)],en]
  wrappers.append(wrapper)
  for n in range(len(wrapper)+1):dec('every-PKCS8-wrapper-truncation',wrapper[:n])
  for i,t in itertools.product(range(len(wrapper)),tokens):dec('every-PKCS8-wrapper-position-mutation',wrapper[:i]+[t]+wrapper[i+1:])
  dec('PKCS8-valid-nested-key-and-outer-remainder',wrapper+[['null']])
 for outer,local,pub in itertools.product([[],[['null']],[['oid',curve]],[['null'],['oid',curve]]],[None,[],[['null']],[['oid',curve]],[['oid',o([1,2,3])]]],[False,True]):
  bare=[st,['integer','1'],['octets',[0,1]]]
  if local is not None:bare += [['start',ctx(3,0)]]+local+[['end',ctx(0,9)]]
  if pub:bare += [['start',ctx(0,1)],['bits','0',[]],['end',ctx(3,8)]]
  bare += [en,['null']]
  dec('SEC1-local-versus-inherited-curve-and-skipped-public-bits',[st,['integer','0'],st,['oid',ec]]+outer+[en,['octets',der(bare)] ,en])
 # A skipped public BIT STRING may itself be undefined: observe the unencoded ASN1 input.
 for cl in range(4):dec('SEC1-ignored-public-field-payload',[st,['integer','1'],['octets',[1]],['start',ctx(cl,0)],['oid',curve],['end',ctx(0,99)],['start',ctx(cl,1)],['bitsError'],['end',ctx(3,99)],en])
 # EC local parameters override inherited parameters; public fields and tag classes are permissive.
 for cl,tag,content in itertools.product(range(4),[0,1],[[],[['oid',curve]],[['null'],['oid',curve]],[['oid',[]]],[['null']]]):
  vs=[st,['integer','1'],['octets',[1]],['start',ctx(cl,tag)]]+content+[['end',ctx(3,99)],en,['null']]
  dec('EC-parameter-and-public-tag-class-End-and-leftover-behavior',vs)
 # Curve PKCS8 versions, optional attributes/public-key, errors and byte lengths.
 for typ,size,last in [('x25519',32,110),('x448',56,111),('ed25519',32,112),('ed448',57,113)]:
  for n in [0,1,size-1,size,size+1]:
   payload=der([['octets',[255]*n]])
   for version,cl,attr,pub in itertools.product([-1,0,1,2],range(4),[False,True],[False,True]):
    extras=([['start',ctx(cl,0)],['integerError'],['end',ctx(0,99)]] if attr else [])+([['otherError',cl,1]] if pub else [])
    dec('fixed-secret-key-version-size-attributes-and-public-field-ignored',[st,['integer',str(version)],st,['oid',o([1,3,101,last])],en,['octets',payload]]+extras+[en,['null']])
  for payload in [[],[255],[5,0],[4,0,5,0],[0x30,0x80,0,0]]:dec('fixed-secret-nested-BER-error-and-inner-shape',[st,['integer','0'],st,['oid',o([1,3,101,last])],en,['octets',payload],en])
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['group'].startswith('original-')]
 print('Original X509 private keys verified',len(cases),'observations and 2000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'private-key observations; SHA256',manifest['caseHash'])
