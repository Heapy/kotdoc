#!/usr/bin/env python3
"""Observe unchanged X509 certificates and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-certificate/data.jsonl.gz'
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
sources += [f for f in (root/'tools/haskell-x509-certificate-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
header=original[:original.index('instance Arbitrary RSA.PublicKey')].replace('module Main where','module OriginalCertificateTests where').replace('import Test.Tasty\nimport Test.Tasty.QuickCheck','import Test.QuickCheck').replace('import Data.X509\n','import Data.X509.Cert\nimport Data.X509.CRL\nimport Data.X509.PublicKey\nimport Data.X509.PrivateKey\nimport Data.X509.AlgorithmIdentifier\nimport Data.X509.DistinguishedName\nimport Data.X509.ExtensionRaw\nimport Data.X509.Ext\n')
assert (root/'tools/haskell-x509-certificate-reference/OriginalCertificateTests.hs').read_text()==header+original[original.index('instance Arbitrary RSA.PublicKey'):original.index('main =')]
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['group']=='original-certificate-generator-and-property' for c in cases)==1000
 if a.check_source:print('Verified',len(cases),'X509 certificate observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 def query(rs):return [json.loads(x) for x in subprocess.check_output([a.oracle],input=''.join(json.dumps(r)+'\n' for r in rs).encode(),timeout=180).splitlines()]
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()];assert len(cases)==1000
 rng=random.Random(0x5835303943455254)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def o(xs):return list(map(str,xs))
 st=['start',['sequence']];en=['end',['sequence']]
 def ctx(cl,n):return ['container',cl,n]
 def dt(y,m=0,d=1,h=0,mi=0,se=0,ns=0):return [y,m,d,h,mi,se,ns]
 dn=[[o([2,5,4,3]),1,[101,120,97,109,112,108,101]]]
 sig=['with',4,0];key=['rsa',1,'127','3'];ext=[o([2,5,29,15]),False,[3,3,7,0,0]]
 base=['2','123',sig,dn,[dt(2025),dt(2030)],[],key,None]
 def changed(i,x,c=base):return c[:i]+[x]+c[i+1:]
 keys=[key,['dsa','17','2','3','5'],['ecNamed',12,[4,1,2]],['unknown',o([1,2,3]),[0,255]]]+[[ty,[0]*n] for ty,n in [('x25519',32),('x448',56),('ed25519',32),('ed448',57)]]
 sigs=[['with',h,k] for h,k in itertools.product(range(7),[0,1,2,3])]+[['intrinsic',6],['intrinsic',7],['unknown',o([1,2,3])],['unknown',[]]]
 dates=[dt(y) for y in [-2**63,-1,0,1,1949,1950,2049,2050,2051,2**63-1]]+[dt(2049,11,31,23,59,59,999999999),dt(2050,0,1,0,0,0,-1),dt(2050,0,1,0,0,0,1),dt(2050,0,0),dt(2050,1,1),dt(2025,1,29,25,-1,61,-1)]
 models=[base]+[changed(0,str(v)) for v in [-2**130,-2**63,-2,-1,0,1,2,3,2**63-1,2**63,2**130]]+[changed(1,str(n)) for n in [-2**512,-1,0,1,2**512]]
 models += [changed(2,s) for s in sigs]+[changed(3,x) for x in [[],dn,dn+dn,[[[],0,[]]],[[o([-1,2**128]),1,list(range(256))]]]]+[changed(5,x) for x in [[],dn,dn+dn]]
 models += [changed(4,[a,b]) for a,b in itertools.product(dates,repeat=2)]+[changed(6,k) for k in keys]+[changed(7,e) for e in [None,[],[ext],[ext,ext],[[ext[0],True,ext[2]]]]]
 for value in models:
  add('certificate-record-Show-Eq-and-fields','value',value=value,other=base)
  for part in ['values','bytes']:add('header-field-stream-and-DER-without-outer-wrapper','encode',value=value,values=[],tailError=False,part=part,count=9999)
  add('certificate-native-roundtrip-and-source-asymmetries','roundtrip',value=value,part='all',count=2)
 for x,y in itertools.product(models[:18],repeat=2):add('record-Eq-field-order-and-short-circuit','equal',value=x,other=y)
 broken=[None]+[changed(i,None) for i in range(7)]+[changed(7,'extensionsError'),changed(7,'listError'),changed(4,[None,dt(2050)]),changed(4,[dt(2049),None])]
 for value,field in itertools.product(broken,['version','serial','signature','issuer','validity','subject','key','extensions']):add('independent-record-field-demand','field',value=value,field=field)
 for value,n,part in itertools.product(broken+[base,changed(7,[])],range(38),['spine','kinds','values','shown','bytes']):add('header-prefix-constructor-and-field-demand', 'encode',value=value,count=n,part=part,values=[['null']],tailError=True)
 selected=[base,changed(7,[]),changed(7,[ext]),changed(2,['intrinsic',6]),changed(2,['with',4,1]),changed(6,keys[1]),changed(6,keys[2])]
 canonical=query([dict(api='encode',value=c,part='values',count=9999,values=[],tailError=False) for c in selected]);assert all(isinstance(c,list) for c in canonical)
 tokens=[['null'],['integer','0'],['integer','1'],['integer','-1'],['oid',[]],['oid',o([1,2,3])],['octets',[]],['bits','0',[]],['boolean',True],['string',1,[]],['time',0,dt(2050),None],st,en,['start',['set']],['end',['set']],['start',ctx(2,0)],['end',ctx(3,99)]]
 def dec(group,vs,tailError=False):
  for part in ['tag','remaining','all']:add(group,'from',values=vs,tailError=tailError,part=part,count=2)
 for vs in canonical:
  for n in range(len(vs)+1):dec('every-certificate-field-stream-truncation',vs[:n])
  for i,t in itertools.product(range(len(vs)),tokens):dec('every-position-ASN1-constructor-mutation',vs[:i]+[t]+vs[i+1:])
  dec('no-outer-Sequence-wrapper-is-inserted',[st]+vs+[en])
  dec('complete-fields-with-failing-tail',vs,True)
  for i in range(len(vs)):
   for t in [['oidError'],['integerError'],['octetsError'],['bitsError'],['timeError']]:
    for part in ['tag','remaining','all','rekinds']:add('deferred-input-fields-versus-shape-and-reencoding','from',values=vs[:i]+[t]+vs[i+1:],tailError=False,part=part,count=14)
 for _ in range(200):dec('random-malformed-certificate-field-stream',rng.choices(tokens,k=rng.randrange(45)))
 # Version is optional, wraps to Int64, and always writes an explicit Context 0.
 for n,cl,endKind in itertools.product([-2**130,-2**63,-1,0,1,2,3,2**63-1,2**130],range(4),[['sequence'],ctx(0,7),ctx(2,0)]):
  vs=[['start',ctx(cl,0)],['integer',str(n)],['end',endKind]]+canonical[0][3:]
  dec('version-signed-wrap-class-and-permissive-container-End',vs)
 dec('absent-version-defaults-to-zero',canonical[0][3:])
 # ASN1Time tags and timezone are ignored by the validity parser.
 timeStart=next(i for i,t in enumerate(canonical[0]) if t[0]=='time')
 for ty1,ty2,tz1,tz2 in itertools.product([0,1],[0,1],[None,-840,0,840],[None,-840,0,840]):
  vs=canonical[0].copy();vs[timeStart]=['time',ty1,dt(2050),tz1];vs[timeStart+1]=['time',ty2,dt(1949),tz2]
  dec('validity-time-kind-and-zone-ignored',vs)
  add('validity-writer-reselects-time-type-and-zero-offset','from',values=vs,tailError=False,part='reencode',count=9999)
 for body in [[],[['timeError']],[['timeError'],['timeError']],[['timeError'],['timeError'],['null']]]:
  vs=canonical[0][:timeStart]+body+canonical[0][timeStart+2:]
  for part in ['tag','rekinds','all']:add('validity-constructor-before-kind-date-and-zone','from',values=vs,tailError=False,part=part,count=timeStart+3)
 # Unique identifiers are not skipped by this upstream parser; subsequent extensions stay in the remainder.
 for suffix in [[['other',2,1,[0]]],[['otherError',2,2]],[['other',2,1,[]]]+canonical[2][-8:],[['null']],[['oidError']]]:
  dec('unsupported-unique-identifiers-and-extensions-after-them-remain',canonical[0]+suffix)
 issuerStart=next(i for i in range(4,len(canonical[0])) if canonical[0][i:i+2]==[st,['start',['set']]])
 partialDn=canonical[0][:issuerStart]+[st,['start',['set']],st,['oidError'],['null']]
 partialRaw=canonical[0]+[['start',ctx(2,3)],st,st,['oidError'],['integerError']]
 for vs in [partialDn,partialRaw]:dec('error-message-payload-priority-before-failing-structure-tail',vs,True)
 # Presence is not restricted by certificate version.
 for v in [-1,0,1,2,3]:
  vs=canonical[2].copy();vs[1]=['integer',str(v)];dec('extensions-independent-of-version',vs)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['group']=='original-certificate-generator-and-property']
 print('Original X509 certificates verified',len(cases),'observations and 1000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'certificate observations; SHA256',manifest['caseHash'])
