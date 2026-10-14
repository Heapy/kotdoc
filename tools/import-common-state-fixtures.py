#!/usr/bin/env python3
"""Native CommonState and byte-exact PandocMonad state/log definitions; no Kotlin."""
import argparse, atexit, collections, gzip, hashlib, itertools, json, os, random, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
target = root/'core/src/test/resources/common-state/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle', default=os.environ.get('KOTDOC_COMMON_STATE_REFERENCE'))
p.add_argument('--check-source', action='store_true')
args = p.parse_args()
source = (root/'upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs').read_text()
projection = (root/'tools/haskell-common-state-reference/Text/Pandoc/Class/PandocMonad.hs').read_text()
for start,end in [('  getCommonState ::','\n-- * Functions defined'),('setVerbosity ::','-- | Get the current UTC time.'),('toLang ::','-- | Specialized version'),('setUserDataDir ::',None)]:
 a = source.index(start)
 b = source.index(end,a) if end else source.index('\n\n',source.index('getUserDataDir =',a))
 assert source[a:b] in projection, start
shared = (root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text()
projected = (root/'tools/haskell-common-state-reference/Text/Pandoc/Shared.hs').read_text()
for start in ['makeCanonical ::','tshow ::']:
 a=shared.index(start);b=shared.index('\n\n',a)
 assert shared[a:b] in projected
assert (root/'tools/haskell-common-state-reference/Citeproc.hs').read_bytes() == (root/'tools/haskell-error-reference/Citeproc.hs').read_bytes()
assert projection.count('getCommonState ::') == 1 and 'finally' not in projection
sources = ['tools/import-common-state-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Unicode-collation-BSD-2-Clause.txt','upstream/pandoc/src/Text/Pandoc/Class/CommonState.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocIO.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocPure.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/Logging.hs','upstream/pandoc/src/Text/Pandoc/Error.hs','upstream/pandoc/src/Text/Pandoc/MediaBag.hs','upstream/pandoc/src/Text/Pandoc/MIME.hs','upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/src/Text/Pandoc/Translations/Types.hs','upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-log.lua','core/src/test/resources/logging/data.jsonl.gz','core/src/test/resources/media-state/data.jsonl.gz','vendor/unicode-collation-0.1.3.7/src/Text/Collate/Lang.hs','vendor/citeproc-0.14.1/src/Citeproc/Types.hs']
for folder in ['tools/haskell-common-state-reference','vendor/ghc-common-state-9.12.2']:
 sources += sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file())
