#!/usr/bin/env python3
"""Capture pinned original grid/table observations, never Kotlin expectations."""
import argparse, atexit, collections, copy, gzip, hashlib, itertools, json, math, os, random, struct, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/tables/grid.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_GRID_TABLE_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-grid-table-fixtures.py','tools/prepare-grid-table-reference.py',
 'tools/haskell-model-show-reference/PandocOptionsModels.hs','tools/haskell-model-show-reference/TemplateAdapter.hs',
 'upstream/pandoc/src/Text/Pandoc/Options.hs','upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs',
 'upstream/pandoc/src/Text/Pandoc/Writers/AnnotatedTable.hs','upstream/pandoc/test/Tests/Shared.hs',
 'upstream/pandoc/test/Tests/Writers/AnnotatedTable.hs','upstream/pandoc-types/pandoc-types.cabal',
 'core/src/test/resources/native/upstream.json','vendor/ghc-9.12.2/GHC/Internal/Float/RealFracMethods.hs',
 'licenses/pandoc-GPL-2.0.md','licenses/pandoc-types-BSD-3-Clause.txt','licenses/DocLayout-BSD-3-Clause.txt',
 'licenses/Doctemplates-BSD-3-Clause.txt','licenses/Skylighting-core-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/Utf8-string-BSD-3-Clause.txt']
