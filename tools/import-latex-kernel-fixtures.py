#!/usr/bin/env python3
"""Observe pinned original LaTeX.Parsing, including private numerical helpers."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
folder=root/'tools/haskell-latex-kernel-reference'
target=root/'core/src/test/resources/latex-kernel/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_LATEX_KERNEL_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs').read_text()
expected=src.replace('module Text.Pandoc.Readers.LaTeX.Parsing','module OriginalParsing',1).replace('import Text.Pandoc.Class.PandocMonad (PandocMonad, report)','import Text.Pandoc.Class (PandocMonad, report)',1).replace('  ( DottedNum(..)','  ( evalInteval, evalFpeval, formatFp, delimitedToks, verbatimArg\n  , verbatimBodyToks, applyArgProcessors, tokGroup, splitToksOn\n  , DottedNum(..)',1)
assert (folder/'OriginalParsing.hs').read_text()==expected
sources=['tools/import-latex-kernel-fixtures.py','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs','upstream/pandoc/src/Text/Pandoc/TeX.hs','upstream/pandoc/src/Text/Pandoc/Class.hs','upstream/pandoc/pandoc.cabal','upstream/pandoc/test/command/latex3-usrguide.md','upstream/pandoc/test/command/latex3-commands.md','vendor/ghc-9.12.2/GHC/Internal/Float.hs','licenses/pandoc-GPL-2.0.md','licenses/GHC-base-BSD-3-Clause.txt']
sources+=sorted(str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
sources+=['vendor/ghc-math-9.12.2/GHC/Internal/Real.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs']
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original LaTeX kernel observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
modules=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX']
for name in modules:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+name+',\n' for name in modules))
tracked=[x for x in subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0') if x]
for name in tracked:assert (args.prepared/'pandoc'/name).read_bytes()==(changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()),name
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group,**extra):
 value=ask(req)
 assert req[0]=='demand' or group=='invalid-primitive-character-boundaries' or not(isinstance(value,dict) and 'exception' in value),(req,value)
 cs.append(dict(request=req,expected=value,group=group,**extra))
def expression(kind,s,group,**extra):add([kind,list(map(ord,s))],group,**extra)
for file in ['latex3-commands.md','latex3-usrguide.md']:
 text=(root/'upstream/pandoc/test/command'/file).read_text()
 for kind,s in re.findall(r'\\(inteval|fpeval)\{([^{}\\]*)\}',text):expression('integer' if kind=='inteval' else 'float',s,'original-command-evaluator-expressions',source=file)
for a,b,op in itertools.product(range(-12,13),range(-12,13),['+','-','*','/']):
 s=f'{a}{op}{b}'
 for kind in ['integer','float']:expression(kind,s,'signed-binary-operations-and-division-ties')
expressions=['',' ','\t\r\n','+','-','1+','*1','1/0','0/0','1e309','1e-999','1e','1e+','1e-','1E+2','.','..','.1','1.','1..2','0x1','0b10','1 2','1\u00a0+2','١+2','𝟡+2','1\x00','()','(1)','((1))','1)','(1','1+(2*3)','-(2+3)','--2','+-2','2^3^2','-2^2','2^-2','2**3','2***3','sqrt 2^2','pi','deg','pi(2)','min()','min(1,)','min(,1)','min(1,2,3)','foo(1)','sinx 1','sin(sqrt(2))','max(0/0,1)','min(0/0,1)','max(1,0/0)','min(1,0/0)','sign(-0)','sign(0/0)','fact(0/0)','fact(-1)','fact(170)','fact(171)','round(1,0/0)','round(1,1/0)','floor(1/0)','ceil(-1/0)','trunc(0/0)','round(2.345,2)','round(2.355,2)']
for s in expressions:
 for kind in ['integer','float']:expression(kind,s,'literal-grammar-invalid-input-and-ieee-boundaries')
for fn,x in itertools.product(['round','floor','ceil','trunc'],['-0.0','0.0','-0.1','0.1','-1e-300','1e-300']):
 for form in [f'sign(1/{fn}({x}))',f'atan(-0.0,{fn}({x}))',f'min(1/{fn}({x}),1)',f'max(1/{fn}({x}),-1)']:
  expression('float',form,'integer-rounding-results-use-positive-zero')
unary=['abs','sign','sqrt','exp','ln','fact','sin','cos','tan','cot','sec','csc','asin','acos','atan','acot','asec','acsc','sind','cosd','tand','cotd','secd','cscd','asind','acosd','atand','acotd','asecd','acscd','round','floor','ceil','trunc','unknown']
for fn,x in itertools.product(unary,['-2','-1','-0.0','0','0.1','0.5','1','2','3.5','90','180','1e-100','1e100','1/0','0/0']):
 for s in [f'{fn}({x})',f'{fn} {x}']:expression('float',s,'all-functions-prefix-and-parenthesized-domain-boundaries')
for fn,x,n in itertools.product(['round','floor','ceil','trunc'],['-2.345','-0.1','0','2.345','2.355','1e-200','1e200'],['-400','-324','-23','-3','0','1','2','23','308','309','400','1.1']):expression('float',f'{fn}({x},{n})','rounding-precision-ties-overflow-and-underflow')
for fn,x,y in itertools.product(['atan','atand','min','max','abs','unknown'],['-0.0','0','1','-1','1/0','0/0'],['-0.0','0','1','-1','1/0','0/0']):expression('float',f'{fn}({x},{y})','binary-function-arity-quadrants-and-nonfinite-operands')
r=random.Random(61948)
for i in range(800):
 x,y=(r.uniform(-1e5,1e5) for _ in range(2))
 for fn in ['atan','atand']:expression('float',f'{fn}({x!r},{y!r})','ghc-atan2-division-and-quadrant-operation-order')
def expr(depth):
 if depth==0:return r.choice(['0','1','2','3','7','.1','1e-3','2.345'])
 if r.randrange(5)==0:return r.choice(['sin','cos','sqrt','round','floor','abs','ln'])+'('+expr(depth-1)+')'
 return '('+expr(depth-1)+r.choice(['+','-','*','/','^'])+expr(depth-1)+')'
for i in range(1400):
 s=expr(3)
 for kind in ['integer','float']:expression(kind,s,'seeded-nested-expression-grammar')
for n in [1500,10000]:
 for kind in ['integer','float']:
  for s in ['('*n+'1'+')'*n,'-'*n+'1','1+'*n+'1']:
   expression(kind,s,'deep-finite-expression-grammar')
bits=[0,1,2,0x000fffffffffffff,0x0010000000000000,0x3ff0000000000000,0x3ff8000000000000,0x7fefffffffffffff,0x7ff0000000000000,0x7ff8000000000000,0x7fffffffffffffff]
bits+= [b|(1<<63) for b in bits]+[r.getrandbits(64) for _ in range(1800)]
for b in bits:
 add(['format',b],'sixteen-significant-digits-fixed-decimal-and-integer-shortcut')
 add(['fixed',b],'ghc-fixed-shortest-format')
 for n in [-2,0,1,2,6,15,16,20]:add(['exponent',b,n],'ghc-scientific-format-ties-and-carries')
def raw(s):return list(map(ord,s))
pos=[raw('source'),2,3]
def token(kind,s,p=pos):return [p,kind,raw(s)]
def tokens(s):return ask(['tokens',pos,raw(s)])
def parse(s,action,group,changes=None,**extra):
 add(['parse',changes or [],tokens(s) if isinstance(s,str) else s,action],group,**extra)
primitives=['any','all','peek','control','symbol','single','escape','spaces','spaces1','sp','blankline','endline','whitespace','newline','comment','bgroup','egroup','braced','braced-url','braced-or-token','bracketed','bracketed-toks','grouped','paren','verbatim-arg','dimen','keyvals','overlay','rawopt','skipopts','label','caption','tok-with','do-macros']
sample=['','x','abc def','😀é𐐀','  x','\n','\n  x','\n \n x','%x\n y','{a{b}c}tail','{{ab}}z','{a]b}c','{http://x/%20a}z','[a{b]c}d]z','[a[b]c]z','(a(b)c)z','|a b|tail','|a\nb|','^^a^^4f','\\bgroup ab\\egroup z','\\begingroup{a}\\endgroup','\\foo bar','\\iftrue a\\else b\\fi tail','\\iffalse a\\iftrue b\\else c\\fi\\else d\\fi','\\fpeval{0.1+0.2}tail','\\inteval{1/0}tail','\\label{foo}tail','[a=one,b={two,three},c-d,e_f=x_y]tail','<1-3,5>tail','<article>tail','<nonsense>tail','=12.3pt plus 2pt','.5em','-2pt','a','1unknown']
for action,s in itertools.product(primitives,sample):
 for wrap in [lambda p:p,lambda p:['raw',p],lambda p:['or',['try',p],['pure','fallback']]]:
  parse(s,wrap([action]),'token-primitives-groups-raw-capture-backtracking-and-errors')
for s in ['[a=b{c}d,e=f]','[a=,b=]','[a=\\foo bar,b={x}]','[ a =  one\n two ,b]','[a={x}','[a={x}z]','[a==b]']:
 parse(s,['keyvals'],'keyval-braced-unbraced-values-and-failures')
for kind,txt in [(['Word'],''),(['Word'],'abc'),(['Symbol'],'😀ab'),(['Symbol'],'{}'),(['Esc1'],'^^'),(['Esc1'],'^^😀'),(['Esc2'],'^^ff'),(['Esc2'],'^^xz')]:
 for action in ['single','escape','any','symbol','braced']:
  parse([token(kind,txt)],['raw',[action]],'synthetic-token-primitives-empty-text-and-multiple-codepoints')
for kind,txt in [(['Esc1'],'^^'+chr(0x10ffff)),(['Esc2'],'^^110000'),(['Esc2'],'^^ffffffffffffffff')]:
 parse([token(kind,txt)],['escape'],'invalid-primitive-character-boundaries')
for points in [raw('source'),raw('other'),[0xd800,0xdc00],[0x10000]]:
 parse([token(['Word'],'x',[points,2,3])],['infile',points],'raw-haskell-source-name-comparison')
 parse([token(['Symbol'],'😀x')],['symbol-in',[0x1f600,ord('x')]],'symbol-in-first-codepoint')
for name,s in itertools.product(['write','titleformat','def','vadjust','tiny','Huge','hfill','hskip','vskip','other'],['','123{x}tail','{a}[b]{c}{d}{e}{f}tail','\\a#1{body}tail','pre','12pt plus 2pt minus 3em tail','[a]{x}{y}tail','<1-2>[a]{x}','-2.3pt{x}tail']):parse(s,['raw-command',raw(name),raw('\\'+name)],'raw-command-argument-grammars')
for name,s in itertools.product(['x','foo'],['\\begin{x}z','\\begin {x}z','\\end{foo}z','abc\n  \\end{x}z','\n a\n \\end{x}z','  \\end{x}z','a\n\t\\end{x}z']):
 for kind in ['begin','end','verb-env']:parse(s,[kind,raw(name)],'environment-boundaries-and-verbatim-trailing-newline')
for s in ['','a b',' a\n b\n','\n \n a b\n \n','\t\n\n','a\n\nb\n','{x} \\foo #1']:
 for trim in [False,True]:add(['body',trim,tokens(s)],'verbatim-body-lines-and-visible-space')
for s in sample:
 add(['group',tokens(s)],'pure-balanced-token-group')
 for delim in ['{','}',',','a']:
  add(['split',token(['Symbol'],delim),tokens(s)],'pure-depth-zero-token-splitting')
for s in ['a,b,{c,d}, e',' a ; b ; c ','{a,b},c','\\BooleanTrue',' \\BooleanFalse %c\n','\n x %c\n','']:
 for procs in [['\\TrimSpaces'],['\\ReverseBoolean'],['\\SplitArgument{2}{,}'],['\\SplitArgument{-1}{,}'],['\\SplitArgument{9223372036854775807}{,}'],['\\SplitList{,}'],['\\SplitList'],['\\Unknown'],['  %c\n'],['\\TrimSpaces','\\SplitList{,}'],['\\SplitArgument{2}{;}','\\TrimSpaces']]:
  parse('', ['processors',[tokens(p) for p in procs],tokens(s)],'all-argument-processors-order-and-global-reporting')
def macro(specs,body,default=None,expansion=1):return [0,expansion,specs,None if default is None else tokens(default),tokens(body)]
def define(name,m):return ['define',name,m]
arg=lambda n:['ArgNum',n]
cases=[([],None,'body','\\a tail'),([arg(1)],None,'[#1]','\\a{foo}tail'),([arg(1)],None,'#1','\\a xyz'),([arg(1),arg(2)],'default','#1/#2','\\a{x}tail'),([arg(1),arg(2)],'#2','#1/#2','\\a[opt]{x}tail'),([arg(1),arg(2)],'#2','#1/#2','\\a{x}tail'),([arg(1)],'#1','#1','\\a tail'),([arg(1)],None,'##1/#1','\\a{#2}tail'),([arg(1),['Pattern',tokens(';')]],None,'#1','\\a{a;b}c;tail'),([['Pattern',tokens('X')],arg(1)],None,'#1','\\a X{y}tail'),([],None,'#9tail','\\a tail'),([],None,'body\n','\\a\nnext'),([],None,'body\n\n','\\a\nnext')]
for specs,d,body,s in cases:
 for expansion,rawMode in itertools.product([0,1],[False,True]):
  parse(s,['raw',['all']] if rawMode else ['all'],'ordinary-macro-arguments-default-references-and-newline-substitution',[define('a',macro(specs,body,d,expansion))])
booltok=token(['Symbol'],'*');open=token(['Symbol'],'[');close=token(['Symbol'],']')
specs=[['BoolArg',True,booltok],['BoolArg',False,booltok],['DelimArg',True,open,close,None],['DelimArg',False,open,close,tokens('#2')],['VerbArg'],['EmbellishArg',[[token(['Symbol'],'^'),None],[token(['Symbol'],'_'),tokens('def')]]],['ProcessedArg',[tokens('\\TrimSpaces')],arg(1)],['ProcessedArg',[tokens('\\SplitList{,}')],arg(1)]]
for spec,s in itertools.product(specs,['\\a tail','\\a*tail','\\a *tail','\\a[x[y]z]tail','\\a [x{]}z]tail','\\a{ a,b }tail','\\a|a b|tail','\\a^{up}_{down}tail','\\a _x ^{y}tail']):
 parse(s,['raw',['all']],'xparse-optional-verbatim-embellishment-and-processed-arguments',[define('a',macro([spec],'(#1)(#2)'))])
for kind,trim,s in itertools.product(['BodyArg','VerbBodyArg'],[False,True],['\\begin{x} a\n b \\end{x}tail','\\begin{x}\n\n a {\\end{x}} b\n\n\\end{x}tail','\\begin{x}no-end']):
 parse(s,['all'],'xparse-environment-body-arguments',[define('x',macro([[kind,trim,1],['Pattern',tokens('\\end{x}')]],'#1'))])
for body,s in itertools.product(['\\b','\\b#1','\\expandafter\\b\\c','\\b word','\\iftrue yes\\else no\\fi'],['\\a{arg}tail','\\expandafter\\a\\c tail']):
 for expansion in [0,1]:parse(s,['all'],'recursive-expansion-order-and-control-word-spacing',[define('a',macro([arg(1)],body,expansion=expansion)),define('b',macro([],'B')),define('c',macro([],'word'))])
for n in [0,1,19,20,21,22]:
 for body in ['\\a','\\b','word']:
  parse('\\a tail',['expand',n],'macro-depth-limit-and-exception-propagation',[define('a',macro([],body))])
for s in ['\\begin{x}tail','\\end{x}tail','\\expandafter\\foo bar','\\expandafter\\foo \\b tail','\\xspace word','\\xspace 3','\\xspace!','\\xspace\\b']:
 parse(s,['raw',['all']],'environment-macro-names-expandafter-and-xspace',[define('x',macro([],'begin')),define('endx',macro([],'end')),define('b',macro([],'word'))])
conditionals=['iftrue','iffalse','ifmmode']
for name,body,math in itertools.product(conditionals,['yes\\else no\\fi','yes\\fi','yes\\iftrue inner\\else bad\\fi\\else no\\fi','yes\\ifalias a\\else b\\fi\\else no\\fi','yes\\else no','yes\\else no\\else extra\\fi'],[False,True]):
 parse('\\'+name+' '+body+' tail',['all'],'primitive-conditional-nesting-aliases-math-mode-and-missing-fi',[['math',math],define('ifalias',macro([],'\\iftrue'))])
for prefix,suffix,argtext in itertools.product(['IfNoValue','IfValue','IfBoolean','IfBlank'],['TF','T','F'],['',' ','%comment\n','\\NoValue','-NoValue-','\\BooleanTrue','\\BooleanFalse','x']):
 s='\\'+prefix+suffix+'{'+argtext+'}'+('{yes}' if 'T' in suffix else '')+('{no}' if 'F' in suffix else '')+'tail'
 parse(s,['all'],'all-xparse-conditionals-and-no-value-boolean-blank-tests')
for s in ['\\ifstrequal{a}{a}{yes}{no}tail','\\ifstrequal{\\b}{B}{yes}{no}tail','\\ProcessList{{a}{b}c}{\\b}tail','\\UseName{b}tail','\\ExpandArgs{cnN}\\b{a}{bc}d tail','\\ExpandArgs{q}\\b{x}tail','\\inteval{\\b+2}tail','\\fpeval{1/0}tail','\\dimeval{ 2pt + 3pt }tail','\\skipeval{ 1em plus 2pt }tail']:
 parse(s,['raw',['all']],'special-macro-nested-default-state-evaluation-and-reports',[define('b',macro([],'4'))])
for s,mode in itertools.product(['\\a{z}tail','\\iftrue yes\\else no\\fi','{ab}tail','[a{]}b]tail'],[[],[['verbatim',True]],[['enable-raw',False]],[['raw',tokens('old'),1,2]]]):
 for action in [['raw',['all']],['raw',['raw',['all']]],['pair',['peek'],['raw',['any']]],['or',['try',['then',['raw',['any']],['fail']]],['raw',['all']]]]:
  parse(s,action,'existing-raw-scopes-verbatim-and-backtracking-state-restoration',mode+[define('a',macro([arg(1)],'#1'))])
for wrap in [lambda p:p,lambda p:['or',['try',['then',p,['fail']]],['pure','fallback']]]:
 parse('',wrap(['processors',[tokens('\\Unknown')],tokens('arg')]),'global-reports-survive-parser-state-backtracking')
for verbatim in [False,True]:
 new=define('a',macro([],'new'))
 parse('{\\a}\\a',['then',['bgroup'],['then',['change',new],['pair',['any'],['then',['egroup'],['all']]]]],'group-macro-table-inheritance-and-local-update-pop',[define('a',macro([],'old')),['verbatim',verbatim]])
 parse('\\a\\end{x}\\a',['pair',['env',raw('x'),['then',['change',new],['any']]],['all']],'environment-macro-table-update-and-pop-before-end',[define('a',macro([],'old')),['verbatim',verbatim]])
def il(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
def span(label,attrs=None):return il('Span',[['',['cls'],attrs or [['label',label]]],[il('Str','hidden')]])
for content in [[span('x'),il('Space'),il('SoftBreak'),il('Str','kept')],[il('Str','a'),span('x'),il('Space'),span('x'),il('Space'),il('Str','b')],[span('other'),il('Space'),span('x')],[span('x',[['label','other'],['label','x']]),il('Space')]]:
 doc={'pandoc-api-version':[1,23,1,2],'meta':{'a':il('MetaInlines',content)},'blocks':[il('Para',content),il('BlockQuote',[il('Para',[il('Emph',content)])])]}
 add(['remove-label',raw('x'),doc],'remove-label-walk-order-duplicate-attributes-and-space-removal')
default_options=ask(['default-options'])
for enabled in [False,True]:
 options=default_options.copy();options['extensions']=['latex_macros'] if enabled else []
 changes=[['options',options],define('a',macro([arg(1)],'[#1]')),define('b',macro([],'word'))]
 for text in ['\\a{x}tail','\\b tail','\\fpeval{1/0}','\\iftrue yes\\else no\\fi','abc']:
  add(['bridge',changes,[[pos,raw('untouched')]],['apply',raw(text)]],'sources-apply-macros-extension-guard-errors-and-state')
 for text,prep,value in [('{ab}tail',['braced'],['braced']),('\\a{x}tail',['repeat',2,['any']],['all']),('\\b tail',['any'],['all']),(' \\b tail',['repeat',2,['any']],['all']),('[a{]}b]tail',['bracketed-toks'],['bracketed-toks']),('\\label{x}tail',['repeat',2,['any']],['label']),('{ab}tail',['braced'],['fail']),('x',['fail'],['any'])]:
  for chunks in [[[pos,raw(text)]],[[pos,raw(text[:1])],[[raw('second'),5,6],raw(text[1:])]]]:
   add(['bridge',changes,chunks,['raw',prep,value]],'sources-raw-latex-bridge-lookahead-token-reparse-and-macro-updates')
for n in [1500,10000,100000]:
 for s,action in [('{'*n+'x'+'}'*n,['braced']),('x '*n,['all'])]:
  parse(s,action,'deep-finite-token-grammar')
for kind in range(9):add(['demand',kind],'macro-deferred-demand')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),verifiedOriginalBuildFiles=len(tracked),exposedModules=modules)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for value in [m,*cs]:z.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'LaTeX kernel observations',m['caseHash'],flush=True)
