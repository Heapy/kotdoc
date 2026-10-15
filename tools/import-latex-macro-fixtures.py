#!/usr/bin/env python3
"""Original Macro+Parsing definitions and integration; no Kotlin expectations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-macro-reference';target=root/'core/src/test/resources/latex-macros/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_LATEX_MACRO_REFERENCE'));p.add_argument('--prepared',type=Path);args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Macro.hs').read_text()
assert (folder/'OriginalMacro.hs').read_text()=='{-# LINE 1 "src/Text/Pandoc/Readers/LaTeX/Macro.hs" #-}\n'+src.replace('module Text.Pandoc.Readers.LaTeX.Macro','module OriginalMacro',1).replace('  ( macroDef','  ( macroDef, letterize, xparseArgSpecs',1).replace('import Text.Pandoc.Readers.LaTeX.Parsing','import OriginalParsing',1)
assert (folder/'OriginalParsing.hs').read_bytes()==(root/'tools/haskell-latex-kernel-reference/OriginalParsing.hs').read_bytes()
assert (folder/'KernelObserver.hs').read_text()==(root/'tools/haskell-latex-kernel-reference/LaTeXKernelReference.hs').read_text().replace('module Main where','module KernelObserver where',1)
commands=re.findall(r'"([A-Za-z]+)"',src[src.index('macroDefCommands = Set.fromList'):src.index('insertMacro ::')])
pattern=re.compile(r'\\(?:'+'|'.join(commands)+r')(?![a-zA-Z])')
fixtures=[];original=[]
tracked=subprocess.check_output(['git','ls-files','test/command/*.md'],cwd=root/'upstream/pandoc').decode().splitlines()
for name in tracked:
 text=(root/'upstream/pandoc'/name).read_text()
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',text,re.M|re.S)):
  if pattern.search(m[2]):original.append((name,index,m[1],m[2]));fixtures.append(name)
sources=['tools/import-latex-macro-fixtures.py','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Macro.hs','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs','upstream/pandoc/src/Text/Pandoc/TeX.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/test/Tests/Readers/LaTeX.hs','upstream/pandoc/pandoc.cabal','core/src/test/resources/latex-parsing/data.jsonl.gz','licenses/pandoc-GPL-2.0.md','tools/haskell-latex-kernel-reference/LaTeXKernelReference.hs','tools/haskell-latex-kernel-reference/OriginalParsing.hs']
sources+=['upstream/pandoc/'+f for f in sorted(set(fixtures))]
sources+=sorted(str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs)) and m['originalCommandInputs']==len(original)
 print('Verified',len(cs),'original macro observations and',len(m['sources']),'source hashes');raise SystemExit
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
def init(enabled=True):
 options=default.copy();options['extensions']=['latex_macros'] if enabled else [];return [['options',options]]
def parse(s,action,group,changes=None,**extra):add(['parse',changes if changes is not None else init(),tokens(s),action],group,**extra)
for name,index,options,text in original:
 for enabled in [False,True]:parse(text,['program'],'all-original-macro-command-inputs',init(enabled),source=name,index=index,command=options)
with gzip.open(root/'core/src/test/resources/latex-parsing/data.jsonl.gz','rt') as f:
 next(f);units=[c for line in f if (c:=json.loads(line))['group']=='all-original-reader-unit-inputs']
for c in units:
 text=''.join(map(chr,c['request'][2]))
 for enabled in [False,True]:parse(text,['program'],'all-original-reader-unit-inputs-through-definition-pipeline',init(enabled),source=c['source'])
simple={'global':'\\global\\def\\a{x}','let':'\\let\\a\\b','edef':'\\edef\\a{\\b}','xdef':'\\xdef\\a{\\b}','def':'\\def\\a#1{x#1}','gdef':'\\gdef\\a#1{x#1}','newif':'\\newif\\ifa'}
for command in commands:
 if command in simple:s=simple[command]
 elif command in ['newenvironment','renewenvironment','provideenvironment']:s='\\'+command+'{x}[1][D]{(#1}{)}'
 elif 'DocumentEnvironment' in command:s='\\'+command+'{x}{O{D}m}{#1/#2}{#2}'
 elif 'EnvironmentCopy' in command:s='\\'+command+'{x}{quote}'
 elif 'CommandCopy' in command:s='\\'+command+'\\a\\b'
 elif 'DocumentCommand' in command:s='\\'+command+'\\a{O{D}m}{#1/#2}'
 else:s='\\'+command+'{\\a}[1][D]{#1}'
 for enabled,defined,action in itertools.product([False,True],[False,True],[['definition'],['raw',['definition']],['program'],['or',['try',['then',['definition'],['fail']]],['pure','fallback']]]):
  prefix='\\def\\a{old}\\def\\x{old}' if defined else ''
  if prefix:parse(prefix+s+' tail',['then',['repeat',2,['definition']],action],'every-definition-command-existing-name-and-extension-policy',init(enabled))
  else:parse(s+' tail',action,'every-definition-command-existing-name-and-extension-policy',init(enabled))
for text in ['','x','\\unknown','\\newif\\foo','\\global x','\\newcommand','\\def','\\newcommand{x}{a}','\\newcommand{\\a','\\newcommand\\a[bad]{x}','\\newcommand\\a[-1]{x}','\\newcommand\\a[0x2]{#1/#2}','\\newcommand\\a[18446744073709551616]{x}','\\newcommand\\a[9223372036854775808]{x}','\\newcommand\\a[18446744073709551617]{#1}','\\newenvironment{x}{a}','\\NewDocumentCommand\\a{q}{x}','\\NewDocumentCommand\\a{b}{x}','\\NewDocumentEnvironment{x}{b m}{a}{b}']:
 for enabled,action in itertools.product([False,True],[['definition'],['raw',['definition']],['or',['try',['definition']],['pure','fallback']],['program']]):parse(text,action,'malformed-input-pattern-errors-numeric-read-and-int64-wrapping',init(enabled))
specs=['','m','mm','o m','O{#2}m','s','t+','d()','D(){D}','r<>','R<>{R}','v','e{^_}','E{^_}{{A}{B}}','e{}m','E{abc}{{A}{B}}m','+m','!o','!+O{D}','={short-text}+O{#3}+m','>{\\TrimSpaces}m','>{\\TrimSpaces}>{\\SplitList{,}}m','>{\\ReverseBoolean}s','>{\\TrimSpaces}e{_^}','b','!b','c','!c','>{\\TrimSpaces}b','>{\\TrimSpaces}c','b m','q','O','t','d(','E{^_}{x}','>','!','={x}',' m %x\n o ']
for spec,body in itertools.product(specs,[False,True]):
 parse('{'+spec+'}tail',['specs',body],'every-xparse-specifier-modifier-processor-and-body-position')
for spec in specs:
 for text in ['{arg}tail','[opt]{arg}tail','*{arg}tail','(a(b)c)tail','^{up}_{down}tail','|a_b|tail']:
  parse('\\NewDocumentCommand\\a{'+spec+'}{[#1](#2)}\\a'+text,['program'],'definition-plus-argument-grabbing-integration')
programs=['\\def\\a{old}{\\def\\a{new}\\a}\\a','\\def\\a{old}{\\gdef\\a{new}\\a}\\a','\\def\\a{old}{\\global\\def\\a{new}\\a}\\a','\\gdef\\a{old}{\\let\\b\\a}\\b','\\def\\b{old}\\let\\a\\b\\def\\b{new}\\a\\b','\\def\\b{old}\\edef\\a{\\b}\\def\\b{new}\\a\\b','\\def\\b{old}\\def\\a{\\b}\\def\\b{new}\\a','\\newif\\ifa\\ifa no\\else yes\\fi\\atrue\\ifa yes\\else no\\fi\\afalse\\ifa no\\else yes\\fi','\\newcommand\\noop[1]{#1}\\noop{\\newcommand\\a[1]{#1}}\\a{hi}','\\NewDocumentEnvironment{x}{m}{[#1}{#1]}\\begin{x}{a}one\\begin{x}{b}two\\end{x}three\\end{x}','\\NewDocumentEnvironment{x1😀}{b}{(#1)}{}\\begin{x1😀} body \\end{x1😀}','\\newenvironment{x}[1][D]{(#1}{)}\\begin{x}[a]b\\end{x}','\\NewEnvironmentCopy{x}{quote}\\begin{x}hi\\end{x}','\\NewDocumentCommand\\a{o}{\\IfNoValueTF{#1}{no}{#1}}\\a\\a[x]','\\DeclareMathOperator*{\\a}{sin}\\a','\\NewDocumentCommand\\a{m}{\\ExpandArgs{c}\\NewDocumentCommand{#1}{m}{##1}}\\a{todo}\\todo{x}']
for text in programs:
 for enabled,rawmode in itertools.product([False,True],[False,True]):parse(text,['raw',['program']] if rawmode else ['program'],'snapshots-scopes-newif-nested-definitions-and-environment-helpers',init(enabled))
for text in ['\\def\\a{x}','\\newcommand\\a{x}\\newcommand\\a{y}','\\NewDocumentCommand\\a{}{x}\\NewDocumentCommand\\a{}{y}']:
 for extra,action in itertools.product([[['verbatim',True]],[['enable-raw',False]],[['push']],[['raw',tokens('old'),1,2]]],[['raw',['program']],['or',['try',['then',['program'],['fail']]],['program']]]):parse(text,action,'existing-raw-scopes-verbatim-and-global-report-backtracking',init()+extra)
for start in range(0,0x110000,65536):add(['unicode',start,min(65536,0x110000-start)],'letterize-every-haskell-char')
for s in ['','a1-b_😀é𐐀','123','xabcdef','foo@bar','𝟡',[0xd800,0xdc00]]:add(['letterize',s if isinstance(s,list) else raw(s)],'letterize-retokenization-safe-helper-names')
for depth,updates in [(1500,1),(10000,1),(100000,1),(10000,10),(0,1500),(0,10000),(0,100000)]:add(['deep',depth,updates],'deep-global-scope-maps-and-definition-streams')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalCommandInputs=len(original),originalCommandFiles=len(set(fixtures)),originalReaderInputs=len(units),verifiedOriginalBuildFiles=len(files),exposedModules=exposed)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for c in [m,*cs]:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original macro observations',m['caseHash'])
