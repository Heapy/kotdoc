#!/usr/bin/env python3
"""Unchanged http-client socket orchestration, original async, controlled effects."""
import argparse,collections,gzip,hashlib,itertools,json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-http-socket-reference';target=root/'core/src/test/resources/http-socket/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
s=(root/'vendor/http-client-0.7.19/Network/HTTP/Client/Connection.hs').read_bytes()
s=s.replace(b'module Network.HTTP.Client.Connection',b'module OriginalSocketConnection').replace(b'    ( connectionReadLine',b'    ( firstSuccessful\n    , openSocket\n    , connectionReadLine')
s=s.replace(b'import Control.Concurrent\n',b'import Control.Concurrent hiding (threadDelay)\n')
s=s.replace(b'import Network.HTTP.Client.Types',b'import Network.HTTP.Client.Internal (Connection (..), MaxHeaderLength (..), HttpExceptionContent (..), throwHttp)')
s=s.replace(b'import Network.Socket (Socket, HostAddress)',b'import SocketEffects (Socket, HostAddress)').replace(b'import qualified Network.Socket as NS',b'import qualified SocketEffects as NS')
s=s.replace(b'import Network.Socket.ByteString (sendAll, recv)',b'import SocketEffects (sendAll, recv, threadDelay)')
assert (folder/'OriginalSocketConnection.hs').read_bytes()==s
m=(root/'vendor/http-client-0.7.19/Network/HTTP/Client/Manager.hs').read_bytes()
body=m[m.index(b'rawConnectionModifySocket ::'):m.index(b'-- | Default value for')]
header=b'module OriginalSocketFactories where\nimport qualified SocketEffects as NS\nimport OriginalSocketConnection (openSocketConnection, openSocketConnectionSize)\nimport Network.HTTP.Client.Internal (Connection)\n\n'
assert (folder/'OriginalSocketFactories.hs').read_bytes()==header+body
with gzip.open(root/'core/src/test/resources/http-manager/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/http-manager/data.jsonl.gz','tools/import-http-socket-fixtures.py']
for name in ['network-3.2.9.0','async-2.2.6']:
 base=root/'vendor'/name;provenance=json.loads((base/'provenance.json').read_text())
 assert {str(f.relative_to(base)):hashlib.sha256(f.read_bytes()).hexdigest() for f in base.rglob('*') if f.is_file() and f.name!='provenance.json'}==provenance['files']
 sources += [str(f.relative_to(root)) for f in base.rglob('*') if f.is_file()]
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]+['licenses/Network-BSD-3-Clause.txt','licenses/Async-BSD-3-Clause.txt']
assert (root/'licenses/Network-BSD-3-Clause.txt').read_bytes()==(root/'vendor/network-3.2.9.0/LICENSE').read_bytes()
assert (root/'licenses/Async-BSD-3-Clause.txt').read_bytes()==(root/'vendor/async-2.2.6/LICENSE').read_bytes()
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original socket/race observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle,'+RTS','-N2','-RTS'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
cs=[]
def add(group,api,**args):
 r=dict(api=api,**args);proc.stdin.write(json.dumps(r)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,r
 expected=json.loads(line)
 if r.get('blocked'):assert expected['result']==dict(blocked=True),(r,expected)
 cs.append(dict(group=group,request=r,expected=expected))
def step(phase,i,awaitOnly=False):return dict(phase=phase,id=i,awaitOnly=awaitOnly)
def prepare(n,release=True):
 steps=[step('attempt',0,True)] if n else []
 steps += [step('delay',i,True) for i in range(1,n)]
 if release:
  steps += [step('delay',i) for i in range(1,n)]
  steps += [step('attempt',i,True) for i in range(1,n)]
 return steps
for host,port,address in itertools.product(['localhost','127.0.0.1','[::1]','[v2.huh]','[::1]tail','','a\x00\xff'],[-2**63,-1,0,80,65535,65536,2**63-1],[None,0,0x0100007f,2**32-1]):
 add('resolve-or-lazy-checked-explicit-IPv4-port','with',host=host,port=port,address=address,attempts=[{}],blocked=address is not None and not 0<=port<=65535)
for tag in ['io','fatal','async']:
 add('resolver-failure-propagates-without-socket','with',resolveFailure=tag,attempts=[{}])
for api in ['racer','with']:add('empty-addresses-source-error',api,attempts=[])
for phase,tag in itertools.product(['create','no-delay','tweak','connect','final'],['io','fatal','async']):
 add('bracket-on-error-and-final-action-error','with',attempts=[dict(failPhase=phase,failure=tag)],blocked=phase!='final' and tag!='io')
for phase,tag in itertools.product(['receive','send','close'],['io','fatal','async']):
 add('connection-error-close-idempotence-and-closed-guards','open',attempts=[dict(failPhase=phase,failure=tag)],commands=[dict(op='write',bytes=list(range(256))),dict(op='read'),dict(op='close'),dict(op='close'),dict(op='read'),dict(op='write',bytes=[]),dict(op='unread',bytes=[])])
for size,default,factory in itertools.product([-2**63,-1,0,1,8192,2**63-1],[False,True],[False,True]):
 add('socket-connection-recv-send-unread-and-manager-factories','open',chunkSize=size,defaultSize=default,managerFactory=factory,attempts=[{}],commands=[dict(op='write',bytes=list(range(256))),dict(op='read'),dict(op='unread',bytes=[9,8]),dict(op='read'),dict(op='read'),dict(op='close'),dict(op='read')])
# Fail all addresses in several completion orders; the last INPUT error wins.
for n in range(1,6):
 for order in sorted(set([tuple(range(n)),tuple(reversed(range(n))),tuple(range(1,n))+tuple(range(min(n,1)))])):
  steps=prepare(n)
  for i in order:steps += [step('attempt',i),step('finished',i,True)]
  add('all-IO-failures-select-last-input-address','racer',attempts=[dict(result='io') for _ in range(n)],controlled=True,steps=steps)
for n in range(1,6):
 for winner in range(n):
  for before in [[],[i for i in range(n) if i!=winner]]:
   steps=prepare(n)
   for i in before:steps += [step('attempt',i),step('finished',i,True)]
   steps += [step('attempt',winner),step('finished',winner,True)]
   add('first-success-wins-and-cancels-remaining-callbacks','racer',attempts=[dict(result='success' if i==winner else 'io') for i in range(n)],controlled=True,steps=steps)
for n in range(1,5):
 for fatal,tag in itertools.product(range(n),['fatal','async']):
  add('non-IO-child-error-does-not-fill-parent-result','racer',attempts=[dict(result=tag if i==fatal else 'io') for i in range(n)],controlled=True,steps=prepare(n)+[step('attempt',fatal),step('finished',fatal,True)],blocked=True)
for n,delayed in itertools.product(range(1,6),[False,True]):
 add('external-cancellation-joins-callbacks-and-delays','racer',attempts=[{} for _ in range(n)],controlled=True,steps=prepare(n,not delayed),interrupt=True)
for n in range(1,5):
 for winner in range(n):
  steps=prepare(n)
  add('withSocket-loser-connect-brackets-close','with',attempts=[dict(ipv6=i%2==1) for i in range(n)],controlled=True,steps=steps+[step('attempt',winner)])
for n,delayed in itertools.product(range(1,5),[False,True]):
 add('withSocket-cancel-closes-open-attempts','with',attempts=[{} for _ in range(n)],controlled=True,steps=prepare(n,not delayed),interrupt=True)
for tag in ['io','fatal','async']:
 add('cleanup-error-overrides-connect-error','with',attempts=[dict(result='io',failPhase='close',failure=tag)],blocked=tag!='io')
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 unchanged Connection orchestration; async-2.2.6 original concurrency; controlled socket/delay effects; GHC 9.12.2',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
