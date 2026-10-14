#!/usr/bin/env python3
"""Capture original pure writer helpers, all Char positions and upstream ASTs."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/writers/shared.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_WRITER_SHARED_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-writer-shared-fixtures.py','tools/prepare-writer-shared-reference.py',
 'tools/haskell-model-show-reference/PandocOptionsModels.hs','tools/haskell-model-show-reference/TemplateAdapter.hs',
 'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs','upstream/pandoc/src/Text/Pandoc/Options.hs',
 'upstream/pandoc/src/Text/Pandoc/Extensions.hs','upstream/pandoc/test/Tests/Shared.hs',
 'upstream/pandoc-types/pandoc-types.cabal','core/src/test/resources/native/upstream.json',
 'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json','core/src/main/resources/io/heapy/kotdoc/regex-char-data.json',
 'tools/import-parser-char-data.py','tools/import-regex-char-data.py',
 'licenses/pandoc-GPL-2.0.md','licenses/pandoc-types-BSD-3-Clause.txt','licenses/DocLayout-BSD-3-Clause.txt',
 'licenses/Doctemplates-BSD-3-Clause.txt','licenses/Skylighting-core-BSD-3-Clause.txt',
 'licenses/GHC-base-BSD-3-Clause.txt','licenses/Unicode-3.0.txt','licenses/Utf8-string-BSD-3-Clause.txt']
for folder in ['tools/haskell-writer-shared-reference','upstream/pandoc-types/src','vendor/doclayout-0.6',
 'vendor/doctemplates-0.11.1','vendor/skylighting-core-0.15/src','vendor/utf8-string-1.0.2','vendor/ghc-9.12.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
docs=[(r['path'],r['json']) for r in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if r['accepted']]
sources+=['upstream/pandoc/'+path for path,_ in docs]
inlineNames=set('Str Emph Underline Strong Strikeout Superscript Subscript SmallCaps Quoted Cite Code Space SoftBreak LineBreak Math RawInline Link Image Note Span'.split())
blockNames=set('Plain Para LineBlock CodeBlock RawBlock BlockQuote OrderedList BulletList DefinitionList Header HorizontalRule Table Figure Div'.split())
def nodes(x):
 if isinstance(x,dict):
  if x.get('t') in inlineNames|blockNames:yield x
  for v in x.values():yield from nodes(v)
 elif isinstance(x,list):
  for v in x:yield from nodes(v)
seen=set();inputs=[];occurrences=collections.Counter()
for path,doc in docs:
 for value in nodes(doc):
  op='inline' if value['t'] in inlineNames else 'block';occurrences[op]+=1
  key=json.dumps(value,sort_keys=True,separators=(',',':'))
  if key not in seen:seen.add(key);inputs.append((op,value))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 subprocess.run(['python3','tools/prepare-writer-shared-reference.py','--check'],cwd=root,check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs)) and m['upstreamOccurrences']==dict(occurrences)
 assert [(c['path'],c['input']) for c in cs if c.get('upstreamDocument')]==docs
 assert [(c['op'],c['input']) for c in cs if c.get('upstreamNode')]==inputs
 assert [n for c in cs if c['op']=='char-range' for n in range(*c['input'])]==list(range(0x110000))
 for old in [False,True]:
  assert [n for c in cs if c['op']=='unsmartify' and c.get('allChars') and c['input'][0]==old for n in c['input'][1]]==list(range(0x110000))
 print(f'Verified {len(cs)} original pure writer observations, {len(docs)} documents, {len(inputs)} distinct upstream nodes ({sum(occurrences.values())} occurrences), all Haskell Chars and {len(set(sources))} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(op,v,**extra):
 proc.stdin.write(json.dumps([op,v],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,v)
 expected=json.loads(line);assert not isinstance(expected,dict) or 'error' not in expected,(op,v,expected)
 cs.append(dict(op=op,input=v,expected=expected,**extra))
 if len(cs)%1000==0:print('Captured',len(cs),'pure writer observations',flush=True)
for start in range(0,0x110000,4096):add('char-range',[start,min(start+4096,0x110000)],allChars=True)
for old,start in itertools.product([False,True],range(0,0x110000,4096)):add('unsmartify',[old,list(range(start,min(start+4096,0x110000)))],allChars=True)
for path,doc in docs:add('document',doc,upstreamDocument=True,path=path)
for op,v in inputs:add(op,v,upstreamNode=True)
def tag(t,c=None):return {'t':t,**({'c':c} if c is not None else {})}
def string(s):return tag('Str',s)
A=['identifier',['class'],[['x','1'],['x','2']]]
display=tag('Math',[tag('DisplayMath'),'x+y']);inlineMath=tag('Math',[tag('InlineMath'),'x'])
items=[display,inlineMath,tag('Span',[A,[display]]),tag('Span',[A,[tag('Span',[A,[display]])]]),
 tag('Span',[A,[display,display]]),string(''),string('text'),tag('Space'),tag('SoftBreak'),tag('LineBreak')]
for length in range(5):
 for xs in itertools.product(items,repeat=length):
  add('inlines',list(xs),mathWhitespace=True)
  for t in ['Plain','Para']:add('block',tag(t,list(xs)),mathWhitespace=True)
supported='0123456789+-−=() \t\r\n\v\f\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000'
for s in [supported,'','1a2','1🙂2','١٢٣','²³₁₂','\u0085','\u2028','\u2029','\u200b','\ufeff']:
 add('inline',string(s),scriptBoundary=True)
 for n in range(5):
  node=string(s)
  for i in range(n):node=tag('Span',[A,[tag('Space'),node,tag('LineBreak')]])
  add('inline',node,scriptBoundary=True)
for t,mark,rest,tail in itertools.product(['Plain','Para','Header'],['☐','☒','☑','☐x','☒ '],
 [[],[tag('Space')],[tag('SoftBreak')],[tag('LineBreak')],[tag('Space'),string('body')],[string('body')]],
 [[],[tag('Plain',[string('tail')])]]):
 xs=[string(mark)]+rest
 b=tag(t,[1,A,xs] if t=='Header' else xs)
 add('blocks',[b]+tail,taskMarker=True)
for xs in [[],[tag('Space')],[string('☐')]]:add('inlines',xs,empty=True)
add('blocks',[tag('Para',[])],empty=True)
font=[True,True,3,True,4,6,'font-link']
baseDocs=[['text',9,'x'],['block',4,[[[None,font,'ab']],[]]],['vfill',2,'!?'],['cooked',3,[[None,font,'cd']]],
 ['prefix','  ',['hsep',['x','y']]],['before','x'],['flush','x'],['space'],['after','z'],['cr'],['newline'],
 ['blanklines',2],['concat','a','b'],['styled',['fg',3],'red'],['link','target','link'],['empty']]
def cat(xs,left=False):
 if not xs:return ['empty']
 if left:
  d=xs[0]
  for x in xs[1:]:d=['concat',d,x]
 else:
  d=xs[-1]
  for x in reversed(xs[:-1]):d=['concat',x,d]
 return d
for d,pre,post,left in itertools.product(baseDocs,[[],[['space']], [['newline'],['cr']]], [[],[['space']], [['blanklines',2],['cr']]], [False,True]):
 add('layout',['[',']',cat(pre+[d]+post,left)],docConstructor=True)
for ending,next_,left in itertools.product(['','A.','a.','Z.)','z.)','AB.','漢.','𐐀.','ǅ.','word!','word?','x.)','x.]','x."','x.”','x.)"','...','!',')'],
 [[['space']],[['after','fallback'],['space']],[['space'],['after','fallback']],[['newline']]], [False,True]):
 add('layout',[['text',2,'('],['text',3,')'],cat([['text',17,ending]]+next_+['tail'],left)],sentence=True)
for width in [-(2**63),-1,0,1,2**63-1]:add('layout-structure',['[',']',['concat',['text',width,'end.'],['space']]],cachedWidth=True)
rng=random.Random(0x511A2)
for i in range(700):
 xs=[rng.choice(items) for _ in range(rng.randrange(0,12))]
 add('inlines',xs,seeded=True);add('block',tag(rng.choice(['Plain','Para']),xs),seeded=True)
 ds=[rng.choice(baseDocs+['end.','A.','?','x.)']) for _ in range(rng.randrange(0,12))]
 add('layout',[rng.choice(baseDocs),rng.choice(baseDocs),cat(ds,bool(rng.randrange(2)))],seeded=True)
for depth,bad in itertools.product([0,1,32,256,1000,5000],[False,True]):add('deep-inline',[depth,bad],depth=True)
for depth,success in itertools.product([0,1,32,256,1000,5000],[False,True]):add('deep-ends',[depth,success],depth=True)
for depth,left in itertools.product([0,1,32,256,1000,10000],[False,True]):add('deep-layout',[depth,left],depth=True)
for kind in ['script-stop','plain-last','task-zero','task-success','unsmartify-no-extension','display-direct','display-span','math-all']:add('partial',kind,partial=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),
 upstreamOccurrences=dict(occurrences),oracle='GHC 9.12.2 unchanged Pandoc 3.12.1 pure Writers.Shared, pandoc-types 1.23.1.2 and DocLayout 0.6')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} pure writer observations; SHA {meta["caseHash"]}',flush=True)
