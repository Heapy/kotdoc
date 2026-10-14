#!/usr/bin/env python3
"""Native derived Show observations from unchanged upstream declarations/modules."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/options/model-show.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_MODEL_SHOW_REFERENCE'));args=p.parse_args()
source=(root/'upstream/pandoc/src/Text/Pandoc/Options.hs').read_text()
models=(root/'tools/haskell-model-show-reference/PandocOptionsModels.hs').read_text()
for path,names in [('upstream/pandoc/src/Text/Pandoc/Options.hs',['MathMethod','CiteMethod','ObfuscationMethod','HighlightMethod','WrapOption','TopLevelDivision','ReferenceLocation','CaptionPosition','WriterOptions']),('upstream/pandoc/src/Text/Pandoc/Extensions.hs',['Extension','Extensions']),('upstream/pandoc/src/Text/Pandoc/Chunks.hs',['PathTemplate'])]:
 text=(root/path).read_text()
 for name in names:
  decl=re.search(r'^(?:data|newtype) '+name+r'\b.*?deriving \([^)]*\)',text,re.M|re.S).group()
  assert decl in models,(path,name)
assert source[source.index('instance Default WriterOptions where'):source.index('instance HasSyntaxExtensions WriterOptions')].rstrip() in models
fields=re.findall(r'(writer\w+)\s*::',source[source.index('data WriterOptions ='):source.index('instance Default WriterOptions')]);assert len(fields)==39
production=(root/'core/src/main/kotlin/io/heapy/kotdoc/options/OptionsShow.kt').read_text()
assert re.findall(r'        "(writer\w+)" to',production)==fields
sources=['upstream/pandoc/src/Text/Pandoc/Options.hs','upstream/pandoc/src/Text/Pandoc/Extensions.hs','upstream/pandoc/src/Text/Pandoc/Chunks.hs','tools/import-model-show-fixtures.py','tools/haskell-doctemplates-reference/DoctemplatesReference.hs','tools/haskell-syntax-binary-reference/SyntaxBinaryReference.hs','core/src/test/resources/templates/runtime.jsonl.gz','core/src/test/resources/highlighting/syntax-binary.jsonl.gz','core/src/test/resources/highlighting/styles.jsonl.gz','licenses/pandoc-GPL-2.0.md','licenses/DocLayout-BSD-3-Clause.txt','licenses/Doctemplates-BSD-3-Clause.txt','licenses/Skylighting-core-BSD-3-Clause.txt']
for folder in ['vendor/doctemplates-0.11.1','vendor/doclayout-0.6','vendor/parsec-3.1.18.0','vendor/filepath-1.5.4.0','vendor/skylighting-core-0.15','vendor/utf8-string-1.0.2','tools/haskell-model-show-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cases=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cases) and m['caseHash']==digest(cases)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cases))
 assert m['writerFields']==fields
 assert len([c for c in cases if c.get('originalTemplate')])==62
 assert sorted(set(c['originalTemplate'] for c in cases if c.get('originalTemplate') and not c['originalTemplate'].startswith('unit:')))==sorted(p.name for p in (root/'vendor/doctemplates-0.11.1/test').glob('*.test'))
 assert len(set(c['originalTemplate'] for c in cases if c.get('originalTemplate','').startswith('unit:')))==9
 assert len([c for c in cases if c.get('originalSyntax')])==195
 print(f'Verified {len(cases)} native model Show observations, all 39 writer fields and {len(sources)} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cases=[]
def add(op,input,p=0,**extra):
 proc.stdin.write(json.dumps([op,p,input],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,input)
 expected=json.loads(line);assert isinstance(expected,str),expected
 cases.append(dict(op=op,input=input,precedence=p,expected=expected,**extra))
 if len(cases)%1000==0:print('Captured',len(cases),'native Show cases',flush=True)
precs=[-2147483648,-1,0,6,7,10,11,12,2147483647]
texts=['','ascii','\0\a\b\t\n\v\f\r\x0eH\x1f\x7f','"\\','é9😀0','Привет','\ufffe\uffff','a\r\nb']
ints=[-2**63,-2147483649,-1,0,1,2147483648,2**63-1]
for p in precs:add('writer',{},p)
for field in ['tableOfContents','listOfFigures','listOfTables','incremental','numberSections','sectionDivs','referenceLinks','htmlQTags','setextHeaders','listTables','epubTitlePage','preferAscii','linkImages']:
 for value in [False,True]:add('writer',{field:value})
for field in ['tabStop','dpi','columns','slideLevel','splitLevel','tocDepth']:
 for n in ints:
  for p in [0,11]:add('writer',{field:n},p)
for field in ['identifierPrefix','epubSubdirectory','epubMetadata','referenceDoc','chunkTemplate']:
 for s in texts:add('writer',{field:s})
for n in [None,-1,0,1]:add('writer',{'slideLevel':n})
for field in ['epubMetadata','referenceDoc','template']:add('writer',{field:None})
for xs in [[],ints,[1]*9]:add('writer',{'numberOffset':xs})
for xs in [[],['x','x'],texts]:add('writer',{'epubFonts':xs})
enums={'wrapText':['WrapAuto','WrapNone','WrapPreserve'],'emailObfuscation':['NoObfuscation','ReferenceObfuscation','JavascriptObfuscation'],'citeMethod':['Citeproc','Natbib','Biblatex'],'topLevelDivision':['TopLevelPart','TopLevelChapter','TopLevelSection','TopLevelDefault'],'referenceLocation':['EndOfBlock','EndOfSection','EndOfDocument'],'figureCaptionPosition':['CaptionAbove','CaptionBelow'],'tableCaptionPosition':['CaptionAbove','CaptionBelow']}
for field,values in enums.items():
 for v in values:add('writer',{field:v})
for m in [['PlainMath'],['GladTeX'],['MathML']]+[[tag,s] for tag in ['WebTeX','MathJax','KaTeX'] for s in texts]:
 for p in precs:add('math',m,p)
 add('writer',{'mathMethod':m})
# Original Extension constructor order and duplicate/set handling.
exts=[['builtin',n] for n in re.findall(r'\bExt_(\w+)\b',(root/'upstream/pandoc/src/Text/Pandoc/Extensions.hs').read_text().split('    deriving')[0])]
exts=list({tuple(x):x for x in exts}.values())
exts += [['custom',s] for s in texts]
for ext in exts:
 for p in [0,10,11]:add('extension',ext,p)
for es in [[],exts,exts[::-1],exts+exts,[['custom','😀'],['custom','\ue000'],['builtin','strikeout']]]:
 for p in precs:add('extensions',es,p)
 add('writer',{'extensions':es})
with gzip.open(root/'core/src/test/resources/highlighting/styles.jsonl.gz','rt') as f:
 next(f);styles=[c['expected']['value'] for c in map(json.loads,f) if c['op']=='builtin']
for method in ['NoHighlighting','DefaultHighlighting','IdiomaticHighlighting']+styles:
 for p in precs:add('highlight',method,p)
 add('writer',{'highlightMethod':method})
# All 16 public Doc constructors and all style/font alternatives.
fonts=[]
for w,s,u,st,fg,bg,l in itertools.product([False,True],[False,True],range(4),[False,True],[None,0,7],[None,1,6],[None,'','é😀']):
 f=[w,s,u,st,fg,bg,l];fonts.append(f)
for f in fonts[::7]:
 add('font',f,0);add('font',f,11)
base=[False,False,0,False,None,None,None]
parts=[[None,base,''],['link',fonts[-1],'é😀'],['',fonts[13],'x']]
docs=[['empty'],['space'],['cr'],['newline'],['blanklines',-1],['vfill',-2,'x'],['text',-2,'é😀'],['cooked',9,parts],['block',0,[[],parts]],['prefix','> ',['text',1,'x']],['before',['empty']],['flush',['text',0,'x']],['after','é😀'],['concat',['text',1,'x'],['newline']],['styled',['weight',True],['empty']],['link','url',['text',1,'x']]]
styles2=[[name,b] for name in ['weight','shape','strikeout'] for b in [False,True]]+[['underline',n] for n in range(4)]+[[name,n] for name in ['fg','bg'] for n in [None]+list(range(8))]
for s in styles2:
 for p in precs:add('style',s,p)
 docs.append(['styled',s,['text',1,'x']])
for d in docs:
 for p in precs:add('doc',d,p)
for n,s in itertools.product(ints,texts):add('doc',['text',n,s],11)
for n in ints:
 for p in precs:add('doc-int',['text',n,str(n)],p)
for points in [[],[0,14,72,127,233,57,128512,48],[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000],[0x10ffff,49]]:
 for p in precs:
  add('doc-string-points',[len(points),points],p)
  add('writer-text-points',points,p)
  add('writer-path-points',points,p)
pipes=['pairs','uppercase','lowercase','length','reverse','first','last','rest','allbutlast','chomp','alpha','roman','nowrap']
for a,n,s in itertools.product(range(3),ints,texts):
 pipe=['block',a,n,s,'R'];pipes.append(pipe)
 for p in [0,11]:add('pipe',pipe,p)
for p,s in itertools.product(precs,texts):add('border',[s,s],p)
var=[['x','y'],pipes[:13]+[pipes[-1]]]
for p in precs:
 for ps in [[],[''],texts]:add('variable',[ps,pipes],p)
templates=[['empty'],['interpolate',var],['conditional',var,['empty'],['literal',['text',1,'x']]],['iterate',var,['interpolate',[['it'],[]]],['literal',['text',2,', ']]],['nested',['empty']],['partial',pipes[:13],['interpolate',var]],['literal',['block',1,[parts]]],['concat',['empty'],['empty']]]
for t in templates:
 for p in precs:add('template',t,p)
 add('writer',{'template':t})
vals=[['null'],['bool',False],['bool',True]]+[['simple',d] for d in docs]
vals += [['list',vals[:]],['map',[['z',['null']],['a',['bool',True]],['😀',['simple',['text',1,'x']]],['\ue000',['simple',['text',1,'y']]]]]]
for v in vals:
 for p in [0,11]:add('value',v,p)
for p in precs:
 v=['map',[[s,['simple',['text',1,s]]] for s in texts]]
 add('context',v,p);add('writer',{'variables':v},p)
# Preserve original golden source provenance; observe their actual compiled trees.
with gzip.open(root/'core/src/test/resources/templates/runtime.jsonl.gz','rt') as f:
 next(f);originals=[c for c in map(json.loads,f) if (c.get('original') or c.get('originalUnit')) and c['op']=='runtime']
assert len(originals)==31
for c in originals:
 for p in [0,11]:add('template',c['input'][0],p,originalTemplate=c.get('original') or 'unit:'+c['originalUnit'])
 add('writer',{'template':c['input'][0],'variables':c['input'][1]})
with gzip.open(root/'core/src/test/resources/highlighting/syntax-binary.jsonl.gz','rt') as f:
 next(f);syntaxes={c['input']:c['expected']['value'] for c in map(json.loads,f) if c['op']=='baked'}
for name,syn in syntaxes.items():add('writer',{'syntaxMap':[[name,syn]]},originalSyntax=name)
add('writer',{'syntaxMap':'builtin'},originalDefaultSyntaxMap=True)
rng=random.Random(0x5a0)
for i in range(400):
 o={field:rng.choice(values) for field,values in enums.items()}
 o.update(columns=rng.choice(ints),identifierPrefix=rng.choice(texts),slideLevel=rng.choice([None]+ints),numberOffset=[rng.choice(ints) for _ in range(rng.randrange(9))],extensions=rng.sample(exts,rng.randrange(8)),template=rng.choice(templates),variables=['map',[[str(i),rng.choice(vals)]]],highlightMethod=rng.choice(styles+['NoHighlighting','DefaultHighlighting']))
 add('writer',o,rng.choice(precs))
meta=dict(cases=len(cases),sources=hashes(),caseHash=digest(cases),writerFields=fields,operations=dict(collections.Counter(c['op'] for c in cases)),oracle='GHC 9.12.2, original Pandoc 3.12.1 declarations, doctemplates 0.11.1, doclayout 0.6, Skylighting 0.15')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for row in [meta]+cases:z.write((json.dumps(row,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cases)} native Show observations; {meta["operations"]}; SHA {meta["caseHash"]}',flush=True)
