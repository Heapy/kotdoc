#!/usr/bin/env python3
"""Observe unchanged Pretty/Prim and retain source provenance, including base16."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/asn1-pretty/data.jsonl.gz'
sources=[Path(__file__).resolve()]
for name,archive,notice in [
 ('base16-1.0','86366364910b78609677817cf3f987bf1690e1f75bba04bc91a5ed993d619cde','base16-BSD-3-Clause.txt'),
 ('crypton-asn1-types-0.4.1','61efc63877a0d3ddb992a793839d9427c1597e095a6e1f63c04e05fd3e4e302d','crypton-asn1-types-BSD-3-Clause.txt'),
 ('crypton-asn1-encoding-0.10.0','49813a0ee253f628cb8102dd6857905609b52e490c73c554d7cdf0cbce2926d2','crypton-asn1-encoding-BSD-3-Clause.txt'),
 ('time-hourglass-0.3.0','9c7c7c74dda5033160328606cb3e8724201abadf9a140271c73c89a2612b9d22','time-hourglass-BSD-3-Clause.txt')]:
 d=root/'vendor'/name;m=json.loads((d/'SOURCE.json').read_text());assert m['sha256']==archive
 assert m['package']+'-'+m['version']==name and m['url']=='https://hackage-content.haskell.org/package/'+name+'.tar.gz'
 assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
 for f,h in m['files'].items():assert hashlib.sha256((d/f).read_bytes()).hexdigest()==h,(name,f)
 assert (root/'licenses'/notice).read_bytes()==(d/'LICENSE').read_bytes()
 sources += [root/'licenses'/notice]+[f for f in d.rglob('*') if f.is_file()]
sources += [f for f in (root/'tools/haskell-asn1-pretty-reference').rglob('*') if f.is_file()]
numeric=root/'vendor/ghc-asn1-pretty-9.12.2/GHC/Internal/Numeric.hs'
assert hashlib.sha256(numeric.read_bytes()).hexdigest()=='f7c9919e2de587743a48c0e744a35fb11c7a14685a5164c838bb2b6b42cc32bc'
d=numeric.parents[2];m=json.loads((d/'SOURCE.json').read_text())
assert m['ref']=='ghc-9.12.2-release' and m['package']=='ghc-internal' and m['version']=='9.1202.0'
assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for f,h in m['files'].items():assert hashlib.sha256((d/f).read_bytes()).hexdigest()==h
assert (d/'LICENSE').read_bytes()==(root/'licenses/GHC-base-BSD-3-Clause.txt').read_bytes()
sources += [numeric,d/'SOURCE.json',d/'LICENSE',root/'licenses/GHC-base-BSD-3-Clause.txt']
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 if a.check_source:print('Verified',len(cases),'Pretty/demand observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 cases=[];rng=random.Random(0x505245545459)
 def add(group,api='pretty',**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def pretty(group,vs,single=False,indent=0,prefix=False,count=0,tailError=False):add(group,values=vs,single=single,indent=indent,prefix=prefix,count=count,tailError=tailError)
 edge=[-2**63,-2**63+1,-999,-1,0,1,2,127,128,255,256,999,2**63-1]
 for single,indent,otherSingle,otherIndent in itertools.product([False,True],edge,[False,True],edge):add('PrettyType-derived-Eq-Show','mode',single=single,indent=indent,otherSingle=otherSingle,otherIndent=otherIndent)
 payloads=[[],list(range(256)),[0,1,7,8,9,10,13,27,31,32,34,92,127,128,255]]+[[i] for i in range(256)]+[[rng.randrange(256) for _ in range(n)] for n in [2,3,7,8,15,16,17,31,32,33,63,64,65,127,128,129,255,256,257,511,512,513]]
 vs=[['boolean',False],['boolean',True],['null']]+[['integer',str(i)] for i in edge+[-2**512,2**512,2**512+1]]
 vs += [['bits',str(n),b] for n,b in itertools.product([0,1,7,8,9,2**64-1],payloads[:3])]
 vs += [['octets',b] for b in payloads]
 vs += [['string',e,b] for e,b in itertools.product(range(12),payloads)]
 vs += [['oid',x] for x in [[],['0'],['1','2','840','113549'],[str(-2**512),str(2**512)]]]
 vs += [['enumerated',str(n)] for n in edge]+[['real',str(n)] for n in [0,1,2,2**52-1,2**52,0x3ff0000000000000,0x8000000000000000,0x7fefffffffffffff,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000000,0xfff8000000000000]]
 vs += [['real',str(rng.randrange(2**64))] for _ in range(400)]
 for cl,tag in itertools.product(range(4),edge):
  vs += [['other',cl,tag,payloads[2]],['start',['container',cl,tag]],['end',['container',cl,tag]]]
 vs += [[tag,[kind]] for tag,kind in itertools.product(['start','end'],['sequence','set'])]
 for ty,tz in itertools.product(range(2),[None,-2**63,-9999,-61,-60,-1,0,1,60,61,9999,2**63-1]):
  vs += [['time',ty,[2026,9,16,5,34,12,123456789],tz],['time',ty,[-1,0,0,-1,61,60,-1],tz]]
 for v in vs:
  for single,indent in [(True,0),(False,0),(False,2)]:pretty('every-constructor-byte-escape-hex-Double-and-time',[v],single,indent)
 for _ in range(500):
  stream=rng.choices(vs,k=rng.randrange(20))
  pretty('arbitrary-unbalanced-marker-stream',stream,rng.choice([False,True]),rng.randrange(-5,6))
 for indent,tag in itertools.product(edge,['start','end','null']):
  stream=[[tag,['sequence']]] if tag!='null' else [['null']]
  stream += [['start',['set']],['null'],['end',['sequence']],['null']]
  for n in [-1,0,1,2,3,4,8,16,32]:pretty('indentation-Int64-wrap-and-negative-replicate',stream,False,indent,True,n)
 for v in [['integer','-1'],['timeError','date'],['timeError','time'],['time',0,[2026,9,16,5,34,12,0],-2**63]]:
  for single,indent,n in itertools.product([False,True],[-1,0,2],range(130)):pretty('prefix-demand-before-bottom-fields',[v],single,indent,True,n)
 for stream in [[],[['null']],[['start',['sequence']],['null'],['end',['set']]]]:
  for single,indent,n in itertools.product([False,True],[-1,0,2],range(24)):pretty('prefix-demand-before-input-tail',stream,single,indent,True,n,True)
 invalid=[['oid',[]],['oid',['0']],['start',['sequence']],['end',['set']]]
 streams=[[],[['null']]]+[[v] for v in invalid]+[[['integer','1'],v] for v in invalid]+[[['start',['sequence']],v,['end',['set']]] for v in invalid]+[[['oid',[]],['oid',['0']]]]
 for stream,method,part,n in itertools.product(streams,['list','primitive','one','constructed'],['spine','kinds','headerTags','shown','length'],range(7)):
  value=stream[0] if stream else ['null']
  add('encoded-list-spine-event-constructor-and-strict-header-demand','encode',values=stream,value=value,method=method,part=part,count=n,tailError=False)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 print('Original Pretty/Prim verified',len(cases),'observations');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print(json.dumps(manifest,indent=2))
