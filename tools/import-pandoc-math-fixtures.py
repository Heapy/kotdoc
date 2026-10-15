#!/usr/bin/env python3
"""Capture original complete Writers.Math with actual report/state effects."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-pandoc-math-reference';target=root/'core/src/test/resources/pandoc-math/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_PANDOC_MATH_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
projections=['Text/Pandoc/Class/PandocMonad.hs','Text/Pandoc/Shared.hs','Citeproc.hs']
for name in projections:assert (folder/name).read_bytes()==(root/'tools/haskell-common-state-reference'/name).read_bytes()
options=(root/'upstream/pandoc/src/Text/Pandoc/Options.hs').read_text();parts=[]
for name in ['defaultMathJaxURL','defaultKaTeXURL']:
 at=options.index(name+' :: Text');end=options.index('\n',options.index(name+' =',at));parts.append(options[at:end]+'\n')
assert (folder/'Text/Pandoc/Options.hs').read_text()=='{-# LANGUAGE OverloadedStrings #-}\nmodule Text.Pandoc.Options (defaultMathJaxURL, defaultKaTeXURL) where\nimport Data.Text (Text)\n'+'\n'.join(parts)
sources=['vendor/unicode-collation-0.1.3.7/src/Text/Collate/Lang.hs','tools/import-pandoc-math-fixtures.py','tools/import-common-state-fixtures.py','core/src/test/resources/common-state/data.jsonl.gz','core/src/test/resources/native/upstream.json','core/src/test/resources/math-reader/data.jsonl.gz','licenses/pandoc-GPL-2.0.md','licenses/Texmath-GPL-2.0.txt']
sources+=['tools/haskell-common-state-reference/'+n for n in projections]
sources+=['upstream/pandoc/src/'+n for n in ['Text/Pandoc/Writers/Math.hs','Text/Pandoc/Options.hs','Text/Pandoc/Class/CommonState.hs','Text/Pandoc/Class/PandocMonad.hs','Text/Pandoc/Logging.hs','Text/Pandoc/MediaBag.hs','Text/Pandoc/MIME.hs','Text/Pandoc/URI.hs','Text/Pandoc/UTF8.hs','Text/Pandoc/Shared.hs','Text/Pandoc/Error.hs','Text/Pandoc/Translations/Types.hs']]
sources+=sorted(str(p.relative_to(root)) for base in [folder,root/'vendor/texmath-0.13.3.1'] for p in base.rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
cp=lambda s:list(map(ord,s)) if isinstance(s,str) else s
def original_math():
 out=[]
 def visit(v,path,src):
  if isinstance(v,dict):
   if v.get('t')=='Math':out.append((src,path,v['c'][0]['t'],v['c'][1]))
   for k,x in v.items():visit(x,path+'/'+k,src)
  elif isinstance(v,list):
   for i,x in enumerate(v):visit(x,path+'/'+str(i),src)
 for f in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()):
  if f['accepted']:visit(f['json'],'','upstream/pandoc/'+f['path'])
 return out
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 original=[(c['source'],c['astPath'],c['request'][2],c['request'][3]) for c in cs if c['group']=='original-pandoc-math-nodes']
 assert original==[(s,p,t,cp(v)) for s,p,t,v in original_math()] and len(original)==335
 with gzip.open(root/'core/src/test/resources/math-reader/data.jsonl.gz','rt') as f:next(f);reader=[json.loads(l) for l in f]
 actual={(c['source'],c['request'][1],c['request'][2],tuple(c['request'][3])) for c in cs if c['group']=='original-tex-inputs'}
 expected={(c['source'],mode,t,tuple(c['request'][1])) for c in reader if c['group']=='original-tex-input' for mode,t in itertools.product(['inline','convert'],['InlineMath','DisplayMath'])}
 assert actual==expected and len(actual)==596
 print('Verified',len(cs),'original math bridge observations, all 335 Pandoc math nodes, 149 TeX inputs and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req;return json.loads(line)
def add(req,group,**extra):
 c=dict(request=req,expected=ask(req),group=group,**extra);cs.append(c)
 if len(cs)%1000==0:print('Captured',len(cs),'math bridge observations',flush=True)
 return c
def run(text,type='InlineMath',which='inline',verbosity=1,callback=0,seed=False,demand=True,kind='probe',group='bridge',**extra):return add(['run',which,type,cp(text),verbosity,callback,seed,demand,kind],group,**extra)
add(['urls'],'reexported-url-defaults')
for src,path,type,text in original_math():run(text,type=type,group='original-pandoc-math-nodes',source=src,astPath=path)
with gzip.open(root/'core/src/test/resources/math-reader/data.jsonl.gz','rt') as f:next(f);reader=[json.loads(l) for l in f]
for c in reader:
 if c['group']!='original-tex-input':continue
 for mode,t in itertools.product(['inline','convert'],['InlineMath','DisplayMath']):run(c['request'][1],t,which=mode,group='original-tex-inputs',source=c['source'])
# Every distinct previously captured explicit reader input in both public paths.
unique=list(dict.fromkeys(tuple(c['request'][1]) for c in reader if c['request'][0]=='read'))
for text,(mode,t) in itertools.product(unique,itertools.product(['inline','convert'],['InlineMath','DisplayMath'])):run(list(text),t,which=mode,group='complete-reader-input-integration')
# Parse failures, unsupported successful trees and supported/empty results are distinct.
texts=['','x+y',r'\frac{x}{y}',r'\sqrt{x}',r'\begin{matrix}a&b\end{matrix}',r'\bad',r'\frac{x}',r'\text{a}',r'\hat{x}',r'\underbrace{x}',r'\sum_i x',r'\alpha+\beta','\n\r\tx\x00',[0xd800,0xdc00,0x10000],r'\newcommand{\x}{y}\x']
for text,type,verbosity,callback,seed in itertools.product(texts,['InlineMath','DisplayMath'],range(3),range(5),[False,True]):
 run(text,type,verbosity=verbosity,callback=callback,seed=seed,group='state-and-report-callbacks')
for text,type,kind,demand,verbosity,callback in itertools.product(texts,['InlineMath','DisplayMath'],['probe','pandoc','constant','nothing','throw'],[False,True],[0,1],range(5)):
 run(text,type,which='convert',kind=kind,demand=demand,verbosity=verbosity,callback=callback,seed=True,group='generic-result-demand-and-sharing')
# Realistic error caret lines, line endings, Unicode, fallback delimiters and literals.
for text,type in itertools.product(['$','$$','a$b','\\','%tail','x\r\n\\bad','\n'*25+'x'*80+'\\bad',r'\text{漢🙂}',r'\frac{1}{2}',r'\displaystyle\sum_{i=0}^{n}x_i'],['InlineMath','DisplayMath']):
 run(text,type,group='raw-fallback-and-errors')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalPandocMathNodes=335,originalTeXInputs=149,distinctReaderInputs=len(unique),oracle='Complete unchanged Writers.Math/readTeX/writePandoc and exact original report; native state instance and demand observation only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'math bridge observations; SHA',m['caseHash'],flush=True)
