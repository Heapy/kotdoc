#!/usr/bin/env python3
"""Full original PandocError/CiteprocError/GHC IOError behavior; no Kotlin oracle."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/errors/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_ERROR_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
source=(root/'upstream/pandoc/src/Text/Pandoc/Error.hs').read_text()
decl=source.split('data PandocError =',1)[1].split('deriving (Show',1)[0]
ctors=[l.strip().lstrip('|').split() for l in decl.splitlines() if l.strip()];assert len(ctors)==33
s=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text();a=s.index('tshow ::');b=s.index('\n\n',a);assert s[a:b] in (root/'tools/haskell-error-reference/Text/Pandoc/Shared.hs').read_text()
s=(root/'vendor/citeproc-0.14.1/src/Citeproc/Types.hs').read_text();a=s.index('data CiteprocError =');b=s.index('\n\n-- |',a);assert s[a:b] in (root/'tools/haskell-error-reference/Citeproc.hs').read_text()
sources=['tools/import-error-fixtures.py','upstream/pandoc/src/Text/Pandoc/Error.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/test/test-pandoc.hs','licenses/pandoc-GPL-2.0.md','licenses/Citeproc-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt']
for folder in ['tools/haskell-error-reference','vendor/citeproc-0.14.1','vendor/ghc-errors-9.12.2','vendor/ghc-9.12.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file())
def hashes():return {f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['constructors']==[r[0] for r in ctors]
 assert set(c['request'][1][0] for c in cs if c.get('constructor'))==set(m['constructors'])
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 print(f'Verified {len(cs)} original error observations, all 33 constructors and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(*req,**flags):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 cs.append(dict(request=req,expected=json.loads(line),**flags))
 if len(cs)%2000==0:print('Captured',len(cs),'error observations',flush=True)
def ps(s):return list(map(ord,s)) if isinstance(s,str) else s
def io(t=7,l='',d='detail',n=None,f=None,h=None):return [t,ps(l),ps(d),n,None if f is None else ps(f),h]
def base(t):return {'Text':ps('value'),'Int':1,'Word8':1,'IOError':io(),'CiteprocError':['CiteprocXMLError',ps('value')]}[t]
texts=[ps(s) for s in ['', 'x','doc','pdf','DOC','PDF','epub','漢🙂',"a'\"\\\n\b\f",'\x0eH1','\x00','a\r\nb']]+[[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000],[0xfffd,0xfffd]]
messages=[[n,*[base(t) for t in ts]] for n,*ts in ctors]
for (name,*types),msg in zip(ctors,messages):
 for prec in [-2**63,-1,0,10,11,2**63-1]:add('error',msg,prec,constructor=True)
 add('handle',msg,constructor=True)
 for i,t in enumerate(types):
  if t=='Text':values=texts
  elif t=='Int':values=[-2**63,-10,-1,0,1,2**63-1]
  elif t=='Word8':values=list(range(256))
  elif t=='CiteprocError':values=[[c,s] for c,s in itertools.product(['CiteprocXMLError','CiteprocParseError','CiteprocLocaleNotFound'],texts)]
  else:continue
  for v in values:
   alt=[*msg];alt[i+1]=v;add('error',alt,11,field=True)
   add('handle',alt,field=True)
handles=[None,*[[kind,ps(name),r,w] for kind,name,r,w in itertools.product(['file','duplex'],['','file','other','漢🙂'],[0,1],[0,1])]]
ios=[]
for t in range(19):
 for loc,desc,filename,h in itertools.product(['','loc','漢🙂'],['','detail','x\n'],[None,'','file'],[None,handles[1]]):
  e=io(t,loc,desc,-2147483648,filename,h);ios.append(e);add('io',e,11)
  add('error',['PandocIOError',ps('context'),e],11)
  add('handle',['PandocIOError',ps('context'),e])
for s in texts:
 for where in ['location','description','filename','handle']:
  e=io()
  if where=='location':e[1]=s
  elif where=='description':e[2]=s
  elif where=='filename':e[4]=s
  else:e[5]=['file',s,0,0]
  add('io',e,2**63-1,raw=True);add('error',['PandocIOError',ps('context'),e],11,raw=True)
rng=random.Random(0x20062024)
for h in handles:
 e=io(h=h);ios.append(e)
 add('io',e,0);add('error',['PandocIOError',[],e],0)
for _ in range(2000):
 a=rng.choice(ios);b=rng.choice(ios)
 add('ioEq',a,b)
for h1,h2 in itertools.product(handles,repeat=2):add('ioEq',io(h=h1),io(h=h2),handleIdentity=True)
for n in [-2**63,-2**31-1,-2**31,-1,0,2**31-1,2**31,2**63-1]:add('ioEq',io(n=n),io(n=(n+2**31)%2**32-2**31),errno=True)
for c,s,p in itertools.product(['CiteprocXMLError','CiteprocParseError','CiteprocLocaleNotFound'],texts,[-2**63,0,10,11,2**63-1]):add('cite',[c,s],p)
for a,b in itertools.product(['CiteprocXMLError','CiteprocParseError','CiteprocLocaleNotFound'],repeat=2):
 for s,t in itertools.product(texts,repeat=2):add('citeEq',[a,s],[b,t])
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),constructors=[r[0] for r in ctors],operations=dict(collections.Counter(c['request'][0] for c in cs)),oracle='Unchanged Pandoc 3.12.1 Error/UTF8, exact original CiteprocError 0.14.1 and tshow projections, GHC 9.12.2 IOError/Handle; real handleError exit/stderr capture')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'error observations; SHA',m['caseHash'],flush=True)
