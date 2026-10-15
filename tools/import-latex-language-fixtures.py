#!/usr/bin/env python3
"""Observe the complete original LaTeX Lang module, including common state."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,re,subprocess,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-language-reference';previous=root/'tools/haskell-latex-citation-math-reference';target=root/'core/src/test/resources/latex-language/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_LATEX_LANGUAGE_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--parsec-archive',type=Path);args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Lang.hs').read_text()
assert (folder/'OriginalLang.hs').read_text()==src.replace('module Text.Pandoc.Readers.LaTeX.Lang','module OriginalLang',1).replace('import Text.Pandoc.Readers.LaTeX.Parsing','import OriginalParsing',1)
assert (folder/'CitationMathObserver.hs').read_text()==(previous/'LaTeXCitationMathReference.hs').read_text().replace('module Main where','module CitationMathObserver where',1)
sources=['tools/import-latex-language-fixtures.py','core/src/test/resources/native/upstream.json','licenses/pandoc-GPL-2.0.md','upstream/pandoc/test/Tests/Readers/LaTeX.hs','upstream/pandoc/pandoc.cabal']
sources+=['upstream/pandoc/src/Text/Pandoc/'+s+'.hs' for s in ['Readers/LaTeX/Lang','Readers/LaTeX/Parsing','Readers/LaTeX/Macro','Shared','Translations','Translations/Types','Class/CommonState']]
sources+=['licenses/Parsec-BSD-2-Clause.txt']+sorted(str(f.relative_to(root)) for f in (root/'vendor/parsec-3.1.18.0').rglob('*') if f.is_file())
sources+=sorted(str(f.relative_to(root)) for d in [folder,previous] for f in d.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
pattern=r'\\(?:enquote\*?|foreignquote\*?|hyphenquote\*?|foreignlanguage|setdefaultlanguage|text(?:french|german|latin|english))\b|(?:babel|polyglossia)'
fixtures=[]
for name in subprocess.check_output(['git','ls-files','test/command/*.md'],cwd=root/'upstream/pandoc').decode().splitlines():
 if re.search(pattern,(root/'upstream/pandoc'/name).read_text()):fixtures.append(name)
sources+=['upstream/pandoc/'+s for s in fixtures]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original LaTeX language observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared and args.parsec_archive
provenance=(root/'vendor/parsec-3.1.18.0/KOTDOC-PROVENANCE').read_text()
assert hashlib.sha256(args.parsec_archive.read_bytes()).hexdigest()==re.search(r'SHA256: ([0-9a-f]{64})',provenance)[1]
with tarfile.open(args.parsec_archive) as tar:
 for path in (root/'vendor/parsec-3.1.18.0').rglob('*'):
  if path.is_file() and path.name!='KOTDOC-PROVENANCE':assert path.read_bytes()==tar.extractfile('parsec-3.1.18.0/'+str(path.relative_to(root/'vendor/parsec-3.1.18.0'))).read(),path
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
exposed=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX','Text.Pandoc.Class.CommonState']
for name in exposed:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+n+',\n' for n in exposed))
files=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0')[:-1]
for name in files:assert (args.prepared/'pandoc'/name).read_bytes()==(changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()),name
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 value=json.loads(line);assert not(isinstance(value,dict) and 'exception' in value),(req,value);return value
def add(req,group,**extra):cs.append(dict(request=req,expected=ask(req),group=group,**extra))
def raw(s):return list(map(ord,s))
def tokens(s):return ask(['tokens',[raw('source'),2,3],raw(s)])
def language(l,s=None,r=None,vs=None,es=None,ps=None):return [raw(l),None if s is None else raw(s),None if r is None else raw(r),list(map(raw,vs or [])),es or [],list(map(raw,ps or []))]
default=ask(['default-options'])
def initial():
 opts=default.copy();opts['extensions']=['latex_macros'];return [['options',opts]]
loaded=[language('en'),{'Figure':' cache ','Proof':'Prove'}]
def parse(s,p,group,changes=None,cache=loaded,**extra):add(['parse',initial() if changes is None else changes,cache,tokens(s) if isinstance(s,str) else s,p],group,**extra)
def il(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
def strx(s):return il('Str',s)
def pure(xs,changes=None):return ['pure',xs,changes or []]
registries=ask(['registries']);add(['registries'],'all-language-and-quote-command-names')
options=['','spelling=old','variant=austrian,spelling=old','variant=austrian','variant=swiss,spelling=old','variant=swiss','variant=poly','variant=ancient','variant=classic']+['variant='+s for s in ['australian','canadian','british','newzealand','american']]+['locale='+s for s in ['algeria','mashriq','libya','morocco','mauritania','tunisia']]+['variant=moon','spelling=old,variant=swiss','variant=swiss, spelling=old',' v a r i a n t = b r i t i s h ','VARIANT=BRITISH','variant=swiss\t','variant=swiss\u00a0','variant=[[swiss]]','locale=morocco,other=x','[variant=swiss]','variant=swiss%ignored\n']
for name,o in itertools.product(registries[0]+['unknown','English',' en',''],options):add(['poly',raw(name),raw(o)],'all-polyglossia-functions-and-exact-option-spelling')
aliases=['austrian','naustrian','swissgerman','nswissgerman','german','ngerman','lowersorbian','uppersorbian','polytonicgreek','polutonikogreek','slovene','australian','canadian','british','newzealand','american','classiclatin']
for name in registries[0]+aliases+['','unknown','😀','german ','German','en-US','\ud800']:
 for key in [name,name.upper(),' '+name]:add(['babel',raw(key)],'all-babel-aliases-exact-records-and-unknowns')
# Only ASCII spaces are removed from option strings; other whitespace is retained.
whitespace=[9,10,11,12,13,32,133,160,5760,*range(8192,8203),8232,8233,8239,8287,12288]
for cp in whitespace:
 for name,body in [('english','variant=british'),('german','variant=swiss'),('arabic','locale=morocco')]:
  for o in [chr(cp)+body,body+chr(cp),body.replace('=',chr(cp)+'='+chr(cp))]:add(['poly',raw(name),raw(o)],'ascii-space-filter-versus-unicode-whitespace')
for name,o in itertools.product(registries[0]+['unknown','English',''],options):parse(('['+o+']' if o else '')+'{'+name+'}tail',['raw',['default']],'every-default-language-options-metadata-and-loaded-cache')
for command in registries[2]:
 for o in ['','variant=swiss','variant=[[swiss]]','variant=moon','locale=morocco']:
  head='{ngerman}' if command=='foreignlanguage' else ('['+o+']' if o else '')
  for xs in [[],[strx('x')],[il('Space'),strx('x'),il('Space')],[il('SoftBreak'),strx('x'),il('SoftBreak')],[il('Space')],[il('SoftBreak')],[il('LineBreak')]]:
   parse(head,['language',raw(command),pure(xs)],'every-inline-language-command-and-space-extraction')
for command,ctx,arg in itertools.product(registries[1],range(3),['{x}','[one][two]{x}','<2->[one]{x}','{outer \\enquote{inner}}','{}','{ x }','[broken','{broken','\\x']):
 prefix='{italian}' if command.startswith(('foreign','hyphen')) else ''
 for cb in [['tok'],['quote']]:parse(prefix+arg,['enquote',raw(command),cb],'all-quote-commands-context-options-nesting-and-errors',initial()+[['quote',ctx]])
for name in ['unknown','German','ngerman','polytonicgreek','german','greek','pinyin','classiclatin']:
 for command in ['foreignlanguage','foreignquote','hyphenquote*']:
  for xs in [[],[il('Space'),strx('x'),il('Space')]]:
   parse('{'+name+'}',[('language' if command=='foreignlanguage' else 'enquote'),raw(command),pure(xs)],'babel-versus-polyglossia-language-spans-and-fallback')
# Common effects must survive parser try/lookAhead, while parser metadata rolls back.
for cache in [None,[language('en'),None],loaded,[language('en',vs=['x']),{'Figure':'other'}],[language('en',s='Latn'),{}],[language('en',r='US'),{}],[language('en',es=[[raw('u'),[[raw('co'),raw('phonebk')]]]]),{}],[language('en',ps=['private']),{}]]:
 for source in ['{english}','[variant=british]{english}','{unknown}','{german}']:
  for expr in [['default'],['look',['default']],['or',['try',['then',['default'],['fail']]],['state']],['pair',['default'],['state']]]:parse(source,expr,'translation-cache-equality-and-non-backtracked-common-effects',cache=cache)
for cb in [['fail',0,[['report',raw('callback failure')]]],['fail',1,[['quote',1]]],pure([strx('x')],[['quote',2],['last-label','callback']]),['chunk',[il('Space'),strx('body'),il('Space')],[['report',raw('callback report')]]]]:
 for command in ['enquote','enquote*','foreignquote','textenglish','foreignlanguage']:
  head='{german}' if command in ['foreignquote','foreignlanguage'] else ''
  expr=['enquote' if command in registries[1] else 'language',raw(command),cb]
  for wrap in [lambda p:p,lambda p:['or',['try',p],['state']],lambda p:['raw',p]]:parse(head+'{body}',wrap(expr),'stateful-and-failing-callbacks-quote-restoration-and-reports')
for source in ['[bad','[a[b]c]{english}','[variant=swiss][again]{german}','{}','\\def\\l{english}{\\l}','{english}\\setdefaultlanguage{german}','\\setdefaultlanguage{english}\\enquote{\\setdefaultlanguage{german}x}']:
 for expr in [['default'],['rich'],['or',['try',['default']],['state']]]:parse(source,expr,'default-language-malformed-arguments-and-macro-expansion')
inputs=ask(['inputs'])
for name,text in inputs:parse(''.join(map(chr,text)),['rich'],'all-original-reader-inputs-through-explicit-callback',source=name)
for file in fixtures:
 text=(root/'upstream/pandoc'/file).read_text()
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',text,re.M|re.S)):
  if re.search(pattern,m[2]):parse(m[2],['rich'],'original-command-inputs-through-explicit-callback',source=file,index=index)
# Preserve every original complex inline tree through the language/quote callbacks.
inline_lists=[]
def visit(v):
 if isinstance(v,list):
  if v and all(isinstance(x,dict) and x.get('t') in ['Str','Emph','Strong','Cite','Space','SoftBreak','Span','Note','Math','Link','Image','Code','Quoted','Underline','Superscript','Subscript','SmallCaps','Strikeout','LineBreak','RawInline'] for x in v):inline_lists.append(v)
  for x in v:visit(x)
 elif isinstance(v,dict):
  for x in v.values():visit(x)
docs=[x['json'] for x in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if x['accepted']]
for doc in docs:visit(doc)
for i,xs in enumerate(inline_lists):
 parse('[variant=swiss]',['language',raw('textgerman'),pure(xs)],'all-original-inline-lists-language-space-boundaries',source=i)
 parse('{polytonicgreek}',['enquote',raw('foreignquote'),pure(xs)],'all-original-inline-lists-quote-wrapping',initial()+[['quote',i%3]],source=i)
for kind,n in itertools.product(range(2),[1500,10000,100000]):add(['stress',kind,n],'deep-finite-quote-and-language-callback-composition')
for kind,n in itertools.product(range(2),[0,1,1000,100000]):add(['error-merge',kind,n],'persistent-parsec-diagnostic-order-and-complete-messages')
for n in [31,100000]:add(['error-shared',n],'shared-diagnostic-list-shape-without-forcing-messages')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),registries=registries,originalReaderInputs=len(inputs),originalDocuments=len(docs),originalInlineLists=len(inline_lists),originalCommandFiles=len(fixtures),verifiedOriginalBuildFiles=len(files),exposedModules=exposed)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for c in [m,*cs]:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original LaTeX language observations',m['caseHash'],flush=True)
