#!/usr/bin/env python3
"""Observe original http-client Manager; no conversion algorithm is rewritten here."""
import argparse, collections, gzip, hashlib, itertools, json, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
folder=root/'tools/haskell-http-manager-reference'
target=root/'core/src/test/resources/http-manager/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=root/'vendor/http-client-0.7.19';copied=folder/'http-client-0.7.19'
# Only export-list/package visibility edits; every implementation/test byte is original.
changes={
 'Network/HTTP/Client/Manager.hs':lambda s:s.replace(b'    , newManager\n',b'    , newManager\n    , connKey\n    , mkCreateConnection\n'),
 'Network/HTTP/Client/Types.hs':lambda s:s.replace(b'    , unHttpExceptionContentWrapper\n',b'    , HttpExceptionContentWrapper (..)\n'),
 'http-client.cabal':lambda s:s.replace(b'                       Data.KeyedPool\n',b'').replace(b'  other-modules:',b'                       Data.KeyedPool\n  other-modules:',1),
}
assert {str(f.relative_to(original)) for f in original.rglob('*') if f.is_file()}=={str(f.relative_to(copied)) for f in copied.rglob('*') if f.is_file()}
for f in original.rglob('*'):
 if f.is_file():
  name=str(f.relative_to(original));assert (copied/name).read_bytes()==changes.get(name,lambda s:s)(f.read_bytes()),name
sources=[]
for parent in ['http-loop/data','keyed-pool/data','ghc-environment/macos-aarch64','ghc-environment/linux-x86_64','http-proxy/data']:
 path=root/f'core/src/test/resources/{parent}.jsonl.gz'
 with gzip.open(path,'rt') as f:sources+=list(json.loads(next(f))['sources'])
 sources.append(str(path.relative_to(root)))