def hashes(): return {f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 names=set()
 for c in cs:
  def scan(ops):
   for o in ops:
    if o[0]=='report':names.add(o[1][0])
    if o[0] in ['silent','caught']:scan(o[1])
  scan(c['request'][1])
 assert len(names)==54, len(names)
 assert set(c['original'] for c in cs if c.get('original'))=={'silence prevents main-log changes','silence returns reported messages','silence preserves application results'}
 print(f'Verified {len(cs)} native common-state observations and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(ops,mode=0,group='state',**flags):
 req=['run',ops,mode]
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,req
 cs.append(dict(request=req,expected=json.loads(line),group=group,**flags))
 if len(cs)%500==0:print('Captured',len(cs),'common-state observations',flush=True)
def cp(s):return list(map(ord,s)) if isinstance(s,str) else s
def info(s):return ['ScriptingInfo',cp(s),None]
def warn(s):return ['ScriptingWarning',cp(s),None]
def report(m):return ['report',m]
snap=['snapshot'];log=['getLog'];err=['failPandoc'];host=['failHost']
initial=[['verbosity',2],report(warn('before'))]
add([],group='default')
add(initial+[['silent',[report(info('Just so you know'))],None],log],group='original',original='silence prevents main-log changes')
add(initial+[['silent',[report(info('Just so you know'))],None]],group='original',original='silence returns reported messages')
add(initial+[['silent',[],[8,13]]],group='original',original='silence preserves application results')
messages={}
with gzip.open(root/'core/src/test/resources/logging/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  a=json.loads(line)['request']
  if a[0]=='message':messages.setdefault(a[1][0],a[1])
assert len(messages)==54
for m,v,mode in itertools.product(messages.values(),range(3),range(5)):
 add([['verbosity',v],['log',[warn('old'),info('older')]],snap,report(m),snap,log],mode,group='report')
paths=['','.','./','/','//','C:\\folder','é/漢🙂','foo\0bar',[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000],['raw']]
paths=paths[:-1]
for p,q in itertools.product(paths,paths):
 add([['user',cp(p)],['output',cp(q)],['resource',[cp(q),cp(p)]],['input',[cp(p),cp(q)]],snap],group='paths')
add([['user',[]],['output',[]],['user',None],['output',None],['resource',[]],['input',[]],snap],group='paths')
with gzip.open(root/'core/src/test/resources/media-state/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  a=json.loads(line)['request']
  if a[0]=='uri':add([['input',[a[1],cp('http://ignored.invalid/second')]],snap],group='url')
keys=['A','a','Host','host','X-Test','x-test','', '\0', 'é', [0xd800],[0xdc00],[0xd800,0xdc00]]
for k,v in itertools.product(keys,['','value','\0漢🙂',[0xd800,0xdc00]]):
 h=[[cp('A'),cp('first')],[cp(k),cp('old1')],[cp(k),cp('old2')],[cp('a'),cp('lower')]]
 add([['headers',h],snap,['header',cp(k),cp(v)],snap],group='headers')
for old,new,mid in itertools.product([False,True],[False,True],[None,0,1]):
 add([['certificate',old],['manager',mid],snap,['certificate',new],snap,['certificate',new],snap],group='manager')
record=[['user',cp('original-dir')],['output',[0xd800,0xdc00]],['input',[cp('http://h/book?query#frag'),cp('second')]],['resource',[cp('a'),cp('b')]],['headers',[[cp('A'),cp('B')]]],['manager',1],['translations',cp('en-US'),{'Figure':'  Figure  '}],['media',cp('a.png'),cp('image/png'),[0,255,1]],['traceFlag',True]]
mutate=[['user',cp('action-dir')],['output',cp('action-output')],['input',[cp('https://h/changed?q#f')]],['headers',[[cp('changed'),cp('header')]]],['resource',[cp('changed')]],['certificate',True],['translations',cp('de-DE'),None],['media',cp('../changed.png'),None,[49,50,51]],['traceFlag',False]]
actions=[[],[report(info('first')),report(warn('second'))],mutate+[report(warn('changed'))],mutate+[err],mutate+[host],mutate+[['verbosity',2],report(info('visible'))], [['silent',[report(warn('inner'))],[8,13]],report(warn('outer'))], [['caught',[['silent',mutate+[err],None]]],report(warn('after-caught'))]]
for v,mode,action in itertools.product(range(3),range(5),actions):
 add(record+[['verbosity',v],report(warn('before')),['caught',[['silent',action,{'returned':[8,13]}]]],snap,log],mode,group='silence')
for depth,ending in itertools.product(range(1,9),[[],[err],[host]]):
 ops=mutate+[report(info('deep'))]+ending
 for i in range(depth):ops=[['silent',[report(warn(str(i)))]+ops,[i]]]
 add(record+initial+[['caught',ops],snap,log],group='nested')
for cache in [None,{}, {'Figure':'  Fig  ','Chapter':' Chapter '}, {'References':'漢🙂'}]:
 for tag in [None,'en','en-US','de-DE-u-co-phonebk']:
  add([['translations',None if tag is None else cp(tag),cache],snap,['silent',mutate,None],snap],group='translations')
for value in [None,'','en','en-US','und','EN-us','invalid tag','a','é','en-GB-u-co-phonebk',[0xd800],[0xd800,0xdc00]]:
 for v in range(3):add([['verbosity',v],['toLang',None if value is None else cp(value)],snap,log],group='lang')
for val in ['','hello','a\0b','\0\0','\n','\r\n','漢🙂',[0xd800],[0xd800,0xdc00]]+[chr(i) for i in range(128)]:
 for flag in [False,True]:add([['traceFlag',flag],['trace',cp(val)],snap],group='trace')
rng=random.Random(0x20162020)
simple=[snap,log,report(info('random')),report(warn('random'))]+mutate+record+[['verbosity',n] for n in range(3)]+[['manager',n] for n in [None,0,1]]+[['traceFlag',False]]
def gen(depth=0):
 ops=[]
 for _ in range(rng.randrange(2,18)):
  if depth<3 and rng.random()<.15:ops.append(['silent',gen(depth+1),rng.choice([None,8,[8,13],{'value':True}])])
  elif rng.random()<.06:ops.append(['caught',[rng.choice([err,host])]])
  else:ops.append(rng.choice(simple))
 return ops
for _ in range(800):add(gen(),rng.randrange(5),group='sequences')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),oracle='Unchanged CommonState and byte-exact selected PandocMonad definitions, original ExceptT/StateT error order, native HTTP manager identity and GHC traceIO; frozen GHC 9.12.2 dependencies')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'common-state observations; SHA',m['caseHash'],flush=True)
