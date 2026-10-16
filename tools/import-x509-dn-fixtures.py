#!/usr/bin/env python3
"""Observe unchanged X509 distinguished names and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-dn/data.jsonl.gz'
sources=[Path(__file__).resolve()]
for name,archive,notice in [
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
sources += [f for f in (root/'tools/haskell-x509-dn-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
instances=original[original.index('arbitraryBS r1 r2 ='):original.index('instance Arbitrary DateTime')]
prop=original[original.index('property_unmarshall_marshall_id\n'):original.index('-- | A scalar long enough')]
adapter='-- Unchanged generators/property extracted from crypton-x509 1.9.2 Tests/Tests.hs.\nmodule OriginalDnTests where\nimport Test.QuickCheck\nimport qualified Data.ByteString as B\nimport Control.Monad\nimport Data.ASN1.Types\nimport Data.X509.DistinguishedName\n\n'+instances+prop
assert (root/'tools/haskell-x509-dn-reference/OriginalDnTests.hs').read_text()==adapter
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['request']['api']=='property' for c in cases)==1000
 if a.check_source:print('Verified',len(cases),'X509 DN observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()];assert len(cases)==1000
 rng=random.Random(0x58353039444e)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def to(group,dn,inner=False,part='values',count=9999,tail=None,tailError=False,**v):add(group,'to',value=dn,inner=inner,part=part,count=count,tail=tail or [],tailError=tailError,**v)
 def from_(group,vs,inner=False):
  for part in ['tag','remaining','all','lookup']:add(group,'from',values=vs,inner=inner,part=part,element=rng.randrange(5))
 def o(xs):return [str(x) for x in xs]
 oids=[[2,5,4,3],[2,5,4,6],[2,5,4,10],[2,5,4,11],[1,2,840,113549,1,9,1]]
 extras=[[],[0],[-1],[2,5,4,2],[2,5,4,3,0],[2**512,-2**512,0]]
 payloads=[[],list(range(256))]+[[n] for n in range(256)]+[[rng.randrange(256) for _ in range(n)] for n in [2,3,7,8,15,16,17,31,32,33,63,64,65,127,128,129,255,256,257]]
 singles=[[[o(oids[e%5]),e,b]] for e,b in itertools.product(range(12),payloads)]
 for dn,inner,part in itertools.product(singles,[False,True],['values','bytes']):to('all-string-encodings-every-byte-and-DER',dn,inner,part,count=9999)
 basis=[[]]+[[[o(oid),e,b]] for oid,e,b in itertools.product(oids[:1]+extras,[0,1,11],[[],[0],[255]])]
 basis=basis[:40]+[[[o(oids[0]),e,list(range(256))]] for e in range(12)]
 basis += [[[o(oids[0]),0,[65]],[o(oids[0]),1,[66]]],[[o(oids[1]),0,[65]],[o(oids[0]),1,[66]]]]
 for x,y in itertools.product(basis,repeat=2):add('derived-DN-Inner-Eq-Ord-Show-and-Semigroup-Monoid','value',value=x,other=y)
 for e,other in itertools.product(range(5),repeat=2):add('DnElement-derived-Eq-Show-OIDable','element',element=e,other=other)
 for e,first,second in itertools.product(range(5),range(12),range(12)):
  dn=[[o(oids[e]),first,[0,255,65]],[o(oids[e]),second,[66]]]
  add('getDnElement-first-duplicate-without-string-decoding','lookup',value=dn,element=e)
 for dn,e in itertools.product(basis,range(5)):add('getDnElement-empty-unknown-and-matching-OID','lookup',value=dn,element=e)
 selected=basis[:12]+basis[-14:]+[[[None,0,[65]]]]
 for dn,inner,n,part in itertools.product(selected,[False,True],range(16),['spine','kinds','values','shown']):
  to('DN-and-Inner-prefix-demand-including-failing-OID',dn,inner,part,n,tail=[['null']])
 for dn,inner,n,part in itertools.product([[],basis[-1],selected[-1]],[False,True],range(18),['spine','kinds','values']):
  to('difference-list-tail-and-prefix-demand',dn,inner,part,n,tail=[['null']],tailError=True)
 for e in range(5):
  for dn in [[[o(oids[e]),0,[65]],[None,1,[66]]],[[None,0,[65]],[o(oids[e]),1,[66]]]]:add('lookup-demand-before-later-failing-OID','lookup',value=dn,element=e)
 start=lambda c:['start',c];end=lambda c:['end',c];seq=['sequence'];set_=['set']
 def entry(oid,e,b):return [start(seq),['oid',o(oid)],['string',e,b],end(seq)]
 for inner,rdns,tail in itertools.product([False,True],[[],[[]],[[entry(oids[0],1,[65])]],[[entry(oids[0],1,[65]),entry(oids[1],0,[66])]],[[entry(oids[0],1,[65])],[entry(oids[0],0,[66])]]],[[],[['null']]]):
  body=[]
  for group in rdns:body += [start(set_)]+sum(group,[])+[end(set_)]
  vs=body if inner else [start(seq)]+body+[end(seq)]
  from_('empty-and-multi-attribute-SET-flattening-Inner-consumption-and-tail',vs+tail,inner)
 canonical=[start(seq),start(set_)]+entry(oids[0],1,[65])+[end(set_),start(set_)]+entry(oids[1],0,[66])+[end(set_),end(seq)]
 tokens=[['null'],['boolean',False],['integer','0'],['oid',[]],['octets',[]],['bits','0',[]],['real','0'],['enumerated','0'],['string',1,[]],['time',0,[2026,9,16,5,34,12,0],None],['other',3,-1,[]],start(seq),end(seq),start(set_),end(set_),start(['container',3,-1]),end(['container',0,99])]
 for inner in [False,True]:
  base=canonical[1:-1] if inner else canonical
  for n in range(len(base)+1):from_('every-DN-and-Inner-truncation-including-missing-End',base[:n],inner)
  for i,token in itertools.product(range(len(base)),tokens):from_('every-position-mutation-across-all-ASN1-constructors',base[:i]+[token]+base[i+1:],inner)
  for _ in range(500):from_('arbitrary-malformed-DN-Inner-token-streams',rng.choices(tokens,k=rng.randrange(25)),inner)
  for cl,tag in itertools.product(range(4),[-2**63,-1,0,1,2**63-1]):
   changed=[end(['container',cl,tag]) if token[0]=='end' else token for token in base]
   from_('closing-marker-kinds-and-tags-ignored-by-getConstructedEnd',changed,inner)
  for bad in [['oidError'],['integerError']]:
   for contents in [[bad,['string',1,[65]]],[bad],[["oid",o(oids[0])],bad],[bad,['null']]]:
    body=[start(set_),start(seq)]+contents+[end(seq),end(set_)]
    from_('error-Left-tag-versus-lazy-message-and-payload',body if inner else [start(seq)]+body+[end(seq)],inner)
 for kind,count,tailError in itertools.product([seq,set_]+[['container',c,t] for c,t in itertools.product(range(4),[-1,0,2**63-1])],range(8),[False,True]):
  add('original-Internal-asn1Container-prefix-and-tail','container',kind=kind,values=[['null'],['integer','7']],count=count,tailError=tailError)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['request']['api']=='property']
 print('Original X509 DN verified',len(cases),'observations and 1000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print(json.dumps(manifest,indent=2))
