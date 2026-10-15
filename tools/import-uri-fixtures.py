#!/usr/bin/env python3
"""Original Network.URI observations and calls made by its unchanged test bodies."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-uri-reference';target=root/'core/src/test/resources/uri/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_URI_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
source=(root/'vendor/network-uri-2.6.4.2/tests/uri001.hs').read_text()
assert (folder/'OriginalTests.hs').read_text()==source.replace('module Main where','module OriginalTests where',1)
assert (folder/'CapturedTests.hs').read_text()==source.replace('module Main where','module CapturedTests where',1).replace('import Network.URI\n','import ObservedURI\n',1)
sources=['tools/import-uri-fixtures.py','licenses/Network-uri-BSD-3-Clause.txt','licenses/Network-uri-Graham-Klyne-BSD-3-Clause.txt','licenses/Utf8-string-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','core/src/main/resources/io/heapy/kotdoc/regex-char-data.json']
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'vendor/network-uri-2.6.4.2',root/'vendor/parsec-3.1.18.0',root/'vendor/ghc-9.12.2',root/'vendor/ghc-regex-unicode-9.12.2'] for f in base.rglob('*') if f.is_file() and '__pycache__' not in f.parts and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original URI observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
with tempfile.TemporaryDirectory(prefix='kotdoc-uri-original-') as tmp:
 baseline=subprocess.check_output([args.oracle,'--upstream-tests'],cwd=root,text=True)
 print(baseline[-240:],flush=True)
 capture=Path(tmp)/'calls.jsonl'
 observed=subprocess.check_output([args.oracle,'--capture-tests'],cwd=root,env=dict(os.environ,KOTDOC_URI_CAPTURE=str(capture)),text=True)
 original_calls=[json.loads(line) for line in capture.read_text().splitlines()]
 original_count=int(re.search(r'All (\d+) tests passed',baseline)[1]);assert int(re.search(r'All (\d+) tests passed',observed)[1])==original_count
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group,**extra):
 cs.append(dict(request=req,expected=ask(req),group=group,**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'URI observations',flush=True)
for c in original_calls:
 assert ask(c['request'])==c['expected']
 cs.append(dict(c,group='original-unit-and-quickcheck-api-calls'))
def raw(s):return list(map(ord,s))
def uri(s='',a=None,p='',q='',f=''):return [raw(s),None if a is None else [raw(x) for x in a],raw(p),raw(q),raw(f)]
strings=['','http://a/b/c/d;p?q','g','g:h','http:g','HTTP:g','//host/path','?q','#f','a:b','a/b:c','./a:b','//user:pass@host:80/a','http://host:','http://host:abc','http://host%20a/','http://[::1]/','http://[v1.a]/','http://[v12.a]/','http://[v1.a:b]/','http://[fe80::1%25eth0]/','http://[fe80::1%eth0]/','http://[::192.001.2.3]/','http://1.2.3.4a/','http://999.0.0.1/','http://255.255.255.255/','http://0001.0.0.1/','/','//','///','////a','%','%a','%FF','é','\ud800\udc00','\U00010000','a\n','a\t','a\0','a%20b','[::]','::','1::2::3']
for n in [0,1,254,255,256,257,1000]:strings+=['http://'+'a'*n+'/','http://'+'%41'*n+'/']
for s in strings:
 for k in range(4):add(['parse',k,raw(s)],'all-uri-parser-forms-and-boundaries')
 for k in range(6):add(['valid',k,raw(s)],'all-uri-and-address-predicates')
# Parse all string literals present in original examples; inactive tests stay
# observations and are not reported as active original assertions.
for token in re.findall(r'"(?:[^"\\]|\\.)*"',source):
 try:s=json.loads(token)
 except ValueError:continue
 for k in range(4):add(['parse',k,raw(s)],'all-original-source-string-literals')
parts=['','.', '..','./','../','/','//','a','a/','a:b','a?b','a#b','a/b','/a/b/','a/../b','/a/..//b','a/../../b','é','\ud800\udc00']
models=[uri(s,a,p,q,f) for s,a,p,q,f in itertools.product(['','http:','HTTP:'],[None,('','host',''),('u:p@','host',':80')],parts,['','?q'],['','#f'])]
rng=random.Random(0x55524932)
for i in range(4500):
 a=rng.choice(models);b=rng.choice(models)
 for k in range(3):add(['resolve',k,a,b],'raw-constructor-relative-resolution-and-dot-segments')
for p in parts:add(['segments',uri(p=p)],'path-segments-empty-and-trailing-components')
for u in rng.sample(models,100):add(['components',u],'deprecated-component-accessors')
bytesets=[[],[0],[0x7f],[0x80],[0xbf],[0xc0],[0xc0,0x80],[0xc2,0xa0],[0xdf,0xbf],[0xe0,0x80,0x80],[0xed,0xa0,0x80],[0xee,0x80,0x80],[0xef,0xbf,0xbe],[0xef,0xbf,0xbf],[0xef,0xb7,0x90],[0xf0,0x90,0x80,0x80],[0xf4,0x8f,0xbf,0xbf],[0xf4,0x90,0x80,0x80],[0xf8,0x88,0x80,0x80,0x80],[0xfc,0x84,0x80,0x80,0x80,0x80],[0xfe],[0xff]]
for bs in bytesets:
 for n in range(len(bs)+1):
  s=''.join('%%%02X'%b for b in bs[:n])
  for end in ['', 'x','%GG','%','%7','%41','%80','%ff']:
   add(['unescape',raw(s+end)],'malformed-utf8-prefix-consumption-and-replacement')
for a in range(256):
 for b in range(256):add(['unescape',raw('%%%02X%%%02X'%(a,b))],'every-escaped-byte-pair')
for s in strings+['HTTP://USER:pass@HOST/a/../b','ABC/path%éя','1+:x%ßﬀ','%ab%zz%a','http://a/./b/../../c']:
 for k in range(3):add(['normalize',k,raw(s)],'case-escape-and-path-normalization')
for i in range(2000):
 s=''.join(rng.choice('abcABC0123456789:/.@[]%?#-~_é\0') for _ in range(rng.randrange(40)))
 for k in range(4):add(['parse',k,raw(s)],'generated-uri-grammar-and-backtracking')
for cp in range(0,0x110000,65536):add(['unicode',cp,min(65536,0x110000-cp)],'all-haskell-char-escaping-decoding-case-and-class-witnesses')
for n in [1500,10000]:
 add(['parse',1,raw('/'+'a/'*n)],'finite-deep-path-parsing')
 add(['resolve',0,uri(p='a/'*n+'../'*n+'x'),uri('http:',('','a',''),'/b')],'finite-deep-dot-segments')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalNativeTests=original_count,originalCalls=len(original_calls),quickcheckReplay=314159)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as file:
 with gzip.GzipFile(fileobj=file,mode='wb',mtime=0) as z:
  for value in [m,*cs]:z.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original URI observations',m['caseHash'])
