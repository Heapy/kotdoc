#!/usr/bin/env python3
"""Observe unchanged Pandoc Highlighting/Shared pure functions, never Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/highlighting/highlighting.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_HIGHLIGHTING_REFERENCE'));args=p.parse_args()
source=(root/'upstream/pandoc/src/Text/Pandoc/Highlighting.hs').read_text();shared=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text()
functions=(root/'tools/haskell-highlighting-reference/HighlightingFunctions.hs').read_text()
assert source[source.index('-- | The default highlighting style'):source.index('-- | Lookup style from a name.')] in functions
assert shared[shared.index('safeRead ::'):] in functions
pairs=re.findall(r'\("([^"\\]*)","([^"\\]*)"\)',source[source.index('langsList ='):source.index('-- | Determine listings language')]);assert len(pairs)==103
production=(root/'core/src/main/kotlin/io/heapy/kotdoc/highlighting/ListingsLanguages.kt').read_text()
assert re.findall(r'    "([^"]*)" to "([^"]*)",',production)==pairs
sources=['upstream/pandoc/src/Text/Pandoc/Highlighting.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','tools/import-highlighting-fixtures.py','licenses/pandoc-GPL-2.0.md','core/src/test/resources/highlighting/syntax-binary.jsonl.gz','core/src/main/kotlin/io/heapy/kotdoc/highlighting/ListingsLanguages.kt']
for folder in ['vendor/skylighting-core-0.15','vendor/utf8-string-1.0.2','tools/haskell-highlighting-reference']:sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cases=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cases) and m['caseHash']==digest(cases)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cases))
 assert len([c for c in cases if c.get('originalFile')])==32
 print(f'Verified {len(cases)} Pandoc highlighting observations, all 103 listings pairs, {len(sources)} source files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
def native(op,input):
 proc.stdin.write(json.dumps([op,input],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,input)
 return json.loads(line)
cases=[]
def add(op,input,**extra):
 cases.append(dict(op=op,input=input,expected=native(op,input),**extra))
 if len(cases)%2000==0:print('Captured',len(cases),'highlighting cases',flush=True)
def attr(classes=(),ident='',values=()):return [ident,list(classes),list(values)]
add('languages',None);add('styles',None)
for key,value in pairs:
 for language in [key,key.upper(),key.title(),value,' '+key,key+' ',key+'\0']:
  add('to-listings',language);add('from-listings',language)
for value in ['', 'unknown','csharp','C','C++','TeX','TEX','Lisp','Assembler','make','S','s','Swift','PYTHON','ObjectiveC']:
 add('to-listings',value);add('from-listings',value)
for ext in ['','c','C','.c','cpp','CPP','cs','hs','haskell','py','Python','rb','m','xml','*','?','[ch]','foo/bar.pl','file://foo.pl','😀','é']:
 add('extension',ext)
# Every original tokenized source file goes through the real Pandoc wrapper.
for path in sorted((root/'vendor/skylighting-core-0.15/test/cases').iterdir()):
 add('builtin',[attr([path.suffix[1:]]),path.read_bytes().decode()],originalFile=str(path.relative_to(root)))
# All default syntax names and aliases pass through class selection.
with gzip.open(root/'core/src/test/resources/highlighting/syntax-binary.jsonl.gz','rt') as f:
 next(f);syntaxes={c['input']:c['expected']['value'] for c in map(json.loads,f) if c['op']=='baked'}
for name,syn in syntaxes.items():
 for language in [name,syn[2],name.upper(),name.lower()]:
  add('builtin',[attr([language,'number-lines','lineAnchors'],ident='id',values=[['startFrom','3']]),'x = 1;\n\n😀'])
classes=[[],['unknown'],['number'],['numberLines'],['number-lines'],['Number'],['line-anchors'],['lineAnchors'],['number','unknown'],['unknown','cpp'],['cpp','python'],['python','cpp'],['NUMBER','CPP'],['cpp','numberLines','line-anchors'],['number-lines','cpp','cpp','sourceCode',''],['sourceCode','number','unknown']]
texts=['','\n','\n\n','x\n','x\r\n','x\n\ny','😀é\0\ufffe\uffff','foo = 1;\n# comment','\t  x']
for cls,text in itertools.product(classes,texts):
 add('builtin',[attr(cls,'a"&<😀',[['startFrom','-2'],['startFrom','9'],['startfrom','7']]),text])
nums=['','0','1','-1','+1','001',' 1 ','(1)','((1))','(- 1)','- (1)','1\n','1\u00a0','1\u2000','1\u2028','1\u2029','1.0','1e3','0x12','0XFF','0o77','0O77','0b11','0B11','1_000','1tail','1--comment','--1','-+1','+ 1','( 1 )','(1)tail','(1) (2)','1{- comment -}','{-x-}1','1 -- comment','¹','١','１','9223372036854775807','9223372036854775808','18446744073709551616','-9223372036854775809','9'*1000]
for text in nums:
 add('safe-int',text)
 for cls in [['number'],['cpp'],['unknown']]:add('builtin',[attr(cls,values=[['startFrom',text],['startFrom','9']]),'x\n\ny'])
rng=random.Random(0x41a7)
for _ in range(1800):
 n=str(rng.randrange(-2**130,2**130));n=rng.choice(['','(','(('])+n
 n+=rng.choice(['',')','))','x',' ','\n'])
 add('safe-int',n)
for n in range(-300,301):
 for pattern in ['{}','({})',' (({})) ','{} ','{}x']:
  add('safe-int',pattern.format(n))
# Every possible Char classification around an otherwise valid integer is
# already independently pinned; here sample every GHC Unicode space category.
spaces=[chr(n) for n in [0,9,10,11,12,13,28,32,133,160,5760,6158,8192,8193,8194,8195,8196,8197,8198,8199,8200,8201,8202,8203,8232,8233,8239,8287,12288,65279]]
for space in spaces:
 for pattern in ['{}1','1{}','({}1{})','-{}1']:
  add('safe-int',pattern.format(space,space))
# Synthetic maps preserve map-key ordering distinct from syntax names, shortname
# case folding, class priority, numbering aliases and typed tokenizer errors.
def ctx(name='start',rules=(),syntax='A'):return [name,syntax,list(rules),'NormalTok',[],[],False,[],False]
def syn(name='A',short='A',contexts=None,start='start',exts=()):return [name,'a.xml',short,[],[['start',ctx(syntax=name)]] if contexts is None else contexts,'','','',list(exts),start]
D=list(map(ord,' \n\t.()'))
def rule(m):return [m,'KeywordTok',False,D,False,True,[],False,False,None,[]]
models=[[],[['z',syn('A','İ')],['a',syn('Z','ẞ')]],[["A",syn(exts=['*.one','*.c','x*'])],["B",syn('B','B',exts=['*.c'])]]]
for mp in models:
 add('model-languages',mp)
 for ext in ['','one','c','.c','C','*','?']:add('model-extension',[mp,ext])
 for cls in [[],['a'],['z'],['A'],['number'],['A','B'],['B','A'],['unknown','A'],['number','A']]:
  add('model',[mp,attr(cls,'id',[['startFrom','0x10']]),'x\n\ny'])
errors=[syn(start='missing'),syn(contexts=[['start',ctx(rules=[rule(['RegExpr',['28',True,False]])])]]),syn(contexts=[['start',ctx(rules=[rule(['IncludeRules',['Missing','ctx']])])]])]
for s in errors:
 for cls in [['A'],['unknown','A'],['number','A'],['A','number']]:
  for text in ['', 'x']:add('model',[[['A',s]],attr(cls),text])
for points in [[0xd800],[0xdfff],[0xd800,0xdfff],[65,0xd800,10,66],[0xfffe,0xffff,0x1f600]]:
 for cls in [['number'],['cpp']]:add('points',[attr(cls),points])
# Literal original regression and XML loop behavior are covered independently by
# the tokenizer corpus; wrapper-specific checks preserve their errors/callbacks.
proc.stdin.close();assert proc.wait()==0
m=dict(cases=len(cases),caseHash=digest(cases),sources=hashes(),operations=dict(collections.Counter(c['op'] for c in cases)),seed='0x41a7')
raw='\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cases)+'\n'
with target.open('wb') as out:
 with gzip.GzipFile(filename='',fileobj=out,mode='wb',mtime=0) as f:f.write(raw.encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2),flush=True)
