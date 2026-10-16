#!/usr/bin/env python3
"""Independent original http-client-tls manager/digest/SOCKS and TLS-error observations."""
import argparse,collections,concurrent.futures,gzip,hashlib,itertools,json,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-http-tls-reference'
target=root/'core/src/test/resources/http-tls/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
manifest=json.loads((root/'vendor/http-tls-sources.json').read_text())
for name,m in manifest.items():
 base=root/'vendor'/f'{name}-{m["version"]}'
 assert {str(f.relative_to(base)) for f in base.rglob('*') if f.is_file()}==set(m['files'])
 for path,digest in m['files'].items():assert hashlib.sha256((base/path).read_bytes()).hexdigest()==digest,path
 rev=folder/'revisions'/f'{name}-{m["version"]}.cabal'
 assert hashlib.sha256(rev.read_bytes()).hexdigest()==m['cabalRevision']['sha256']
s=(root/'vendor/http-client-tls-0.4.0/Network/HTTP/Client/TLS.hs').read_bytes();a=s.index(b'module Network.HTTP.Client.TLS');b=s.index(b'import Control.Applicative')
s=s[:a]+b'module OriginalHttpTls where\n\n'+s[b:]
s=s.replace(b'import System.Environment (getEnvironment)',b'import TlsEffects (getEnvironment, defaultManagerSettings, newManager, httpNoBody)')
s=s.replace(b'hiding (host, port)',b'hiding (host, port, defaultManagerSettings, newManager, httpNoBody)').replace(b'import qualified Network.Connection as NC',b'import qualified ConnectionEffects as NC')
assert (folder/'OriginalHttpTls.hs').read_bytes()==s
s=(root/'vendor/tls-2.4.6/Network/TLS/Error.hs').read_bytes().replace(b'module Network.TLS.Error where',b'module OriginalTlsError where').replace(b'import Network.TLS.Imports',b'import Data.Word (Word8)')
assert (folder/'OriginalTlsError.hs').read_bytes()==s
assert (folder/'HttpRedirectProjection.hs').read_bytes()==(root/'tools/haskell-http-loop-reference/HttpRedirectProjection.hs').read_bytes()
with gzip.open(root/'core/src/test/resources/http-execution/data.jsonl.gz','rt') as f:sources=list(json.loads(next(f))['sources'])
sources+=['core/src/test/resources/http-execution/data.jsonl.gz','vendor/http-tls-sources.json','tools/import-http-tls-fixtures.py']
for base in [folder]+[root/'vendor'/f'{n}-{m["version"]}' for n,m in manifest.items()]:sources += [str(f.relative_to(root)) for f in base.rglob('*') if f.is_file()]
sources += ['licenses/http-client-tls-MIT.txt','licenses/crypton-connection-BSD-3-Clause.txt','licenses/crypton-x509-system-BSD-3-Clause.txt','licenses/tls-BSD-3-Clause.txt']
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original TLS-adapter/digest/error observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
cs=[];b=lambda s:list(s.encode('latin1'));cp=lambda s:list(map(ord,s));h=lambda n,v:[b(n),b(v)]
def add(group,api,**v):cs.append(dict(group=group,request=dict(api=api,**v)))
for scheme,host,port,suffix in itertools.product(['socks5','socks5h','SOCKS5','http','socks4',''],['proxy','127.0.0.1','[::1]','','u:p@proxy'],['',':',':0',':1',':65535',':65536',':65537',':-1',':+1',':99999999999999999999999999999999999'],['','/','/path','?','#']):
 add('SOCKS-scheme-authority-port-Word16-overflow-and-suffix','socks',environment=[['http_proxy',scheme+'://'+host+port+suffix]],name='http_proxy')
for keys in [['HTTP_PROXY'],['http_proxy','HTTP_PROXY'],['HTTP_PROXY','http_proxy'],['HtTp_PrOxY','HTTP_PROXY'],['http_proxy','http_proxy'],['KEY','key'],['İ','i\u0307'],['\ud800','\ufffd']]:
 for vals in itertools.product(['','http://x:3','socks5://x:4','socks5h://x:65536'],repeat=len(keys)):
  for name in ['http_proxy','https_proxy',keys[0].lower()]:
   add('environment-exact-first-lowercase-last-and-Unicode','socks',environment=[[cp(k),cp(v)] for k,v in zip(keys,vals)],name=cp(name))
