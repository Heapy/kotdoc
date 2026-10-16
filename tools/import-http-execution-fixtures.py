#!/usr/bin/env python3
"""Observe complete original Core execution/history with controlled clock/transport effects."""
import argparse,collections,gzip,hashlib,itertools,json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-http-execution-reference'
target=root/'core/src/test/resources/http-execution/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
s=(root/'vendor/http-client-0.7.19/Network/HTTP/Client/Core.hs').read_text()
a=s.index('module Network.HTTP.Client.Core');b=s.index('import Network.HTTP.Types')
s=s[:a]+'module OriginalExecution where\n\n'+s[b:]
a=s.index('import Network.HTTP.Client.Manager\n');b=s.index('import Data.Maybe',a)
s=s[:a]+"import Network.HTTP.Client.Internal hiding (withResponse, httpLbs, httpNoBody, httpRaw, httpRaw', requestAction, getModifiedRequestManager, responseOpen, responseClose, httpRedirect, httpRedirect', withConnection, handleClosedRead, getResponse)\n"+s[b:]
s=s.replace('import Data.Time\n','import Data.Time hiding (getCurrentTime)\n').replace('import System.Timeout (timeout)','import ExecutionEffects (timeout, getCurrentTime, getResponse)')
c=(root/'vendor/http-client-0.7.19/Network/HTTP/Client.hs').read_text();a=c.index('data HistoriedResponse body');b=c.index('-- | Set the proxy override value',a)
s+='\n'+c[a:b].replace('deriving (Functor, Data.Traversable.Traversable, Data.Foldable.Foldable, Show, Typeable, Generic)','deriving (Show)')
assert (folder/'OriginalExecution.hs').read_text()==s
assert (folder/'HttpRedirectProjection.hs').read_bytes()==(root/'tools/haskell-http-loop-reference/HttpRedirectProjection.hs').read_bytes()
sources=[]
for parent in ['http-manager/data','http-cookie/data']:
 path=root/f'core/src/test/resources/{parent}.jsonl.gz'
 with gzip.open(path,'rt') as f:sources+=list(json.loads(next(f))['sources'])
 sources.append(str(path.relative_to(root)))
