#!/usr/bin/env python3
"""Capture original TeX constructors, derived instances and ToSources behavior."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/tex/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_TEX_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-tex-fixtures.py','upstream/pandoc/src/Text/Pandoc/TeX.hs','upstream/pandoc/src/Text/Pandoc/Sources.hs',
 'upstream/pandoc/src/Text/Pandoc/Parsing/State.hs','upstream/pandoc/test/Tests/Readers/LaTeX.hs',
 'licenses/pandoc-GPL-2.0.md','licenses/Parsec-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt']
for folder in ['tools/haskell-tex-reference','vendor/parsec-3.1.18.0','vendor/ghc-9.12.2','vendor/text-2.1.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
documents=[]
for f in sorted((root/'upstream/pandoc/test').rglob('*.tex')):
 try:t=f.read_bytes().decode('utf-8')
 except UnicodeDecodeError:continue
 documents.append((str(f.relative_to(root)),t));sources.append(str(f.relative_to(root)))
sources=sorted(set(sources))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 assert not (root/'tools/haskell-tex-reference/Text/Pandoc/TeX.hs').exists()
 assert not (root/'tools/haskell-tex-reference/Text/Pandoc/Sources.hs').exists()
 assert '../../upstream/pandoc/src' in (root/'tools/haskell-tex-reference/kotdoc-tex-reference.cabal').read_text()
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 assert m['documents']==len(documents)
 assert [(c['path'],c['request'][1][0][2]) for c in cs if c.get('document')]==[(path,list(map(ord,t))) for path,t in documents]
 print(f'Verified {len(cs)} original TeX data observations, {len(documents)} TeX documents and {len(sources)} source/input hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(*request,**extra):
 proc.stdin.write(json.dumps(request,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,request
 cs.append(dict(request=request,expected=json.loads(line),**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'TeX observations',flush=True)
def ps(t):return list(map(ord,t))
def tok(t,typ=['Word'],pos=['',1,1]):return [pos,typ,ps(t)]
texts=['','x','foo','\\foo','\r\n','a\tb','漢🙂','\x0eH','\x00'+'1','\"\\\'','\ud800','\ud800\udfff','\ue000','\U00010000']
nums=[-2**63,-1,0,1,9,2**63-1]
types=[[n] for n in ['Spaces','Newline','Symbol','Word','Comment','Esc1','Esc2']]+[['CtrlSeq',ps(t)] for t in texts]+[[name,n] for name,n in itertools.product(['Arg','DeferredArg'],nums)]
positions=[['',1,1],['a',3,8],['a',3,9],['a',4,1],['b',1,1],['漢🙂',-1,0],['\"\r\n\t',-2**63,2**63-1],['\ue000',1,1],['\U00010000',1,1]]
tokens=[tok(t,typ,p) for t,typ,p in itertools.product(texts,types,positions)]
prec=[-1,0,6,10,11,12]
for typ in types:
 for p in prec:add('show','type',p,typ)
for a,b in itertools.product(types,types):add('relations','type',a,b)
for t in tokens:
 for p in [0,11]:add('show','token',p,t)
for name,values in [('scope',['GlobalScope','GroupScope']),('expansion',['ExpandWhenDefined','ExpandWhenUsed'])]:
 for a in values:
  for p in prec:add('show',name,p,a)
  for b in values:add('relations',name,a,b)
base=tok('x',['Word'],['file',3,8]);opening=tok('{',['Symbol'],['file',3,8]);closing=tok('}',['Symbol'],['file',3,9])
specs=[['VerbArg']]+[['ArgNum',n] for n in nums]+[['Pattern',ts] for ts in [[],[base],[opening,base,closing]]]
for b in [False,True]:
 specs.append(['BoolArg',b,base])
 for d in [None,[],[base]]:specs.append(['DelimArg',b,opening,closing,d])
 for n in nums:
  specs.append(['BodyArg',b,n]);specs.append(['VerbBodyArg',b,n])
for es in [[],[[base,None]],[[opening,[]],[closing,[base]]]]:specs.append(['EmbellishArg',es])
for pspec,a in itertools.product([[],[[]],[[base],[opening,closing]]],specs[:]):specs.append(['ProcessedArg',pspec,a])
for a in specs:
 for p in prec:add('show','spec',p,a)
for s,e,args,d,ts in itertools.product(['GlobalScope','GroupScope'],['ExpandWhenDefined','ExpandWhenUsed'],[[],[specs[0]],specs[:10],specs], [None,[],[base],[opening,base]], [[],[base],[opening,base,closing]]):
 m=[s,e,args,d,ts]
 for p in [0,11]:add('show','macro',p,m)
# Source groups retain names, first positions, empty text, CR and exact order.
for s in [[],[base],[base,base],[base,tok('',pos=['file',99,88])],[base,tok('y',pos=['other',1,1]),base],
 [tok('',pos=['a',1,1]),tok('',pos=['b',1,1])],[tok('\r\n',pos=['a',1,1]),tok('\r',pos=['a',9,12])]]:add('sources',s)
for path,t in documents:
 add('sources',[tok(t,pos=[path,1,1])],document=True,path=path)
 add('show','token',0,tok(t,pos=[path,1,1]),documentShow=True,path=path)
rng=random.Random(0x7EAD47A)
for n in range(6000):
 a=rng.choice(tokens);b=rng.choice(tokens)
 if n%3==0:b=[a[0],rng.choice(types),ps(rng.choice(texts))]
 if n%5==0:b=a
 add('relations','token',a,b,seeded=True)
for n in range(2500):
 ts=[tok(rng.choice(texts),rng.choice(types),rng.choice(positions)) for _ in range(rng.randrange(20))]
 add('sources',ts,seededGrouping=True)
for a in [base,tok('🙂',['CtrlSeq',ps('α')],['🙂file',3,8])]:
 for p in [0,11]:
  for n in range(60):add('prefix','token',p,n,a)
for name in ['type-tag-eq','type-tag-ord','type-arg-demand','type-self-demand','token-position-eq','token-position-ord',
 'token-type-eq','token-type-ord','source-ignore-types','source-single-ignore-pos','source-initial-ignore-tail',
 'source-initial-ignore-fields','source-count-ignore-text','source-text-before-next-pos','source-two-text-before-third-pos','source-single-text-before-tail','source-name-demand-token',
 'infinite-source-name']:
 add('partial',name,0,partial=True)
for name in ['prefix-type-text','prefix-type-number','prefix-token-fields','prefix-pattern-list','prefix-pattern-head',
 'prefix-maybe','prefix-macro-scope','infinite-processed','infinite-token-list']:
 for count in [-1,0,*range(1,101),1000]:add('partial',name,count,partial=True)
for count in [0,1,2,10,1000,10000]:add('partial','infinite-source-groups',count,partial=True)
for n in [0,1,10,1000,10000,100000]:add('deep',n,deep=True)
for n in [1,1000,10000,100000]:
 add('sources',[tok('x',['Arg',1],['same',3,i+1]) for i in range(n)],deepGrouping=True)
 add('sources',[tok('',['Word'],['a' if i%2 else 'b',3,i+1]) for i in range(n)],deepGrouping=True)
for n in [0,1,2,3,5,20]:
 for ft,fp,fs in itertools.product([-1,0,1,max(0,n-1)],repeat=3):add('plain',n,ft,fp,fs,sourceDemand=True)
 for ft,fp in itertools.product([-1,0,1,max(0,n-1)],repeat=2):add('group-text',n,ft,fp,groupDemand=True)
for n in [3,4,10,50]:
 for j in range(1,min(n,12)):add('priority',n,j,groupDemand=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['request'][0] for c in cs)),documents=len(documents),oracle='GHC 9.12.2, original Pandoc 3.12.1 TeX/Sources and Parsec 3.1.18.0; derived instances')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} TeX observations; SHA {meta["caseHash"]}',flush=True)
