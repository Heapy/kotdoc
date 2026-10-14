#!/usr/bin/env python3
"""Capture unchanged Pandoc AnnotatedTable conversion and Walkable instances."""
import argparse,atexit,collections,copy,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/tables/annotated.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_ANNOTATED_TABLE_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-annotated-table-fixtures.py','upstream/pandoc/src/Text/Pandoc/Writers/AnnotatedTable.hs','upstream/pandoc/test/Tests/Writers/AnnotatedTable.hs','upstream/pandoc-types/pandoc-types.cabal','core/src/test/resources/native/upstream.json','licenses/pandoc-GPL-2.0.md','licenses/pandoc-types-BSD-3-Clause.txt']
for folder in ['upstream/pandoc-types/src','tools/haskell-annotated-table-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
originals=json.loads((root/'core/src/test/resources/native/upstream.json').read_text())
originalTables=[]
def tables(value):
 if isinstance(value,dict):
  if value.get('t')=='Table':yield value
  for v in value.values():yield from tables(v)
 elif isinstance(value,list):
  for v in value:yield from tables(v)
for row in originals:
 if not row['accepted']:continue
 ts=list(tables(row['json']))
 if ts:sources.append('upstream/pandoc/'+row['path'])
 for i,t in enumerate(ts):originalTables.append((row['path'],i,t))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert [(c['path'],c['tableIndex'],c['input']) for c in cs if 'path' in c]==originalTables
 assert sum(c.get('originalSample',False) for c in cs)==1
 print(f'Verified {len(cs)} original annotated-table captures, {len(originalTables)} upstream tables and {len(sources)} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(v,op='annotate',**extra):
 proc.stdin.write(json.dumps([op,v],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,v)
 c=dict(op=op,input=v,expected=json.loads(line),**extra);cs.append(c)
 if len(cs)%500==0:print('Captured',len(cs),'tables',flush=True)
 return c
A=['',[],[]]
def tag(t,c=None):return {'t':t,**({'c':c} if c is not None else {})}
def cell(name='',h=1,w=1,blocks=None,align='AlignDefault'):
 return [[name,[],[]],tag(align),h,w,blocks or []]
def row(cells):return [A,cells]
def spec(align='AlignDefault',width=None):return [tag(align),tag('ColWidthDefault') if width is None else tag('ColWidth',width)]
def table(cols,head=None,bodies=None,foot=None):
 return tag('Table',[A,[None,[]],cols,[A,list(map(row,head or []))],bodies or [],[A,list(map(row,foot or []))]])
def body(stub,head=None,rows=None):return [A,stub,list(map(row,head or [])),list(map(row,rows or []))]
# The complete original sample input, including cells clipped at section boundaries.
specs=[spec('AlignRight'),spec('AlignLeft'),spec('AlignCenter')]
hs=[[cell('a'),cell('b',3,2)],[cell('c',2,2),cell('d')]]
sample=table(specs,hs,[body(1,[[],[cell('e',5),cell('f',-7,0)]],[[cell('g',4,3),cell('h',4,3)],[],[cell()]]),body(2,[],[[cell('i',4,3),cell('j',4,3)]])],hs)
add(sample,originalSample=True)
for path,i,t in originalTables:add(t,path=path,tableIndex=i)
# Empty sections, width zero, absent padding, stub clamping and signed limits.
spans=[-(2**63),-7,-1,0,1,2,4,2**63-1]
for width,stub in itertools.product(range(9),spans):
 cells=[cell('x',spans[(width+1)%8],spans[width%8]),cell('y',4,8)]
 add(table([spec(['AlignLeft','AlignRight','AlignCenter','AlignDefault'][i%4],None if i%2==0 else .25) for i in range(width)],
  [cells,[]],[body(stub,[cells,[]],[cells,[],[cell('z')]])],[cells,[]]),edge=True)
for h,w in itertools.product(spans,repeat=2):
 add(table(specs,[[cell('a',h,w),cell('b',h,w)],[cell('c')]],
  [body(1,[],[[cell('stub',h,w),cell('body',h,w)],[],[cell('last')]])]),edge=True)
rng=random.Random(0xA6607)
def attr(name):return [name,['class'],[['data-x','1'],['data-x','2']]]
rich=[tag('Para',[tag('Str','A'),tag('Emph',[tag('Str','B'),tag('Note',[tag('Para',[tag('Str','N')])])])]),
      tag('BulletList',[[tag('Plain',[tag('Str','C')])]]),tag('CodeBlock',[attr('code'),'code'])]
def gen():
 width=rng.randrange(0,9)
 specs=[spec(rng.choice(['AlignLeft','AlignRight','AlignCenter','AlignDefault']),rng.choice([None,1/3,.25,-1.,0.])) for _ in range(width)]
 def rows():
  return [[attr('row-'+str(i)),[[attr('cell-'+str(k)),tag(rng.choice(['AlignLeft','AlignRight','AlignCenter','AlignDefault'])),rng.choice(spans),rng.choice(spans),rich if rng.randrange(6)==0 else []] for k in range(rng.randrange(0,7))]] for i in range(rng.randrange(0,6))]
 return tag('Table',[attr('table'),[[tag('Str','caption-short')],[tag('Para',[tag('Str','caption-long')])]],specs,[attr('head'),rows()],
  [[attr('body'),rng.choice(spans),rows(),rows()] for _ in range(rng.randrange(0,4))],[attr('foot'),rows()]])
for i in range(1000):add(gen(),seeded=True)
for width,kind in itertools.product([0,1,3],['tail','unused-cell','cell-list']):
 add([kind,table([spec() for _ in range(width)],[[cell('placed',1,max(1,width))]] if width else [[]])],'partial',partialCells=True)
# fromTable strips arbitrary annotations, without renormalizing the core cells.
for source in cs[:12]:
 raw=copy.deepcopy(source['expected']['annotated'])
 raw[2]=[]
 def cells(rows,body=False):
  for r in rows:
   r[1]=-(2**63)
   for c in (r[2]+r[3] if body else r[2]):
    c[1]=2**63-1;c[2][2]=-8;c[2][3]=0
 raw[0]=attr('raw')
 cells(raw[3][1]);cells(raw[5][1])
 for b in raw[4]:b[1]=2**63-1;cells(b[2]);cells(b[3],True)
 add(raw,'raw',arbitraryAnnotations=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),oracle='GHC 9.12.2 unchanged Pandoc 3.12.1 AnnotatedTable and pandoc-types 1.23.1.2')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} annotated-table observations; SHA {meta["caseHash"]}',flush=True)
