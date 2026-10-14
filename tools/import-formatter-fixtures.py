#!/usr/bin/env python3
"""Exact original formatter/Binary observations; Kotlin never supplies expectations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/highlighting/formatters.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_FORMATTER_REFERENCE'));args=p.parse_args()
folders=['vendor/skylighting-core-0.15','vendor/skylighting-format-ansi-0.1','vendor/skylighting-format-blaze-html-0.1.2.2','vendor/skylighting-format-context-0.1.0.2','vendor/skylighting-format-latex-0.1','vendor/skylighting-format-typst-0.1','vendor/blaze-html-format-0.9.2.0','vendor/blaze-markup-format-0.8.3.0','vendor/ansi-terminal-format-1.1.5','vendor/ansi-terminal-types-format-1.1.3','tools/haskell-formatter-reference']
sources=['licenses/Skylighting-format-BSD-3-Clause.txt', 'licenses/Skylighting-context-BSD-3-Clause.txt', 'licenses/Blaze-BSD-3-Clause.txt', 'licenses/ANSI-terminal-BSD-3-Clause.txt', 'licenses/ANSI-terminal-types-BSD-3-Clause.txt']+['tools/import-formatter-fixtures.py','licenses/Skylighting-core-BSD-3-Clause.txt','licenses/Binary-BSD-3-Clause.txt','core/src/test/resources/highlighting/tokenizer.jsonl.gz','upstream/pandoc/stack.yaml','upstream/pandoc/cabal.project']
for folder in folders:sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cases=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cases) and m['caseHash']==digest(cases)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cases))
 assert len([c for c in cases if c.get('originalFile')])==32*8
 print(f'Verified {len(cases)} formatter/model Binary observations and {len(sources)} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
def native(op,input):
 proc.stdin.write(json.dumps([op,input],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,input)
 value=json.loads(line);assert not isinstance(value,dict) or 'exception' not in value,(op,input,value)
 return value
cases=[]
def add(op,input,**extra):
 result=native(op,input);cases.append(dict(op=op,input=input,expected=result,**extra))
 if len(cases)%2000==0:print('Captured',len(cases),'formatter cases',flush=True)
 return result
levels=['ANSI16Color','ANSI256Color','ANSITrueColor']
tokens='KeywordTok DataTypeTok DecValTok BaseNTok FloatTok ConstantTok CharTok SpecialCharTok StringTok VerbatimStringTok SpecialStringTok ImportTok CommentTok DocumentationTok AnnotationTok CommentVarTok OtherTok FunctionTok VariableTok ControlFlowTok OperatorTok BuiltInTok ExtensionTok PreprocessorTok AttributeTok RegionMarkerTok InformationTok WarningTok AlertTok ErrorTok NormalTok'.split()
empty_style=[[],None,None,None,None]
def ts(color=None,background=None,bold=False,italic=False,underline=False):return [color,background,bold,italic,underline]
def style(specs=(),default=None,background=None,line=None,line_background=None):return [list(specs),default,background,line,line_background]
def opts(**kw):
 d=dict(number=False,start=1,anchors=False,titles=False,code=[],container=[],prefix='',level=levels[0]);d.update(kw)
 return [d[k] for k in ['number','start','anchors','titles','code','container','prefix','level']]
builtins={}
for name in ['pygments','tango','espresso','zenburn','kate','monochrome','breezedark','haddock']:
 result=add('builtin',name);builtins[name]=result[0]
with gzip.open(root/'core/src/test/resources/highlighting/tokenizer.jsonl.gz','rt') as f:
 next(f);originals=[c for c in map(json.loads,f) if c['group']=='golden']
assert len(originals)==32
for case,(name,sty) in itertools.product(originals,builtins.items()):
 add('format',[opts(),sty,case['original']['tokens']],originalFile=case['file'],theme=name)
# Original source enum constructors, whitespace paths, escaping, empty tokens,
# physical-line joins, class ordering and option field combinations.
texts=['',' ','  ','   ','\t\r\n','\u00a0\u2000','a','<>&"\'{}|\\_$%#`-~^/','😀é中\x00\ufffe\uffff','/ETEX/BTEX','/ETEX /BTEX']
for token,text in itertools.product(tokens,texts):
 for titles in [False,True]:add('format',[opts(titles=titles),empty_style,[[[token,text]],[],[['NormalTok',' '],['KeywordTok','x']]]])
for number,anchors,titles,start,level in itertools.product([False,True],[False,True],[False,True],[1,0,-1,2,999,2**63-2,2**63-1,-2**63],levels):
 add('format',[opts(number=number,anchors=anchors,titles=titles,start=start,level=level,code=['x','sourceCode','x','','<>&"\''],container=['x','sourceCode','numberSource','','x'],prefix='id-<>&"\'😀'),builtins['pygments'],[[['KeywordTok','a']],[['NormalTok','b']],[],[['StringTok','c']]]])
for lines in [[],[[]],[[],[]],[[['NormalTok','']]],[[['KeywordTok','']]],[[['NormalTok','a\nb']]]]:
 for start,number in itertools.product([1,3,2**63-1,-2**63],[False,True]):add('format',[opts(start=start,number=number),empty_style,lines])
# All colors on each individual RGB axis exercise integer->fraction formatting
# and colour/ansi-terminal round trips. Boolean styling checks composition order.
for value in range(256):
 for axis in range(3):
  color=[0,0,0];color[axis]=value
  sty=style([['KeywordTok',ts(color,color,True,True,True)]],color,color,color,color)
  add('style',sty)
  add('ansi',[opts(level=levels[2],number=True),sty,[[['KeywordTok','x']],[]]])
for flags in itertools.product([False,True],repeat=3):
 sty=style([[t,ts([1,2,3],[4,5,6],*flags)] for t in tokens],default=[7,8,9],background=[10,11,12])
 add('style',sty)
 for level in levels:add('format',[opts(level=level,number=True,start=7),sty,[[[t,t] for t in tokens]]])
# ANSI nearest-color distance is Manhattan, and ties select the first original
# palette entry (grayscale, then cube, then system colors in 256-color mode).
for color in itertools.product(range(0,256,17),repeat=3):
 sty=style([['KeywordTok',ts(list(color))]],background=[127,128,129])
 for level in levels[:2]:add('ansi',[opts(level=level),sty,[[['KeywordTok','x']]]])
# Every exact palette entry and neighboring half-way colors.
palette=[[8+10*i]*3 for i in range(24)]+[list(c) for c in itertools.product([0,95,135,175,215,255],repeat=3)]
for color,level in itertools.product(palette,levels):
 add('ansi',[opts(level=level,number=True),style(default=color,line=[64,64,64]),[[['NormalTok','x']]]])
rng=random.Random(0xf04a7)
for _ in range(400):
 specs=[[t,ts([rng.randrange(256) for _ in range(3)] if rng.randrange(2) else None,[rng.randrange(256) for _ in range(3)] if rng.randrange(2) else None,*[bool(rng.randrange(2)) for _ in range(3)])] for t in rng.sample(tokens,rng.randrange(32))]
 colors=[None if rng.randrange(2) else [rng.randrange(256) for _ in range(3)] for _ in range(4)]
 sty=style(specs,*colors);add('style',sty)
 ls=[[[rng.choice(tokens),''.join(rng.choice('ab  \\<>\"\'😀é中\r\n') for _ in range(rng.randrange(30)))] for _ in range(rng.randrange(8))] for _ in range(rng.randrange(5))]
 add('format',[opts(level=rng.choice(levels),number=bool(rng.randrange(2)),start=rng.randrange(-10,10)),sty,ls])
option_values=[opts()]+[opts(start=n,level=l) for n,l in itertools.product([-2**63,-1,0,1,2,2**63-1],levels)]
for _ in range(80):option_values.append(opts(number=bool(rng.randrange(2)),start=rng.randrange(-100,100),anchors=bool(rng.randrange(2)),titles=bool(rng.randrange(2)),code=rng.choices(['','x','sourceCode','é','😀'],k=rng.randrange(5)),container=['c','c'],prefix=rng.choice(['','x-','😀','\x00','é']),level=rng.choice(levels)))
for o in option_values:add('opts',o)
for a,b in zip(option_values,option_values[1:]):add('opts-order',[a,b])
# Exact generic Binary field order, Maybe's nonzero tags, constructor errors,
# negative/large list/text lengths, truncated fields and trailing input.
values={'options':option_values,'level':levels,'color':[[r,g,b] for r,g,b in itertools.product([0,1,127,128,254,255],repeat=3)],'token-style':[ts(c,b,*flags) for c,b,flags in itertools.product([None,[1,2,255]],[None,[0,128,64]],itertools.product([False,True],repeat=3))],'style':[empty_style]+list(builtins.values())}
encoded={}
for kind,vs in values.items():
 encoded[kind]=[]
 for v in vs:
  raw=bytes.fromhex(add('encode',[kind,v]));encoded[kind].append(raw)
  add('decode',[kind,raw.hex()]);add('decode',[kind,(raw+b'\x00\xfftail').hex()])
  for n in range(len(raw)):add('decode',[kind,raw[:n].hex()])
for kind in values:
 for byte in range(256):
  raw=encoded[kind][0]
  add('decode',[kind,(bytes([byte])+raw[1:]).hex()])
for kind in ['token-style','options']:
 raw=encoded[kind][0]
 positions=range(len(raw)) if kind=='token-style' else [0,9,10,len(raw)-1]
 for position in positions:
  for byte in range(256):add('decode',[kind,(raw[:position]+bytes([byte])+raw[position+1:]).hex()])
# Containers' original fromDistinctAscList accepts invalid ordered/duplicate maps;
# keep their complete observed entry list and re-encoded bytes.
i=lambda n:struct.pack('>q',n)
ts_raw=encoded['token-style'][0]
for keys in [[],[1,0],[1,1],[3,1,0],[0,0,0],[30,0,1]]:
 raw=i(len(keys))+b''.join(bytes([k])+ts_raw for k in keys)+b'\0'*4
 add('decode',['style',raw.hex()])
for kind in ['options','style']:
 for length in [-2**63,-1,0,1,2,2**63-1]:
  raw=encoded[kind][0];position=11 if kind=='options' else 0
  add('decode',[kind,(raw[:position]+i(length)+raw[position+8:]).hex()])
proc.stdin.close();assert proc.wait()==0
manifest=dict(cases=len(cases),caseHash=digest(cases),operations=dict(collections.Counter(c['op'] for c in cases)),sources=hashes(),seed='0xf04a7',versions={'ansi':'0.1','html':'0.1.2.2','context':'0.1.0.2','latex':'0.1','typst':'0.1'})
raw='\n'.join(json.dumps(x,separators=(',',':')) for x in [manifest]+cases)+'\n'
with target.open('wb') as out:
 with gzip.GzipFile(filename='',fileobj=out,mode='wb',mtime=0) as f:f.write(raw.encode())
print(json.dumps({k:v for k,v in manifest.items() if k!='sources'},indent=2),flush=True)
