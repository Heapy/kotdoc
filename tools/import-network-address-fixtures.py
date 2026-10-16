#!/usr/bin/env python3
"""Pinned native network and GHC CString/errno observations, per POSIX ABI."""
import argparse,collections,gzip,hashlib,itertools,json,re,subprocess,random
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-network-address-reference'
p=argparse.ArgumentParser();p.add_argument('--platform',required=True,choices=['macos-64','linux-64']);p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
target=root/f'core/src/test/resources/network-address/{args.platform}.jsonl.gz';asset=root/f'core/src/main/resources/io/heapy/kotdoc/http/network-abi-{args.platform}.json'
original=root/'vendor/network-3.2.9.0';copied=folder/'network-3.2.9.0'
paths=[f for f in original.rglob('*') if f.is_file()]
assert {f.relative_to(copied) for f in copied.rglob('*') if f.is_file()}=={f.relative_to(original) for f in paths}
for f in paths:
 rel=f.relative_to(original);raw=f.read_bytes()
 if str(rel)=='network.cabal':raw=raw.replace(b'        Network.Socket.Info\n',b'').replace(b'        Network.Socket.Types\n',b'').replace(b'    exposed-modules:\n',b'    exposed-modules:\n        Network.Socket.Info\n        Network.Socket.Types\n',1)
 assert (copied/rel).read_bytes()==raw,str(rel)
