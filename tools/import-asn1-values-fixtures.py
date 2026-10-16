#!/usr/bin/env python3
"""Original ASN1 value models, stream splitting, and ParseASN1 observations."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-asn1-values-reference';target=root/'core/src/test/resources/asn1-values/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
pins={'crypton-asn1-types-0.4.1':'61efc63877a0d3ddb992a793839d9427c1597e095a6e1f63c04e05fd3e4e302d','crypton-asn1-parse-0.10.0':'35c0f278d8fcb720b4ad89c2a7f256f7fb098b9f71a0aeb493a4c3c0fe7728d9','time-hourglass-0.3.0':'9c7c7c74dda5033160328606cb3e8724201abadf9a140271c73c89a2612b9d22'}
sources=[Path(__file__).resolve()]
for name,h in pins.items():
 d=root/'vendor'/name;m=json.loads((d/'SOURCE.json').read_text());assert m['package']+'-'+m['version']==name and m['sha256']==h and m['url']=='https://hackage-content.haskell.org/package/'+name+'.tar.gz'
 assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
 for path,sha in m['files'].items():assert hashlib.sha256((d/path).read_bytes()).hexdigest()==sha,(name,path)
 notice=root/'licenses'/(m['package']+'-BSD-3-Clause.txt');assert notice.read_bytes()==(d/'LICENSE').read_bytes()
 sources += [notice]+[f for f in d.rglob('*') if f.is_file()]
sources += [f for f in folder.rglob('*') if f.is_file()]
assert set(folder.rglob('*.hs'))=={folder/'Asn1ValuesReference.hs'}
assert '../../vendor/time-hourglass-0.3.0' in (folder/'cabal.project').read_text()
for d in ['crypton-asn1-types-0.4.1','crypton-asn1-parse-0.10.0']:assert '../../vendor/'+d+'/src' in (folder/'kotdoc-asn1-values-reference.cabal').read_text()
for d in ['crypton-asn1-types-0.4.1','crypton-asn1-parse-0.10.0']:assert 'test-suite' not in (root/'vendor'/d/(d.rsplit('-',1)[0]+'.cabal')).read_text()
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 if a.check_source:print('Verified',len(cs),'original ASN1 values/ParseASN1 observations and',len(m['sources']),'source hashes');raise SystemExit
else:
 cs=[];rng=random.Random(0x41534e3150415253)
 def add(group,api,**v):cs.append(dict(group=group,request=dict(api=api,**v)))
 seq=['sequence'];st=['set'];kinds=[seq,st]+[['container',cl,tag] for cl,tag in itertools.product(range(4),[-2**63,-1,0,16,31,2**63-1])]
 dt=[2026,9,16,7,30,12,123456789]
 scalars=[['boolean',False],['boolean',True],['null']]+[[ty,str(n)] for ty,n in itertools.product(['integer','enumerated'],[-(1<<512),-129,-1,0,1,128,1<<512])]
 scalars += [['bits',str(n),b] for n,b in [(0,[]),(1,[128]),(9,[128,1]),(2**64-1,[])]]
 scalars += [['octets',[]],['octets',list(range(256))],['oid',[]],['oid',['2','9999999999999999999999999999999999999999999999','-1']]]
 scalars += [['real',str(bits)] for bits in [0,1,0x3ff0000000000000,0x8000000000000000,0xbff0000000000000,0x7fefffffffffffff,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000000,0xfff8000000000123]]
 scalars += [['string',e,list(range(256))] for e in range(12)]
 scalars += [['time',ty,dt,tz] for ty,tz in itertools.product(range(2),[None,0,60,-360,2**63-1])]
 scalars += [['other',cl,tag,[0,34,92,255]] for cl,tag in itertools.product(range(4),[-2**63,-1,0,16,2**63-1])]
 markers=[[label,c] for c,label in itertools.product(kinds,['start','end'])]
 for value in scalars+markers:add('all-ASN1-constructors-complete-payload-Show-Eq-and-self-Eq','values',values=[value,value])
 for _ in range(500):
  xs=rng.choices(scalars+markers,k=rng.randrange(17));add('heterogeneous-value-Eq-constructor-discrimination-and-NaN','values',values=xs)
 for _ in range(500):
  value=['real',str(rng.randrange(1<<64))];add('IEEE754-raw-bits-Haskell-Show-signed-zero-and-NaN','values',values=[value,value,['real','0'],['real','9223372036854775808']])
 edges=[-2**63,-999,-1,0,1,59,60,999999999,2**63-1]
 for year,month in itertools.product(edges,range(12)):
  a0=[year,month,rng.choice(edges)]+[rng.choice(edges) for _ in range(4)];b0=a0.copy();b0[rng.choice([0,2,3,4,5,6])]=rng.choice(edges)
  add('hourglass-model-Show-Eq-Ord-and-unchecked-constructors','timeModels',first=a0,second=b0,tz1=rng.choice(edges),tz2=rng.choice(edges))
 for _ in range(500):
  a0=[rng.randrange(-10000,10001),rng.randrange(12),rng.randrange(-50,51)]+[rng.randrange(-1000000,1000001) for _ in range(4)]
  b0=[rng.randrange(-10000,10001),rng.randrange(12),rng.randrange(-50,51)]+[rng.randrange(-1000000,1000001) for _ in range(4)]
  add('hourglass-model-component-order-and-offset-wrapping','timeModels',first=a0,second=rng.choice([a0,b0]),tz1=rng.randrange(-2**63,2**63),tz2=rng.randrange(-2**63,2**63))
 atom=['integer','1'];alphabet=[atom,['start',seq],['end',st],['start',['container',2,3]],['end',['container',3,4]]]
 streams=[list(xs) for n in range(5) for xs in itertools.product(alphabet,repeat=n)]
 for xs,depth in itertools.product(streams,[-2**63,-1,0,1,2**63-1]):
  add('nested-stream-extraction-mismatched-unclosed-and-machine-depth','stream',values=xs,depth=depth,events=[[[i%256],rng.choice(['begin','end'])] for i in range(len(xs))])
 for _ in range(500):
  xs=rng.choices(scalars+markers,k=rng.randrange(40));add('representation-value-event-association-and-raw-payloads','stream',values=xs,depth=rng.choice([-2,0,2]),events=[[[rng.randrange(256) for _ in range(rng.randrange(5))]] for x in xs])
 base=[dict(op='next'),dict(op='has'),dict(op='object'),dict(op='empty'),dict(op='fail',message='failed'),dict(op='monadFail',message='failed'),dict(op='pure',value=[1,2]),dict(op='bottom',message='value demanded')]
 base += [dict(op='optional',select=s) for s in ['any','integer','boolean','start','null','never','error','bottomSome']]
 base += [dict(op=op,kind=c) for op,c in itertools.product(['container','containerMaybe'],[seq,st,['container',2,3]])]
 base += [dict(op=op,kind=seq,program=p0) for op,p0 in itertools.product(['on','onMaybe'],[dict(op='next'),dict(op='many',program=dict(op='next')),dict(op='pure',value=0),dict(op='bottom',message='inside demanded')])]
 base += [dict(op='many',program=p0) for p0 in [dict(op='next'),dict(op='object'),dict(op='container',kind=seq)]]
 base += [dict(op='map',mapper=mapper,value=0,message='mapped demanded',program=dict(op='bottom',message='argument demanded')) for mapper in ['wrap','constant','error']]
 base += [dict(op='choice',first=dict(op='then',first=dict(op='next'),second=dict(op='fail',message='after consume')),second=dict(op='next'))]
 base += [dict(op='then',first=dict(op='bottom',message='ignored'),second=dict(op='pure',value=0)),dict(op='sequence',programs=[]),dict(op='sequence',programs=[dict(op='bottom',message='ignored'),dict(op='fail',message='later failure')])]
 base += [dict(op='apply',function=dict(op=fop,mapper=mapper,value=42,message='function demanded'),program=dict(op=arg,**({'message':'argument demanded'} if arg in ['bottom','fail'] else {'value':3}))) for fop,mapper,arg in itertools.product(['pure','consume','bottom','fail'],['wrap','constant','error'],['pure','bottom','fail'])]
 base += [dict(op='bind',program=dict(op='has'),equals=True,yes=dict(op='next'),no=dict(op='pure',value='empty'))]
 for xs,p0 in itertools.product([x for x in streams if len(x)<=2],base):add('all-ParseASN1-combinators-state-full-backtracking-and-lazy-results','parse',values=xs,program=p0)
 for xs in [[],[atom],[atom,['boolean',True]],[atom,atom]]:
  add('getMany-later-failure-precedes-unneeded-earlier-value','parse',values=xs,program=dict(op='many',program=dict(op='map',mapper='error',message='earlier value demanded',program=dict(op='object'))))
 for points in [[],[0,10,34,92],[0xd800,0xdc00],[0x10000],[0xdbff,0xdfff],[0x10ffff],[0xffff],list(range(256))]:
  error=dict(op='failPoints',points=points)
  for api,xs,p0 in itertools.product(['parse','parsePoints'],[[],[atom],[['start',seq],atom,['end',st]]],[error,dict(op='map',mapper='wrap',program=error),dict(op='on',kind=seq,program=error),dict(op='choice',first=error,second=dict(op='failPoints',points=list(reversed(points))))]):
   add('raw-Haskell-String-errors-surrogate-pair-versus-supplementary-Char',api,values=xs,program=p0)
 def random_program(depth):
  if depth==0:return rng.choice(base[:len(base)-36])
  op=rng.choice(['sequence','choice','then','map','on','bind'])
  if op=='sequence':return dict(op=op,programs=[random_program(depth-1) for _ in range(rng.randrange(4))])
  if op in ['choice','then']:return dict(op=op,first=random_program(depth-1),second=random_program(depth-1))
  if op=='map':return dict(op=op,mapper=rng.choice(['wrap','constant','error']),value=17,message='map demanded',program=random_program(depth-1))
  if op=='on':return dict(op=rng.choice(['on','onMaybe']),kind=rng.choice(kinds),program=random_program(depth-1))
  return dict(op='bind',program=dict(op='has'),equals=True,yes=random_program(depth-1),no=random_program(depth-1))
 for _ in range(1000):add('composed-parser-programs-and-arbitrary-typed-streams','parse',values=rng.choices(scalars+markers,k=rng.randrange(20)),program=random_program(3))
 for n in [0,1,2,1000,10000]:
  xs=[['start',seq]]*n+[['null']]+[['end',st]]*n+[['integer','9']]
  add('deep-finite-construction-extraction','stream',values=xs,depth=0,events=[[] for _ in xs])
  add('iterative-getMany-through-finite-long-stream','parse',values=[atom]*n,program=dict(op='many',program=dict(op='next')))
 for n,xs in itertools.product([-2**63,-1,0,1,2**512],[[],[atom],[atom,['null']],[['boolean',True]]]):add('ASN1Object-test-dictionary-and-tail-builder-contract','object',integer=str(n),values=xs)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cs).encode());inp.seek(0);subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=180);out.seek(0);observed=[json.loads(line) for line in out]
assert len(cs)==len(observed),(len(cs),len(observed))
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cs,observed)):assert c['expected']==o,(i,c,o)
 print('Independent oracle agrees on all',len(cs),'ASN1 value/parser observations');raise SystemExit
for c,o in zip(cs,observed):c['expected']=o
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for x in [m]+cs:gz.write((json.dumps(x,separators=(',',':'))+'\n').encode())
print('Wrote',len(cs),'original observations;',target.stat().st_size,'bytes;',m['caseHash'])
