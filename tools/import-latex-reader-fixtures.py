#!/usr/bin/env python3
"""Capture complete original readLaTeX results and untouched unit expectations."""
import argparse,collections,gzip,hashlib,json,re,subprocess,itertools,random,os,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-reader-reference';target=root/'core/src/test/resources/latex-reader/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--prepared',type=Path);p.add_argument('--time-archive',type=Path);args=p.parse_args()
src=(root/'upstream/pandoc/test/Tests/Readers/LaTeX.hs').read_text()
expected=src.replace('module Tests.Readers.LaTeX (tests) where','module OriginalReaderTests (tests) where').replace('import Test.Tasty\n','').replace('import Test.Tasty.HUnit (HasCallStack)\n','import GHC.Stack (HasCallStack)\n').replace('import Tests.Helpers','import ReaderCases')
assert (folder/'OriginalReaderTests.hs').read_text()==expected
with gzip.open(root/'core/src/test/resources/latex-table/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/latex-table/data.jsonl.gz','tools/import-latex-reader-fixtures.py','tools/import-text-212-case.py','tools/check-latex-cli.py','upstream/pandoc/test/fb2/test-small.png','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX.hs','upstream/pandoc/src/Text/Pandoc/Parsing/General.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/test/Tests/Old.hs','upstream/pandoc/test/latex-reader.latex','upstream/pandoc/test/latex-reader.native','vendor/text-2.1.2/src/Data/Text/Internal/Fusion/Common.hs','vendor/text-2.1.2/src/Data/Text/Internal/Fusion/CaseMapping.hs']
sources+=sorted(str(x.relative_to(root)) for x in folder.rglob('*') if x.is_file())
sources+=sorted(str(x.relative_to(root)) for x in (root/'vendor/time-1.14').rglob('*') if x.is_file())
sources+=['licenses/Time-BSD-2-Clause.txt','upstream/pandoc/test/lhs-test.latex+lhs','upstream/pandoc/test/lhs-test.native','upstream/pandoc/src/Text/Pandoc/App/Input.hs','upstream/pandoc/src/Text/Pandoc/Sources.hs']
command_files=[]
for f in sorted((root/'upstream/pandoc/test/command').glob('*.md')):
 if any(re.search(r'(?:-f\s*|-r\s*|--from(?:=|\s+))latex\b',m[1]) for m in re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',f.read_text(),re.M|re.S)):command_files.append(f)
sources+=[str(f.relative_to(root)) for f in command_files]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def links():return {s:os.readlink(root/s) for s in sorted(set(sources)) if (root/s).is_symlink()}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['sourceLinks']==links() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'complete original LaTeX reader observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared and args.time_archive
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
exposed=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX','Text.Pandoc.Class.CommonState']
for name in exposed:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+n+',\n' for n in exposed))
files=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0')[:-1]
for name in files:assert (args.prepared/'pandoc'/name).read_bytes()==(changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()),name
assert hashlib.sha256(args.time_archive.read_bytes()).hexdigest()=='dfa0fe7cc9728e20d123a067161fa23ee27a5b180d58fa09d7738cb8ad737f3e'
with tarfile.open(args.time_archive) as tar:
 for path in (root/'vendor/time-1.14').rglob('*'):
  if path.is_file():assert path.read_bytes()==tar.extractfile('time-1.14/'+str(path.relative_to(root/'vendor/time-1.14'))).read(),path
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def ask(v):
 proc.stdin.write(json.dumps(v,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,v
 out=json.loads(line);assert not isinstance(out,dict) or 'exception' not in out,(v,out)
 return out
opts=ask(['defaults']);tests=ask(['tests']);assert len(tests)==90
cs=[]
for name,text,golden,doc in tests:
 cs.append(dict(group='all-original-unit-tests',name=name,request=['read',opts,[[[[],1,1],text]],[],[]],expected={'document':doc},nativeGolden=golden))
def read(text,group,options=None,files=None,env=None):
 req=['read',options or opts,[[[[],1,1],list(map(ord,text))]],files or [],env or []]
 cs.append(dict(group=group,request=req,expected=ask(req)))
for text in ['', 'word','a\n','a\n\nb','a # b & c ^ d _ e','{}','{a}','{a {b} c}',r'\emph{A}',r'\texttt{a--b}',r'\footnote{a}\footnotemark\footnotetext{b}',r'\section{A}\label{x}\ref{x}',r'\chapter{A}\section{B}',r'\part{A}\chapter{B}',r'\unknown{x}',r'\unknown{x}\n\nY','\\begin{unknown}X\\end{unknown}',r'\documentclass{article}\title{Title}\author{A\and B}\begin{document}Hello\end{document}ignored']:
 for extensions in [opts['extensions'],[],['raw_tex'],['smart'],['auto_identifiers','ascii_identifiers'],['latex_macros','smart']]:
  o=opts.copy();o['extensions']=extensions;read(text,'extension-and-full-document-boundaries',o)
for env in ['document','abstract','sloppypar','letter','minipage','figure','figure*','subfigure','center','quote','quotation','verse','itemize','description','enumerate','alltt','code','comment','verbatim','Verbatim','BVerbatim','lstlisting','minted','obeylines','tikzpicture','tikzcd','lilypond','ly','proof','CSLReferences','otherlanguage','french','unknown']:
 for body in ['','Text','a\n\nb',r'\item First\item Second',r'\item[Term] Definition']:
  prefix={'minipage':'{3cm}','subfigure':'{3cm}','minted':'{python}','CSLReferences':'{1}{2}','otherlanguage':'{french}'}.get(env,'')
  read('\\begin{'+env+'}'+prefix+body+'\\end{'+env+'}','every-environment-complete-reader')
for command in ['MakeUppercase','MakeLowercase','MakeTitlecase','MakeTitlecase[words=all]','textbf','emph','textsf','texttt','hbox','mbox','vbox','passthrough','lettrine','footnote','thanks','href{url}','hyperlink{x}','hyperref[x]','hypertarget{x}','textcolor{red}','colorbox{blue}']:
 for value in ['abc def','ß ﬃ ǳ ŉ','a \\emph{b} c',r'a $X$ \NoCaseChange{Keep} \cite{x}',r'\texttt{A\%B}']:
  read('\\'+command+'{'+value+'}','inline-command-and-case-transforms')
read((root/'upstream/pandoc/test/latex-reader.latex').read_text(),'original-complete-reader-golden-input')
for input_name,golden_name,extensions,standalone in [('latex-reader.latex','latex-reader.native',['raw_tex'],True),('lhs-test.latex+lhs','lhs-test.native',['literate_haskell'],False)]:
 o=opts.copy();o['extensions']=sorted(set(opts['extensions']+extensions))
 read((root/'upstream/pandoc/test'/input_name).read_text(),'original-fixed-reader-goldens',o)
 golden=(root/'upstream/pandoc/test'/golden_name).read_text()
 original=ask(['native',standalone,cs[-1]['expected']['document']])
 assert original+'\n'==golden or original==golden,(input_name,original[:200],golden[:200])
 cs[-1].update(nativeGolden=golden.removesuffix('\n'),nativeStandalone=standalone,source=input_name)
for f in command_files:
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',f.read_text(),re.M|re.S)):
  fmt=re.search(r'(?:-f\s*|-r\s*|--from(?:=|\s+))(latex(?:[+-][a-z_]+)*)',m[1])
  if not fmt:continue
  o=opts.copy();extensions=set(opts['extensions'])
  for sign,name in re.findall(r'([+-])([a-z_]+)',fmt[1][5:]):
   name='literate_haskell' if name=='lhs' else name
   if sign=='+':extensions.add(name)
   else:extensions.discard(name)
  o['extensions']=sorted(extensions)
  read(m[2],'all-original-command-inputs',o);cs[-1].update(source=str(f.relative_to(root)),index=index,command=m[1])
for kind,text,extensions in itertools.product(['inline','block','command'],['',r'\emph{a}{} tail',r'\input{missing} tail',r'\newcommand{\x}{A}\x tail',r'\begin{quote}a\end{quote} tail',r'\makeatletter\newcommand{\x}{A}\makeatother tail',r'\begin{math}x\end{math} tail',r'\unknown{a} tail'],[opts['extensions'],[],['raw_tex']]):
 o=opts.copy();o['extensions']=extensions;req=['bridge',kind,o,[[[[115,114,99],2,3],list(map(ord,text))]]]
 cs.append(dict(group='all-public-raw-sources-entry-points',request=req,expected=ask(req)))
for text in [r'\bibliography{,a,,b,}',r'\footnotemark[0]\footnotetext[0]{zero}',r'\DeclareUppercaseExclusions{word}\MakeUppercase{word other}',r'\DeclareTitlecaseExclusions{word}\MakeTitlecase[words=all]{word other}',r'\graphicspath{{a/}{b/}}\includegraphics{image}',r'\newtoggle{x}\iftoggle{x}{yes}{no}\toggletrue{x}\iftoggle{x}{yes}{no}',r'\title{T}\date{\today}\author{A\and B}',r'\section{A}\section{A}\section{B}\label{x}\section{C}\label{x}']:
 read(text,'metadata-label-toggle-case-and-path-regressions')
for file,text in [('x.tex','Included'),('x.sty',r'\newcommand{\hello}{Hello}'),('x.py','first\nsecond\nthird\n')]:
 for source in [r'\input{x}',r'\include{x}',r'\subfile{x}',r'\usepackage{x}\hello',r'\lstinputlisting[firstline=2,lastline=2]{x.py}',r'\inputminted{python}{x.py}']:
  for encoding in [text.encode(),b'\xef\xbb\xbf'+text.encode(),b'\xff',text.replace('\n','\r\n').encode()]:
   read(source,'file-effects-utf8-bom-cr-and-listings',files=[[list(map(ord,file)),list(encoding)]])
for text in [r'\begin{filecontents}{x.tex}included\end{filecontents}\input{x}',r'\input{x}\input{x}',r'\usepackage{x}\hello']:
 read(text,'texinputs-filecontents-and-include-order',files=[[list(map(ord,'dir/x.tex')),list(b'Included')],[list(map(ord,'dir/x.sty')),list(b'\\newcommand{\\hello}{Hello}')]],env=[[list(map(ord,'TEXINPUTS')),list(map(ord,'missing:dir:'))]])
for d,p,tz in itertools.product([-10**50,-678576,-1,0,40587,60000,10**50],[-10**40,-1,0,10**12*86399,10**12*86400,10**40],[-2**63,-1440,-1,0,1,1440,2**63-1]):
 req=['date',d,p,tz];cs.append(dict(group='unbounded-calendar-leap-seconds-and-int64-timezone',request=req,expected=ask(req)))
for start in [-10**40,-678576,0,10**40]:
 req=['date-range',start,146097];cs.append(dict(group='complete-gregorian-400-year-cycles',request=req,expected=ask(req)))
req=['case-data'];cs.append(dict(group='all-haskell-char-text-212-case-mappings',request=req,expected=ask(req)))
old_case=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/case-mappings.json').read_text())['lower']
new_case=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-case.json').read_text())['lower']
for cp in sorted(int(k,16) for k in set(old_case)|set(new_case) if old_case.get(k)!=new_case.get(k)):
 for extension in [[],['ascii_identifiers'],['gfm_auto_identifiers']]:
  o=opts.copy();o['extensions']=sorted(set(opts['extensions']+extension))
  read('\\section{'+chr(cp)+'}\\section{'+chr(cp)+'}','pinned-text-212-header-identifiers',o)
for n in ['1',' 1','1 ','(1)','(1 )','-1','9223372036854775808','0x10','bad']:
 read('\\footnotemark['+n+']\\footnotetext['+n+']{note}','exact-reads-footnote-numbers-and-trailing-spaces')
for epoch in ['0','-1',str(10**40),str(-10**40),'invalid']:
 read(r'\date{\today}\today','source-date-epoch-unbounded-calendar',env=[[list(map(ord,'SOURCE_DATE_EPOCH')),list(map(ord,epoch))]])
for n,text in itertools.product([0,1,2,3,4,8,-1,-2,-9223372036854775808],['','x','\t','a\tb','😀\tX\t','\r\t','\n\t','\t\n\n']):
 req=['tabs',n,list(map(ord,text))];cs.append(dict(group='exact-shared-tab-filter',request=req,expected=ask(req)))
if target.exists():
 with gzip.open(target,'rt') as f:next(f);old=[json.loads(line) for line in f]
 assert cs[:len(old)]==old,'Previously captured original observations changed'
proc.stdin.close();proc.wait();assert proc.returncode==0
manifest={'upstream':'Pandoc 3.12.1','sources':hashes(),'sourceLinks':links(),'cases':len(cs),'caseHash':digest(cs),'groups':dict(collections.Counter(c['group'] for c in cs)),'originalUnitCount':90}
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw:
 with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0) as gz:
  for c in [manifest,*cs]:gz.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'complete original LaTeX reader observations',manifest['caseHash'],flush=True)
