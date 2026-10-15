#!/usr/bin/env python3
"""Actual pinned Pandoc translations module, with observable file/state effects."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/translation-loading/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_TRANSLATION_LOADING_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=(root/'upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs').read_text()
projection=(root/'tools/haskell-translation-loading-reference/Text/Pandoc/Class.hs').read_text()
for start,end in [('  getCommonState ::','\n-- * Functions defined'),('report ::','-- | Run an action'),('toTextM ::',None)]:
 a=original.index(start);b=original.index(end,a) if end else original.index('\n-- |',original.index('findDecodingError =',a))
 assert original[a:b] in projection,start
assert 'getUserDataDir = getsCommonState stUserDataDir' in projection
assert (root/'tools/haskell-translation-loading-reference/Citeproc.hs').read_bytes()==(root/'tools/haskell-common-state-reference/Citeproc.hs').read_bytes()
assert (root/'tools/haskell-translation-loading-reference/Text/Pandoc/Shared.hs').read_bytes()==(root/'tools/haskell-common-state-reference/Text/Pandoc/Shared.hs').read_bytes()
sources=['tools/import-translation-loading-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Yaml-BSD-2-Clause.txt','licenses/Unicode-collation-BSD-2-Clause.txt','upstream/pandoc/src/Text/Pandoc/Translations.hs','upstream/pandoc/src/Text/Pandoc/Translations/Types.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs','upstream/pandoc/src/Text/Pandoc/Class/CommonState.hs','upstream/pandoc/src/Text/Pandoc/Data.hs','upstream/pandoc/src/Text/Pandoc/Logging.hs','upstream/pandoc/src/Text/Pandoc/Error.hs','upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/src/Text/Pandoc/MediaBag.hs','upstream/pandoc/src/Text/Pandoc/MIME.hs','upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/test/Tests/Writers/Docx.hs','upstream/pandoc/test/Tests/Writers/OOXML.hs','vendor/unicode-collation-0.1.3.7/src/Text/Collate/Lang.hs','core/src/test/resources/yaml-values/data.jsonl.gz','core/src/test/resources/lang/data.jsonl.gz']
for folder in ['tools/haskell-translation-loading-reference','upstream/pandoc/data/translations','vendor/yaml-0.11.11.2','vendor/libyaml-0.1.4','vendor/libyaml-clib-0.2.5']:
 # cabal freeze may create solver caches beside its project; those are not sources.
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():
 return {f:hashlib.sha256(os.readlink(root/f).encode() if (root/f).is_symlink() else (root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 paths={c['path'] for c in cs if c.get('group')=='original-yaml'}
 assert paths=={str(p.relative_to(root)) for p in (root/'upstream/pandoc/data/translations').glob('*.yaml')}
 assert paths=={c['path'] for c in cs if c.get('group')=='original-file-runtime'}
 assert sum(c['group']=='utf8-byte-pairs' for c in cs)==65536
 assert {c['expected']['status'] for c in cs if c['request'][0]=='run'}>={'success','host','PandocSomeError "callback-error"'}
 print(f'Verified {len(cs)} original translation loading/text/error observations, all {len(paths)} translation files and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def add(req,group,**flags):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 cs.append(dict(request=req,expected=json.loads(line),group=group,**flags))
 if len(cs)%5000==0:print('Captured',len(cs),'translation loading observations',flush=True)
def cp(s):return [ord(c) for c in s] if isinstance(s,str) else s
def read(s,group='yaml-read',**flags):add(['read',cp(s)],group,**flags)
def content(s,ops=()):return ['bytes',list(s.encode()) if isinstance(s,str) else list(s),list(ops)]
def file(path,*responses):return [cp(path),list(responses)]
def run(ops,files=(),mode=0,group='runtime',**flags):add(['run',ops,list(files),mode],group,**flags)
get=['get'];snap=['snapshot'];all_terms=[['term',i] for i in range(22)]
master=[]
for path in sorted((root/'upstream/pandoc/data/translations').glob('*.yaml')):
 relative=str(path.relative_to(root));read(path.read_text(),'original-yaml',path=relative)
 master.append(file('translations/'+path.name,content(path.read_bytes())))
 run([['set',path.stem],get]+all_terms+[get,snap],master,group='original-file-runtime',path=relative)
run([['set','en-US'],get]+all_terms,master,group='original-OOXML-language-selection',original='Tests.Writers.OOXML sets en-US')
# Fresh readTranslations results for syntax inputs; never reuse previous expected values.
seen=set()
with gzip.open(root/'core/src/test/resources/yaml-values/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  c=json.loads(line);a=c['request']
  if a[0] in ['exception','warning'] or a[1]!='bytes':continue
  s=bytes(a[2]).decode('utf-8','replace')
  if s in seen:continue
  seen.add(s);read(s,'syntax-'+c['group'])
terms=['Abstract','Appendix','Bibliography','Cc','Chapter','Contents','Encl','Figure','Glossary','Index','Listing','ListOfFigures','ListOfTables','Page','Part','Preface','Proof','References','See','SeeAlso','Table','To']
for key,scalar in itertools.product(terms+['unknown','figure','',"'\\",'true'],['text','null','true','1','0x10','!!str true','" true "','[]','{}','|\n  text\n','>\n  text\n']):read(json.dumps(key)+': '+scalar,'typed-schema')
for s in ['','\n','# comments','{}','--- {}\n--- {Figure: valid}','--- {Figure: valid}\n--- null','--- {Figure: valid}\n--- {unknown: invalid}','--- {Figure: valid}\n--- [','Figure: &a " true "\nTable: *a','Figure: &a true\nTable: *a','Figure: one\nFigure: two','<<: {Figure: merged}\nFigure: explicit','--- &a {Figure: old}\n--- *a','--- {Figure: good}\n--- {Figure: bad}\n--- [','? [complex]\n: value']:
 read(s,'documents-and-aliases')
for raw in [[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000],cp('Figure: \"é\"')]:read(raw,'raw-text')
langs=['en','en-US','zh-Hans','zh-Hans-CN','de-DE-u-co-phonebk','sl-rozaj','fr-CA-x-private','und','',[[233],None,None,[],[],[]], [cp('en'),[],[],[],[],[]], [cp('x/../y'),cp('script'),cp('region'),[],[],[]]]
known_langs={json.dumps(l,sort_keys=True) for l in langs}
with gzip.open(root/'core/src/test/resources/lang/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  a=json.loads(line)['request']
  if a[0]=='record' and len(langs)<100:
   key=json.dumps(a[1],sort_keys=True)
   if key not in known_langs:langs.append(a[1]);known_langs.add(key)
# Each fallback rank, malformed first documents, valid empty tables and read failures.
texts=['Figure: full','Figure: scripted','Figure: base','','# comment','[]','null','Figure: true','--- {Figure: valid}\n--- null','Figure: ""','Figure: "  trimmed  "']
paths=['translations/zh-Hans-CN.yaml','translations/zh-Hans.yaml','translations/zh.yaml']
for first,second,third in itertools.product(texts[:6],texts[:6],texts[:6]):
 run([['set','zh-Hans-CN'],get,get]+all_terms+[snap],[file(p,content(t)) for p,t in zip(paths,[first,second,third])],group='fallback-content')
for l in langs:
 for table in [None,{}, {'Figure':'  cached  '}, {'Figure':''}]:
  run([['cache',l,table],snap,['set',l],get,get,['set','de'],get,snap],master,group='cache-and-language-equality')
 run([['set',l],get,get,snap],group='candidate-deduplication')
# Equal rendering is insufficient for cache equality: compare all six Lang fields.
records=[
 [cp('en'),None,None,[],[],[]],
 [cp('en'),cp('Latn'),None,[],[],[]],
 [cp('en-Latn'),None,None,[],[],[]],
 [cp('en'),None,None,[cp('Latn')],[],[]],
 [cp('en'),None,None,[],[[cp('u'),[[cp('co'),cp('phonebk')]]]],[]],
 [cp('en'),None,None,[],[],[cp('private')]],
]
for a,b in itertools.product(records,records):
 run([['cache',a,{'Figure':'cached'}],['set',b],snap,get,snap],master,group='record-cache-equality')
for mode,verbosity in itertools.product(range(6),range(3)):
 for ops in [[get,get],all_terms,[get,['set','en-US'],get], [['caught',[get]],snap,[ 'term',7 ]]]:
  run([['verbosity',verbosity],['set','en-US']]+ops,[file('translations/de.yaml',content('Figure: German'))],mode,group='log-callbacks')
mutations=[[],[['user',cp('changed')],['headers',[[cp('A'),cp('B')]]],['resources',[cp('one'),cp('two')]],['set','de']], [['cache','de',{'Figure':'callback'}]], [['report',cp('file callback')]], [['set','de'],get], [['failTyped',cp('nested-error')]], [['failHost']]]
for hooks,kind in itertools.product(mutations,['bytes','typed','host']):
 response=content('Figure: loaded',hooks) if kind=='bytes' else ['typed',cp('file-error'),hooks] if kind=='typed' else ['host',hooks]
 run([['set','en-US'],['caught',[get]],get,snap],[file('translations/en-US.yaml',response,content('Figure: after')),file('translations/en.yaml',content('Figure: base')),file('translations/de.yaml',content('Figure: German'))],group='file-effect-order')
for text in ['Figure: valid','\ufeffFigure: valid','Figure:\r\n  text\r\n','Figure: \"bad\rvalue\"','Figure: valid\n--- null']:
 for prefix in [[],[0xef,0xbb,0xbf], [13],[0xef,0xbb,0xbf,13]]:
  run([['set','en'],get,get,snap],[file('translations/en.yaml',content(prefix+list(text.encode())))],group='file-utf8-bom-cr')
for b in range(65536):add(['text',cp('input.yaml'),[b>>8,b&255]],'utf8-byte-pairs')
bad=[[0x80],[0xc0],[0xe0,0x80,0x80],[0xed,0xa0,0x80],[0xf0,0x80,0x80,0x80],[0xf4,0x90,0x80,0x80],[0xe2,0x82],[0xf0,0x90,0x80],[0xe2,0x28,0xa1],[0xff]]
for path,prefix,suffix in itertools.product(['','file.yaml','漢🙂',cp(chr(0xd800)+chr(0xdc00))],[[],[13],[0xef,0xbb,0xbf],[0xef,0xbb,0xbf,13],list(b'abc\r\n')],bad):
 add(['text',cp(path),prefix+suffix],'utf8-errors-offset-path')
 for language in ['en','en-US']:
  run([['set',language],get,get,snap],[file('translations/'+language+'.yaml',content(prefix+suffix))],group='utf8-read-fallback')
rng=random.Random(0x20172024)
for _ in range(500):
 lang=rng.choice(['en','en-US','zh-Hans-CN','de'])
 ops=[rng.choice([get,snap,['set',lang],['set','de'],['term',rng.randrange(22)],['cache',lang,rng.choice([None,{}, {'Figure':'cached'}])]]) for _ in range(rng.randrange(1,15))]
 run(ops,[file('translations/en.yaml',content(rng.choice(texts))),file('translations/zh.yaml',content(rng.choice(texts))),file('translations/de.yaml',content('Figure: Deutsch'))],rng.randrange(3),group='generated-operations')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),oracle='Complete unchanged pinned Text.Pandoc.Translations and original selected state/report/toTextM definitions; controlled readDataFile effect boundary, actual yaml/Aeson, frozen GHC 9.12.2')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'translation loading observations; SHA',m['caseHash'],flush=True)