s=(original/'Network/Socket/Types.hsc').read_text();family=s[s.index('familyBijection ::'):s.index('\ninstance Show Family')];sock=s[s.index('socktypeBijection ::'):s.index('\ninstance Show SocketType')]
assert (folder/'SocketPatterns.hs').read_text()=='{-# LANGUAGE PatternSynonyms #-}\nmodule SocketPatterns where\nimport Network.Socket\ntype Bijection a b = [(a,b)]\n\n'+sock+'\n'+family+'\n'
ghc=root/'vendor/ghc-network-9.12.2';pin=json.loads((ghc/'SOURCE.json').read_text());assert pin['revision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc'
for name,sha in pin['files'].items():assert hashlib.sha256((ghc/name).read_bytes()).hexdigest()==sha,name
s=(ghc/'libraries/ghc-internal/src/GHC/Internal/Foreign/C/Error.hs').read_text();pairs=re.findall(r'\| errno == (e\w+)\s*= (\w+)',s)
assert (folder/'GhcErrnoPatterns.hs').read_text()=='module GhcErrnoPatterns where\nimport Foreign.C.Error\nerrnoTypePatterns :: [(Errno,String)]\nerrnoTypePatterns = [\n'+',\n'.join('    ('+name+',"'+typ+'")' for name,typ in pairs)+'\n    ]\n'
with gzip.open(root/'core/src/test/resources/http-socket/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/http-socket/data.jsonl.gz','tools/import-network-address-fixtures.py']
sources += [str(f.relative_to(root)) for f in ghc.rglob('*') if f.is_file()]
sources += [str((copied/f.relative_to(original)).relative_to(root)) for f in paths]
sources += [str(f.relative_to(root)) for f in folder.iterdir() if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs) and m['platform']==args.platform
 assert m['abi']==json.loads(asset.read_text()) and m['abiHash']==hashlib.sha256(asset.read_bytes()).hexdigest()
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original network/foreign-string observations,',args.platform,'and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
# Batch the protocol to avoid introducing SSH latency per observation.
def ask(rs):
 proc=subprocess.run([args.oracle],input=''.join(json.dumps(r)+'\n' for r in rs),text=True,capture_output=True)
 assert proc.returncode==0,proc.stderr
 out=[json.loads(line) for line in proc.stdout.splitlines()];assert len(out)==len(rs),(len(rs),len(out),proc.stderr)
 return out
abi=ask([dict(api='abi')])[0]
asset.parent.mkdir(parents=True,exist_ok=True);asset.write_text(json.dumps(abi,sort_keys=True,indent=2)+'\n')
cs=[]
def add(group,api,**extra):cs.append(dict(group=group,request=dict(api=api,**extra)))
def v4(port=80,host=0x0100007f):return dict(kind='v4',port=port,host=host)
def v6(port=80,host=(0,0,0,1),flow=0,scope=0):return dict(kind='v6',port=port,host=host,flow=flow,scope=scope)
for host,port in itertools.product([0,1,0xffffffff,0x0100007f,0x7f808182],[0,1,80,65535]):add('IPv4-native-words-network-ports-and-memory','address',address=v4(port,host))
for flags in itertools.product([0,1],repeat=8):
 fields=[0 if x==0 else 0xabcd+i for i,x in enumerate(flags)];words=[fields[i]*65536+fields[i+1] for i in range(0,8,2)]
 add('IPv6-all-zero-run-patterns-RFC5952-and-memory','address',address=v6(host=words,flow=0xffffffff,scope=0x12345678))
for last,third in itertools.product([0,1,65535,65536,0x01020304,0xffffffff],[0,65535]):add('IPv4-compatible-and-mapped-IPv6-format','address',address=v6(host=(0,0,third,last)))
for length,kind in itertools.product([0,1,2,100,103,104,107,108,120],['plain','abstract','bytes','surrogates']):
 path=([97]*length if kind=='plain' else [0]+[65]*length if kind=='abstract' else [i%256 for i in range(length)] if kind=='bytes' else [0xd800+i%2048 for i in range(length)])
 add('Unix-char8-NULs-capacity-and-abstract-length','address',address=dict(kind='unix',path=path))
addresses=[v4(0,0),v4(1,0),v4(1,0xffffffff),v6(),v6(flow=1),v6(scope=1),v6(host=(0xffffffff,0,0,0)),dict(kind='unix',path=[]),dict(kind='unix',path=[0,255,0xd800]),dict(kind='unix',path=[0x10000])]
for a,b in itertools.product(addresses,repeat=2):add('derived-address-Eq-Ord-unsigned-fields-and-Char-slots','compare',left=a,right=b)
for n,prec in itertools.product(list(range(-3,48))+[-2**31,-300,300,2**31-1],[-1,0,10,11,100]):add('all-named-family-type-aliases-and-unknown-Show','integer-types',value=n,precedence=prec)
flags=[x[0] for x in abi['aiFlags']]
for selection in itertools.product([False,True],repeat=len(flags)):
 fs=[f for f,yes in zip(flags,selection) if yes]
 add('all-AI-flag-combinations-and-unknown-bits','flags',flags=fs+fs[:1],mask=sum(v for name,v in abi['aiFlags'] if name in fs) | 0x40000000)
for family,typ,proto in itertools.product([-1,0,2,300],[-1,0,1,300],[-2**31,-1,0,6,2**31-1]):
 add('AddrInfo-derived-Show-and-original-poke-zeroing','hints',hints=dict(family=family,type=typ,protocol=proto,flags=['AI_NUMERICHOST','AI_NUMERICHOST'],canon=[0xd800,0x10000],address=v6()))
for code in list(range(-2,160))+[-2**31,2**31-1,999]:add('actual-POSIX-errno-type-description-and-fields','errno',errno=code,location='test-location',filename=[65,0x10000])
encodings=['UTF-8','ASCII','ISO-8859-1','UTF16','UTF16LE','UTF16BE','UTF32','UTF32LE','UTF32BE','CP1251','SHIFT_JIS','ISO-2022-JP','CP1252']
edges=[[],[0],[255],[0xc3,0xa9],[0xed,0xa0,0x80],[0xf4,0x90,0x80,0x80],[0xff,0xfe,65,0],[0,0,0xfe,0xff],list(range(256))]
for enc,raw,mode in itertools.product(encodings,edges,['decode','cstring']):add('foreign-ignore-decoder-with-and-without-C-NUL','codec',name=enc,mode=mode,bytes=raw)
points=[[],[0],[65],[0xd800],[0xdc80],[0xd800,0xdc00],[0x10000],[0x10ffff],list(range(256)),[0x1b,0x3042,0x3044,0xd800,0x3046,65]]
for enc,pts,nul in itertools.product(encodings,points,[False,True]):add('foreign-ignore-encoder-buffer-growth-BOM-and-state','codec',name=enc,mode='encode',points=pts,nullTerminate=nul)
for enc,raw in itertools.product(encodings,[False,True]):
 for start in range(0,0x110000,0x10000):
  pts=list(range(start,start+0x10000))
  add('all-Haskell-Chars-foreign-encode-grouped-hash','codec',name=enc,mode='encode',points=pts,nullTerminate=raw,hashOutput=True)
for name in ['utf-8','ASCII','latin1','UTF_16BE','UTF_16LE','UCS-2LE','UCS-4LE','UTF_16','not-a-real-encoding','UTF-8//IGNORE']:
 add('foreign-codec-alias-probe-and-native-error-record','codec',name=name,mode='encode',points=[97,0xd800,0x10ffff],nullTerminate=True)
for host,service,family in itertools.product(['127.128.129.130','::1','2001:db8:85a3::8a2e:370:7334','127.0.0.1\0ignored'],['0','80','65535','65536','-1','not-a-service'],[0,abi['families'][3][1]]):
 add('actual-numeric-getaddrinfo-native-result-or-structured-error','resolve',host=list(map(ord,host)),service=list(map(ord,service)),hints=dict(flags=['AI_NUMERICHOST','AI_NUMERICSERV'],family=family,type=1))
for host,service in [(None,None),(None,[]),([],None),(list(b'127.0.0.1'),None),(list(b'127.0.0.1'),[])]:
 add('actual-null-versus-empty-C-string-arguments','resolve',host=host,service=service,hints=dict(flags=['AI_NUMERICHOST','AI_NUMERICSERV'],type=1))
for hints in [None,{},dict(type=1),dict(family=300),dict(type=300),dict(protocol=-1)]:
 add('native-null-hints-versus-default-record-and-invalid-enums','resolve',host=list(b'127.0.0.1'),service=list(b'80'),hints=hints)
add('original-getaddrinfo-IPv4-example','resolve',host=list(b'127.128.129.130'),service=None,hints=dict(flags=['AI_NUMERICHOST','AI_ADDRCONFIG']))
add('original-macOS-numeric-service-absence-regression','resolve',host=list(b'localhost'),service=None,hints=dict(flags=['AI_NUMERICSERV']),void=True)
# Large complete-Char ranges are represented by independent original-output digests.
for enc,start in itertools.product(encodings,range(0,65536,256)):
 add('all-byte-pairs-foreign-ignore-decoding-256-strings','codec-pairs',name=enc,start=start,count=256)
for nodes in [[],[dict(address=v4())],[dict(address=v6(),canonBytes=[])],[dict(address=v4(),canonBytes=[65,255,66]),dict(address=v4(),canonBytes=[0]),dict(address=v6(),family=300,protocol=-1,canonBytes=[240,144,128,128])]]:
 add('original-linked-AddrInfo-peek-order-duplicates-and-C-string-decoding','follow',nodes=nodes)
for start in range(0,len(cs),96):
 group=cs[start:start+96];answers=ask([c['request'] for c in group])
 for c,out in zip(group,answers):
  if c['request'].get('hashOutput') and isinstance(out,list):
   c['expected']=dict(count=len(out),sha256=hashlib.sha256(bytes(out)).hexdigest())
   pts=c['request'].pop('points');c['request']['rangeStart']=pts[0];c['request']['rangeCount']=len(pts)
  else:c['expected']=out
m=dict(upstream='network-3.2.9.0 original native library; GHC 9.12.2 original CString/errno',platform=args.platform,abi=abi,abiHash=hashlib.sha256(asset.read_bytes()).hexdigest(),sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k not in ['sources','abi']},indent=2))
