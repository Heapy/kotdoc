#!/usr/bin/env python3
"""Independent original Definition Eq/Ord dictionaries, generators and boundaries."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/ast-order/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_AST_ORDER_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
show_path='core/src/test/resources/ast-show/data.jsonl.gz'
with gzip.open(root/show_path,'rt') as f:show_meta=json.loads(next(f));show_cases=[json.loads(l) for l in f]
native=json.loads((root/'core/src/test/resources/native/upstream.json').read_text());docs=[c for c in native if c['accepted']]
sources=['tools/import-ast-order-fixtures.py',show_path,'core/src/test/resources/native/upstream.json','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/ghc-prim-BSD-3-Clause.txt','licenses/Containers-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt']
sources+=['upstream/pandoc-types/'+s for s in subprocess.check_output(['git','-C',str(root/'upstream/pandoc-types'),'ls-files'],text=True).splitlines()]
sources+=['upstream/pandoc/'+c['path'] for c in native]
for folder in ['tools/haskell-ast-order-reference','vendor/ghc-ast-order-9.12.2','vendor/containers-syntax-0.7','vendor/text-2.1.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
sources=sorted(set(sources))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 originals=[c for c in cs if c.get('document')]
 assert [(c['path'],c['request'][2]) for c in originals]==[(d['path'],d['json']) for d in docs for _ in range(3)]
 assert m['documents']==len(docs) and m['generators']==dict(collections.Counter(c['request'][1] for c in cs if c.get('originalGenerator')))
 assert '../../upstream/pandoc-types' in (root/'tools/haskell-ast-order-reference/cabal.project').read_text()
 print(f'Verified {len(cs)} original AST Eq/Ord observations, {len(docs)} documents and {len(sources)} hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def call(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(*req,**extra):
 cs.append(dict(request=req,expected=call(req),**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'AST Eq/Ord observations',flush=True)
values=collections.defaultdict(dict)
for c in show_cases:
 a=c['request']
 if a[0]=='show':values[a[1]][json.dumps(a[3],sort_keys=True)]=a[3]
rng=random.Random(0xA570AD)
for a,b in itertools.product([{'t':'ColWidthDefault'},{'t':'ColWidth','c':0.0},{'t':'ColWidth','c':-0.0},{'t':'ColWidth','c':1e23}],repeat=2):
 add('relations','colWidth',a,b,constructorFields=True)
# Constructor cross-products plus field extremes from the complete Show inputs.
for kind,by_json in values.items():
 if kind=='pandoc':continue
 vs=list(by_json.values())
 if len(vs)>65:
  chosen={}
  for v in vs:
   if isinstance(v,dict) and 't' in v:chosen.setdefault(v['t'],v)
  sample=rng.sample(vs,65)
  vs=list({json.dumps(v,sort_keys=True):v for v in [*chosen.values(),*sample]}.values())
 for a,b in itertools.product(vs,vs):add('relations',kind,a,b,constructorFields=True)
 for v in by_json.values():add('relations',kind,v,v,self=True)
empty={'pandoc-api-version':[1,23,1,2],'meta':{},'blocks':[]}
for i,d in enumerate(docs):
 for other in [d['json'],docs[(i+1)%len(docs)]['json'],empty]:add('relations','pandoc',d['json'],other,document=True,path=d['path'])
# Every original Arbitrary instance supported by the relevant AST data types.
for kind in ['pandoc','meta','inline','block','citation','row','cell','caption','tableHead','tableBody','tableFoot','style','delim','alignment','quote','math','citationMode']:
 for c in call(['sample',kind,0xA570AD,80]):cs.append(dict(c,originalGenerator=True))
# Hash-only Ord ignores all other Citation fields; Eq still checks them.
def tag(n,c=None):return dict(t=n,**({} if c is None else dict(c=c)))
cites=[]
for name,prefix,suffix,mode,n,h in itertools.product(['','a','b'],[[],[tag('Space')]], [[],[tag('LineBreak')]],['AuthorInText','SuppressAuthor','NormalCitation'],[-2**63,0,2**63-1],[0,7]):
 cites.append(dict(citationId=name,citationPrefix=prefix,citationSuffix=suffix,citationMode=tag(mode),citationNoteNum=n,citationHash=h))
for i in range(6000):
 a=rng.choice(cites);b=rng.choice(cites)
 if i%2==0:b=dict(b,citationHash=a['citationHash'])
 add('relations','citation',a,b,citationCollision=True)
for kind in ['inline','block','metaValue','colSpec','citation','row','cell']:
 vs=list(values[kind].values())[:8];ls=[[],[vs[0]],vs,vs[::-1]]
 for a,b in itertools.product(ls,ls):add('list',kind,a,b)
bits=[0,2**63,1,2**63+1,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000000,0xfff8000000000001,0x7ff0000000000001,0x7ff8000000000007,0x7fefffffffffffff,0x0010000000000000]
bits += [struct.unpack('>Q',struct.pack('>d',x))[0] for x in [-1.,1.,-1e23,1e23,1e-7,1e-6]]
contexts=['double','colWidth','colSpec','colSpecs','block','inline','metaValue','meta','pandoc','pandoc-blocks','caption','caption-short','cell','row','tableHead','tableFoot','tableBody','tableBody-head']
for a,b,kind in itertools.product(bits,bits,contexts):add('width',kind,a,b,ieee=True)
for _ in range(1000):
 a=rng.getrandbits(64);b=rng.getrandbits(64)
 if rng.randrange(3)==0:b=a
 for kind in contexts:add('width',kind,a,b,ieee=True)
# Isolated surrogates are replaced by original Text.pack before Eq/Ord.
texts=[list(map(ord,t)) for t in ['', 'a','A','ß','SS','σ','ς','Σ','漢🙂','\ue000','\U00010000','\x0eH','\x00'+'1','Ꭰ','ꭰ']]
for kind,a,b in itertools.product(['text','format','inline'],texts,texts):add('text',kind,a,b)
for cp in [*range(256),*range(0xd800,0xe000),0xffff,0x10000,0x10ffff]:
 for kind in ['text','format','inline']:
  add('text',kind,[cp,49],[rng.randrange(0x110000),49]);add('text',kind,[cp],[cp])
for name in ['inline-tag','inline-element','inline-length','inline-first','inline-self','inline-tail','citation-hash','citation-self',
 'meta-size','meta-size-more','meta-key','meta-first','meta-demand','pandoc-meta','caption-short','caption-short-length','row-attr','cell-attr','block-attr']:
 for op in ['eq','ne','compare','lt','le','gt','ge']:add('partial',name,op,partial=True)
for kind,n,diff in itertools.product(['inline','block','metaValue','mixed'],[0,1,10,1000,10000,100000],[False,True]):add('deep',kind,n,diff,deep=True)
for n in [0,1,2,10,1000,10000,100000]:add('infinite',n,infinite=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['request'][0] for c in cs)),documents=len(docs),generators=dict(collections.Counter(c['request'][1] for c in cs if c.get('originalGenerator'))),oracle='Unchanged pandoc-types 1.23.1.2 instances and Arbitrary; GHC 9.12.2, Text 2.1.2, QuickCheck 2.16.0.0')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} AST Eq/Ord observations; SHA {meta["caseHash"]}',flush=True)
