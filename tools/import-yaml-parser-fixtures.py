#!/usr/bin/env python3
"""Original libyaml parser events/state; does not run Kotlin or reuse expected output."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/yaml-parser/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_YAML_PARSER_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-yaml-parser-fixtures.py','licenses/Libyaml-MIT.txt','core/src/test/resources/yaml-scanner/data.jsonl.gz','upstream/pandoc/src/Text/Pandoc/Translations.hs']
for folder in ['tools/haskell-yaml-parser-reference','vendor/libyaml-clib-0.2.5','vendor/yaml-0.11.11.2','vendor/libyaml-0.1.4']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file())
def hashes():return {f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 events=set();states=set();errors=set()
 for c in cs:
  for s in c['expected']['steps']:events.add(s['event']['type']);states.add(s['state']['state'])
  errors.add(c['expected']['error']['type'])
 assert events==set(range(11)),events
 assert {0,2,3,4}<=errors,errors
 # States 6/7 are legacy node dispatcher entries not assigned by this parser's
 # normal productions; the corresponding node routines run directly instead.
 assert set(range(24))-states=={6,7},states
 print(f'Verified {len(cs)} original parser observations, all event families/state productions and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def add(bs,chunks=(),encoding=0,limit=500,group='parser',**flags):
 if isinstance(bs,str):bs=bs.encode()
 req=['parse',list(bs),list(chunks),encoding,limit]
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 cs.append(dict(request=req,expected=json.loads(line),group=group,**flags))
 if len(cs)%1000==0:print('Captured',len(cs),'parser observations',flush=True)
with gzip.open(root/'core/src/test/resources/yaml-scanner/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  c=json.loads(line);a=c['request']
  flags={k:c[k] for k in ['path','literal','index','example'] if k in c}
  add(a[1],a[2],a[3],a[4],group='scanner-'+c['group'],**flags)
for doc in ['','---','...','---\n...','---\n---','...\n...\n---','---\n...\n...\n---','%YAML 1.1\n---','%YAML 1.2\n---','%YAML 1.0\n---','%YAML 1.3\n---','%YAML 2.0\n---','%YAML 1.2\n%YAML 1.2\n---','%YAML 1.2\nvalue','%TAG !e! tag:example/\n---\n!e!name value','%TAG !e! one\n%TAG !e! two\n---','%TAG ! changed:\n---\n!foo value','%TAG !! changed:\n---\n!!str value','%TAG !e! tag:one/\n---\n!e!a value\n...\n---\n!e!a value']:
 for ending in ['','\n','\n...\n']:add(doc+ending,group='documents-directives')
properties=['','!','!<foo>','!<>','!!str','!!seq','!!map','!!null','!unknown!name','!<%00>','!%00','!<%c0%80>','&a','&a !','! &a','&a !!str','!!str &a','&a &b','!!str !!int','*a','&a *a']
nodes=['','value','\'quoted\'','"quoted"','[]','{}','- first\n- second','key: value','[one, two]','{key: value}']
for prop,node in itertools.product(properties,nodes):
 for context in ['{}','key: {}','- {}','[{}]']:
  add(context.replace('{}',prop+' '+node),group='node-properties')
collection_inputs=['[?]','[?:]','[? : ]','[? key]','[? key : value]','[key: value]','[key:]','[: value]','[,]','[one two]','[one,]','[one,,two]','[? key, next]','[? : value, next]','{key}','{key:}','{:value}','{?}','{?:}','{? key: value}','{key, other}','{key: value,}','{key: value other: next}','{key: value,, next: end}','{[a,b]: value}','{? [a,b]: value}','-\n- value\n-','key:\n-\n- value\n-\nother: next','?\n: value\n? key\n:','key:\nother:','key: {nested}\nnext: [last]']
for text in collection_inputs:
 for prefix in ['', '---\n', '&anchor ', '!!seq ']:add(prefix+text,group='collection-productions')
for level in [1,2,8,32,100,300]:
 for opening,closing in [('[',']'),('{key: ','}'),('[? key: ',']')]:
  add(opening*level+'value'+closing*level,limit=5*level+20,group='nested-stacks')
rng=random.Random(0x251375)
def node(depth=0):
 if depth>3 or rng.random()<.45:return rng.choice(['value','null','true','1','\'string\'','"\\u0000"','!custom scalar','&a scalar','*a'])
 if rng.random()<.5:return '['+', '.join(node(depth+1) for _ in range(rng.randrange(5)))+']'
 return '{'+', '.join('key'+str(i)+': '+node(depth+1) for i in range(rng.randrange(5)))+'}'
for _ in range(500):add(node(),group='generated-nodes')
for text in ['key: [value]','---\nvalue\n...','%TAG !e! tag:example/\n---\n!e!name value','{key: value}','- one\n- two','&a !!str text']:
 for size in range(len(text)+1):add(text,[1]*size+[-1],group='read-failure-schedules')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),oracle='Complete unchanged libyaml 0.2.5 api/reader/scanner/parser; events and actual internal stacks/tags/token peek state after every parse call, frozen GHC 9.12.2 transport')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'parser observations; SHA',m['caseHash'],flush=True)
