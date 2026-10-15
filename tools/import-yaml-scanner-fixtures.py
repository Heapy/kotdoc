#!/usr/bin/env python3
"""Complete original libyaml scanner observations, including per-call hidden state."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/yaml-scanner/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_YAML_SCANNER_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-yaml-scanner-fixtures.py','licenses/Libyaml-MIT.txt','upstream/pandoc/src/Text/Pandoc/Translations.hs','core/src/test/resources/yaml/data.jsonl.gz']
for folder in ['tools/haskell-yaml-scanner-reference','vendor/libyaml-clib-0.2.5','vendor/yaml-0.11.11.2','vendor/libyaml-0.1.4']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file())
paths=[]
for folder in ['upstream/pandoc/data/translations','vendor/yaml-0.11.11.2/test']:
 for f in sorted((root/folder).rglob('*.yaml')):
  path=str(f.relative_to(root));paths.append(path)
  if path not in sources:sources.append(path)
def hashes():return {f:hashlib.sha256(os.readlink(root/f).encode() if (root/f).is_symlink() else (root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert set(c['path'] for c in cs if c.get('path'))==set(paths)
 tokens=set();errors=set()
 for c in cs:
  tokens.update(s['token']['type'] for s in c['expected']['steps']);errors.add(c['expected']['error']['type'])
 assert tokens==set(range(22)),tokens
 assert {0,2,3}<=errors,errors
 print(f'Verified {len(cs)} complete original scanner observations, all token types and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def query(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(bs,chunks=(),encoding=0,limit=500,group='syntax',**flags):
 if isinstance(bs,str):bs=bs.encode()
 req=['scan',list(bs),list(chunks),encoding,limit]
 cs.append(dict(request=req,expected=query(req),group=group,**flags))
 if len(cs)%1000==0:print('Captured',len(cs),'scanner observations',flush=True)
examples=['','a scalar',"'a scalar'","---\n'a scalar'\n...", "'a scalar'\n---\n'another scalar'\n---\n'yet another scalar'", '&A [ *A ]','!!float "3.14"  # A good approximation.','---\n--- a plain scalar\n--- \'a single-quoted scalar\'\n--- "a double-quoted scalar"\n--- |-\n  a literal scalar\n--- >-\n  a folded\n  scalar','[item 1, item 2, item 3]','{a simple key: a value, ? a complex key: another value,}','%YAML   1.1\n%TAG    !   !foo\n%TAG    !yaml!  tag:yaml.org,2002:\n---','key:\n- item 1\n- item 2','- - one\n  - two\n- three','? [one, two]\n: value','key: &a value\nother: *a','key: |+\n  first\n\n  last\n\n','key: >-\n  first\n  second\n\n  third\n','[!foo, !!str \'text\']']
for i,s in enumerate(examples):
 for chunks in [[],[1]*20,[2,3,1,4]]:add(s,chunks,group='original-notes',example=i)
for path in paths:
 bs=(root/path).read_bytes()
 for chunks in [[],[1]*12,[2,5,3,1]]:add(bs,chunks,group='source-files',path=path,limit=2000)
# Decode every quoted literal from the complete original Haskell YAML tests
# using GHC Read, then run this input through the actual C scanner. These are
# component observations, not a claim that encoder/parser/property suites pass.
spec=(root/'vendor/yaml-0.11.11.2/test/Data/YamlSpec.hs').read_text()
for i,literal in enumerate(re.findall(r'"(?:[^"\\]|\\[\s\S])*"',spec)):
 bs=query(['literal',literal])
 if bs is not None:add(bs,group='source-literals',literal=literal,index=i)
scalars=['plain','a:b','a: b','a #comment','a#b','a\nb','a\n b','a\n\tb','漢🙂','é\u0085x','a\u2028b','a\u2029b','\ufeffa','null','',"'single''quote'",'"double\\nquote"','"\\0\\x00\\u0085\\U0001f642"','|\n text\n','>\n text\n next\n','?abc',':abc','-abc','@bad','`bad','%bad','!foo','!<tag:example,x>','!!str text','&a text','*a']
contexts=['{}','[{}]','{{key: {}}}','key: {}','- {}','? {}\n: value','key: {}\nnext: end','--- {}\n...']
for scalar,context in itertools.product(scalars,contexts):add(context.replace('{}',scalar),group='contexts')
for value,nl,quote in itertools.product(['',' ','\t','plain',"single'quote",'"double"','漢🙂','a: b','a#b','---','...'],['\n','\r','\r\n','\u0085','\u2028','\u2029'],['','\'', '"']):
 add('key: '+quote+value+nl+'  next'+quote+nl+'after: end',group='folding')
for style,chomp,inc,body,nl in itertools.product(['|','>'],['','+','-'],['','0','1','2','9'],['','\n',' text\n next\n','  text\n    indented\n\n  last\n','\tbad\n','   \n text\n',' text\n\n\n'],['\n','\r\n','\u0085','\u2028']):
 text='key: '+style+chomp+inc+nl+body.replace('\n',nl)+'after: end'+nl
 add(text,group='block-scalars')
for cp in range(256):
 char=chr(cp)
 for context in ['{}','key: {}','[{}]','"{}"','&{} value','!{} value']:
  add(context.replace('{}',char),group='character-contexts')
for escape in ['0','a','b','t','n','v','f','r','e',' ','"','/','\\','N','_','L','P','x00','x7f','xFF','u0000','uD7FF','uD800','uDFFF','uE000','uFFFE','U00010000','U0010FFFF','U00110000','Uffffffff','q','x','xG0','u123','U0000🙂']:
 for suffix in ['"',' plain','\n  end"','']:
  add('"prefix\\'+escape+suffix,group='escapes')
for percent,context in itertools.product(['%00','%00b','a%00b','%7f','%80','%c0%80','%ed%a0%80','%f4%90%80%80','%ff','%c2','%c2%41','%e2%82%ac','%e2%82','%','%a','%GG'],['!{} value','!<{}> value','%TAG ! {}\n---\n']):add(context.replace('{}',percent),group='tag-uri')
for directive in ['%','%YAML','%YAML 1','%YAML 1.','%YAML 1.a','%YAML 123456789.0','%YAML 1234567890.0','%YAML 1.2 extra','%YAML 1.2 #comment','%TAG ! tag:example','%TAG !foo tag:example','%TAG !foo! tag:example','%TAG !! !','%TAG ! !<bad>','%OTHER 1.2','%YAMLé 1.2']:
 for tail in ['','\n---\n','\r\n---\r\n']:add(directive+tail,group='directives')
for length,tail in itertools.product([0,1,1023,1024,1025,2048],[': value','\nnext','\nnext: value',' : value']):add('a'*length+tail,group='simple-keys')
for text in ['a: b\n  c: d\ne: f','a:\n - b\n  - c','[a, [b], {c: d}]','{[a,b]: [c,d]}','}\n',']\n','[,]','a:\tvalue','\tkey: value','a\n\ufeffb','---\n\ufeffkey: value']:
 for nl in ['\n','\r','\r\n','\u0085','\u2028','\u2029']:add(text.replace('\n',nl),group='indent-flow')
for size,tail in itertools.product([16380,16383,16384,16385,32768],['',': value','\nnext','\u2028next']):add('a'*size+tail,group='buffers')
for text,enc,bom,chunks in itertools.product(['key: 漢🙂\nnext: é','key: "\\u0000"','[&a x,*a]'],[1,2,3],[False,True],[[],[1]*20,[2,1,3]]):
 name={1:'utf-8',2:'utf-16le',3:'utf-16be'}[enc];bs=text.encode(name)
 if bom:bs={1:b'\xef\xbb\xbf',2:b'\xff\xfe',3:b'\xfe\xff'}[enc]+bs
 add(bs,chunks,0,group='encodings');add(bs,chunks,enc,group='encodings')
for text,chunks in itertools.product(['','key: value','\"quoted value\"','[a,b]','key: |\n text\n'],[[-1],[1,-1],[3,-1],[5,-1],[3,0],[0]]):add(text,chunks,group='input-errors')
rng=random.Random(0x253598)
alphabet='ab01 :?,[]{}-#&*!|>\'"%@`\\\t\n\r漢🙂'
for _ in range(4000):
 s=''.join(rng.choice(alphabet) for _ in range(rng.randrange(60)))
 add(s,group='syntax-fuzz')
for _ in range(500):add(rng.randbytes(rng.randrange(30)),rng.choice([[],[1]*10,[2,1,3],[-1]]),rng.randrange(4),group='byte-fuzz')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),oracle='Complete unchanged libyaml 0.2.5 api/reader/scanner C modules, actual tokens and internal state after every scan; GHC 9.12.2 frozen transport')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'scanner observations; SHA',m['caseHash'],flush=True)