strings=['', 'text', '\x00\"\\\n\t', 'Русский😀', '\ud800']
for kind,exKind,s in itertools.product(range(10),range(6),strings):
 err=dict(kind=kind,description=cp(s),other=cp(s[::-1]),alert=255)
 ex=dict(kind=exKind,error=err,description=cp(s),fatal=bool(kind%2))
 add('original-TLS-error-exception-Eq-Show-and-precedence','errors',error=err,exception=ex,otherError=err,otherException=ex,level=kind,alert=kind)
for n in range(256):
 err=dict(kind=1,description=cp('x'),alert=n);other=dict(err,alert=(n+1)%256)
 ex=dict(kind=2,error=err);otherex=dict(kind=1,error=other)
 add('every-alert-description-level-and-inequality','errors',error=err,exception=ex,otherError=other,otherException=otherex,level=n,alert=n)
TAGS=['io','vanished','post-eof','handshake-eof','uncontextualized-eof','terminated-eof','post-misc','tls-not-established','tls-missing-handshake','line','resolve','connect','content-closed','request-closed','nodata','incomplete','async','fatal']
for custom,tag in itertools.product([False,True],TAGS):add('TLS-retry-and-wrap-direct-exception-types','wrap',custom=custom,failure=tag,request=dict(host=b('x'),body=dict(kind='io')))
socks=[None,dict(kind='simple',host=cp('socks'),port=1080),dict(kind='environment',name=None),dict(kind='other',host=cp('proxy'),port=-1)]
cmd=lambda op,**v:dict(op=op,**v)
commands=[cmd('read'),cmd('unread',bytes=[1,2]),cmd('read'),cmd('write',bytes=[0,255]),cmd('close'),cmd('close'),cmd('read'),cmd('write',bytes=[])]
for proxy,context,sock,host,port in itertools.product([False,True],[None,42],socks,['host','[::1]','[v1.name]','[]'],[-2**63,-1,0,443,65536,2**63-1]):
 add('factory-host-stripping-port-truncation-context-and-IO-ownership','factory',proxy=proxy,context=context,socks=sock,host=cp(host),port=port,commands=commands,chunks=[[7,8],[9]])
for proxy,phase,tag in itertools.product([False,True],['contextFailure','beforeConnectFailure','connectFailure','writeFailure','secureFailure','checkFailure','closeFailure','read'],TAGS):
 v=dict(proxy=proxy,commands=commands,chunks=[[1],[2]])
 if phase=='read':v['chunks']=[tag]
 else:v[phase]=tag
 add('factory-exception-boundary-'+phase,'factory',**v)
for context,proxy,fail in itertools.product([None,42],[False,True],[None,'io','fatal']):
 add('factory-initialization-before-any-connect','initialize',context=context,proxy=proxy,**({} if fail is None else dict(contextFailure=fail)))
for proxy,close,read in itertools.product([False,True],[False,True],[False,True]):
 add('proxy-check-before-secure-and-preclosed-converted-handle','factory',proxy=proxy,checkClose=close,checkRead=read,chunks=[[1],[2]],commands=commands)
for before,force in itertools.product([None,'io','fatal'],[False,True]):
 add('SOCKS-and-CONNECT-conflict-remains-lazy-inside-connect-effect','factory',proxy=True,socks=socks[1],forceContext=force,**({} if before is None else dict(beforeConnectFailure=before)))
for http,https,custom,context in itertools.product(socks,socks,[False,True],[None,42]):
 add('settings-preserve-fields-reset-raw-and-factories-initialization','manager',commands=[cmd('mk',httpSocks=http,httpsSocks=https,custom=custom,context=context),cmd('connect',manager=0,request=dict(host=b('a'))),cmd('connect',manager=0,request=dict(host=b('a'),secure=True))])
for raw,secure,custom in itertools.product([None,'http://http:80','socks5://http:1080'],[None,'http://https:80','socks5h://https:1080'],[False,True]):
 env=[[k,v] for k,v in [('http_proxy',raw),('https_proxy',secure)] if v is not None]
 add('new-manager-environment-SOCKS-policy-and-with-preserves-user-TLS','manager',environment=env,commands=[cmd('new',**{'with':custom},custom=custom),cmd('proxy',manager=0,request=dict(host=b('a'))),cmd('proxy',manager=0,request=dict(host=b('a'),secure=True)),cmd('connect',manager=0,request=dict(host=b('a'))),cmd('connect',manager=0,request=dict(host=b('a'),secure=True))])
