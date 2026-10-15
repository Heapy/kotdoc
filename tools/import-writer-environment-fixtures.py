#!/usr/bin/env python3
"""Observe pinned original Pandoc date/language setup; never use Kotlin for expectations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-writer-environment-reference';target=root/'core/src/test/resources/writer-environment/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_WRITER_ENVIRONMENT_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
s=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text();w=(root/'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs').read_text();t=(root/'upstream/pandoc/src/Text/Pandoc/Translations.hs').read_text()
def region(s,a,b):return s[s.index(a):s.index(b,s.index(a))]
projection=(folder/'Text/Pandoc/Shared.hs').read_text();writer=(folder/'SourceWriterEnvironment.hs').read_text()
for body in [region(s,'normalizeDate ::','--\n-- Pandoc block'),region(s,'stringify ::','-- | Bring all regular')]:assert body in projection
assert ('makeCanonical ::'+(root/'tools/haskell-common-state-reference/Text/Pandoc/Shared.hs').read_text().split('makeCanonical ::',1)[1]) in projection
for body in [region(w,'lookupMetaString ::','-- | Tries to convert'),region(w,'setupTranslations ::','-- True if the string'),region(t,'setTranslations ::','-- | Load term map.')]:assert body in writer
sources=['tools/import-writer-environment-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Time-BSD-2-Clause.txt','core/src/test/resources/native/upstream.json','core/src/test/resources/lang/data.jsonl.gz','vendor/unicode-collation-0.1.3.7/src/Text/Collate/Lang.hs']
sources+=['tools/haskell-common-state-reference/'+n for n in ['Text/Pandoc/Class/PandocMonad.hs','Text/Pandoc/Shared.hs','Citeproc.hs']]
sources+=['upstream/pandoc/src/Text/Pandoc/'+n+'.hs' for n in ['Shared','Writers/Shared','Class/CommonState','Class/PandocMonad','Logging','MediaBag','MIME','URI','UTF8','Error','Translations','Translations/Types']]
sources+=['upstream/pandoc/test/Tests/'+n+'.hs' for n in ['Shared','Writers/HTML','Writers/Docx','Writers/OOXML']]
for base in [folder,root/'vendor/time-1.14']:
 sources+=sorted(str(p.relative_to(root)) for p in base.rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
originals=[f for f in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if f['accepted']]
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert {c['source'] for c in cs if c['group']=='original-pandoc-metadata'}=={f['path'] for f in originals}
 assert sum(c['group']=='year-range' for c in cs)==30036
 assert sum(c['group']=='all-two-digit-years' for c in cs)==1200
 assert len([c for c in cs if c['group']=='report-and-cache-order'])==1800
 print('Verified',len(cs),'original writer environment/date observations,',len(originals),'original Pandoc documents and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def add(req,group,**extra):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 cs.append(dict(request=req,expected=json.loads(line),group=group,**extra))
 if len(cs)%10000==0:print('Captured',len(cs),'writer environment observations',flush=True)
def cp(s):return list(map(ord,s)) if isinstance(s,str) else s
def date(s,group='date',**extra):add(['date',cp(s)],group,**extra)
def tag(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
def doc(meta):return {'pandoc-api-version':[1,23,1,2],'meta':meta,'blocks':[]}
def setup(meta,cache=None,verbosity=1,callback=0,group='setup',**extra):add(['setup',doc(meta),cache,verbosity,callback],group,**extra)
for f in originals:
 setup(f['json']['meta'],group='original-pandoc-metadata',source=f['path'])
 for key,value in f['json']['meta'].items():setup({'lang':value},group='original-metadata-representations',source=f['path'],key=key)
# All original source Lang unit-test inputs are integrated through metadata.
with gzip.open(root/'core/src/test/resources/lang/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  c=json.loads(line)
  if c['request'][0]=='original':setup({'lang':tag('MetaString',c['expected']['input'])},group='original-lang-tests')
# Date sources are passed directly to the native helper, not reformatted in Kotlin.
def strings(v):
 if isinstance(v,str):yield v
 elif isinstance(v,list):
  for x in v:yield from strings(x)
 elif isinstance(v,dict):
  if v.get('t') in ['MetaString','Str','Code','Math']:yield from strings(v.get('c'))
  else:
   for key,x in v.items():
    if key!='t':yield from strings(x)
for f in originals:
 if 'date' in f['json']['meta']:
  for value in strings(f['json']['meta']['date']):date(value,'original-date-metadata',source=f['path'])
for y in range(0,10012):
 for s in [str(y),f'{y:04}-01-01',f'{y:04}0101']:date(s,'year-range')
for y,m,d in itertools.product([1600,1601,1699,1700,1800,1900,1968,1969,1970,1999,2000,2004,2068,2069,2100,2400,9999,10000],range(0,14),range(0,34)):
 date(f'{y}-{m:02}-{d:02}','calendar-validity')
for y,m,d in itertools.product(range(100),[1,2,12],[1,28,29,31]):date(f'{m:02}/{d:02}/{y:02}','all-two-digit-years')
months=['January','February','March','April','May','June','July','August','September','October','November','December']
for m,d,y,case in itertools.product(months,[0,1,9,10,28,29,30,31,32],[1600,1601,1900,2000,9999,10000],range(3)):
 name=[m,m.upper(),m.lower()][case]
 for s in [f'{d:02} {name[:3]} {y}',f'{d} {name} {y}',f'{name[:3]}. {d}, {y}',f'{name} {d}, {y}']:date(s,'month-names-and-padding')
spaces=['',' ','  ','\t','\n','\r\n','\v','\f','\x85','\xa0','\u1680','\u180e','\u2000','\u200b','\u2028','\u2029','\u202f','\u205f','\u3000']
for sp,s in itertools.product(spaces,['2024-01-02','20240102','202401','2024','01/02/69','01 Jan 2024','Jan. 2, 2024','January 2, 2024']):
 for value in [sp+s,s+sp,sp+s+sp]:date(value,'edge-whitespace')
 for value in [f'2024-{sp}01-02',f'01{sp}Jan{sp}2024',f'January{sp}2,{sp}2024',f'Jan.{sp}2,{sp}2024',f'-{sp}2024-01-02']:
  date(value,'internal-whitespace')
for n in [2**63-1,2**63,2**64-1,2**64,2**64+1,2**64+31,2**64+32,2**128+1,10**400,10**400+1]:
 for s in [f'January {n}, 2024',f'{n} January 2024',f'{n}-01-01',str(n)]:date(s,'integer-overflow')
for s in ['', '0','01','99','0001','00001601','-0','-0000','-1601','+2024','2024-1-01','2024-01-1','2024-02-30','2024 01 01','2024/01/01','01/02/2024','01/02/0024','69/01/02','01 jan. 2024','January 1st, 2024','ſeptember 1, 2024','Aprıl 1, 2024','APRİL 1, 2024','2024\x00','2024-01-01T00:00:00Z',[0xd800],[0xdc00],[0xd800,0xdc00],cp('Jan. 1, 2024')+[0xd800]]:date(s,'malformed-and-unicode')
rng=random.Random(0x301214)
for _ in range(3000):
 s=rng.choice(['2024-01-02','02 Jan 2024','January 2, 2024','20240102','01/02/68'])
 for _ in range(rng.randrange(1,4)):
  at=rng.randrange(len(s)+1);s=s[:at]+rng.choice(['',' ', '\t','0','-','/','A','\x00','é','\u2000'])+s[at+(rng.randrange(2) if at<len(s) else 0):]
 date(s,'mutated-dates')
strval=lambda s:tag('MetaString',s)
values=[None,strval(''),strval('en'),strval('en-US'),strval('de'),strval('zh-Hans-CN'),strval('!invalid'),strval(' invalid'),strval('é'),strval('en with garbage'),strval('EN_us'),strval('en--x'),tag('MetaBool',True),tag('MetaBool',False),tag('MetaList',[]),tag('MetaMap',{}),tag('MetaList',[strval('de')]),tag('MetaInlines',[]),tag('MetaInlines',[tag('Str','en-US')]),tag('MetaBlocks',[tag('Para',[tag('Str','en'),tag('SoftBreak'),tag('Str','ignored')])])]
cache_default=[cp('en'),cp('US'),None,[],[],[]]
caches=[None,['en-US',{'Figure':'loaded'}],[cache_default,{'Figure':'default'}],['de',{}],['en',None]]
for value,cache,verbosity,callback in itertools.product(values,caches,range(3),range(6)):
 setup({} if value is None else {'lang':value},cache,verbosity,callback,group='report-and-cache-order')
# Every inline constructor is represented by original document nodes; original
# stringify/Walk still supplies the expected language text and all effects.
inlines={'Str','Space','SoftBreak','LineBreak','Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps','Quoted','Cite','Code','Math','RawInline','Link','Image','Note','Span'}
seen=set()
def visit(v,source):
 if isinstance(v,dict):
  if v.get('t') in inlines:
   key=json.dumps(v,sort_keys=True)
   if key not in seen:
    seen.add(key);setup({'lang':tag('MetaInlines',[v])},group='original-inline-stringification',source=source)
  for x in v.values():visit(x,source)
 elif isinstance(v,list):
  for x in v:visit(x,source)
for f in originals:visit(f['json'],f['path'])
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),oracle='Byte-exact pinned Shared normalizeDate and Writers.Shared setupTranslations/lookupMetaString; original TimeLib 1.14 and state/report; observable IO instance only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'writer environment/date observations; SHA',m['caseHash'],flush=True)
