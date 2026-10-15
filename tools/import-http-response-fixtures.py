#!/usr/bin/env python3
"""Independent original HTTP response, connection and streaming inflate observations."""
import argparse, collections, gzip, hashlib, itertools, json, subprocess, random
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-http-response-reference';target=root/'core/src/test/resources/http-response/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
for n in ['Headers','Body','Response','Connection']:
    src=(root/f'vendor/http-client-0.7.19/test-nonet/Network/HTTP/Client/{n}Spec.hs').read_text().replace(f'module Network.HTTP.Client.{n}Spec where',f'module Original{n}Spec where')
    assert (folder/f'Original{n}Spec.hs').read_text()==src
src=(root/'vendor/http-client-0.7.19/Network/HTTP/Client/Response.hs').read_text().replace('module Network.HTTP.Client.Response','module OriginalResponse')
for n in ['Types','Request','Body','Headers']:src=src.replace('import Network.HTTP.Client.'+n+'\n','')
src=src.replace('import Network.HTTP.Client.Util','import Network.HTTP.Client.Internal hiding (getResponse,getRedirectedRequest,lbsResponse,getOriginalRequest)').replace('import Data.KeyedPool','import ObserverManaged')
assert (folder/'OriginalResponse.hs').read_text()==src
for name in ['LICENSE','LICENSE.gz']:
    original=root/'vendor/streaming-commons-0.2.3.1'/('test/LICENSE.gz' if name=='LICENSE.gz' else 'LICENSE')
    destination=target.parent/name
    if args.check_source:assert destination.read_bytes()==original.read_bytes()
    else:destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(original.read_bytes())
