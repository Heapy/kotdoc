#!/usr/bin/env python3
"""Observe the complete original LaTeX Inline module, including common state."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-inline-reference';previous=root/'tools/haskell-latex-siunitx-reference';language_folder=root/'tools/haskell-latex-language-reference';older=root/'tools/haskell-latex-citation-math-reference';target=root/'core/src/test/resources/latex-inline/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_LATEX_INLINE_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--parsec-archive',type=Path);p.add_argument('--unicode-cache',type=Path);args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Inline.hs').read_text()
assert (folder/'OriginalInline.hs').read_text()==src.replace('module Text.Pandoc.Readers.LaTeX.Inline','module OriginalInline',1).replace('  ( acronymCommands','  ( accentWith, romanNumeralArg, acronymCommands',1).replace('import Text.Pandoc.Readers.LaTeX.Parsing','import OriginalParsing',1).replace('import Text.Pandoc.Class.PandocMonad (PandocMonad (..))','import Text.Pandoc.Class (PandocMonad (..))',1)
assert (folder/'SIunitxObserver.hs').read_text()==(previous/'LaTeXSIunitxReference.hs').read_text().replace('module Main where','module SIunitxObserver where',1)
sources=['tools/import-latex-inline-fixtures.py','tools/import-unicode-nfc.py','core/src/main/resources/io/heapy/kotdoc/unicode-nfc-0.9.0.json','core/src/test/resources/native/upstream.json','licenses/pandoc-GPL-2.0.md','upstream/pandoc/test/Tests/Readers/LaTeX.hs','upstream/pandoc/pandoc.cabal']
sources+=['upstream/pandoc/src/Text/Pandoc/'+s+'.hs' for s in ['Data','Class/PandocPure','Highlighting','Sources','Parsing','Readers/LaTeX/Inline','Readers/LaTeX/Lang','Readers/LaTeX/Parsing','Readers/LaTeX/Macro','Shared','Translations','Translations/Types','Class/CommonState']]
sources+=['licenses/Parsec-BSD-2-Clause.txt']+sorted(str(f.relative_to(root)) for f in (root/'vendor/parsec-3.1.18.0').rglob('*') if f.is_file())
sources+=sorted(str(f.relative_to(root)) for d in [folder,previous,language_folder,older] for f in d.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def unescape(s):
 out='';i=0
 while i<len(s):
  if s[i]!='\\':out+=s[i];i+=1;continue
  i+=1
  if s[i].isdigit():
   j=i
   while j<len(s) and s[j].isdigit():j+=1
   out+=chr(int(s[i:j]));i=j
  elif s[i]=='x':
   j=i+1
   while j<len(s) and s[j] in '0123456789abcdefABCDEF':j+=1
   out+=chr(int(s[i+1:j],16));i=j
  else:out+={'n':'\n','r':'\r','t':'\t','&':''}.get(s[i],s[i]);i+=1
 return out
names=[unescape(x) for x in re.findall(r'^\s+[\[,]\s+\("((?:\\.|[^"\\])*)",',src,re.M)]
pattern=r'\\(?:'+'|'.join(re.escape(x)+(r'(?![A-Za-z])' if x and x[-1].isalpha() else '') for x in names)+')'

fixtures=[]
for name in subprocess.check_output(['git','ls-files','test/command/*.md'],cwd=root/'upstream/pandoc').decode().splitlines():
 if re.search(pattern,(root/'upstream/pandoc'/name).read_text()):fixtures.append(name)
sources+=['upstream/pandoc/'+s for s in fixtures]
for path in sorted((root/'upstream/pandoc/data/translations').glob('*.yaml')):sources.append(str(path.relative_to(root)))
sources+=['licenses/Unicode-transforms-BSD-3-Clause.txt','licenses/Unicode-data-Apache-2.0.txt','licenses/Unicode-data-NOTICE.txt']
for package in ['unicode-transforms-0.4.0.1','unicode-data-0.9.0']:
 sources+=sorted(str(path.relative_to(root)) for path in (root/'vendor'/package).rglob('*') if path.is_file())
for notice,original in [('Unicode-transforms-BSD-3-Clause.txt','unicode-transforms-0.4.0.1/LICENSE'),('Unicode-data-Apache-2.0.txt','unicode-data-0.9.0/LICENSE'),('Unicode-data-NOTICE.txt','unicode-data-0.9.0/NOTICE')]:
 assert (root/'licenses'/notice).read_bytes()==(root/'vendor'/original).read_bytes()
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def links():return {s:os.readlink(root/s) for s in sorted(set(sources)) if (root/s).is_symlink()}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['sourceLinks']==links()
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original LaTeX Inline observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared and args.parsec_archive and args.unicode_cache
for package,version in [('unicode-transforms','0.4.0.1'),('unicode-data','0.9.0')]:
 base=root/'vendor'/(package+'-'+version)
 arc=args.unicode_cache/package/version/(package+'-'+version+'.tar.gz')
 assert hashlib.sha256(arc.read_bytes()).hexdigest()==re.search(r'SHA256: ([0-9a-f]{64})',(base/'KOTDOC-PROVENANCE').read_text())[1]
 with tarfile.open(arc) as tar:
  for path in base.rglob('*'):
   if path.is_file() and path.name!='KOTDOC-PROVENANCE':assert path.read_bytes()==tar.extractfile(package+'-'+version+'/'+str(path.relative_to(base))).read(),path
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
def parse(s,p,group,changes=None,cache=loaded,files=None,**extra):add(['parse',initial() if changes is None else changes,cache,files or [],tokens(s) if isinstance(s,str) else s,p],group,**extra)
def il(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
def strx(s):return il('Str',s)
def pure(xs,changes=None):return ['pure',xs,changes or []]
registries=ask(['registries']);add(['registries'],'all-eight-inline-command-registries')
registry=dict(registries);tok=['tok'];atom=['atom'];fail=['fail',0,[]]
def entry(kind,name,cb=tok):return ['entry',kind,raw(name),cb]
for kind,keys in registries:
 if kind in ['misc','char']:
  for name in keys:
   for text in ['', '[x] \n next','{abc}']:
    for in_table in [False,True]:parse(text,entry(kind,name),'every-literal-and-char-command-with-table-guard',initial()+[['table-cell',in_table]])
 if kind=='acronym':
  for name,text in itertools.product(keys,['{}','{alpha}','{two words}','{x-y}','{{nested}}','{bad','none']):parse(text,['raw',entry(kind,name)],'all-acronym-singular-plural-forms-and-raw-capture')
 if kind=='biblatex':
  for name,text in itertools.product(keys,['{}','{x}','{ x }','{a\\emph{b}}','{bad','word',' 12','{12}','{014}']):parse(text,entry(kind,name),'every-biblatex-inline-command-argument-and-fallback')
 if kind=='ref':
  for name,text,raw_tex in itertools.product(keys,['{}','{alpha}','{a b}','[options]{key}','{bad','not-braced','{x}{extra}'],[False,True]):
   csx=initial()+[['last-label','old']]
   if raw_tex:csx[0][1]['extensions'].append('raw_tex')
   parse(text,['raw',entry(kind,name)],'all-reference-label-commands-with-and-without-raw-tex',csx)
for name in registry['verb']:
 for marker,body,options in itertools.product(['|','!','{','$','😀'],['','abc','a%comment','a\\macro{b}','a\nb','a\r\nb','a\tb',' a b ','missing'],['','[language=Python]','[language=C++,language=Java]','[x=1]']):
  close='}' if marker=='{' and name in ['lstinline','mintinline'] else marker
  text=options+('{python}' if name=='mintinline' else '')+marker+body+('' if body=='missing' else close)+'tail'
  parse(text,['raw',entry('verb',name)],'verbatim-inline-code-markers-options-newlines-and-missing-terminators')
# Explicit malformed symbol payloads exercise T.uncons's one-Char marker guard.
for text in ['', 'ab', '😀', '\n']:
 ts=[[[raw('symbol'),2,3],['Symbol'],raw(text)]]
 for name in registry['verb']:parse(ts,entry('verb',name),'synthetic-empty-multichar-and-supplementary-marker-tokens')
opts=[[],[[raw('language'),raw('Python')]],[[raw('language'),raw('C++')]],[[raw('language'),raw('Unknown')]],[[raw('Language'),raw('Python')]],[[raw('language'),raw('Java')],[raw('language'),raw('C++')]]]
listing_source=(root/'upstream/pandoc/src/Text/Pandoc/Highlighting.hs').read_text()
listing_names=re.findall(r'\("[^"\n]+",\s*"([^"\n]+)"\)',listing_source)
for language_name in listing_names+['','Python','python','C++','Java','unknown','😀']:
 for key in ['language','Language']:add(['listings',[[raw(key),raw(language_name)]]],'listings-language-original-mapping-and-unknown-fallback')
for options in opts:add(['listings',options],'listings-language-first-duplicate-option')
for text in ['0','1','7','12','29','014','41','3999','4000','10000','9223372036854775807','9223372036854775808','18446744073709551616','18446744073709551630','999999999999999999999999999999','12a','0x10','12 3','+1','-1','١٢','𝟡','', '1.0']:
 for source in [text,'{'+text+'}',' { '+text+' }','\n'+text+'\n']:
  parse(source,['roman'],'roman-number-argument-whitespace-digit-rejection-and-int64-wrapping')
  for command in ['RN','Rn']:parse(source,entry('biblatex',command),'roman-number-upper-lower-empty-and-range-rendering')
for name in ['ref','label','write','titleformat','unknown','href']:
 for raw_tex,cb in itertools.product([False,True],[pure([strx('fallback')],[['last-label','fallback']]),['fail',0,[['report',raw('fallback failure')]]]]):
  csx=initial()
  if raw_tex:csx[0][1]['extensions'].append('raw_tex')
  parse('{a}[b]{c}', ['raw-inline',raw(name),cb],'raw-inline-or-preserves-gate-and-fallback-effects',csx)
# Every original inline list exercises wrapping and accent fallback/first-Str rules.
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
 parse('',entry('biblatex','mkbibquote',pure(xs)),'all-original-inline-trees-biblatex-wrapping',source=i)
 parse('',entry('accent',"'",pure(xs)),'all-original-inline-trees-accent-first-str-and-fallback',source=i)
accent_inputs=[[],[il('Space')],[strx('')],[strx('ab')],[strx('a\u0301b')],[strx('é-tail')],[strx('😀tail')],[strx('\u0301tail')],[il('Space'),strx('x')],[il('SoftBreak')],[il('Emph',[strx('a')])],[strx('a'),il('Space'),strx('tail')]]
for command,xs in itertools.product(registry['accent'],accent_inputs):parse('',entry('accent',command,pure(xs)),'every-accent-command-empty-space-tail-and-non-str-shapes')
for mark,fallback,xs in itertools.product([0,769,8413,0xd800,0x10ffff],[None,32,94,0xd800],accent_inputs[:5]):parse('',['accent-direct',mark,fallback,pure(xs)],'direct-accent-optional-char-and-text-pack-boundaries')
for command,cb,text in itertools.product([('accent',"'"),('biblatex','mkbibbold')],[pure([strx('x')],[['last-label','callback']]),['fail',0,[['report',raw('failure')]]],['fail',1,[['quote',1]]]],['','x','{x}']):
 for wrap in [lambda p:p,lambda p:['or',['try',p],['state']],lambda p:['look',p]]:parse(text,wrap(entry(*command,cb)),'stateful-failing-accent-and-biblatex-callbacks')
all_terms={t:'translated-'+t for t in re.findall(r'doTerm Translations\.(\w+)',src)}
for cache in [None,loaded,[language('en'),all_terms]]:
 for name in registry['name']:parse('',entry('name',name),'every-name-command-cache-and-no-translation-reporting',cache=cache)
translation_files=sorted((root/'upstream/pandoc/data/translations').glob('*.yaml'))
for path in translation_files:
 selected=ask(['parsed-lang',raw(path.stem)])
 for name in registry['name']:parse('',entry('name',name),'all-original-translation-files-through-inline-name-commands',cache=[selected,None],source=path.name)
selected_en=ask(['parsed-lang',raw('en')]);selected_regional=ask(['parsed-lang',raw('en-US')])
for payload in [b'Figure: Custom\nProof: P\n',b'Figure: 1\n',b'',b'bad: [\n',b'Figure: \xff\n']:
 fs=[[raw('user/translations/en.yaml'),list(payload)]]
 for name in ['figurename','proofname']:
  for selected in [selected_en,selected_regional]:parse('',entry('name',name),'user-translation-override-fallback-and-decode-errors',cache=[selected,None],files=fs)
for tag in ['en-XX','de-XX','sr-Latn-XX','zz-ZZ','xx']:
 selected=ask(['parsed-lang',raw(tag)])
 parse('',entry('name','figurename'),'translation-region-script-and-missing-language-fallback',cache=[selected,None])
fs=[[raw('user/translations/en.yaml'),list(b'Figure: NEW\n')]]
parse('',['pair',entry('name','figurename'),['then',['files',fs],entry('name','figurename')]],'translation-cache-retention-after-file-change',cache=[selected_en,None])
parse('{german}',['pair',entry('name','figurename'),['then',['default'],entry('name','figurename')]],'term-effects-read-current-language-after-parser-change',cache=[selected_en,None])
for name in ['label','ref']:
 for old in [False,True]:parse('{id}',['or',['try',['then',entry('ref',name),['fail']]],['state']],'label-local-state-backtracking-and-raw-mode',initial()+[['verbatim',old]])
inputs=ask(['inputs'])
for name,value in inputs:parse(''.join(map(chr,value)),['rich'],'all-original-latex-reader-inputs-through-explicit-callback',source=name)
for file in fixtures:
 text=(root/'upstream/pandoc'/file).read_text()
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',text,re.M|re.S)):
  if re.search(r'(?:-f\s*|--from(?:=|\s+))latex\b',m[1]) and re.search(pattern,m[2]):parse(m[2],['rich'],'original-inline-command-inputs-through-explicit-callback',source=file,index=index)
# Preserve the original NFC check's five independently supplied column expectations.
nfc_assertions=0;nfc_failures=0
for file in ['test/data/NormalizationTest.txt','test/data/extra/NormalizationTest.txt']:
 for line_no,line in enumerate((root/'vendor/unicode-transforms-0.4.0.1'/file).read_text().splitlines(),1):
  if not line.strip() or line.startswith(('#','@')):continue
  fields=[[int(x,16) for x in value.split()] for value in line.split('#')[0].split(';')[:5]]
  assert len(fields)==5
  for index,points in enumerate(fields):
   expected=fields[1] if index<3 else fields[3]
   value=ask(['normalize',points]);passed=value==expected
   cs.append(dict(request=['normalize',points],expected=value,group='all-original-nfc-normalization-column-assertions',source=file,line=line_no,column=index,upstreamExpected=expected,upstreamPassed=passed))
   nfc_assertions+=1;nfc_failures+=not passed
marks=sorted({int(x) for x in re.findall(r'accent \'\\(\d+)\'',src)})
assert len(marks)==22
for start in range(0,0x110000,0x10000):add(['unicode-nfc',start,min(0x10000,0x110000-start),marks],'every-haskell-char-with-every-distinct-inline-accent-normalization')
for kind,n in itertools.product(range(4),[1500,10000,100000]):add(['stress',kind,n],'deep-finite-verbatim-inline-code-and-accent-inputs')
# Preserve every observation from the initial capture, byte-for-byte in meaning.
# Further probes exercise the general pinned NFC algorithm beyond accent pairs.
data=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/unicode-nfc-0.9.0.json').read_text())['data']
for cp,parts in data['decompositions']:
 for value in [[cp],parts,[cp,803,769],parts+[769,803]]:add(['normalize',value],'pinned-canonical-decompositions-and-blocking')
for cp,cc in data['classes']:
 for value in [[cp,803,769],[769,cp,803],[97,cp,803,769],[97,769,cp,803],[cp,cp,769]]:add(['normalize',value],'all-pinned-combining-classes-stable-reordering-and-blocking')
for a,b,c in data['compositions']+data['starterCompositions']:
 for value in [[a,b],[a,769,b],[a,803,b],[a,b,769,803]]:add(['normalize',value],'all-pinned-composition-pairs-and-intervening-marks')
for cp in range(0xac00,0xd7a4):
 offset=cp-0xac00;parts=[0x1100+offset//588,0x1161+(offset%588)//28]
 if offset%28:parts.append(0x11a7+offset%28)
 add(['normalize',parts],'all-hangul-syllables-from-jamo')
for a,b in itertools.product(range(0x1100,0x11c3),repeat=2):add(['normalize',[a,b]],'all-jamo-pairs-including-gap-and-empty-trailer')
rng=random.Random(9301301)
alphabet=[cp for cp,cc in data['classes']]+[cp for cp,parts in data['decompositions']]+list(range(0x1100,0x11c3))+[0xac00,0xac01,0xd7a3,97,101,0xd800,0x10ffff]
for _ in range(1024):add(['normalize',[rng.choice(alphabet) for _ in range(rng.randrange(1,65))]],'mixed-pinned-normalization-sequences')
if target.exists():
 with gzip.open(target,'rt') as old:
  next(old)
  for index,line in enumerate(old):assert cs[index]==json.loads(line),index
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),sourceLinks=links(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),registries=registries,originalReaderInputs=len(inputs),originalDocuments=len(docs),originalInlineLists=len(inline_lists),originalCommandFiles=len(fixtures),originalNfcAssertions=nfc_assertions,originalNfcFailures=nfc_failures,accentMarks=marks,translationFiles=len(translation_files),verifiedOriginalBuildFiles=len(files),exposedModules=exposed)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for c in [m,*cs]:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original LaTeX inline observations',m['caseHash'],'original NFC predicate failures',nfc_failures,flush=True)
