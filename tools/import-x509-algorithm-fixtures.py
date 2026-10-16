#!/usr/bin/env python3
"""Observe unchanged X509 algorithms and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-algorithm/data.jsonl.gz'
sources=[Path(__file__).resolve()]
for name,archive,notice in [
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
sources += [f for f in (root/'tools/haskell-x509-algorithm-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
instances=original[original.index('instance Arbitrary HashALG'):original.index('arbitraryBS r1 r2 =')]
prop=original[original.index('property_unmarshall_marshall_id\n'):original.index('-- | A scalar long enough')]
adapter='-- Unchanged instances/property extracted from crypton-x509 1.9.2 Tests/Tests.hs.\nmodule OriginalSignatureTests where\nimport Test.QuickCheck\nimport Data.ASN1.Types\nimport Data.X509.AlgorithmIdentifier\n\n'+instances+prop
assert (root/'tools/haskell-x509-algorithm-reference/OriginalSignatureTests.hs').read_text()==adapter
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['request']['api']=='property' for c in cases)==1000
 if a.check_source:print('Verified',len(cases),'X509 algorithm observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 generated=subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120)
 cases=[json.loads(x) for x in generated.splitlines()];assert len(cases)==1000
 rng=random.Random(0x58353039414c47)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def oid(xs):return ['oid',[str(x) for x in xs]]
 def from_(group,vs):
  for part in ['tag','remaining','all']:add(group,'from',values=vs,part=part)
 source=(root/'vendor/crypton-x509-1.9.2/Data/X509/AlgorithmIdentifier.hs').read_text()
 known=[[int(n.strip()) for n in ns.split(',')] for ns in re.findall(r'\(\[([^\]]+)\], SignatureALG',source)]
 assert len(known)==21
 extra=[[],[0],[-1],[0,0],[1,2,3],[2**512,-2**512,0]]
 oids=known+extra+[[rng.randrange(-2**rng.randrange(1,100),2**rng.randrange(1,100)) for _ in range(rng.randrange(12))] for _ in range(100)]
 keys=list(range(9))+[['unknown',[str(n) for n in o]] for o in extra]
 sigs=[['with',h,k] for h,k in itertools.product(range(7),keys)]+[['intrinsic',k] for k in keys]+[['unknown',[str(n) for n in o]] for o in extra]
 for h,k in itertools.product(range(7),repeat=2):add('HashALG-derived-Eq-Show','hash',hash=h,other=k)
 for k,o in itertools.product(keys,repeat=2):add('PubKeyALG-derived-Eq-Show-and-OIDable','key',key=k,other=o)
 for s,o in itertools.product(sigs,repeat=2):add('SignatureALG-all-constructors-derived-Eq-Show','signature',signature=s,other=o)
 tails=[[],[['integer','7'],['null']]]
 for s,n,part in itertools.product(sigs,range(25),['spine','kinds','values','shown']):
  add('toASN1-all-valid-unsupported-unknown-and-lazy-field-prefixes','to',signature=s,tail=tails[n%2],tailError=False,count=n,part=part)
 for s,n in itertools.product(sigs,range(16)):
  add('DER-prefix-demand-and-invalid-identifier-failures','to',signature=s,tail=[],tailError=False,count=n,part='bytes')
 for s,index in itertools.product(sigs,[0,1,5,12,17,20]):
  add('individual-output-field-demand','to',signature=s,tail=[['null']]*24,tailError=False,count=0,part='field',index=index)
 for s,n,part in itertools.product(sigs[:15]+[['with',4,1],['intrinsic',6],['unknown',['1','2','3']]],range(26),['spine','kinds','values']):
  add('difference-list-tail-failure-and-output-prefix-demand','to',signature=s,tail=[['null']],tailError=True,count=n,part=part)
 start=['start',['sequence']];end=['end',['sequence']];null=['null']
 for o,parameter,tail in itertools.product(oids,[[],[null],[["integer","0"]],[["octets",[]]]],[[],[['integer','7']]]):
  from_('simple-OID-absent-NULL-invalid-parameter-and-preserved-tail',[start,oid(o)]+parameter+[end]+tail)
 pss=[1,2,840,113549,1,1,10];mgf=[1,2,840,113549,1,1,8]
 def pss_stream(h1,h2,nulls,outer=None,salt=['integer','32']):
  outer=outer or [['container',2,i] for i in range(3)]
  st=lambda c:['start',c];en=lambda c:['end',c]
  return [start,oid(pss),start,st(outer[0]),start,oid(h1)]+([null] if nulls else [])+[end,en(outer[0]),st(outer[1]),start,oid(mgf),start,oid(h2)]+([null] if nulls else [])+[end,end,en(outer[1]),st(outer[2]),salt,en(outer[2]),end,end]
 good=[]
 for h1,h2,nulls in itertools.product(oids[:27],oids[:5]+extra,[False,True]):
  vs=pss_stream(h1,h2,nulls,salt=['integer',str(rng.choice([-2**512,-1,0,32,2**512]))]);good.append(vs)
  from_('PSS-hash-OID-mapping-and-ignored-second-hash-salt',vs+[['null']])
 replacements=[null,['integer','0'],['oid',[]],['start',['sequence']],['end',['sequence']],['start',['set']],['end',['set']],['start',['container',3,-1]],['end',['container',0,99]],['boolean',False],['octets',[]]]
 for nulls in [False,True]:
  vs=pss_stream(known[13],known[14],nulls)
  for n in range(len(vs)+1):from_('PSS-every-truncation',vs[:n])
  for i,replacement in itertools.product(range(len(vs)),replacements):from_('PSS-every-position-token-kind-mutation',vs[:i]+[replacement]+vs[i+1:])
  for kinds in itertools.product([['sequence'],['set'],['container',0,-2**63],['container',1,2**63-1],['container',2,0],['container',3,-1]],repeat=3):
   stream=pss_stream(known[13],extra[-1],nulls,list(kinds));from_('PSS-arbitrary-wrapper-kinds-and-tags',stream)
   # Source matches each outer End independently of its corresponding Start.
   for i,token in enumerate(stream):
    if token[0]=='end' and token[1]!=['sequence']:stream[i]=['end',['container',0,-99]]
   from_('PSS-unmatched-wrapper-End-kinds',stream)
  for index in [5,12 if not nulls else 13,17 if not nulls else 19]:
   changed=vs.copy();changed[index]=['integerError'] if changed[index][0]=='integer' else ['oidError']
   from_('PSS-ignored-fields-versus-demanded-first-OID-bottom',changed)
 for vs in [[],[start],[start,['oidError'],end],[start,['oidError'],null,end],[start,oid([1,2,3]),end,['integerError']]]:from_('fromASN1-Left-Right-value-and-tail-demand',vs)
 for _ in range(500):from_('arbitrary-malformed-identifier-token-streams',rng.choices(replacements,k=rng.randrange(28)))
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 original=[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['request']['api']=='property']
 assert fresh==original
 print('Original X509 verified',len(cases),'observations and 1000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print(json.dumps(manifest,indent=2))