with gzip.open(root/'core/src/test/resources/http-request/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/http-request/data.jsonl.gz','tools/import-http-response-fixtures.py','core/src/test/resources/http-response/LICENSE','core/src/test/resources/http-response/LICENSE.gz']
for directory in ['tools/haskell-http-response-reference','vendor/streaming-commons-0.2.3.1']:
    sources+=[str(f.relative_to(root)) for f in (root/directory).rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
    assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
    assert len(m['originalTests'])==26 and m['groups']==dict(collections.Counter(c['group'] for c in cs))
    print('Verified',len(cs),'original HTTP response/inflate observations, all 26 original tests and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def ask(v):
    proc.stdin.write(json.dumps(v)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,v
    return json.loads(line)
original=ask(dict(mode='original-tests'));assert len(original)==26 and isinstance(original,list),original
cs=[];b=lambda s:list(s.encode('latin1'));cp=lambda s:list(map(ord,s))
def add(group,**request):cs.append(dict(group=group,request=request,expected=ask(request)))
def split(data,n):return [data[i:i+n] for i in range(0,len(data),n)]
def inp(*strings):return [b(s) for s in strings]
bodygzip,responsegzip=ask(dict(mode='original-gzip-inputs'))
add('original-body-gzip',mode='body',kind='gzip',input=bodygzip,ops=[['consume'],['read']])
add('original-response-gzip',mode='response',input=inp('HTTP/1.1 200 OK\r\n','Key1: Value1\r\n','Content-Encoding: gzip\r\n\r\n')+responsegzip,ops=[['consume'],['close']])
statuslines=['HTTP/1.1 200 OK','HTTP/1.0 200','HTTP/2.0 404 Not Found','HTTP/1.1 +200 yes','HTTP/+1.-2 -1 odd','HTTP/1.1 000200 OK','HTTP/1.1 9223372036854775807 max','HTTP/1.1 9223372036854775808 overflow','HTTP/1.1 -9223372036854775808 min','HTTP/1.1 -9223372036854775809 underflow','HTTP/18446744073709551616.1 200 overflow','HTTP/1.1 200x nope','HTTP/1.1\t200 OK','http/1.1 200 OK','HTTP/1 200 OK','garbage','HTTP/1.1  200   spaces','HTTP/1.1 200\tbad']
for line,n,limit in itertools.product(statuslines,[1,2,7,9999],[None,0,8]):
    add('status-syntax-overflow-and-chunking',mode='headers',input=split(b(line+'\r\n\r\nREST'),n),maxLength=limit)
for h in ['Foo:bar',' Foo :  bar  ','\tFoo\t:\tbar\t','invalid',': empty key','Foo:','Foo:bar:baz','Foo: a\r\rb','Header\x00: a\x00b']:
    for n,limit in itertools.product([1,9999],[None,0,1,2,3]):
        add('header-whitespace-invalid-lines-and-count-limit',mode='headers',input=split(b('HTTP/1.1 200 OK\r\n'+h+'\r\nSecond: x\r\n\r\nREST'),n),maxHeaders=limit)
for prefix,n,cont in itertools.product(['','\r\n','\r\n'*3,'\r\n'*4,'HTTP/1.1 100 Continue\r\n\r\n','HTTP/1.1 100 Continue\r\n\r\n'*2,'HTTP/1.1 103 Early Hints\r\nLink: a\r\n\r\n','HTTP/1.1 103 Hints\r\nLink: a\r\n','HTTP/1.1 103 Hints\r\nLink: a\r\n\r\nHTTP/1.1 100 Continue\r\n\r\n'],[1,5,9999],[False,True]):
    add('blank-status-lines-continue-and-early-hints',mode='headers',input=split(b(prefix+'HTTP/1.1 200 OK\r\nX: y\r\n\r\nREST'),n),**{'continue':cont})
for data in [[],[[]],inp('HTTP/1.1'),inp('HTTP/1.1 200 OK\r\n'),inp('HTTP/1.1 200 OK\r\nX: y'),inp('HTTP/1.1 103 Hints\r\nX: y\r\n'),inp('HTTP/1.1 100 Continue\r\n\r\n')]:
    for cont in [False,True]:add('empty-and-truncated-status-headers',mode='headers',input=data,**{'continue':cont})
for timeout in [0,-1,-100,10000000]:
    for cont in [False,True]:add('system-timeout-zero-negative-and-success',mode='headers',input=inp('HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\n\r\n'),timeout=timeout,**{'continue':cont})
for data,n,limit in itertools.product(['','a','a\n','a\r\n','ab\r\nnext\n','abc\r\r\nnext\n','x'*30+'\n','x'*30], [1,2,8,9999],[None,-1,0,1,10]):
    add('connection-line-consumption-and-length-boundary',mode='line',input=split(b(data),n),maxLength=limit,ops=[['read'],['read'],['unread',b('insert')],['read']])
for data in inp('a','a\n','a\r\n','x\nrest'):
    add('connection-read-line-with-explicit-first-chunk',mode='line',input=inp('tail\r\nrest'),ops=[['with',data],['read']])
for host in ['example.com','127.0.0.1','[::1]','[::127.0.0.1]','[v2.huh]','[::1]foo','[]','[','[V2.huh]','[a]b]','[😀]','[\ud800\udc00]']:
    add('stripped-host-original-and-raw-char-cases',mode='host',host=cp(host))
for operations in [[['read'],['unread',b('x')],['unread',[]],['read'],['read'],['read'],['close'],['close'],['read'],['unread',b('after')],['write',b('after')]], [['close'],['close'],['write',[]],['read']]]:
    add('connection-stack-close-idempotence-and-closed-errors',mode='make',input=inp('a','b'),ops=operations)
for value in [[],[0],[10],[13],[13,10],b('a\nb'),list(range(256))]:
    add('request-header-newline-validation',mode='validate',headers=[[b('X'),value],[b('Bad\nName'),value],[b('Other'),b('ok')]])
bodyops=[['read'],['some',1],['some',0],['some',-1],['consume'],['read'],['read']]
for n,length,data in itertools.product([1,3,9999],[-1,0,1,5,11,12,99],['','hello world','hello world done']):
    add('length-body-chunks-truncation-and-cleanup',mode='body',kind='length',length=length,input=split(b(data),n),ops=bodyops)
for kind,chunks in itertools.product(['const','unlimited'],[[],inp('a','b'),[b('a'),[],b('after')]]):
    add('const-and-unlimited-repeatable-eof-and-read-some',mode='body',kind=kind,input=chunks,ops=bodyops)
chunked=['0\r\n\r\nREST','5\r\nhello\r\n6\r\n world\r\n0\r\nTrailer: yes\r\n\r\nREST','4;extension=x\r\ndata\r\n0\r\n\r\nREST','4 ignored\r\ndata\r\n0\r\n\r\nREST','1\r\nx\n0\n\nREST','4\r\ndataBAD\r\n0\r\n\r\n','4\r\ndat','x\r\n','\r\n',' 4\r\ndata\r\n','-1\r\n','FFFFFFFFFFFFFFFF\r\nREST','8000000000000000\r\n\r\nREST','10000000000000000\r\n\r\nREST','0\r\nTrailer: x']
for data,n,raw in itertools.product(chunked,[1,2,7,9999],[False,True]):
    add('chunked-raw-trailers-overflow-and-malformed-input',mode='body',kind='chunked',raw=raw,input=split(b(data),n),ops=bodyops)
for method,code,version,connection in itertools.product(['GET','HEAD','head'],[100,101,199,200,204,205,304],['1.0','1.1','2.0'],['close','Close','keep-alive']):
    if code==100:continue # standalone 100 expects another response, covered above.
    data=f'HTTP/{version} {code} Message\r\nConnection: {connection}\r\nContent-Length: 3\r\n\r\nabcREST'
    add('response-no-body-and-connection-reuse-policy',mode='response',method=b(method),input=inp(data),ops=[['original'],['read'],['read'],['close'],['close']])
for cl,te,n in itertools.product(['0','3','(3)','0x3','18446744073709551616','-1','bad',' 3 '],['','chunked','CHUNKED','chunked, gzip'],[1,9999]):
    data='HTTP/1.1 200 OK\r\nContent-Length: '+cl+'\r\n'+('Transfer-Encoding: '+te+'\r\n' if te else '')+'\r\n3\r\nabc\r\n0\r\n\r\nREST'
    add('response-length-read-syntax-and-transfer-encoding',mode='response',input=split(b(data),n),ops=[['some',1],['consume'],['close']])
compressed=[]
for data in [[],b('hello'),b('Hello world!')*5000,[10]*(32752*4+1),list(range(256))*300]:
    gz=ask(dict(mode='compress',data=data));compressed.append(gz)
    for size in [1,7,32752,999999]:
        add('gzip-streaming-output-boundaries',mode='body',kind='gzip',input=split(gz,size),ops=[['read']]*8+[['consume'],['read']])
    for raw,mime,encoding in itertools.product([False,True],['text/plain','application/x-tar'],['gzip','GZIP']):
        add('response-gzip-raw-mime-and-header-case',mode='response',raw=raw,input=inp('HTTP/1.1 200 OK\r\nContent-Encoding: '+encoding+'\r\nContent-Type: '+mime+'\r\nContent-Length: '+str(len(gz))+'\r\n\r\n')+[gz],ops=[['consume'],['close']])
gz=compressed[2]
for data in [gz[:i] for i in [0,1,2,9,10,len(gz)//2,len(gz)-8,len(gz)-1]]+[gz[:-8]+[x^255 for x in gz[-8:]],gz[:2]+[255]+gz[3:],b('not gzip'),gz+compressed[1],gz+b('unused')]:
    for size in [1,7,999999]:add('gzip-truncation-errors-members-and-unused-input',mode='body',kind='gzip',input=split(data,size),ops=[['read']]*8+[['consume'],['read']])
def inflate(group,data,bits=31,dictionary=None,chunksize=999999):
    operations=[['complete'],['unused'],['finish']];i=0
    for chunk in split(data,chunksize):
        operations+=[['feed',chunk]]+[['pop',i]]*8+[['complete'],['unused'],['flush']];i+=1
    operations += [['finish'],['flush'],['complete'],['unused']]
    add(group,mode='inflate',bits=bits,**(dict(dictionary=dictionary) if dictionary is not None else {}),ops=operations)
for data,bits in itertools.product([compressed[1],compressed[2],compressed[3],[],b('bad')],[0,15,31,47,-15,100]):inflate('streaming-inflate-feed-pop-flush-finish-unused-complete',data,bits)
for size in [1,7,999999]:inflate('original-license-single-and-multi-inflate',list((root/'vendor/streaming-commons-0.2.3.1/test/LICENSE.gz').read_bytes()),31,chunksize=size)
for dictionary in [b('INITIALDICTIONARY'),[],b('NITIALDICTIONARY')]:
    data=ask(dict(mode='compress',dictionary=b('INITIALDICTIONARY'),data=b('INITIALDICTIONARY hello dictionary')*30))
    inflate('original-dictionary-and-different-dictionary',data,15,dictionary)
data=ask(dict(mode='compress',gzip=False,data=b('This data is stored compressed.')))+b("This data isn't.")
inflate('original-get-unused-inflate',data,15)
for first in ['HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n', 'HTTP/1.1 103 Early Hints\r\nLink: first\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n']:
    data=first+'HTTP/1.1 103 Early Hints\r\nLink: second\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabcREST'
    add('get-original-request-preserves-early-hint-callback',mode='response',input=inp(data),ops=[['again'],['close']])
data=ask(dict(mode='compress',raw=True,data=[10]*(32752*4+1)))
inflate('original-large-raw-inflate',data,-15)
license=(root/'vendor/streaming-commons-0.2.3.1/LICENSE').read_bytes()
data=ask(dict(mode='compress',dictionary=b('INITIALDICTIONARY'),data=list(license)))
inflate('original-different-dictionary-license',data,15,b('NITIALDICTIONARY'))
add('real-response-timeout-during-blocked-read',mode='headers',input=inp('HTTP/1.1 200 OK\r\n\r\n'),readDelay=1200000,timeout=1000000)
add('expect-continuation-outside-header-timeout-scope',mode='headers',input=inp('HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\n\r\n'),continueDelay=1200000,timeout=1000000,**{'continue':True})
for n in range(256):
    lower=n+32 if 65<=n<=90 or 192<=n<=214 or 216<=n<=222 else n
    for value in [[],[n],[255-n]]:
        add('header-ci-equality-and-byte-tuple-order',mode='header-pair',left=[[n],value],right=[[lower],[n]])
for n in range(256):
    for left,right in [([[n],[]],[[(n+1)%256],[]]), ([[n],[]],[[n,0],[]]), ([[n,255],[]],[[n,0],[]])]:
        add('header-case-folded-name-order-and-byte-prefixes',mode='header-pair',left=left,right=right)
proc.stdin.close();assert proc.wait()==0
m=dict(cases=len(cs),caseHash=digest(cs),sources=hashes(),originalTests=original,groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:z.write(('\n'.join(json.dumps(x,separators=(',',':')) for x in [m]+cs)+'\n').encode())
print('Captured',len(cs),'observations;',m['caseHash'])