for folder in ['tools/haskell-grid-table-reference','upstream/pandoc-types/src','vendor/doclayout-0.6',
               'vendor/doctemplates-0.11.1','vendor/skylighting-core-0.15/src','vendor/utf8-string-1.0.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
originals=json.loads((root/'core/src/test/resources/native/upstream.json').read_text())
def tables(x):
 if isinstance(x,dict):
  if x.get('t')=='Table':yield x
  for v in x.values():yield from tables(v)
 elif isinstance(x,list):
  for v in x:yield from tables(v)
originalTables=[]
for r in originals:
 if not r['accepted']:continue
 for i,t in enumerate(tables(r['json'])):
  originalTables.append((r['path'],i,t));sources.append('upstream/pandoc/'+r['path'])
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 subprocess.run(['python3','tools/prepare-grid-table-reference.py','--check'],cwd=root,check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert [(c['path'],c['tableIndex'],c['input'][2]) for c in cs if c.get('upstream') and c['input'][:2]==[72,0]]==originalTables
 assert m['numericBaseline']=='GHC 9.12.2 macOS ARM64 -O2'
 print(f'Verified {len(cs)} original grid/table observations, {len(originalTables)} upstream tables and {len(set(sources))} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(op,v,**extra):
 proc.stdin.write(json.dumps([op,v],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,v)
 expected=json.loads(line)
 assert not isinstance(expected,dict) or 'error' not in expected,(op,v,expected)
 cs.append(dict(op=op,input=v,expected=expected,**extra))
 if len(cs)%1000==0:print('Captured',len(cs),'grid observations',flush=True)
A=['',[],[]]
def tag(t,c=None):return {'t':t,**({'c':c} if c is not None else {})}
alignments=['AlignDefault','AlignLeft','AlignCenter','AlignRight']
def spec(a='AlignDefault',w=None):return [tag(a),tag('ColWidthDefault') if w is None else tag('ColWidth',w)]
def cell(name='',rs=1,cs=1,blocks=None):return [[name,[],[]],tag('AlignRight'),rs,cs,blocks or []]
def row(cells):return [A,cells]
def body(stub,hs=(),rs=()):return [A,stub,list(map(row,hs)),list(map(row,rs))]
def table(specs,hs=(),bs=(),fs=()):return tag('Table',[A,[None,[]],specs,[A,list(map(row,hs))],list(bs),[A,list(map(row,fs))]])
docs=[['hsep',['alpha','beta','gamma']],['prefix','> ',['vcat',['a','b']]],'漢字 e\u0301 🙂',
      ['bold',['link','https://example.test/','linked']],['empty'],['concat',['before','x'],['concat',['space'],['after','tail']]]]
for path,i,t in originalTables:
 for n,w in itertools.product([72,17,1],[0,1,2]):add('grid',[n,w,t,docs],upstream=True,path=path,tableIndex=i)
 add('tables',[t],upstreamHelper=True,path=path,tableIndex=i)
spanValues=[-(2**63),-7,-1,0,1,2,4,2**63-1]
for n,w,a,rs in itertools.product([-72,0,1,17,72,2**63-1],[0,1,2],alignments,spanValues):
 columns=[spec(a,.25),spec('AlignLeft',-.125),spec('AlignRight',1/3)]
 add('row',[n,w,[[columns,-17,cell('raw',rs,99)],[columns[1:],2**63-1,cell('raw2',rs,-1)]],docs],rawAnnotation=True)
for width,stub in itertools.product(range(7),spanValues):
 specs=[spec(alignments[i%4],None if i%2==0 else .25) for i in range(width)]
 hs=[[cell('h',3,2),cell('h2')],[],[cell('h3',-1,3)]]
 b=[body(stub,[[],[cell('intermediate',5,2)]],[[cell('stub',3,3),cell('body',2,3)],[],[cell('last')]]),body(stub,[],[[cell('second',4,3)],[]])]
 fs=[[cell('f',2,3)],[]]
 t=table(specs,hs,b,fs)
 for n,w in itertools.product([-1,0,5,11,40,100],[0,1,2]):add('grid',[n,w,t,docs],spanSections=True)
 add('tables',[t],spanSections=True)
failureTable=table([spec(),spec()],[[cell(blocks=[tag('Plain',[tag('Str','head')])]),cell()]],
 [body(1,[[cell(blocks=[tag('Para',[tag('Str','intermediate')])]),cell()]],[[cell(blocks=[tag('Plain',[tag('Str','body')])]),cell()]])],
 [[cell(blocks=[tag('Plain',[tag('Str','foot')])]),cell()]])
for at in range(-1,10):add('failure',[72,0,failureTable,at],callbackFailure=True)
# All Doc constructors, all seven font components, and cached widths which do
# not equal the payload's display width. These are callback outputs, not a
# substitute document writer.
font=[True,True,3,True,4,6,'font-link']
allDocs=[['text',9,'x'],['block',4,[[[None,font,'ab']],[]]],['vfill',2,'!?'],
 ['cooked',3,[[None,font,'cd']]],['prefix','  ',['hsep',['x','y']]],['before','x'],['flush','x'],
 ['space'],['after','z'],['cr'],['newline'],['blanklines',2],['concat','a','b'],
 ['styled',['fg',3],'red'],['link','target','link'],['empty']]
for d,n,w in itertools.product(allDocs,[0,4,20,72],[0,1,2]):
 add('grid',[n,w,table([spec('AlignCenter'),spec('AlignRight')],[[cell(),cell()]],[body(0,[],[[cell(),cell()],[cell(),cell()]])],[[cell(),cell()]]),[d]],docConstructor=True)
specs=[spec(a) for a in alignments]
def rc(n=0,s=1,a='AlignDefault',rs=1,w=3,d='x',b=1,t=1):return [n,s,specs,tag(a),rs,w,d,b,t]
# Border precedence, ragged rows, empty intermediate rows and per-cell styles.
borderRows=[[],[rc(0,a='AlignLeft'),rc(1,a='AlignCenter',w=0),rc(2,a='AlignRight',w=-3),rc(3)],
 [rc(0,2,'AlignRight',3,5,'long',0,1),rc(2,2,'AlignCenter',1,2,'short',1,0)],[]]
for s,markers in itertools.product(range(5),[False,True]):add('borders',[s,markers,borderRows])
for x,y in itertools.product(['','+','|',':','-','=',' ','x','漢','🙂','|--+','====',' : +'],repeat=2):add('combine',[x,y])
rng=random.Random(0x661D)
for i in range(800):
 width=rng.randrange(0,8)
 rs=[]
 for r in range(rng.randrange(0,6)):
  cells=[];at=rng.randrange(-2,2)
  for k in range(rng.randrange(0,7)):
   span=rng.randrange(1,5)
   cells.append(rc(at,span,rng.choice(alignments),rng.choice(spanValues),rng.randrange(-3,15),rng.choice(docs),rng.randrange(5),rng.randrange(5)))
   at+=span+rng.randrange(2)
  rs.append(cells)
 add('pipeline',[rng.randrange(-3,100),rng.randrange(3),specs[:width],rs],seeded=True)
for n in spanValues:
 for span in [-2,0,1,2,4,2**63-1]:
  add('reset',[[n,3,5],[rc(s=span),rc(n=3,s=2),rc(n=5)]],signedWidth=True)
# Nonpositive column spans don't evaluate offset or division in replicate.
for w in range(3):add('widths',[72,w,[[rc(s=0,d=['raw',2**63-1,'x']),rc(s=-1,d=['raw',-(2**63),'x'])],[rc(s=2,d=['raw',-9,'x'])]]])
for i in range(600):
 infos=[[rng.choice(spanValues),rng.choice(spanValues),rng.choice(spanValues),rng.choice([0,0,0]+spanValues)] for _ in range(rng.randrange(0,9))]
 add('recalculate',[rng.choice(spanValues),infos],signedWidth=True)
# Floating conversion is observed directly, without allocating astronomical
# borders or concealing nonfinite inputs. Native Int64 behavior is ARM64-specific.
def bits(d):return struct.unpack('>Q',struct.pack('>d',d))[0]
floats=[0.0,-0.0,math.inf,-math.inf,math.nan,5e-324,-5e-324,1.7976931348623157e308,-1.7976931348623157e308]
for exponent in [0,1,3,31,32,52,53,62,63,64,65,80,100,1023]:
 x=math.ldexp(1.0,exponent)
 for sign in [1,-1]:floats += [sign*x,math.nextafter(sign*x,math.inf),math.nextafter(sign*x,-math.inf)]
payloads=[bits(x) for x in floats]+[0x7ff0000000000001,0x7fffffffffffffff,0xfff8000000000001]+[rng.getrandbits(64) for _ in range(500)]
for n,payload in itertools.product([-(2**63),-72,-1,0,1,2,17,72,2**63-1],payloads):add('char-width',[n,payload],ieee=True)
for column,rs,span in itertools.product(spanValues,spanValues,spanValues):
 m=[[n,spanValues[(i+2)%8],spanValues[(i+3)%8]] for i,n in enumerate(spanValues)]
 for op in ['insert','take','trailing']:add('spans',[op,column,m,rs,span],signedSpan=True)
# Data.Map results expose ascending key order even with shuffled transport input.
# The Kotlin observation adapter must not sort away an incorrect returned map.
for column,rs,action,oldSpan in itertools.product([-10,-8,3,7],[-1,1,2],['insert','take','trailing'],[1,3]):
 add('spans',[action,column,[[7,oldSpan,8],[-10,3,-1],[0,0,6]],rs,5],mapOrder=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),
 oracle='unchanged Pandoc 3.12.1 Writers.Shared / AnnotatedTable, pandoc-types 1.23.1.2 and DocLayout 0.6',numericBaseline='GHC 9.12.2 macOS ARM64 -O2')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} grid/table observations; SHA {meta["caseHash"]}',flush=True)
