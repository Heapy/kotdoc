#!/usr/bin/env python3
"""Observe unchanged X509 raw extensions and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-extension-raw/data.jsonl.gz'
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
sources += [f for f in (root/'tools/haskell-x509-extension-raw-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
instances=original[original.index('instance Arbitrary Extensions'):original.index('instance Arbitrary ExtKeyUsagePurpose')]
prop=original[original.index('property_unmarshall_marshall_id\n'):original.index('-- | A scalar long enough')]
adapter='-- Unchanged generators/property extracted from crypton-x509 1.9.2 Tests/Tests.hs.\nmodule OriginalExtensionsTests where\nimport Test.QuickCheck\nimport Data.List(nub,sort)\nimport Data.ASN1.Types\nimport Data.X509.ExtensionRaw\nimport Data.X509.Ext\n\n'+instances+prop
assert (root/'tools/haskell-x509-extension-raw-reference/OriginalExtensionsTests.hs').read_text()==adapter
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['request']['api']=='property' for c in cases)==1000
 if a.check_source:print('Verified',len(cases),'X509 raw-extension observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()];assert len(cases)==1000
 rng=random.Random(0x58353039455854)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def to(group,value,set=False,part='values',count=9999,tail=None,tailError=False,**v):add(group,'to',value=value,set=set,part=part,count=count,tail=tail or [],tailError=tailError,**v)
 def from_(group,vs,set=False):
  for part in ['tag','remaining','all']:add(group,'from',values=vs,set=set,part=part)
 def o(xs):return [str(x) for x in xs]
 oid=o([2,5,29,15]);otherOid=o([2,5,29,19])
 payloads=[[],list(range(256)),[0,1,7,8,9,10,13,27,31,32,34,92,127,128,255]]+[[n] for n in range(256)]+[[rng.randrange(256) for _ in range(n)] for n in [2,3,7,8,15,16,17,31,32,33,63,64,65,127,128,129,255,256,257,511,512,513]]
 oids=[oid,otherOid,[],['0'],o([-1,-2,-3]),o([2**512,-2**512,0]),o([1,2,840,113549,1,9,1])]
 raws=[[oi,critical,b] for oi,critical,b in itertools.product(oids,[False,True],payloads[:3])]
 for raw,b in itertools.product(raws[:4],payloads):
  value=[raw[0],raw[1],b]
  add('raw-record-all-bytes-Show-and-Eq','rawValue',value=value,other=raw)
  for part in ['values','bytes']:to('raw-omitted-False-explicit-True-and-DER',value,part=part)
 for raw,other in itertools.product(raws,repeat=2):add('raw-record-all-OID-critical-content-Eq-combinations','rawEqual',value=raw,other=other)
 sets=[None,[]]+[[raw] for raw in raws[:10]]+[[raws[0],raws[1]],[raws[1],raws[0]],[raws[0],raws[0]]]
 for value,other in itertools.product(sets,repeat=2):add('Extensions-Nothing-Just-empty-order-duplicates-Eq-Show','setValue',value=value,other=other)
 bad=[[None,False,[5,0]],[oid,None,[5,0]],[oid,False,None],[None,None,None],[None,False,None]]
 for raw,part in itertools.product(bad,['oid','critical','content']):add('independent-deferred-record-fields','field',value=raw,part=part)
 for raw,other in itertools.product(raws[:3]+bad,raws[:3]+bad):add('record-Eq-field-demand-order','rawEqual',value=raw,other=other)
 selected=raws[:4]+raws[-2:]+bad
 for raw,n,part in itertools.product(selected,range(12),['spine','kinds','values','shown','bytes']):to('raw-prefix-constructor-field-and-error-priority',raw,part=part,count=n,tail=[['null']])
 for value,n,part in itertools.product(sets+[[],[bad[0]],[bad[1]],[bad[2]],'listError'],range(14),['spine','kinds','values','shown','bytes']):to('Extensions-Maybe-list-and-record-demand',value,set=True,part=part,count=n,tail=[['null']])
 for value in sets+['listError']:add('Extensions-presence-does-not-force-list','presence',value=value)
 for value,other in itertools.product([None,[],'listError',raws[:1]],[None,[],'listError',raws[:1]]):add('Maybe-Eq-short-circuit-before-list','setEqual',value=value,other=other)
 for value,set_,n,part in itertools.product([raws[0],bad[0]],[False,True],range(12),['spine','kinds','values']):to('builder-tail-prefix-demand', [value] if set_ else value,set_,part,n,tail=[['null']],tailError=True)
 seq=['sequence'];st=['start',seq];en=['end',seq]
 tokens=[['null'],['boolean',False],['boolean',True],['integer','0'],['oid',[]],['octets',[]],['bits','0',[]],['real','0'],['enumerated','0'],['string',1,[]],['time',0,[2026,9,16,5,34,12,0],None],['other',3,-1,[]],st,en,['start',['set']],['end',['set']],['start',['container',3,-1]],['end',['container',0,99]]]
 canonical=[[st,['oid',oid],['octets',[5,0]],en],[st,['oid',oid],['boolean',False],['octets',[]],en],[st,['oid',oid],['boolean',True],['octets',[2,1,7]],en]]
 for vs in canonical:
  for set_ in [False,True]:
   base=[st]+vs+[en] if set_ else vs
   for n in range(len(base)+1):from_('every-raw-and-Extensions-truncation',base[:n],set_)
   for i,token in itertools.product(range(len(base)),tokens):from_('every-position-mutation-across-ASN1-constructors',base[:i]+[token]+base[i+1:],set_)
   for tail in [[],[['null']],[['oidError']]]:from_('remaining-input-after-scalar-and-optional-container',base+tail,set_)
 for set_ in [False,True]:
  for _ in range(400):from_('arbitrary-malformed-scalar-and-Extensions-input',rng.choices(tokens,k=rng.randrange(25)),set_)
  for body in [[['oidError'],['octets',[5,0]]],[['oid',oid],['octetsError']],[['oidError'],['integerError']],[['integerError'],['octets',[]]],[['oidError']]]:
   scalar=[st]+body+[en]
   from_('Left-message-suffix-and-deferred-record-field-demand',[st]+scalar+[en] if set_ else scalar,set_)
 for cl,tag in itertools.product(range(4),[-2**63,-1,0,1,2**63-1]):
  end=['end',['container',cl,tag]]
  from_('scalar-End-must-be-Sequence',canonical[0][:-1]+[end],False)
  from_('outer-Extensions-End-kind-ignored',[st]+canonical[0]+[end],True)
 ber=[[],[5,0],[2,0],[2,1,7],[1,1,1],[6,0],[9,1,66],[4,0x80],[0x30,0x80,5,0,0,0],[4,0x81,0],[0xff],[0,0],[0x30,3,2,0,0]]
 ber += [[t,0] for t in range(256)]+[[t,1,b] for t,b in itertools.product(range(32),[0,1,127,128,255])]
 ber += [[rng.randrange(256) for _ in range(rng.randrange(30))] for _ in range(400)]
 for b,api,n,part in itertools.product(ber,['try','unsafe'],[0,1,2,8],['values','spine']):add('BER-raw-Left-versus-lazy-typed-error-and-unsafe-prefix',api,value=[oid,False,b],count=n,part=part)
 for b in ber:
  add('try-Either-tag-does-not-force-typed-values','try',value=[oid,False,b],count=0,part='tag')
 for raw in [[None,None,[5,0]],[None,None,[0xff]],[oid,None,[2,0]],[None,None,[2,0]],[oid,None,None]]:
  for api,n,part in itertools.product(['try','unsafe'],[0,1,2],['tag','values','spine']):add('BER-ignores-critical-and-demands-OID-only-for-raw-error-message',api,value=raw,count=n,part=part)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['request']['api']=='property']
 print('Original X509 raw extensions verified',len(cases),'observations and 1000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print(json.dumps(manifest,indent=2))
