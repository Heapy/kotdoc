#!/usr/bin/env python3
"""Original live/controlled GHC environment and filesystem byte decoding."""
import argparse, collections, gzip, hashlib, itertools, json, random, struct, subprocess, os
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-environment-reference'
p=argparse.ArgumentParser();p.add_argument('--platform',required=True,choices=['macos-aarch64','linux-x86_64']);p.add_argument('--oracle');p.add_argument('--original-test');p.add_argument('--check-source',action='store_true');args=p.parse_args()
target=root/'core/src/test/resources/ghc-environment'/f'{args.platform}.jsonl.gz'
s=(root/'vendor/ghc-environment-9.12.2/GHC/Internal/System/Environment.hs').read_text();divvy=s[s.index('divvy ::'):]
posix='getEnvironment = do'+s.split('#else\ngetEnvironment = do',1)[1].split('\nforeign import ccall',1)[0]
windows='getEnvironment ='+s.split('#if defined(mingw32_HOST_OS)\ngetEnvironment =',1)[1].split('\nforeign import ccall',1)[0]
assert (folder/'OriginalPosixEnvironment.hs').read_text().split('getEnvironment :: IO [(String,String)]\n',1)[1]==posix+'\n\n'+divvy
wsource=(root/'vendor/ghc-environment-9.12.2/GHC/Internal/Foreign/C/String.hs').read_text()
wide='cWcharsToChars ='+wsource.split('cWcharsToChars =',1)[1].split('\ncharsToCWchars =',1)[0]
assert (folder/'OriginalWindowsEnvironment.hs').read_text().split('cWcharsToChars :: [CWchar] -> [Char]\n',1)[1]==wide+'\ngetEnvironment :: IO [(String,String)]\n'+windows+'\n\n'+divvy
assert (root/'vendor/ghc-environment-9.12.2/LICENSE.txt').read_bytes()==(root/'licenses/GHC-base-BSD-3-Clause.txt').read_bytes()
provenance=json.loads((root/'vendor/ghc-environment-9.12.2/SOURCE.json').read_text())
assert provenance['revision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc'
for name,sha in provenance['files'].items():assert hashlib.sha256((root/'vendor/ghc-environment-9.12.2'/name).read_bytes()).hexdigest()==sha,name
with gzip.open(root/'core/src/test/resources/http-windows-proxy/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/http-windows-proxy/data.jsonl.gz','tools/import-environment-fixtures.py']
for d in ['tools/haskell-environment-reference','vendor/ghc-environment-9.12.2']:
 sources += [str(f.relative_to(root)) for f in (root/d).rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs) and m['platform']==args.platform
 assert m['compiler']=='9.12.2' and m['originalNativeTest']=='T12494' and isinstance(m['nativeLocale'],str)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original GHC environment fixture groups,',args.platform,'and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.original_test
original=subprocess.run([args.original_test],capture_output=True,check=True,env={k:v for k,v in os.environ.items() if k!='AN_ENVIRONMENT_VARIABLE'})
assert original.stdout==(root/'vendor/ghc-environment-9.12.2/tests/T12494.stdout').read_bytes(),original.stdout
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def ask(command):
 proc.stdin.write(command+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,command[:100]
 return json.loads(line)
assert ''.join(map(chr,ask('Version')[0][0]))=='9.12.2'
native_locale=''.join(map(chr,ask('Locale')[0][0]))
cs=[]
def array(x):return '['+','.join(map(str,x))+']'
def arrays(xs):return '['+','.join(array(x) for x in xs)+']'
def add(group,request,command,hash_output=False):
 if request.get('encoding')=='':request['locale']=native_locale
 expected=ask(command)
 if hash_output:
  flat=[x for row in expected for item in row for x in item]
  expected=dict(sha256=hashlib.sha256(b''.join(struct.pack('<I',x) for x in flat)).hexdigest(),count=len(flat))
 cs.append(dict(group=group,request=request,expected=expected))
codecs=['UTF-8','ASCII','ISO-8859-1','UTF16','UTF16LE','UTF16BE','UTF32','UTF32LE','UTF32BE','CP1251','SHIFT_JIS','ISO-2022-JP','CP1252']
for enc in codecs:
 for start in range(0,65536,256):
  samples=[[i//256,i%256] for i in range(start,start+256)]
  r=dict(api='decode',encoding=enc,bytes=samples,posixOnly=enc in codecs[9:])
  add('all-byte-pairs-per-encoding-256-independent-strings',r,'Decode '+json.dumps(enc)+' '+arrays(samples))
edges=[[],[0],[255],[0xef,0xbb,0xbf],[0xfe,0xff],[0xff,0xfe],[0,0,0xfe,0xff],[0xff,0xfe,0,0],
 [0xed,0xa0,0x80],[0xf0,0x90,0x80,0x80],[0xf4,0x8f,0xbf,0xbf],[0xf4,0x90,0x80,0x80],
 [0xe2,0x82],[0xf0,0x90,0x80],[0xff,0xfe,0,0xd8,0,0xdc],[0xfe,0xff,0xd8,0,0xdc,0],
 [0x1b,0x24,0x42,0x24,0x22,0x1b,0x28,0x42,0x61],[0x1b,0x24,0x42,255,0x24,0x22,0x1b,0x28,0x42]]
for enc in codecs+['utf-8','UTF_8','ASCII','latin1','UTF_16BE','UTF_16LE','UCS-2LE','UCS-2BE','UCS-4LE','UCS-4BE','UTF_16','UTF_32','UTF-8//IGNORE','not-a-real-encoding','']:
 add('BOM-state-surrogates-truncation-stateful-reset-aliases-and-unknown-name',dict(api='decode',encoding=enc,bytes=edges,posixOnly=enc not in codecs[:9]+['utf-8','ASCII','latin1']),
     'Decode '+json.dumps(enc)+' '+arrays(edges))
def encode_raw(cp):
 if cp<128:return [cp]
 if cp<0x800:return [0xc0|(cp>>6),0x80|(cp&63)]
 if cp<0x10000:return [0xe0|(cp>>12),0x80|((cp>>6)&63),0x80|(cp&63)]
 return [0xf0|(cp>>18),0x80|((cp>>12)&63),0x80|((cp>>6)&63),0x80|(cp&63)]
for start in range(0,0x110000,1024):
 data=[b for cp in range(start,start+1024) for b in encode_raw(cp)]
 add('every-Haskell-Char-raw-UTF8-including-surrogate-byte-escapes',dict(api='range',start=start,count=1024),
     'Decode "UTF-8" '+arrays([data]),True)
byte=lambda s:list(s.encode('utf8'))
records=[None,[],[[]],[byte('no-equals')],[byte('=value')],[byte('key=one'),byte('key=two'),byte('KEY=three')],
 [byte('A=first=rest'),byte('A='),byte('=')],[[65,61,255],[65,61,196,128],[66,61,0,255]],
 [list(range(1,256)),byte('last=value')]]
for api,enc,recs in itertools.product(['Posix','Live'],['UTF-8','ASCII','ISO-8859-1','CP1251'],records):
 value='Nothing' if recs is None else '(Just '+arrays(recs)+')'
 add('ordered-duplicate-malformed-and-nameless-live-versus-controlled-POSIX',dict(api=api.lower(),encoding=enc,records=recs,posixOnly=True),api+' '+json.dumps(enc)+' '+value)
for recs in [None,[]]:
 add('null-block-skips-encoding-nonnull-empty-block-still-reads-encoding',dict(api='posix',encoding='not-a-real-encoding',records=recs,posixOnly=True),
     'Posix "not-a-real-encoding" '+('Nothing' if recs is None else '(Just [])'))
def units(s):return list(struct.unpack('<'+'H'*(len(s.encode('utf-16le',errors='surrogatepass'))//2),s.encode('utf-16le',errors='surrogatepass')))
blocks=[None,[],[0],[0,0]]
for strings in [['A=one','A=two','KEY=value'],['noequals','=empty','=C:=C:\\path'],['X=ĀA=B','last=value'],
 ['X=😀B=C','last=value'],['X=\ud800A=\udc00','end=last'],['X=\udb00\udc00=tail','last=value']]:
 blocks.append([n for s in strings for n in units(s)+[0]]+[0])
for cp in range(0,65536,256):
 blocks.append(units('X='+chr(cp)+'A=B')+[0]+units('end=last')+[0,0])
for cp in [0,1,61,128,255,256,0xd800,0xdb00,0xdc00,0xdd00,0xffff]:
 for start in ['',chr(cp)]:blocks.append(units(start+'A='+chr(cp)+'B=C')+[0,0])
for block in blocks:
 add('Windows-original-Word8-scan-UTF16-pairs-surrogates-drive-and-null-block',dict(api='windows',units=block),
     'Windows '+('Nothing' if block is None else '(Just '+array(block)+')'))
rng=random.Random(9122)
for _ in range(180):
 enc=rng.choice(codecs);batch=[[rng.randrange(256) for _ in range(rng.randrange(1,100))] for _ in range(8)]
 add('deterministic-malformed-and-stateful-byte-sequences',dict(api='decode',encoding=enc,bytes=batch,posixOnly=enc in codecs[9:]),'Decode '+json.dumps(enc)+' '+arrays(batch))
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='GHC 9.12.2 original getEnvironment/divvy and filesystem ROUNDTRIP decoding',platform=args.platform,compiler='9.12.2',nativeLocale=native_locale,originalNativeTest='T12494',sources=hashes(),cases=len(cs),
       caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
