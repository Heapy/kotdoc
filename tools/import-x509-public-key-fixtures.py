#!/usr/bin/env python3
"""Observe unchanged X509 public keys and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-public-key/data.jsonl.gz'
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
sources += [f for f in (root/'tools/haskell-x509-public-key-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
imports="""-- Unchanged original public-key generators/property, crypton-x509 1.9.2 Tests/Tests.hs.
module OriginalPublicKeyTests where
import Test.QuickCheck
import Control.Monad
import qualified Data.ByteString as B
import Crypto.Error(throwCryptoError)
import qualified Crypto.PubKey.RSA as RSA
import qualified Crypto.PubKey.DSA as DSA
import qualified Crypto.PubKey.Curve25519 as X25519
import qualified Crypto.PubKey.Curve448 as X448
import qualified Crypto.PubKey.Ed25519 as Ed25519
import qualified Crypto.PubKey.Ed448 as Ed448
import Data.ASN1.Types
import Data.X509.PublicKey

"""
parts=[original[original.index('instance Arbitrary RSA.PublicKey'):original.index('instance Arbitrary RSA.PrivateKey')],original[original.index('instance Arbitrary X25519.SecretKey'):original.index('instance Arbitrary PrivKey where')],original[original.index('arbitraryBS r1 r2'):original.index('instance Arbitrary ASN1StringEncoding')],original[original.index('property_unmarshall_marshall_id\n'):original.index('-- | A scalar long enough')]]
assert (root/'tools/haskell-x509-public-key-reference/OriginalPublicKeyTests.hs').read_text()==imports+''.join(parts)
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['group']=='original-public-key-generator-and-property' for c in cases)==1000
 if a.check_source:print('Verified',len(cases),'X509 public-key observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 def query(rs):return [json.loads(x) for x in subprocess.check_output([a.oracle],input=''.join(json.dumps(r)+'\n' for r in rs).encode(),timeout=180).splitlines()]
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()];assert len(cases)==1000
 rng=random.Random(0x58353039505542)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def o(xs):return list(map(str,xs))
 st=['start',['sequence']];en=['end',['sequence']]
 rsa=o([1,2,840,113549,1,1,1]);dsa=o([1,2,840,10040,4,1]);ec=o([1,2,840,10045,2,1])
 add('curve-OID-table-and-enum-order','curves')
 curves=query([dict(api='curves')])[0]
 payloads=[[],[0],[255],list(range(256)),[0,1,7,8,9,10,13,27,31,32,34,92,127,128,255]]
 nums=[-2**512,-65537,-65536,-32769,-32768,-257,-256,-255,-129,-128,-127,-1,0,1,127,128,255,256,257,32768,65536,2**512]
 keys=[['rsa',sz,str(n),str(e)] for sz,n,e in itertools.product([0,1,64,-1],nums,[-1,0,3,65537])]
 keys += [['dsa',str(p),str(g),str(q),str(y)] for p,g,q,y in itertools.product([-1,0,1,2**128],repeat=4)]
 keys += [['ecNamed',c,b] for c,b in itertools.product(range(33),payloads)]
 keys += [['ecPrime',b,str(a),str(a+1),str(a+2),b,str(a+3),str(a+4),str(a+5)] for a,b in itertools.product([-1,0,1,2**128],payloads)]
 keys += [['dh',str(n),'2','3',j,[0,255],str(n)] for n,j in itertools.product([-1,0,2**128],[None,'-1','0','1'])]
 keys += [['unknown',oi,b] for oi,b in itertools.product([[],['0'],rsa,ec,o([-1,2**128])],payloads)]
 for typ,size in [('x25519',32),('x448',56),('ed25519',32),('ed448',57)]:
  keys += [[typ,[byte]*size] for byte in range(256)]
  keys += [[typ,[rng.randrange(256) for _ in range(n)]] for n in [0,1,size-1,size+1,128,256]]
 for k in keys:
  add('key-record-Show-Eq-and-owned-bytes','value',value=k,other=k)
  add('pubkeyToAlg-all-variants','algorithm',value=k)
  add('ASN1-encoder-all-variants','encode',value=k,part='values',count=9999,values=[],tailError=False)
  add('DER-encoder-all-variants','encode',value=k,part='bytes',count=9999,values=[],tailError=False)
  add('ASN1-roundtrip-source-success-change-and-errors','roundtrip',value=k,part='all',count=9999)
 selected=[keys[0],keys[200],['rsa',64,'65536','3'],['dsa','1','2','3','4'],['ecNamed',0,[0]],['ecPrime',[], '1','2','3',[],'4','5','6'],['dh','1','2','3',None,[],'4'],['unknown',rsa,[0]],['unknown',[],[]]]
 selected += [[ty,[0]*sz] for ty,sz in [('x25519',32),('x448',56),('ed25519',32),('ed448',57)]]
 for x,y in itertools.product(selected,repeat=2):add('derived-Eq-constructor-and-field-order','value',value=x,other=y)
 broken=[['rsa',0,None,None],['dsa',None,None,None,None],['ecNamed',0,None],['ecPrime',None,None,None,None,None,None,None,None],['unknown',None,None]]+[[ty,None] for ty in ['x25519','x448','ed25519','ed448']]
 for k,n,part in itertools.product(selected+broken,range(14),['spine','kinds','values','shown','bytes']):add('encoder-constructor-field-tail-and-prefix-demand','encode',value=k,part=part,count=n,values=[['null']],tailError=True)
 for k in broken:add('algorithm-ignores-unrelated-key-payload','algorithm',value=k)
 tables=[[],[['1',rsa]], [['1',rsa],['2',rsa],['1',ec]], [['1',rsa],['1',ec]], [[None,rsa]], [['1',None]], [[None,ec],['1',rsa]]]
 for table,byOid,part in itertools.product(tables,[False,True],['tag','all']):
  for queryTarget in ([rsa,ec,[],None] if byOid else ['0','1','2',None]):add('generic-OID-table-first-match-and-deferred-fields','lookup',table=table,byOid=byOid,part=part,target=queryTarget)
 # Every-position structural mutation and truncation of independent original encodings.
 canonical=query([dict(api='encode',value=k,part='values',count=9999,values=[],tailError=False) for k in selected])
 canonical=[vs for vs in canonical if isinstance(vs,list)]
 canonical += [[st,st,['oid',ec],['oid',c[2]],en,['bits','0',[]],en] for c in curves]
 tokens=[['null'],['integer','0'],['integer','1'],['integer','-1'],['oid',[]],['oid',rsa],['oid',ec],['bits','0',[]],['bits','1',[255]],['octets',[]],['boolean',True],['string',1,[]],st,en,['start',['set']],['end',['set']],['start',['container',2,0]],['end',['container',2,0]]]
 def dec(group,vs,tailError=False):
  for part in ['tag','remaining','algorithm','all']:add(group,'from',values=vs,part=part,count=2,tailError=tailError)
 for vs in canonical:
  for n in range(len(vs)+1):dec('every-SPKI-truncation',vs[:n])
  for i,t in itertools.product(range(len(vs)),tokens):dec('every-SPKI-position-constructor-mutation',vs[:i]+[t]+vs[i+1:])
  dec('successful-key-preserves-or-does-not-force-tail',vs,True)
 for _ in range(200):dec('random-malformed-key-stream',rng.choices(tokens,k=rng.randrange(20)))
 # RSA inner BER remainder is prepended to outer remainder; PKCS8 branch discards nested remainder.
 for inner in [[0x30,6,2,1,0,2,1,3],[0x30,6,2,1,128,2,1,3],[5,0],[0x30,0],[],[255]]:
  for tail in [[],[5,0],[2,1,7]]:
   vs=[st,st,['oid',rsa],['null'],en,['bits','1',inner+tail],en,['integer','99']]
   dec('RSA-BER-inner-remainder-before-outer-tail',vs)
  for version,oi in itertools.product([0,1,-1],[rsa,[],ec]):
   # Wrap the nested RSA in the alternate source rsaPubFromASN1 production.
   wrapper=[0x30,0x80,2,1,version & 255,0x30,13,6,9,42,134,72,134,247,13,1,1,1,5,0,4,len(inner)]+inner+[0,0]
   if oi==ec:wrapper=[0x30,0x80,2,1,version & 255,0x30,11,6,7,42,134,72,206,61,2,1,5,0,4,len(inner)]+inner+[0,0]
   elif oi!=rsa:wrapper=[0x30,0x80,2,1,version & 255,0x30,6,6,2,42,3,5,0,4,len(inner)]+inner+[0,0]
   dec('RSA-alternate-version-and-nested-BER', [st,st,['oid',rsa],en,['bits','0',wrapper],en])
 # EC explicit prime branch demands only literal version/OID while matching the shape.
 prime=[st,['integer','1'],st,['oid',o([1,2,840,10045,1,1])],['integer','-3'],en,st,['octets',[255]],['octets',[]],['bits','1',[255]],en,['octets',[4,1,2]],['integer','-5'],['integer','-7'],en,en,['bits','3',[1,2]],en]
 explicit=[st,st,['oid',ec]]+prime
 for n in range(len(explicit)+1):dec('explicit-EC-prime-all-truncations',explicit[:n])
 for i,t in itertools.product(range(len(explicit)),tokens):dec('explicit-EC-prime-all-field-mutations',explicit[:i]+[t]+explicit[i+1:])
 dec('explicit-EC-prime-original-values-and-remainder',explicit+[['null']])
 for vs in canonical[:8]+[explicit]:
  for i in range(len(vs)):
   for t in [['oidError'],['integerError'],['bitsError'],['octetsError']]:dec('key-pattern-payload-and-Either-demand',vs[:i]+[t]+vs[i+1:])
 for ty,size,alg in [('x25519',32,110),('x448',56,111),('ed25519',32,112),('ed448',57,113)]:
  for n,pad in itertools.product([0,1,size-1,size,size+1,256],[0,1,7,8,999]):
   dec('fixed-key-size-check-ignores-bit-length',[st,st,['oid',o([1,3,101,alg])],en,['bits',str(pad),[255]*n],en])
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['group']=='original-public-key-generator-and-property']
 print('Original X509 public keys verified',len(cases),'observations and 1000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'public-key observations; SHA256',manifest['caseHash'])
