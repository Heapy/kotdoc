#!/usr/bin/env python3
"""Observe the complete original LaTeX SIunitx module, including common state."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,re,subprocess,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-siunitx-reference';previous=root/'tools/haskell-latex-language-reference';older=root/'tools/haskell-latex-citation-math-reference';target=root/'core/src/test/resources/latex-siunitx/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_LATEX_SIUNITX_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--parsec-archive',type=Path);args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/SIunitx.hs').read_text()
assert (folder/'OriginalSIunitx.hs').read_text()==src.replace('module Text.Pandoc.Readers.LaTeX.SIunitx','module OriginalSIunitx',1).replace('  ( siunitxCommands','  ( siunitxCommands, tonum, parseNum, siUnit, emptyOr160, hyphenToMinus, siUnitMap, siUnitModifierMap',1).replace('import Text.Pandoc.Readers.LaTeX.Parsing','import OriginalParsing',1).replace('import Text.Pandoc.Class.PandocMonad ( PandocMonad )','import Text.Pandoc.Class ( PandocMonad )',1)
assert (folder/'LanguageObserver.hs').read_text()==(previous/'LaTeXLanguageReference.hs').read_text().replace('module Main where','module LanguageObserver where',1)
sources=['tools/import-latex-siunitx-fixtures.py','core/src/test/resources/native/upstream.json','licenses/pandoc-GPL-2.0.md','upstream/pandoc/test/Tests/Readers/LaTeX.hs','upstream/pandoc/pandoc.cabal']
sources+=['upstream/pandoc/src/Text/Pandoc/'+s+'.hs' for s in ['Sources','Parsing','Readers/LaTeX/SIunitx','Readers/LaTeX/Lang','Readers/LaTeX/Parsing','Readers/LaTeX/Macro','Shared','Translations','Translations/Types','Class/CommonState']]
sources+=['licenses/Parsec-BSD-2-Clause.txt']+sorted(str(f.relative_to(root)) for f in (root/'vendor/parsec-3.1.18.0').rglob('*') if f.is_file())
sources+=sorted(str(f.relative_to(root)) for d in [folder,previous,older] for f in d.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
pattern=r'\\(?:SI(?:range|list)?|si|unit|qty(?:range|list)?|num(?:range|list)?|ang)\b|siunitx'
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
 print('Verified',len(cs),'original LaTeX SIunitx observations and',len(m['sources']),'source hashes');raise SystemExit
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
 value=json.loads(line);return value
def add(req,group,allow_exception=False,**extra):
 result=ask(req)
 assert allow_exception or not(isinstance(result,dict) and 'exception' in result),(req,result)
 cs.append(dict(request=req,expected=result,group=group,**extra))
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
registries=ask(['registries']);add(['registries'],'all-siunitx-command-unit-and-modifier-names');add(['maps'],'complete-original-unit-and-modifier-asts')
nums=['','+','-','.','..','1','123','0001','0.00','.1','-.1','1.2.3','1,2','1e-3','+-','1+-2i','10.0 \\pm 3.3','1x2','i','e','1e','1e+','1E3','1\t2','1\n2','1\u00a02','١٢','𝟡','-0','+0','(1)','1(0)','1(00)','1(001)','1.0(001)','1.23(000)','1.(20)','1.2345(1.)',' 1 ','+ 1','--1','\\pm','1\\pm-2','1+-2','1e2e3','1.2(3)4','1.2(3)(4)','1,2(3)','1(2','1()','1(abc)','1(2))','1.2(3.4)','1x',' x ','x2','1e.2','1e-0.2(3)']
nums+=[''.join(t) for t in itertools.product(['','+','-'],['0','0.0','.1','1.23456','20.','..'],['','(0)','(2)','(00100)','(.)','(1.0)','()','(bad)'],['','e3','e-3','e+3','E3'])]
import random
rng=random.Random(530187)
nums+=[''.join(rng.choice('0123456789,.()+-ieEx \\pm\t\n\u00a0١𝟡') for _ in range(rng.randrange(46))) for i in range(2500)]
nums=list(dict.fromkeys(nums))
for value in nums:
 add(['tonum',raw(value)],'number-formatting-original-parser-and-fallback')
 add(['parse-num',raw(value)],'complete-pure-number-parser-values-positions-and-errors')
fail=['fail',0,[]];tok=['tok']
for name in registries[2]:
 parse('\\'+name,['si-unit',[],fail],'every-unit-base-command')
 for modifier in registries[1]:parse('\\'+modifier+'\\'+name,['si-unit',[],fail],'every-modifier-unit-combination')
 for prefix in ['\\square','\\cubic','\\raisetothe{4}']:parse(prefix+'\\'+name,['si-unit',[],tok],'every-unit-prefix-exponent')
 for suffix in ['\\squared','\\cubed','\\tothe{-2}','^{-2}','_{polymer}']:parse('\\'+name+suffix,['si-unit',[],tok],'every-unit-suffix-and-ast-hyphen-walk')
unit_samples=['','m','kg.m/s^2','g_{polymer}~mol_{cat}.s^{-1}','\\kilogram\\metre\\per\\second','\\square\\volt\\cubic\\lumen\\per\\farad','\\metre\\squared\\per\\gray\\cubic\\lux','\\gram\\per\\cubic\\centi\\metre','\\bohr','\\planckbar','\\meter\\per\\second\\per\\ampere','m/s/s','\\per\\metre','\\kilo','\\square','\\raisetothe{-2}\\metre','\\metre\\tothe{-2}','m^-2','m^{--2}','m^2_3','m_{}','m_{a-b}','m\\per\\second^{-2}','m\\per','m/','m..s','~ m ~ s','m\n s','m%comment\ns','\\unknown','\\metre\\square','{m}','{{m}}','{m','m}','.[m]','\\kilo\\kilo\\metre','\\metre\\per{bad}','m/\\square\\metre','\\square[ignored]\\metre']
options=[[],[[raw('per-mode'),raw('symbol')]],[[raw('per-mode'),raw('power')]],[[raw('per-mode'),raw('symbol')],[raw('per-mode'),raw('power')]],[[raw('per-mode'),raw('power')],[raw('per-mode'),raw('symbol')]]]
for unit,opt in itertools.product(unit_samples,options):parse(unit,['si-unit',opt,tok],'unit-separators-prefix-suffix-infix-options-malformed-and-fallback')
for command in ['si','unit']:
 for u,opt in itertools.product(unit_samples,['','[per-mode=symbol]','[per-mode=power,per-mode=symbol]','[other=x]']):
  for source in [opt+'{'+u+'}',opt+u]:parse(source,['raw',['si',raw(command),tok]],'unit-command-grouped-and-ungrouped-raw-capture')
for command in ['SI','qty']:
 for value,u,prefix,opt in itertools.product(['','1','-123','.3e45','0.135(21)','bad'],['','m','\\celsius','\\kilo\\gram','\\square\\metre'],['','[pre]','[]'],['','[round-precision=2]']):parse(opt+'{'+value+'}'+prefix+'{'+u+'}',['si',raw(command),tok],'quantity-number-value-prefix-and-unit-spacing')
for command in ['SIrange','qtyrange','numrange']:
 for start,stop,p1,p2,u in itertools.product(['','1','-1.2e3','bad'],['2','0.3(21)'],['','[pre]'],['','[post]'],['','m','\\cubic\\metre']):
  source='[round-precision=2]{'+start+'}'+p1+'{'+stop+'}'+p2+('{' + u + '}' if command!='numrange' else '')
  parse(source,['si',raw(command),tok],'all-range-aliases-prefixes-optional-units-and-ignored-rounding')
for command in ['SIlist','qtylist','numlist']:
 for values,u,opt in itertools.product(['',';','1;2','1;;2;','0.13;0.67;0.80','1e3;-2;bad','1(2);1.2(3)'],['','m','\\milli\\metre'],['','[per-mode=symbol]']):parse(opt+'{'+values+'}'+('{' + u + '}' if command!='numlist' else ''),['si',raw(command),tok],'all-list-aliases-empty-parts-unit-repetition-and-conjunction')
for command in ['num','ang']:
 for value in nums[:len(nums):max(1,len(nums)//130)]+['1;2;3',';;1','+10;;','-0;1;','1;2;3;4','+;++;+++',' 1 ; 2 ; 3 ','1e3;2;3',';',';;']:
  for source in ['{'+value+'}','[ignored]{'+value+'}']:parse(source,['si',raw(command),tok],'number-and-angle-command-options-signs-and-first-three-parts')
for cb in [['chunk',[strx('custom-unit')],[['report',raw('callback report')]]],['fail',0,[['report',raw('callback failure')]]],['fail',1,[['last-label','failed']]]]:
 for value in ['\\unknown','m\\per\\unknown','m^\\unknown','\\raisetothe\\unknown m','\\kilo\\unknown']:
  for wrap in [lambda p:p,lambda p:['raw',p],lambda p:['or',['try',p],['state']]]:parse(value,wrap(['si-unit',[],cb]),'stateful-consuming-failing-callbacks-and-global-reports')
for value in ['', 'm','m\\per\\unknown']:
 parse(value,['si-unit',[],pure([strx('empty-callback')])],'empty-successful-callback-many-contract',allow_exception=True)
for source in ['\\def\\myunit{\\kilo\\metre}\\si{\\myunit}','\\num{\\ifmmode 1\\else 2\\fi}','\\SI{1}[pre]{\\gram}','\\si{\\metre\\tothe{\\textbf{-2}}}','\\si{m\\per\\second^{-2}}']:
 for csx in [initial(),initial()+[['enable-raw',False]],initial()+[['verbatim',True]]]:parse(source,['rich'],'macro-expansion-scope-raw-and-verbatim-component-behavior',csx)
inputs=ask(['inputs'])
for name,value in inputs:parse(''.join(map(chr,value)),['rich'],'all-original-latex-reader-inputs-through-explicit-callback',source=name)
for file in fixtures:
 text=(root/'upstream/pandoc'/file).read_text()
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',text,re.M|re.S)):
  if re.search(pattern,m[2]):parse(m[2],['rich'],'all-original-siunitx-command-inputs-through-explicit-callback',source=file,index=index)
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
 add(['walk',xs],'all-original-inline-trees-exponent-hyphen-walk',source=i)
 add(['empty-space',xs],'all-original-inline-lists-conditional-nonbreaking-space',source=i)
for value in [[],[strx('')],[strx('x-y')],[dict(t='Superscript',c=[strx('-2')])]]:
 add(['walk',value],'empty-and-direct-hyphen-ast-inputs');add(['empty-space',value],'empty-and-direct-spacing-inputs')
for start in range(0,0x110000,0x10000):add(['unicode',start,min(0x10000,0x110000-start)],'every-haskell-char-number-formatting-and-fallback')
for kind,n in itertools.product(range(3),[1500,10000,100000]):add(['stress',kind,n],'deep-finite-number-modifier-and-exponent-walk')
for n in [1500,10000]:add(['stress',3,n],'deep-finite-per-infix-chain')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),registries=registries,originalReaderInputs=len(inputs),originalDocuments=len(docs),originalInlineLists=len(inline_lists),originalCommandFiles=len(fixtures),verifiedOriginalBuildFiles=len(files),exposedModules=exposed)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for c in [m,*cs]:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original LaTeX SIunitx observations',m['caseHash'],flush=True)