sources+=['tools/import-http-execution-fixtures.py']+[str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original HTTP execution observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
cs=[];b=lambda s:list(s.encode('latin1'));h=lambda n,v:[b(n),b(v)]
APIS=['raw','raw-request','open','with','lbs','no-body','history','with-history','connection']
T=1700000000000000000000
req=lambda **x:dict(dict(host=b('example.com'),cookieJar=None),**x)
def wire(status=200,body='ok',headers=(),**extra):
 data=b(f'HTTP/1.1 {status} Message\r\nContent-Length: {len(body)}\r\n'+''.join(n+': '+v+'\r\n' for n,v in headers)+'\r\n'+body)
 return dict(chunks=[data],**extra)
def add(group,api='open',initial=None,connections=None,**extra):
 r=dict(api=api,initial=req() if initial is None else initial,connections=[wire()] if connections is None else connections,**extra)
 proc.stdin.write(json.dumps(r)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,r
 expected=json.loads(line);cs.append(dict(group=group,request=r,expected=expected))
for api,consume,close,prime in itertools.product(APIS,[False,True],[False,True],[False,True]):
 # Distinct read chunks let a keep-alive connection serve the second request.
 add('API-body-ownership-brackets-and-reused-connection',api,connections=[dict(chunks=wire()['chunks']*2)],consume=consume,close=close,prime=prime)
for api,count,n in itertools.product(APIS[:-1],[-2,-1,0,1,2,3,10],[0,1,2,4]):
 conn=[]
 for i in range(n):conn.append(wire(302,'redirect'+str(i),[('Location','http://hop'+str(i)+'.com/next'),('Connection','close')]))
 conn.append(wire(200,'final'))
 add('redirect-limits-and-history-order',api,initial=req(redirectCount=count),connections=conn,consume=True)
for api,n in itertools.product(['open','history','with-history'],[0,1,1023,1024,1025,2048,3072]):
 chunks=[b('HTTP/1.1 302 Found\r\nLocation: http://next.com/\r\nContent-Length: '+str(n)+'\r\n\r\n')]+[b('a'*min(n,1024))]+([b('b'*(n-1024))] if n>1024 else [])
 add('history-first-read-then-loop-second-flush',api,connections=[dict(chunks=chunks),wire()],consume=True)
for api,status,check in itertools.product(APIS[:-1],[199,200,204,299,300,302,304,400,404,500],[False,True]):
 if status==199:continue # no final response after an informational status
 add('status-check-is-only-on-ordinary-open-path',api,initial=req(checkStatus=check,redirectCount=0),connections=[wire(status,'error'*300)],consume=True)
for api,override,policy,suffix in itertools.product(['raw','open','history'],[False,True],['none','append','override','redirect0'],[False,True]):
 config={'none':{},'append':dict(appendHeaders=[h('X-Modify','yes')]),'override':dict(override=True),'redirect0':dict(redirectCount=0)}[policy]
 if suffix:config=dict(config,suffix=b('/modified'))
 add('manager-override-and-mutation-count-order',api,initial=req(override=override),managers=[config,dict(appendHeaders=[h('X-Manager','one')])],consume=True)
for api,phase,tag in itertools.product(APIS,['connectFailure','writeFailure','read','modifyFailure','responseFailure','checkFailure','actionFailure'],['closed','nodata','incomplete','response-timeout','request-closed','vanished','io','async','fatal']):
 conn=wire();initial=req();extra={};managers=[{}]
 if phase in ['connectFailure','writeFailure']:conn[phase]=tag
 elif phase=='read':conn['chunks']=[tag]
 elif phase in ['modifyFailure','responseFailure']:managers=[{phase:tag}]
 elif phase=='checkFailure':initial['checkFailure']=tag
 else:extra['actionFailure']=tag
 add('exception-boundary-'+phase,api,initial=initial,connections=[conn],managers=managers,consume=True,**extra)
for api,tag,phase in itertools.product(['raw','raw-request','open','with','history','with-history'],['closed','nodata','incomplete','response-timeout','request-closed','vanished','io','async','fatal'],['initial-body','redirect-body','final-body']):
 first=wire(302,'x'*2048,[('Location','http://next.com/final')])
 final=wire(200,'z'*2048)
 def split(c):
  raw=bytes(c['chunks'][0]);header,body=raw.split(b'\r\n\r\n',1)
  return dict(chunks=[list(header+b'\r\n\r\n'),list(body[:1]),tag])
 if phase=='initial-body':connections=[split(final)]
 elif phase=='redirect-body':connections=[split(first),wire()]
 else:connections=[first,split(final)]
 add('exception-provenance-and-no-retry-after-body-start-'+phase,api,connections=connections,consume=True)
for api,tag,phase in itertools.product(['raw','open','history'],['closed','nodata','incomplete','response-timeout','request-closed','vanished','io','async','fatal'],['read','write']):
 # Prime succeeds and releases. The next send/parse fails only on the reused connection.
 conn=dict(chunks=wire()['chunks']+[tag])
 if phase=='write':conn=dict(chunks=wire()['chunks'],writeFailure=tag,writeFailAt=1)
 add('retry-only-reused-direct-exceptions',api,connections=[conn,wire()],prime=True,consume=True)
for api,different,strip,count in itertools.product(['open','history','with-history'],[False,True],[False,True],[0,1,2]):
 initial=req(headers=[h('Authorization','secret')],onlyDifferent=different,strip=dict(kind='names',names=[b('Authorization')]) if strip else dict(kind='none'),redirectCount=count,cookieJar=[])
 # Same-host redirect preserves a live pool connection; next host exercises the source's original-host comparison.
 chain=[wire(302,'first',[('Location','/two'),('Set-Cookie','sid=one; Path=/')]),wire(307,'second',[('Location','http://other.com/three'),('Set-Cookie','next=two; Path=/')]),wire(200,'final')]
 add('original-ClientSpec-strip-headers-and-cookies-across-history',api,initial=initial,connections=[dict(chunks=chain[0]['chunks']+chain[1]['chunks']),chain[2]],consume=True)
for api,jar,secure,setting in itertools.product(['raw-request','open','history'],[None,[]],[False,True],['session','expire','path','domain','secure','multiple']):
 values={'session':['a=1; Path=/'],'expire':['a=gone; Max-Age=-1'],'path':['a=1; Path=/else'],'domain':['a=1; Domain=else.com'],'secure':['a=1; Secure'],'multiple':['a=1; Path=/','b=2; Path=/']}[setting]
 add('cookies-clock-selection-update-and-Set-Cookie-retention',api,initial=req(cookieJar=jar,secure=secure),connections=[wire(headers=[('Set-Cookie',x) for x in values])],consume=True)
for api,requestTimeout,managerTimeout,spent in itertools.product(['raw','open'],['default','none',-1,0,10000000],['default','none',-1,0,20000000],[0,1,499999,500000,500001,1500000,100000000000000]):
 add('timeout-selection-exact-picoseconds-and-bankers-rounding',api,initial=req(timeout=requestTimeout),managers=[dict(timeout=managerTimeout)],times=[T,T+spent],consume=True)
for api in ['raw','open','history']:
 add('connection-timeout-no-connection',api,initial=req(timeout=1000000),acquireExpires=True)
for api,name,value in itertools.product(['raw','open','history'],['X','X\nName'],['safe','a\nb','a\rb']):
 add('header-validation-before-manager-and-exception-wrapper',api,initial=req(headers=[h(name,value)]),consume=True)
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 complete original Core and original responseOpenHistory/withResponseHistory; controlled clock/connection/header-timer effects; GHC 9.12.2',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for v in [m]+cs:gz.write((json.dumps(v,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
