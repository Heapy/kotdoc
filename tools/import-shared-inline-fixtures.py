#!/usr/bin/env python3
"""Observe original Shared inline/metadata functions; no Kotlin-produced expectations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-shared-inline-reference';target=root/'core/src/test/resources/shared-inline/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_SHARED_INLINE_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=(root/'upstream/pandoc/test/Tests/Shared.hs').read_text();start=original.index('stringifyInlinesMatchesStringify ::');end=original.index('\nmakeSectionsIsIdempotent ::',start)
assert (folder/'OriginalSharedProperty.hs').read_text().split('import Text.Pandoc.Shared (stringifyInlines, stringify)\n')[1]==original[start:end].rstrip()+'\n'
fixtures=subprocess.check_output(['git','ls-files','test/*.native','test/**/*.native'],cwd=root/'upstream/pandoc').decode().splitlines()
sources=['tools/import-shared-inline-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','upstream/pandoc/test/Tests/Shared.hs','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal']+['upstream/pandoc/'+x for x in fixtures]
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'upstream/pandoc/src',root/'upstream/pandoc-types/src',root/'vendor/text-2.1.2'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts and '__pycache__' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
modules=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX']
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
for name in modules:
 line='                   '+name+',\n';assert changed.count(line)==1;changed=changed.replace(line,'')
needle='  exposed-modules: Text.Pandoc,\n';assert changed.count(needle)==1
changed=changed.replace(needle,needle+''.join('                   '+name+',\n' for name in modules))
tracked=[x for x in subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0') if x]
info=dict(exposedModules=modules,originalCabalHash=hashlib.sha256(base.encode()).hexdigest(),exposedCabalHash=hashlib.sha256(changed.encode()).hexdigest(),verifiedOriginalBuildFiles=len(tracked),originalPropertyTrials=1000,originalPropertySeed=314159)
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs)) and all(m[k]==v for k,v in info.items())
 print('Verified',len(cs),'original Shared inline observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared,'--oracle and --prepared required'
for name in tracked:
 expected=changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()
 assert (args.prepared/'pandoc'/name).read_bytes()==expected,name
control=subprocess.check_output([args.oracle,'--original-property'],cwd=root,text=True);assert '+++ OK, passed 1000 tests.' in control,control
print(control.strip(),flush=True)
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req[:2]
 return json.loads(line)
def add(req,group,**extra):
 cs.append(dict(request=req,expected=ask(req),group=group,**extra))
 if len(cs)%2000==0:print('Captured',len(cs),'Shared observations',flush=True)
def il(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
a0=['',[],[]];a1=['id',['one','two','one'],[['x','1'],['x','2'],['é','中']]];attrs=[a0,a1,['😀',['code'],[['',''],['k','v\n']]]]
strx=il('Str','x');space=il('Space');soft=il('SoftBreak');line=il('LineBreak')
leaf=[strx,il('Str',''),space,soft,line,il('Code',[a1,'a b']),il('RawInline',['html','<br>']),il('RawInline',['HTML','<br>']),il('RawInline',['html','raw']),il('Math',[{'t':'InlineMath'},'x']),il('Quoted',[{'t':'SingleQuote'},[strx]]),il('Quoted',[{'t':'DoubleQuote'},[]])]
for tag in ['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps']:leaf.append(il(tag,[space,strx,soft]))
leaf.extend([il('Span',[a1,[space,strx,soft]]),il('Link',[a1,[space,strx,soft],['url','title']]),il('Image',[a1,[space,strx,soft],['url','title']]),il('Note',[il('Para',[space,strx,soft])])])
citation=dict(citationId='cite',citationPrefix=[space,strx,soft],citationSuffix=[strx],citationMode={'t':'NormalCitation'},citationNoteNum=-9223372036854775808,citationHash=9223372036854775807)
leaf.append(il('Cite',[[citation],[space,strx,soft]]))
add(['span-tags'],'complete-html-span-like-elements')
for a in attrs:
 for x in [[]]+[[i] for i in leaf]:
  add(['inlines',a,x],'every-inline-constructor-and-attribute-form')
  for wrapper in range(9):add(['extract',wrapper,a,x],'caller-preserving-extract-spaces-and-empty-inputs')
# Pair/triple boundaries exercise groupBy's non-equivalence predicate and Builder meld.
tokens=leaf[:12]+[leaf[14],leaf[-1],leaf[-2],leaf[-5]]
for n in [2,3]:
 for i,x in enumerate(itertools.product(tokens,repeat=n)):add(['inlines',attrs[i%len(attrs)],list(x)],'all-plaintext-group-boundaries')
for x in itertools.product([space,soft,line,strx,leaf[1]],repeat=3):
 for wrapper in range(9):add(['extract',wrapper,a1,list(x)],'all-leading-trailing-space-softbreak-and-linebreak-combinations')
values=[il('MetaString',''),il('MetaString','new'),il('MetaBool',False),il('MetaInlines',[space,strx,soft]),il('MetaBlocks',[il('Para',[strx])]),il('MetaList',[]),il('MetaList',[il('MetaString','a'),il('MetaList',[il('MetaBool',True)])]),il('MetaMap',{'k':il('MetaString','v')})]
converted=[(0,v) for v in values]+[(1,'text'),(2,[0xd800,0xdc00,0x10000]),(3,True),(4,[space,strx,soft]),(5,[il('Para',[strx])]),(6,values),(7,{'k':values[0],'a':values[-1]})]
for old,(kind,value),key in itertools.product([None]+values,converted,['k','','é','😀']):
 m={'unrelated':values[-1]};m.update({key:old} if old is not None else {})
 add(['meta',key,kind,value,m],'all-metadata-conversions-list-flattening-and-preserved-fields')
for fixture in fixtures:
 doc=ask(['native',(root/'upstream/pandoc'/fixture).read_text()])
 add(['walk',doc],'all-native-document-inline-walk-and-query-orders',source=fixture)
 lists=ask(['inline-lists',doc])
 for i,x in enumerate(lists):add(['inlines',attrs[i%len(attrs)],x],'all-original-native-inline-lists',source=fixture)
for size in [0,1,2,3,4,6,8,10,12,16]:
 samples=ask(['samples',314159+size,size,100])
 for x in samples:
  add(['property',x],'original-shared-stringify-property-with-original-generators')
  assert cs[-1]['expected'] is True
  add(['inlines',a1,x],'original-pandoc-types-generator-inline-lists')
for cp in range(0,0x110000,65536):add(['unicode',cp,min(65536,0x110000-cp)],'all-haskell-char-text-string-code-and-space-witnesses')
for kind in range(3):
 for depth in ([1500,10000,100000] if kind<2 else [1500,5000]):add(['deep',kind,depth],'finite-deep-inline-block-note-walking-and-quoted-stringification')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),**info)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as file:
 with gzip.GzipFile(fileobj=file,mode='wb',mtime=0) as z:
  for value in [m,*cs]:z.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original Shared inline observations',m['caseHash'],flush=True)