for tag in [None,'io','post-eof','async','fatal']:
 for field in ['contextFailure','environmentFailure']:
  program=[cmd('get'),cmd('get'),cmd('new'),cmd('get')] if tag is not None and field=='environmentFailure' else [cmd('new'),cmd('get'),cmd('get'),cmd('set',manager=0),cmd('get'),cmd('connect',manager=0,request=dict(secure=True)),cmd('connect',manager=0,request=dict(secure=True))]
  add('global-context-and-manager-sharing-and-initialization-failure','manager',commands=program,**({} if tag is None else {field:tag}))
headers=['','Basic realm="a"','Digest','Digest ','dIgEsT realm="a", nonce="b"','Digest realm="a", nonce="b"','Digest nonce=x','Digest realm=x','Digest realm=, nonce=','Digest realm="a,b",nonce="x=y",qop="auth,auth-int",opaque="o"','Digest realm=first,realm=second,nonce=n,qop=','Digest realm="unclosed,nonce=n','Digest Realm=x,nonce=y','Digest realm = x , nonce = y , qop = auth','Digest realm=x,,nonce=y,','Digest =x,realm=x,nonce=y','Digest realm=x,nonce="n"ignored,qop=anything']
for status,header,defer in itertools.product([200,401,403,500],headers,[False,True]):
 add('digest-deferred-validation-details-parser-duplicates-and-quoting','digest',request=dict(host=b('x'),headers=[h('authorization','old'),h('X','keep'),h('Authorization','second')],query=b('?excluded')),user=b('user'),password=b('passwd'),response=dict(status=status,headers=[] if header=='' else [h('WWW-Authenticate',header)]),defer=defer)
for enabled in [None,[]]:
 add('digest-first-auth-header-and-cookie-jar-enablement','digest',request=dict(host=b('x'),cookieJar=enabled),user=b('u'),password=b('p'),response=dict(headers=[h('WWW-Authenticate','Digest realm=r,nonce=n'),h('WWW-Authenticate','Basic')],jar=[b('cookie')]))
 add('digest-first-auth-header-is-not-skipped','digest',request=dict(host=b('x'),cookieJar=enabled),user=b('u'),password=b('p'),response=dict(headers=[h('WWW-Authenticate','Basic'),h('WWW-Authenticate','Digest realm=r,nonce=n')]))
for n in range(256):
 add('digest-every-credential-and-challenge-byte','digest',request=dict(host=b('x'),method=[n],path=[47,n]),user=[n],password=[255-n],response=dict(headers=[[b('WWW-Authenticate'),b('Digest realm="')+[n]+b('",nonce="')+[255-n]+b('",qop="auth",opaque="')+[n]+b('"')]],jar=[b('cookie')]))
for tag in TAGS:add('digest-probe-errors-are-outside-deferred-validation','digest',request=dict(host=b('x'),body=dict(kind='io')),user=[],password=[],probeFailure=tag)
add('original-Spec-digest-authentication-local-challenge','digest',request=dict(host=b('127.0.0.1'),path=b('/digest-auth/qop/user/passwd')),user=b('user'),password=b('passwd'),response=dict(headers=[h('WWW-Authenticate','Digest realm="test",nonce="nonce",qop="auth"')]))
rng=random.Random(0x544c53)
for i in range(200):
 data=bytes(rng.choice(b'realmnonceqopopaque=, "\\\t\r\n') for _ in range(rng.randrange(100)))
 add('digest-seeded-malformed-challenges','digest',request=dict(host=b('x')),user=b('user'),password=b('pass'),response=dict(headers=[[b('WWW-Authenticate'),b('Digest ')+list(data)]]))
def observe(c):
 p=subprocess.run([args.oracle],input=json.dumps(c['request'],ensure_ascii=True)+'\n',text=True,capture_output=True)
 assert p.returncode==0,(c,p.stderr)
 c['expected']=json.loads(p.stdout);return c
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 results=[]
 for i,c in enumerate(pool.map(observe,cs),1):
  results.append(c)
  if i%1000==0:print('Observed',i,'original programs',flush=True)
 cs=results
m=dict(upstream='http-client-tls-0.4.0 complete original module with connection/environment/probe effects; original tls-2.4.6 Error; GHC 9.12.2',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for v in [m]+cs:gz.write((json.dumps(v,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