sources+=['tools/import-http-manager-fixtures.py']+[str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original Manager observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
cs=[]
def add(group,api,**args):
 r=dict(api=api,**args);proc.stdin.write(json.dumps(r)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,r
 cs.append(dict(group=group,request=r,expected=json.loads(line)))
b=lambda s:list(s.encode('latin1'))
proxy=dict(host=b('proxy'),port=8080)
headers=[[[*b('pRoXy-AuThOrIzAtIoN')],b('first')],[b('X-Test'),b('kept')],[b('Proxy-Authorization'),b('second')]]
hosts=[[],b('a'),b('[::1]'),b('A\x00\xff\n'),list(range(256))]
ports=[-2**63,-1,0,80,443,65535,65536,2**63-1]
for host,port,secure,addr,prox,mode in itertools.product(hosts,ports,[False,True],[None,0,2**32-1],[None,proxy],[False,True]):
 add('request-key-and-proxy-auth-filter','key',request=dict(host=host,port=port,secure=secure,address=addr,proxy=prox,connect=mode,headers=headers,latentBody=True))
keys=[]
for kind,host,port,addr in itertools.product(['raw','secure'],[[],b('a'),b('A\xff')],[-1,0,443],[None,0,2**31,2**32-1]):keys.append(dict(kind=kind,host=host,port=port,address=addr))
for host,port,auth,ultimate,up in itertools.product([[],b('p')],[-1,80],[None,[],b('x')],[[],b('u')],[-1,443]):keys.append(dict(kind='proxy',host=host,port=port,auth=auth,ultimateHost=ultimate,ultimatePort=up))
# All adjacent/constructor boundaries plus a deterministic cross-product sample.
for i,left in enumerate(keys):
 for j,right in enumerate(keys):
  if i==j or abs(i-j)==1 or (i*37+j*17)%31==0:add('derived-key-Eq-Ord','compare',left=left,right=right)
add('source-defaults','defaults')
tags=['io','async','incomplete','nodata','timeout','tls','closed','request-nodata','request-incomplete','nested-connection','nested-internal','fatal']
for tag in tags:
 add('retry-direct-exception-type','retry',exception=tag)
 add('wrap-only-IO-exceptions','wrap',request=dict(latentBody=True),exception=tag)
add('wrap-return-value','wrap',request=dict(latentBody=True),result=dict(value=[1,None,'ok']))
for policy,secure,mode in itertools.product(['request','none','use','without-connect'],[False,True],[False,True]):
 add('proxy-override-preserves-deferred-request','override',policy=policy,secure=secure,proxy=proxy,requests=[dict(proxy=p,secure=s,connect=mode,headers=headers,latentBody=True) for p,s in itertools.product([None,dict(host=b('old'),port=-1)],[False,True])])
for kind in ['raw','secure']:
 for host,port,addr in itertools.product(hosts,ports,[None,0,2**32-1]):
  add('direct-factory-Latin1-host-and-raw-port','factory',keys=[dict(kind=kind,host=host,port=port,address=addr)])
for auth,host,port in itertools.product([None,[],b('Basic x'),list(range(256))],hosts,ports):
 add('CONNECT-wire-bytes','factory',keys=[dict(kind='proxy',host=b('proxy\xff'),port=-1,auth=auth,ultimateHost=host,ultimatePort=port)])
raw=dict(kind='raw',host=b('a'),port=80);tls=dict(kind='secure',host=b('a'),port=443)
connect=dict(kind='proxy',host=b('p'),port=8080,ultimateHost=b('u'),ultimatePort=-1)
for phase,tag,api in itertools.product(['http','https','raw','tls','proxy'],tags,['factory','manager']):
 add('construction-effect-order-and-exception-identity',api,keys=[raw,tls,connect],commands=[],**{phase:dict(initFailure=tag)})
for kind,tag in itertools.product(['raw','tls','proxy'],tags):
 add('connect-error-wrapping','factory',keys=[{'raw':raw,'tls':tls,'proxy':connect}[kind]],**{kind:dict(failure=tag)})
for ks in [[tls],[connect],[raw,tls,connect]]:add('default-TLS-is-unsupported','factory',defaultTls=True,keys=ks)
responses=[b('HTTP/1.1 200 OK\r\n\r\n'),b('HTTP/1.0 200 unusual\r\n\r\nbody'),b('HTTP/1.1 201 OK\r\n\r\n'),b('HTTP/1.1 407 denied\r\nX: y\r\n\r\n'),b('HTTP/1.1 -1 bad\r\n\r\n'),b('HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\n\r\n'),b('HTTP/1.1 103 Early\r\nLink: x\r\n\r\nHTTP/1.1 200 OK\r\n\r\n'),b('bad\r\n\r\n'),[],b('HTTP/1.1 200 OK\r\nX: y\r\n')]
for response in responses:
 for split in [None,0,1,len(response)//2,max(0,len(response)-1)]:
  chunks=[response] if split is None else [response[:split],response[split:]]
  add('CONNECT-parser-status-interim-EOF-and-chunk-boundaries','factory',keys=[connect],proxy=dict(chunks=chunks))
for count in [0,1,2,3]:
 add('connector-controls-parser-callback','factory',keys=[connect],proxy=dict(parseCount=count,chunks=[responses[0]+responses[0]]))
for tag in tags:add('CONNECT-read-error-wrapping','factory',keys=[connect],proxy=dict(chunks=[tag]))
for limit,nh,fragment in itertools.product([-1,0,1,15,16,17,4096],[0,1,2,3,100],[False,True]):
 response=b('HTTP/1.1 200 OK\r\nX: y\r\n\r\n')
 add('CONNECT-configured-header-limits','factory',keys=[connect],maxLength=limit,maxHeaders=nh,proxy=dict(chunks=[[c] for c in response] if fragment else [response]))
for unlimitedLength,unlimitedHeaders in itertools.product([False,True],repeat=2):
 add('CONNECT-unlimited-header-settings','factory',keys=[connect],maxLength=0,maxHeaders=0,unlimitedLength=unlimitedLength,unlimitedHeaders=unlimitedHeaders)
cmd=lambda op,**extra:dict(op=op,**extra)
take=lambda name,request:cmd('take',request=request,**{'as':name})
release=lambda name,reuse=True:cmd('release',handle=name,reuse=reuse)
requests=[dict(host=b('a')),dict(host=b('A')),dict(host=b('a'),address=0),dict(host=b('a'),secure=True),dict(host=b('a'),secure=True,proxy=proxy,headers=headers)]
for per,total,reverse in itertools.product([-1,0,1,2,10],[-1,0,1,2,512],[False,True]):
 rs=[requests[0],requests[0],requests[1],requests[0],requests[2],requests[3],requests[4]]
 order=list(range(len(rs)));order=order[::-1] if reverse else order
 commands=[take('a'+str(i),r) for i,r in enumerate(rs)]+[release('a'+str(i)) for i in order]+[cmd('close')]
 commands += [take('b'+str(i),r) for i,r in enumerate(rs)]+[release('b'+str(i),False) for i in range(len(rs))]
 add('actual-pool-idle-limits-key-partition-LIFO-closeManager-no-op','manager',count=per,idle=total,commands=commands)
for reuse in itertools.product([False,True],repeat=3):
 add('actual-pool-release-is-once','manager',commands=[take('a',requests[0])]+[release('a',x) for x in reuse]+[take('b',requests[0]),release('b',False)])
for host,secure,prox in itertools.product([[],b('a')],[False,True],[None,proxy]):
 add('empty-ultimate-host-rejected-before-pool','manager',commands=[take('a',dict(host=host,secure=secure,proxy=prox))])
for tag,per,total in itertools.product(tags,[0,1],[0,1]):
 add('pool-overflow-close-errors-and-duplicate-release','manager',count=per,idle=total,raw=dict(closeFailure=tag),commands=[take('a',requests[0]),take('b',requests[0]),release('a'),release('b'),release('a',False),release('b',False),take('c',requests[0]),release('c',False)])
for tag in tags:
 add('connection-write-failures-stay-unwrapped','manager',raw=dict(writeFailure=tag),commands=[take('a',requests[0]),cmd('write',handle='a',bytes=list(range(256))),release('a',False),cmd('read',handle='a')])
for http,https in itertools.product([None,proxy],repeat=2):
 add('manager-selects-prepared-proxy-by-request-security','manager',http=dict(proxy=http),https=dict(proxy=https),commands=[cmd('proxy',request=dict(host=b('a'),secure=s,proxy=p,latentBody=True)) for s,p in itertools.product([False,True],[None,dict(host=b('old'),port=-1)])])
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 original complete Manager, actual original KeyedPool, explicit transport effects; GHC 9.12.2',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
