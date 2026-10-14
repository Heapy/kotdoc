#!/usr/bin/env python3
"""Capture original Future and Reader instances, including partial demands."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/parsing/future.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_FUTURE_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-future-fixtures.py','upstream/pandoc/src/Text/Pandoc/Parsing/Future.hs',
 'upstream/pandoc/src/Text/Pandoc/Parsing/State.hs','core/src/test/resources/native/upstream.json',
 'licenses/pandoc-GPL-2.0.md','licenses/Mtl-BSD-3-Clause.txt','licenses/Transformers-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt']
for folder in ['tools/haskell-future-reference','vendor/mtl-future-2.3.1','vendor/transformers-future-0.6.1.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
docs=[(r['path'],r['json']) for r in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if r['accepted']]
sources+=['upstream/pandoc/'+path for path,_ in docs];sources=sorted(set(sources))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
assert not (root/'tools/haskell-future-reference/Text/Pandoc/Parsing/Future.hs').exists()
assert 'mtl ==2.3.1' in (root/'tools/haskell-future-reference/kotdoc-future-reference.cabal').read_text()
assert 'transformers ==0.6.1.2' in (root/'tools/haskell-future-reference/kotdoc-future-reference.cabal').read_text()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['input'][0] for c in cs))
 assert [c['path'] for c in cs if c.get('upstreamDocument')]==[p for p,_ in docs]
 print(f'Verified {len(cs)} original Future observations, {len(docs)} upstream documents and {len(sources)} source/input hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(v,**extra):
 proc.stdin.write(json.dumps(v,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,v
 cs.append(dict(input=v,expected=json.loads(line),**extra))
 if len(cs)%1000==0:print('Captured',len(cs),'Future observations',flush=True)
N=lambda n:['n',n];S=lambda s:['s',s];L=lambda xs:['l',xs];P=lambda a,b:['p',a,b];O=lambda xs:['o',xs]
bad=['err','unused'];zero=N(0);one=N(1);identity=['id'];const=['const',N(7)]
datums=[N(-2**63),N(-1),zero,one,N(2**63-1),S(''),S('α 漢 𐐀'),L([]),L([one,bad,N(3)]),
 P(one,bad),P(bad,one),O([['x',one],['unused',bad]]),bad,['cons',one,bad],['a',{'nested':[None,True,'🙂']}]]
functions=[identity,const,['const',bad],['err','function'],['add',1],['times',-2],['negate'],['first'],['second'],
 ['field','x'],['head'],['tail'],['length'],['nth',0],['nth',1],['nth',-1],['nth',30],
 ['pair',identity,const],['list',[const,identity]],['compose',const,['err','ignored']]]
for d,f in itertools.product(datums,functions):
 for op in ['run','runDelayed']:
  for p in [['map',f,['pure',d]],['asks',f],['constructor',f],['bind',['pure',d],f],['ap',['pure',f],['pure',d]]]:
   add([op,p,d],functionMatrix=True)
for env,arg,f in itertools.product(datums,datums,[['first'],['second'],const,['pair',['first'],['second']]]):
 for p in [['ap',['env',f],['pure',arg]],['ap',['mapped',f,['pure',env]],['pure',arg]],
           ['bind-env',['pure',arg],f],['lift',f,['pure',env],['pure',arg]]]:
  add(['run',p,env],sharedEnvironment=True)
programs=[['ask'],['asks',identity],['constructor',identity],['pure',one],['fail','action'],['pure',bad]]
for p,q in itertools.product(programs,repeat=2):
 for op in ['then','before','append-left','append-right']:
  for env in [one,bad]:add(['run',[op,p,q],env],ignoredAction=True)
for d in datums:
 for op in ['empty','empty-sum','empty-product']:add(['run',[op],d],monoid=True)
 for left,right in itertools.product([L([]),L([one]),L([bad]),['cons',one,bad],bad],repeat=2):
  p=['append',['pure',left],['pure',right]]
  for f in [identity,['head'],['tail'],['nth',0],const]:add(['run',['map',f,p],d],semigroupList=True)
for op,left,right in itertools.product(['sum','product'],[-2**63,-1,0,1,2**63-1],[-2**63,-1,0,1,2**63-1]):
 add(['run',[op,['pure',N(left)],['pure',N(right)]],bad],numericMonoid=True)
for kind,d,env in itertools.product(['maybe','list','identity','io','proxy'],datums,[one,bad]):
 add(['return',kind,d,env],outerMonad=True)
for kind,d in itertools.product(['maybe','list','identity','io','proxy'],datums):add(['return-shape',kind,d],outerShape=True)
for kind in ['value','failure']:add(['memo',kind],sharedDemand=True)
for p in [['pure',['self']],['map',const,['pure',['self']]],['bind',['pure',['self']],const],['asks',const],['ask']]:
 add(['run',p,['self']],sharedCycle=True)
for f in [['head'],['nth',0],['nth',100000],const]:add(['run',['map',f,['pure',['repeat',one]]],bad],infinitePrefix=True)
for kind,n in itertools.product(['nothing','ask','map','drop','bind','empty','sum'],[-2**63,-1,0,1,2**63-1]):add(['nullable',kind,n],nullable=True)
for depth,kind in itertools.product([0,1,32,1000,10000,100000],['map','map-lazy','bind','bind-lazy','then','before']):
 add(['deep',kind,depth,one],deep=True)
for path,doc in docs:
 state=O([['doc',['a',doc]],['meta',['a',doc['meta']]],['ignored',bad]])
 add(['run',['asks',['field','doc']],state],upstreamDocument=True,path=path)
 add(['run',['bind-env',['fail','ignored'],['compose',['field','meta'],['second']]],state],upstreamMetadata=True,path=path)
 add(['multi',['asks',['field','meta']],[O([['meta',['a',{}]]]),state,O([['meta',['a',{'final':['changed']}]]])]],finalStates=True,path=path)
rng=random.Random(0xF0702)
def tree(depth):
 if depth==0:return rng.choice([['pure',N(rng.choice([-2**63,-1,0,1,2**63-1]))],['ask'],['asks',['field','x']],['fail','unused']])
 op=rng.choice(['map','bind','bind-env','ap','then','before','lift','sum','product','append-left','append-right'])
 f=rng.choice(functions)
 if op=='map':return [op,f,tree(depth-1)]
 if op in ['bind','bind-env']:return [op,tree(depth-1),f]
 if op=='ap':return [op,rng.choice([['pure',f],['env',f],['mapped',f,tree(depth-1)]]),tree(depth-1)]
 if op=='lift':return [op,f,tree(depth-1),tree(depth-1)]
 return [op,tree(depth-1),tree(depth-1)]
for n in range(3500):add(['run',tree(rng.randrange(1,6)),rng.choice(datums)],seeded=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['input'][0] for c in cs)),
 oracle='GHC 9.12.2 original Pandoc 3.12.1 Future and local unmodified mtl 2.3.1/transformers 0.6.1.2 Reader/Identity instances')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} Future observations; SHA {meta["caseHash"]}',flush=True)
