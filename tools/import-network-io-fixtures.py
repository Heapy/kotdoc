#!/usr/bin/env python3
"""Native original network IO, errno guards and verbatim sendAll observations."""
import argparse,collections,gzip,hashlib,itertools,json,subprocess
from pathlib import Path
r=Path(__file__).resolve().parents[1];folder=r/'tools/haskell-network-io-reference';original=r/'vendor/network-3.2.9.0';copied=folder/'network-3.2.9.0'
p=argparse.ArgumentParser();p.add_argument('--platform',required=True,choices=['macos-64','linux-64']);p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');a=p.parse_args()
target=r/f'core/src/test/resources/network-io/{a.platform}.jsonl.gz';asset=r/f'core/src/main/resources/io/heapy/kotdoc/http/network-io-abi-{a.platform}.json'
paths=[f for f in original.rglob('*') if f.is_file()]
assert {f.relative_to(copied) for f in copied.rglob('*') if f.is_file()}=={f.relative_to(original) for f in paths}
for f in paths:
 raw=f.read_bytes();rel=f.relative_to(original)
 if str(rel)=='network.cabal':raw=raw.replace(b'        Network.Socket.Options\n',b'').replace(b'    exposed-modules:\n',b'    exposed-modules:\n        Network.Socket.Options\n',1)
 assert (copied/rel).read_bytes()==raw,rel
source=(original/'Network/Socket/ByteString/IO.hsc').read_text();snippet=source[source.index('send :: Socket'):source.index('-- | Send data to the socket.  The recipient can be specified',source.index('sendAll s bs0'))]
assert (folder/'OriginalSend.hs').read_text().endswith(snippet)
pin=json.loads((r/'vendor/ghc-socket-9.12.2/SOURCE.json').read_text());assert pin['revision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc'
for name,h in pin['files'].items():assert hashlib.sha256((r/'vendor/ghc-socket-9.12.2'/name).read_bytes()).hexdigest()==h
with gzip.open(r/'core/src/test/resources/network-address'/f'{a.platform}.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=set(parent['sources'])|{f'core/src/test/resources/network-address/{a.platform}.jsonl.gz',f'core/src/main/resources/io/heapy/kotdoc/http/network-abi-{a.platform}.json','tools/import-network-io-fixtures.py'}
sources|={str(f.relative_to(r)) for f in folder.rglob('*') if f.is_file()}
sources|={str(f.relative_to(r)) for f in (r/'vendor/ghc-socket-9.12.2').rglob('*') if f.is_file()}
def hashes():return {s:hashlib.sha256((r/s).read_bytes()).hexdigest() for s in sorted(sources)}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs) and m['platform']==a.platform
 assert m['abi']==json.loads(asset.read_text()) and m['abiHash']==hashlib.sha256(asset.read_bytes()).hexdigest()
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original native IO observations,',a.platform,'and',len(sources),'source hashes');raise SystemExit
assert a.oracle
def ask(requests):
 p=subprocess.run([a.oracle],input=''.join(json.dumps(x)+'\n' for x in requests),text=True,capture_output=True,timeout=120)
 assert p.returncode==0,p.stderr
 xs=[json.loads(x) for x in p.stdout.splitlines()];assert len(xs)==len(requests),(len(xs),len(requests),p.stderr);return xs
abi=ask([dict(api='abi')])[0];assert 'error' not in abi,abi
asset.parent.mkdir(parents=True,exist_ok=True);asset.write_text(json.dumps(abi,sort_keys=True,indent=2)+'\n')
cs=[]
def add(group,api,**kw):cs.append(dict(group=group,request=dict(api=api,**kw)))
errors=[0,abi['EINTR'],abi['EAGAIN'],abi['EWOULDBLOCK'],abi['EBADF'],1,2,13,22,32,2**31-1]
for mode,prefix,last in itertools.product(['once','retry','block'],[[],[abi['EINTR']],[abi['EAGAIN']],[abi['EINTR'],abi['EAGAIN']],[abi['EAGAIN'],abi['EINTR'],abi['EAGAIN']]],errors):
 add('original-errno-guard-retry-and-wait-order','guard',mode=mode,script=[[-1,e] for e in prefix+[last]]+[[23,0]])
for mode,n in itertools.product(['once','retry','block'],[-2**31,-2,0,1,2**31-1]):add('non-minus-one-guard-results','guard',mode=mode,script=[[n,abi['EBADF']]])
for write in [False,True]:add('pending-original-GHC-event-manager-invalid-registration','wait-invalid',write=write)
for data in [[],[0],[65,66,67],list(range(256))]:
 n=len(data)
 for script in [[[0,n]],[[0,0],[0,n]],[[0,-2],[0,n]],[[0,n+1],[0,0]],[[1,abi['EBADF']]]]:
  add('verbatim-sendAll-drop-short-write-zero-wait-and-errors','send-script',bytes=data,script=script)
 if n>1:
  for i in range(1,min(n,10)):add('verbatim-sendAll-every-short-prefix','send-script',bytes=data,script=[[0,i],[0,n-i]])
for operation in ['recv','send','sendAll','set','get','connect','bind','listen','name','peer','shutdown']:
 for size in [-2**63,-1,0,1,17]:
  if operation!='recv' and size!=1:continue
  for data in ([[],[65,0,255]] if operation in ['send','sendAll'] else [[]]):add('actual-invalid-socket-and-recv-validation','invalid',operation=operation,size=size,bytes=data)
for family,typ,protocol in itertools.product([-1,0,1,2,30 if a.platform=='macos-64' else 10,300],[-1,0,1,2,300],[0,6,17,-1]):
 # No raw/packet sockets or elevated permissions; all successful sockets are closed.
 add('actual-socket-creation-default-options-nonblock-and-cloexec','socket',family=family,type=typ,protocol=protocol)
for data,size in itertools.product([[],[0],[65,0,255],list(range(256))],[1,3,22,8192]):add('actual-TCP-sendAll-recv-and-half-close','tcp',bytes=data,size=size)
for start in range(0,len(cs),96):
 group=cs[start:start+96]
 for c,result in zip(group,ask([c['request'] for c in group])):c['expected']=result
m=dict(upstream='network-3.2.9.0 original native IO and sendAll; GHC 9.12.2 errno guards',platform=a.platform,abi=abi,abiHash=hashlib.sha256(asset.read_bytes()).hexdigest(),sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for x in [m]+cs:gz.write((json.dumps(x,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k not in ['sources','abi']},indent=2))
