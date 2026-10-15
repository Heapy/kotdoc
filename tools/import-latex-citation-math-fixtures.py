#!/usr/bin/env python3
"""Capture complete original Citation/Math modules with explicit parser callbacks."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-citation-math-reference';target=root/'core/src/test/resources/latex-citation-math/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_LATEX_CITATION_MATH_REFERENCE'));p.add_argument('--prepared',type=Path);args=p.parse_args()
for name,extra,marker in [('Citation','addPrefix, addSuffix, handleCitationPart, inNote','  ( citationCommands'),('Math','extractLabelFromBlock, addTitle, addQed, italicize','  ( withMathMode')]:
 src=(root/f'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/{name}.hs').read_text()
 assert (folder/f'Original{name}.hs').read_text()==src.replace(f'module Text.Pandoc.Readers.LaTeX.{name}',f'module Original{name}',1).replace('import Text.Pandoc.Readers.LaTeX.Parsing','import OriginalParsing',1).replace(marker,marker+', '+extra,1)
for name in ['OriginalParsing.hs','OriginalMacro.hs','KernelObserver.hs']:assert (folder/name).read_bytes()==(root/'tools/haskell-latex-macro-reference'/name).read_bytes()
for name in ['OriginalLaTeXInputs.hs','TokenInputs.hs']:assert (folder/name).read_bytes()==(root/'tools/haskell-latex-parsing-reference'/name).read_bytes()
assert (folder/'StateObserver.hs').read_text()==(root/'tools/haskell-latex-parsing-reference/LaTeXParsingReference.hs').read_text().replace('module Main where','module StateObserver where',1)
sources=['tools/import-latex-citation-math-fixtures.py','core/src/test/resources/native/upstream.json','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Citation.hs','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Math.hs','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Macro.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/test/Tests/Readers/LaTeX.hs','upstream/pandoc/pandoc.cabal','licenses/pandoc-GPL-2.0.md','tools/haskell-latex-parsing-reference/LaTeXParsingReference.hs','tools/haskell-latex-parsing-reference/OriginalLaTeXInputs.hs','tools/haskell-latex-parsing-reference/TokenInputs.hs']
sources+=['tools/haskell-latex-macro-reference/'+n for n in ['OriginalParsing.hs','OriginalMacro.hs','KernelObserver.hs']]
fixtures=[]
for name in subprocess.check_output(['git','ls-files','test/command/*.md'],cwd=root/'upstream/pandoc').decode().splitlines():
 text=(root/'upstream/pandoc'/name).read_text()
 if re.search(r'\\(?:[Cc]ite\w*|[Aa]utocite\w*|[Tt]extcite\w*|newtheorem|theoremstyle)|\\begin\{(?:proof|theorem|thm|align\*?|equation\*?)\}',text):fixtures.append(name)
sources+=['upstream/pandoc/'+f for f in fixtures]
sources+=sorted(str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original citation/math observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
exposed=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX']
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
pos=[raw('source'),2,3]
def tokens(s):return ask(['tokens',pos,raw(s)])
default=ask(['default-options'])
def initial():
 opts=default.copy();opts['extensions']=['latex_macros'];return [['options',opts]]
def parse(s,p,group,changes=None,**extra):add(['parse',initial() if changes is None else changes,tokens(s) if isinstance(s,str) else s,p],group,**extra)
def il(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
def strx(s):return il('Str',s)
def purei(xs,changes=None):return ['pure',xs,changes or []]
def pureb(xs,changes=None):return ['pure',xs,changes or []]
def th(name='Theorem',style=0,series=None,sync=None,number=True,num=None):return [tokens(name),style,series,sync,number,num if num is not None else [0]]
registries=ask(['registries']);add(['registries'],'all-citation-commands-and-math-environments')
atom=['atom'];sequence=['sequence'];opt=['bracket',atom];fail=['fail',0,[]]
samples=['{}','{a}','{a,b,c}','[suffix]{a,b}','[pre][suffix]{a,b}','(pre)(post)[x][y]{a}[z]{b}','[\\def\\x{Q}]{a}\\x','[\\emph{see}][p. \\textbf{32}]{a}','{. : ; ? ! ` \' ( ) / * @ _ + = - & [ ]}','{a b}','{a,}','{,a}','{a#b}','{é,𐐀,𝟡}','[a{b]c}]{x}','[bad','{bad','not-group']
for name,s in itertools.product(registries[0],samples):
 for wrap in [lambda p:p,lambda p:['raw',p]]:parse(s,wrap(['citation',raw(name),atom]),'every-citation-command-prefix-suffix-modes-and-errors')
for m,multi,s in itertools.product(range(3),[False,True],samples+['(p){a}{b}','(p)(s){}[x]{b}{}','(p)(s)[pre]{a}[post]{b}','( )(s){a}{b}']):parse(s,['cites',m,multi,atom],'citation-mode-and-multicite-note-placement')
inputs=ask(['inputs'])
for name,text in inputs:parse(''.join(map(chr,text)),['rich'],'all-original-reader-inputs-through-explicit-callback',source=name)
for file in fixtures:
 text=(root/'upstream/pandoc'/file).read_text()
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',text,re.M|re.S)):
  if re.search(r'\\(?:[Cc]ite\w*|[Aa]utocite\w*|[Tt]extcite\w*|newtheorem|theoremstyle)|\\begin\{(?:proof|theorem|thm|align\*?|equation\*?)\}',m[2]):parse(m[2],['rich'],'original-command-inputs-through-explicit-callback',source=file,index=index)
for s in ['\\citetext{pre \\cite{a} post; \\citeyear{b}}','\\citeauthor{a} \\citetext{\\citeyear{a}; \\citealp{b}}','\\citetext{}','\\citetext{text}','\\citetext{\\cite{a};;\\cite{b}}','\\nocite{a}\\nocite{b}','\\cite[\\def\\x{q}]{a}\\x']:
 for changes in [initial(),initial()+[['raw',tokens('old'),1,2]],initial()+[['enable-raw',False]],initial()+[['meta','nocite',il('MetaString','old')]]]:
  for p in [['rich'],['raw',['rich']],['or',['try',['then',['rich'],['fail']]],['state']]]:parse(s,p,'complex-citations-optional-argument-state-and-nocite-metadata',changes)
for cb in [['chunk',[il('Emph',[strx('custom')])],[['meta','k',il('MetaString','value')]]],['chunk',[],[['report',raw('inline callback')]]],['fail',0,[['report',raw('inline failure')]]],['fail',1,[['log','local']]]]:
 for s in ['[pre]{a,b}','[pre][post]{a}','(pre){a}{b}','{a}']:
  for multi in [False,True]:parse(s,['or',['try',['cites',0,multi,cb]],['state']],'callback-result-monoids-state-errors-and-global-reports')
for s in ['', '$','$x$','$$','$$$','$$$$','$$x$$','$x$tail','$x\\$y$','$x{a$b}c$','$x}y$','$x{y$','$ x \\ $','\n$x$']:
 for p in [['dollars'],['raw',['dollars']],['or',['try',['dollars']],['state']]]:parse(s,p,'dollar-math-delimiters-groups-escape-and-failure')
for name,body in itertools.product(registries[1],['',' x ','\n x\n ','x\\ ','x\u00a0','\\ifmmode yes\\else no\\fi']):
 for p,text in [(['env-entry',raw(name)],body+'\\end{'+name+'}tail'),(['math-env'],'\\begin{'+name+'}'+body+'\\end{'+name+'}tail')]:parse(text,['raw',p],'every-math-environment-wrapper-and-right-trimming')
for name in ['plain','definition','remark','unknown',' plain ']:parse('{'+name+'}', ['style'],'theorem-style-selection')
for prefix,series,sync in itertools.product(['','*'],['','[shared]','[]'],['','[section]','[]']):
 parse(prefix+'{thm}'+series+'{Theorem}'+sync+'tail',['newtheorem'],'newtheorem-numbering-series-sync-and-raw-name')
for s in ['{thm}[bad','{thm}X[section]','{thm}\\x','{thm}{}','* {thm}{Name}','{thm}[shared]{T}[bad']:
 parse(s,['raw',['newtheorem']],'newtheorem-single-token-name-and-malformed-arguments')
docs=[x['json'] for x in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if x['accepted']]
for i,doc in enumerate(docs):
 bs=doc['blocks'];title=[strx('Title'),il('Space')]
 add(['walk-italicize',bs],'all-original-documents-math-block-walk',source=i)
 add(['qed',bs],'all-original-documents-qed',source=i)
 add(['title',title,bs],'all-original-documents-title-concatenation',source=i)
 for block in bs:
  add(['extract-label',block],'all-original-top-level-block-labels',source=i)
  add(['italicize',block],'all-original-top-level-block-italicization',source=i)
def span(label,attrs=None):return il('Span',[['',[],attrs or [['label',label]]],[strx('hidden')]])
image=il('Image',[['',[],[]],[],['img','']])
bodies=[[],[il('Para',[])],[il('Plain',[strx('body')])],[il('Para',[strx('body')])],[il('Para',[image])],[il('Plain',[image])],[il('Para',[span('x'),il('Space'),strx('body')])],[il('Para',[il('Emph',[span('x')])])],[il('Para',[span('',[['label','']])])],[il('Para',[span('x',[['label','first'],['label','second']])])],[il('BlockQuote',[il('Para',[span('nested')])])]]
for style,number,body in itertools.product(range(3),[False,True],bodies):
 changes=initial()+[['theorem','thm',th(style=style,number=number)],['caption',[None,[il('Plain',[strx('old')])]]]]
 parse('\\end{thm}tail',['theorem',raw('thm'),pureb(body),sequence,fail],'theorem-title-style-label-detection-and-image-exception',changes)
 parse('\\end{proof}tail',['proof',pureb(body),purei([strx('Sketch')])],'proof-title-and-qed-block-shapes',changes)
for header,series,num in itertools.product([[],[0],[2],[2,3],[9223372036854775807]], [None,'shared','missing',''], [[0],[4],[2,4],[9223372036854775807]]):
 changes=initial()+[['theorem','thm',th(series=series,num=num)],['theorem','shared',th(num=num)],['chapters',True],['header',header]]
 parse('\\end{thm}',['theorem',raw('thm'),pureb([il('Para',[span('x')])]),sequence,fail],'theorem-series-chapter-counters-and-int64-overflow',changes)
for actions in [[['theorem-style',2]],[['theorem','thm',th('changed',2,num=[8])]], [['macro','a',1]],[['log','body']],[['report',raw('callback report')]]]:
 changes=initial()+[['theorem','thm',th('Old',0)],['caption',[None,[]]]]
 p=['theorem',raw('thm'),pureb([il('Para',[span('x'),strx('body')])],actions),['chunk',[strx('Title')],[['last-label','title-label']]],purei([strx('opt')])]
 parse('\\end{thm}',p,'theorem-callback-state-snapshots-and-name-parse-state',changes)
for p in [['with-math',['pair',['math-mode'],['then',['change',['math',False]],['math-mode']]]],['with-math',['fail']],['or',['try',['with-math',['then',['consume',1],['fail']]]],['state']]]:
 for old in [False,True]:parse('x',p,'math-mode-success-failure-and-backtracking-state',initial()+[['math',old]])
for name,b,o in itertools.product(['thm','missing'],[pureb([]),['fail',0,[['report',raw('body failure')]]],['fail',1,[['last-label','failed']]]],[fail,purei([]),['fail',1,[['report',raw('option failure')]]]]):
 p=['theorem',raw(name),b,sequence,o]
 parse('\\end{thm}',['or',['try',p],['state']],'theorem-callback-failure-and-caption-backtracking',initial()+[['theorem','thm',th()],['caption',[None,[]]]])
# Citation helpers observe complete inline/citation trees from the original corpus.
inline_lists=[];citations=[]
def visit(v):
 if isinstance(v,list):
  if v and all(isinstance(x,dict) and x.get('t') in ['Str','Emph','Strong','Cite','Space','SoftBreak','Span','Note','Math','Link','Image','Code','Quoted','Underline','Superscript','Subscript','SmallCaps','Strikeout','LineBreak','RawInline'] for x in v):inline_lists.append(v)
  for x in v:visit(x)
 elif isinstance(v,dict):
  if v.get('t')=='Cite':citations.append(v['c'][0])
  for x in v.values():visit(x)
for doc in docs:visit(doc)
for i,xs in enumerate(inline_lists):
 add(['part',xs],'all-original-inline-lists-citation-part',source=i)
 if i<300:add(['note',xs],'original-inline-note-concatenation',source=i)
for i,csx in enumerate(citations+[[],[dict(citationId='x',citationPrefix=[],citationSuffix=[],citationMode={'t':'NormalCitation'},citationNoteNum=-9223372036854775808,citationHash=9223372036854775807)]]):
 for part in [[],[strx('prefix'),il('Space')],[il('Emph',[strx('x')])]]:
  add(['prefix',part,csx],'original-citation-prefix-prepend',source=i);add(['suffix',part,csx],'original-citation-suffix-append',source=i)
for t in ['',' x ','\t\r\nx\\ \t','\u00a0x\u00a0','x\\\n ','😀']:
 add(['math-inline',raw(t)],'shared-math-trimming');add(['math-display',raw(t)],'shared-math-trimming')
for kind in ['part','note']:add([kind,[]],'empty-citation-helper-inputs')
add(['title',[],[]],'empty-title-and-block-inputs')
for kind,n in itertools.product(range(4),[1500,10000,100000]):add(['stress',kind,n],'deep-finite-math-citation-mode-and-theorem-walk')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),citationCommands=registries[0],mathEnvironments=registries[1],originalReaderInputs=len(inputs),originalDocuments=len(docs),originalInlineLists=len(inline_lists),originalCitationLists=len(citations),verifiedOriginalBuildFiles=len(files),exposedModules=exposed)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for c in [m,*cs]:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original citation/math observations',m['caseHash'],flush=True)
