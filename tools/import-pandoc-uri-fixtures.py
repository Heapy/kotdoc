#!/usr/bin/env python3
"""Observe pinned URI exports, incremental parsing and Base64. No Kotlin oracle."""
import argparse,ast,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-pandoc-uri-reference';target=root/'core/src/test/resources/pandoc-uri/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_PANDOC_URI_REFERENCE'));p.add_argument('--check-source',action='store_true');p.add_argument('--prepared',type=Path);args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/URI.hs').read_text()
assert (folder/'SourceUriPosix.hs').read_text()==src.replace('module Text.Pandoc.URI (','module SourceUriPosix (',1)
assert (folder/'SourceUriWindows.hs').read_text()==src.replace('module Text.Pandoc.URI (','module SourceUriWindows (',1).replace('import qualified Network.HTTP.Types','#define _WINDOWS\nimport qualified Network.HTTP.Types',1)
sources=['tools/import-pandoc-uri-fixtures.py','tools/import-media-fixtures.py','core/src/main/resources/io/heapy/kotdoc/uri-schemes.txt','upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/src/Text/Pandoc/MIME.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal','licenses/Http-types-BSD-3-Clause.txt','licenses/Attoparsec-BSD-3-Clause.txt','licenses/Base64-bytestring-BSD-3-Clause.txt']
for base in [folder,root/'vendor/attoparsec-0.14.4',root/'vendor/http-types-0.12.6',root/'vendor/base64-bytestring-1.2.1.0',root/'vendor/network-uri-2.6.4.2']:
 sources+=sorted(str(f.relative_to(root)) for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original Pandoc URI/Attoparsec observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared,'--oracle and --prepared required'
# The complete original library build has exposure-only packaging edits for the
# previous HTML phase. URI itself is compiled verbatim twice, with CPP platforms.
modules=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX'];base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
for name in modules:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+name+',\n' for name in modules))
tracked=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0')
for name in filter(None,tracked):assert (args.prepared/'pandoc'/name).read_bytes()==(changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()),name
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req[:2]
 return json.loads(line)
def add(req,group):
 cs.append(dict(request=req,expected=ask(req),group=group))
 if len(cs)%5000==0:print('Captured',len(cs),'URI observations',flush=True)
def raw(s):return list(map(ord,s))
def chunks(xs):return list(map(raw,xs))
schemes=ask(['schemes']);assert schemes==sorted((root/'core/src/main/resources/io/heapy/kotdoc/uri-schemes.txt').read_text().splitlines())
add(['schemes'],'complete-pinned-scheme-set')
for scheme in schemes+['unknown','a','A+B.C-9','0http','é','']:
 for suffix in ['',':',':x',':/x',':?q#f',':a b',':é',':%FF',':%zz',':/a[b]']:
  for prefix in [scheme,scheme.upper()]:add(['uri',raw(prefix+suffix)],'all-schemes-and-absolute-uri-boundaries')
for s in ['','/','//','///','/c:/foo','c:/foo','C:\\foo','/é/😀','http://u:p@[::1]:80/a?b#c','http://[::ffff:192.0.2.1]/','http://[:::]/','file:///a b','\\server\\share']:
 add(['uri',raw(s)],'paths-ipv6-and-platform-branches')
# All Haskell Chars, including surrogate Chars replaced by Text.singleton.
for start in range(0,0x110000,65536):add(['unicode',start,min(65536,0x110000-start)],'all-haskell-char-encoding-escaping-and-path-witnesses')
module=ast.parse((root/'tools/import-media-fixtures.py').read_text())
data=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='data_cases' for t in n.targets))
data+=['data:a/b;base64,','DaTa:a/b; \tcharset=x;foo=a=b;base64,QQ==','data:a/b;\u00a0x=y;base64,QQ==','data:a/b;base64,%3D%3DQQ','data:a/b;base64,Q%3DQ','data:a/b;base64,QQ%3DAAA','data:a/b;base64,%80QQ','data:a/b;base64,QQ==junk','data:a/b;BASE64,QQ==','data:a/b;base64,QQ%','data:a/b;base64,QQ%a','data:a/b;base64,QQ%aaX','data:a/b;base64,QQ%gg','data:a/b;p=one,two;base64,QQ==','data:a/b;p=;base64,QQ==','data:a/b;p=bad;base64,QQ==;rest']
for s in data:
 add(['uri',raw(s)],'original-data-fetch-cases-and-grammar-boundaries')
 for full in [False,True]:
  add(['complete',full,raw(s)],'parse-only-prefix-and-end-of-input')
  for i in range(len(s)+1):add(['data',full,chunks([s[:i],s[i:],''])],'every-single-chunk-boundary-and-terminal-feed')
  add(['data',full,chunks(list(s)+[''])],'single-character-fragmentation')
