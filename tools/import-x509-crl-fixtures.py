#!/usr/bin/env python3
"""Observe unchanged X509 CRLs and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-crl/data.jsonl.gz'
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
sources += [f for f in (root/'tools/haskell-x509-crl-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
header=original[:original.index('instance Arbitrary RSA.PublicKey')].replace('module Main where','module OriginalCrlTests where').replace('import Test.Tasty\nimport Test.Tasty.QuickCheck','import Test.QuickCheck').replace('import Data.X509\n','import Data.X509.Cert\nimport Data.X509.CRL\nimport Data.X509.PublicKey\nimport Data.X509.PrivateKey\nimport Data.X509.AlgorithmIdentifier\nimport Data.X509.DistinguishedName\nimport Data.X509.ExtensionRaw\nimport Data.X509.Ext\n')
assert (root/'tools/haskell-x509-crl-reference/OriginalCrlTests.hs').read_text()==header+original[original.index('instance Arbitrary RSA.PublicKey'):original.index('main =')]
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['group']=='original-crl-generator-and-property' for c in cases)==1000
 if a.check_source:print('Verified',len(cases),'X509 CRL observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 def query(rs):return [json.loads(x) for x in subprocess.check_output([a.oracle],input=''.join(json.dumps(r)+'\n' for r in rs).encode(),timeout=180).splitlines()]
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()];assert len(cases)==1000
 rng=random.Random(0x5835303943524c)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def o(xs):return list(map(str,xs))
 st=['start',['sequence']];en=['end',['sequence']]
 def ctx(cl,n):return ['container',cl,n]
 def dt(y,m=0,d=1,h=0,mi=0,se=0,ns=0):return [y,m,d,h,mi,se,ns]
 dn=[[o([2,5,4,3]),1,[101,120,97,109,112,108,101]]];sig=['with',4,0]
 ext=[o([2,5,29,15]),False,[3,3,7,0,0]]
 rev=['7',dt(2025),None]
 base=['1',sig,dn,dt(2025),None,[],None]
 def change(i,x,c=base):return c[:i]+[x]+c[i+1:]
 dates=[dt(y) for y in [-2**63,-1,0,1,1949,1950,2049,2050,2051,2**63-1]]+[dt(2049,11,31,23,59,59,999999999),dt(2050,0,1,0,0,0,-1),dt(2050,1,29,25,-1,61,-1)]
 extensions=[None,[],[ext],[ext,ext],[[ext[0],True,list(range(256))]]]
 revs=[rev]+[[str(n),date,e] for n,date,e in itertools.product([-2**256,-1,0,1,2**256],dates[:4]+dates[6:9],extensions)]
 models=[base]+[change(0,str(n)) for n in [-2**512,-2**63,-1,0,1,2,2**63,2**512]]
 models += [change(1,s) for s in [['with',h,k] for h,k in itertools.product(range(7),[0,1,2,3])]+[['intrinsic',6],['intrinsic',7],['unknown',o([1,2,3])],['unknown',[]]]]
 models += [change(2,v) for v in [[],dn,dn+dn,[[[],0,[]]],[[o([-1,2**128]),1,list(range(256))]]]]
 models += [change(3,d) for d in dates]+[change(4,d) for d in dates]+[change(5,rs) for rs in [[rev],[rev,rev],revs[:3],list(reversed(revs[:3]))]]+[change(6,e) for e in extensions]
 for value in models:
  add('CRL-record-Eq-Show-and-separate-fields','value',value=value,other=base)
  for part in ['values','bytes']:add('bare-CRL-field-stream-DER-and-generalized-dates','encode',value=value,part=part,count=9999,values=[],tailError=False)
  add('CRL-native-roundtrip-and-normalization','roundtrip',value=value,part='all',count=2)
 for r in revs:
  add('revoked-record-Eq-Show','revokedValue',value=r,other=rev)
  for part in ['values','bytes']:add('revoked-Sequence-wrapper-and-GeneralizedTime','revokedEncode',value=r,part=part,count=9999,values=[],tailError=False)
  add('revoked-native-roundtrip','revokedRoundtrip',value=r,part='all',count=2)
 for x,y in itertools.product(models[:12],repeat=2):add('CRL-derived-Eq-field-order','equal',value=x,other=y)
 for x,y in itertools.product(revs[:12],repeat=2):add('revoked-derived-Eq-field-order','revokedEqual',value=x,other=y)
 broken=[None]+[change(i,None) for i in [0,1,2,3,5]]+[change(4,'maybeError'),change(4,'datetimeError'),change(5,[None]),change(5,[rev,None]),change(6,'extensionsError'),change(6,'listError')]
 for v,field,n in itertools.product(broken,['version','signature','issuer','this','next','nextTag','revoked','revokedSpine','revokedPrefix','extensions'],[0,1,2]):add('CRL-field-Maybe-list-spine-and-element-demand','field',value=v,field=field,count=n)
 for v,n,part in itertools.product(broken+[base,change(5,[rev]),change(6,[])],range(28),['spine','kinds','values','shown','bytes']):add('CRL-writer-prefix-and-failing-tail-demand','encode',value=v,part=part,count=n,values=[['null']],tailError=True)
 revBroken=[None,[None,dt(2025),None],['7',None,None],['7',dt(2025),'extensionsError'],['7',dt(2025),'listError']]
 for v,field in itertools.product(revBroken,['serial','date','extensions']):add('revoked-independent-field-demand','revokedField',value=v,field=field)
 for v,n,part in itertools.product(revBroken+[rev,revs[-1]],range(15),['spine','kinds','values','shown','bytes']):add('revoked-constructor-payload-and-failing-tail-demand','revokedEncode',value=v,part=part,count=n,values=[['null']],tailError=True)
 selected=[base,change(4,dt(2030)),change(5,[rev]),change(5,[rev,rev]),change(6,[]),change(6,[ext]),change(1,['with',4,1]),change(1,['intrinsic',6])]
 canonical=query([dict(api='encode',value=c,part='values',count=9999,values=[],tailError=False) for c in selected]);assert all(isinstance(c,list) for c in canonical)
 revCanonical=query([dict(api='revokedEncode',value=r,part='values',count=9999,values=[],tailError=False) for r in [rev,['8',dt(2050),[]],['9',dt(1949),[ext]]]])
 tokens=[['null'],['integer','0'],['integer','1'],['integer','-1'],['oid',[]],['oid',o([1,2,3])],['octets',[]],['bits','0',[]],['boolean',True],['string',1,[]],['time',0,dt(2050),None],st,en,['start',['set']],['end',['set']],['start',ctx(2,0)],['end',ctx(3,99)]]
 def dec(group,vs,revoked=False,tailError=False):
  for part in ['tag','remaining','all']:add(group,'revokedFrom' if revoked else 'from',values=vs,tailError=tailError,part=part,count=2)
 for revoked,streams in [(False,canonical),(True,revCanonical)]:
  for vs in streams:
   for n in range(len(vs)+1):dec('every-CRL-and-revoked-truncation',vs[:n],revoked)
   for i,t in itertools.product(range(len(vs)),tokens):dec('every-CRL-and-revoked-position-mutation',vs[:i]+[t]+vs[i+1:],revoked)
   dec('decoded-state-versus-failing-outer-tail',vs+[['null']],revoked,True)
   for i in range(len(vs)):
    for t in [['oidError'],['integerError'],['octetsError'],['bitsError'],['timeError']]:
     for part in ['tag','remaining','all','rekinds']:add('deferred-input-values-and-reencoding-prefix','revokedFrom' if revoked else 'from',values=vs[:i]+[t]+vs[i+1:],tailError=False,part=part,count=20)
  for _ in range(150):dec('random-malformed-CRL-and-revoked-stream',rng.choices(tokens,k=rng.randrange(35)),revoked)
 dec('CRL-is-not-surrounded-by-Sequence',[st]+canonical[0]+[en])
 for n in [-2**512,-2**63,-1,0,1,2**63,2**512]:dec('CRL-version-unbounded-Integer-not-Int64',[['integer',str(n)]]+canonical[0][1:])
 timeIndex=next(i for i,t in enumerate(canonical[0]) if t[0]=='time')
 for ty,tz in itertools.product([0,1],[None,-840,0,840]):
  c=canonical[1].copy();c[timeIndex]=['time',ty,dt(1949),tz];c[timeIndex+1]=['time',1-ty,dt(2050),tz]
  dec('this-and-next-time-kind-and-timezone-ignored',c)
  add('all-writer-dates-GeneralizedTime-zero-offset','from',values=c,tailError=False,part='reencode',count=9999)
  r=revCanonical[0].copy();r[2]=['time',ty,dt(1949),tz];dec('revocation-time-kind-and-zone-ignored',r,True)
 # Optional containers permit any End kind, but their Start classes/tags remain exact.
 for cl,endKind in itertools.product(range(4),[['sequence'],['set'],ctx(0,99),ctx(2,0)]):
  dec('empty-revoked-container-normalizes-to-omission',canonical[0]+[st,['end',endKind]])
  dec('CRL-extension-start-class-and-End-permissiveness',canonical[0]+[['start',ctx(cl,0)],st,en,['end',endKind]])
  dec('revoked-outer-End-kind-is-ignored',revCanonical[0][:-1]+[['end',endKind]],True)
 for suffix in [[['timeError']],[['null']],[['otherError',2,1]],[['oidError']]]:dec('optional-time-and-container-selection-preserves-suffix',canonical[0]+suffix)
 # Explicit Time constructor demand ignores its kind/zone/date until a field needs them.
 for revoked,vs,idx in [(False,canonical[0],timeIndex),(False,canonical[1],timeIndex+1),(True,revCanonical[0],2)]:
  c=vs.copy();c[idx]=['timeError']
  for part,n in itertools.product(['tag','rekinds','reencode','all'],[0,2,20]):add('time-constructor-before-payloads','revokedFrom' if revoked else 'from',values=c,tailError=False,part=part,count=n)
 issuerStart=next(i for i in range(1,len(canonical[0])) if canonical[0][i:i+2]==[st,['start',['set']]])
 partialDn=canonical[0][:issuerStart]+[st,['start',['set']],st,['oidError'],['null']]
 partialExt=canonical[0]+[['start',ctx(2,0)],st,st,['oidError'],['integerError']]
 for vs in [partialDn,partialExt]:dec('CRL-error-message-before-failing-structural-tail',vs,False,True)
 for vs in [[st,['oidError']],[st,['integer','7'],['boolean',False]]]:dec('revoked-error-before-failing-structural-tail',vs,True,True)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['group']=='original-crl-generator-and-property']
 print('Original X509 CRLs verified',len(cases),'observations and 1000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'CRL observations; SHA256',manifest['caseHash'])