for cp in range(256):
 for pos in ['','Q','QQ','QQQ','%3D','QQ%3D','%FF']:
  s='data:a/b;base64,'+pos+'%'+format(cp,'02X')+'QQ=='
  add(['uri',raw(s)],'every-percent-octet-in-sextet-and-padding-positions')
  for full in [False,True]:add(['data',full,chunks([s,''])],'every-percent-octet-in-sextet-and-padding-positions')
for cp in list(range(128))+[0x85,0xa0,0x1680,0x180e,*range(0x2000,0x200c),0x2028,0x2029,0x202f,0x205f,0x3000,0xd800,0xdc00,0x10ffff]:
 for s in ['data:'+chr(cp)+'/a;base64,QQ==','data:a/b;'+chr(cp)+'x=y;base64,QQ==','data:a/b;base64,Q'+chr(cp)+'Q==']:
  add(['uri',raw(s)],'mime-and-base64-ascii-unicode-predicates')
  add(['data',True,chunks([s,''])],'mime-and-base64-ascii-unicode-predicates')
# Preserve strict decoder semantics even where percent escapes introduce padding.
for n in range(7):
 for xs in itertools.product([65,81,47,61,255],repeat=n):add(['base64',list(xs)],'strict-lenient-base64-exhaustive-short-inputs')
rng=random.Random(314159)
for _ in range(1000):add(['base64',[rng.randrange(256) for _ in range(rng.randrange(160))]],'strict-lenient-base64-deterministic-byte-inputs')
char=lambda c:['char',ord(c)]
a=char('a');b=char('b');end=['eof'];ab=['string',raw('ab')]
exprs=[a,ab,['ascii',raw('aB')],['take',raw('ab'),False],['take',raw('ab'),True],['or',['then',a,b],a],['or',ab,['string',raw('ac')]],['many',['then',a,b]],['many1',a],['skip',a],['before',a,end],['pair',['look',ab],ab],['look',['then',a,b]],['match',['many',a]],['label',['label',['then',a,b],'inner'],'outer'],['or',['label',ab,'left'],['label',b,'right']],['then',['many',a],['or',b,end]],['pair',['many',a],['many',b]],['before',['string',raw('😀a')],end],['or',['then',a,['fail','bad']],['pure',42]]]
for expr in exprs:
 for n in range(4):
  for letters in itertools.product('ab!😀',repeat=n):
   s=''.join(letters)
   for i in range(len(s)+1):add(['kernel',expr,chunks([s[:i],s[i:],'','tail'])],'kernel-choices-labels-lookahead-match-and-rechunking')
for expr in exprs:
 for first in ['','a','ab','😀']:
  add(['branch',expr,raw(first),chunks(['b','']),chunks(['c',''])],'branchable-immutable-partial-continuations')
for k in range(2):
 for depth in [1500,10000,100000]:add(['deep',k,depth],'deep-finite-map-and-bind-continuations')
for k in range(3):
 for depth in [1500,10000,100000]:add(['fragmented',k,depth],'deep-fragmented-literal-many-and-backtracking-buffer')
for k in range(4):add(['lazy',k],'unforced-parser-results')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),verifiedOriginalBuildFiles=len(list(filter(None,tracked))),exposedModules=modules,sourceUriCopies='module rename only; Windows defines _WINDOWS before imports')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for v in [m,*cs]:z.write((json.dumps(v,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original URI observations',m['caseHash'],flush=True)
