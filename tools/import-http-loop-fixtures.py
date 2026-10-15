#!/usr/bin/env python3
"""Independent observations of actual http-client redirect loops and errors."""
import argparse,collections,gzip,hashlib,itertools,json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-http-loop-reference';target=root/'core/src/test/resources/http-loop/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
projection=(root/'tools/haskell-http-redirect-reference/HttpRedirectReference.hs').read_bytes().split(b'run :: Value -> Value')[0].replace(b'module Main where',b'module HttpRedirectProjection where')
assert (folder/'HttpRedirectProjection.hs').read_bytes()==projection
with gzip.open(root/'core/src/test/resources/http-redirect/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/http-redirect/data.jsonl.gz','tools/import-http-loop-fixtures.py']
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
    assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
    assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
    print('Verified',len(cs),'original HTTP redirect-loop observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def ask(v):
    proc.stdin.write(json.dumps(v)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,v
    return json.loads(line)
cs=[];b=lambda s:list(s.encode('latin1'));h=lambda n,v:[b(n),b(v)]
def req(host='example.com',**extra):return dict(host=b(host),**extra)
def chunk(n,byte=65):return dict(size=n,byte=byte)
def response(redirect=True,**extra):return dict(dict(redirect=redirect,chunks=[b('body')]),**extra)
def add(group,api='extended',count=2,responses=None,initial=None,**extra):
    r=dict(dict(api=api,count=count,responses=[response()] if responses is None else responses,initial=req() if initial is None else initial),**extra)
    cs.append(dict(group=group,request=r,expected=ask(r)))
limits=[-(2**63),-2,-1,0,1,2,3,4,5,10,2**63-1]
for api,count,n,pattern in itertools.product(['simple','extended'],limits,[0,1,2,3,5],['all','last-final','first-final','middle-final']):
    rs=[response(redirect=not(pattern=='last-final' and i==n-1 or pattern=='first-final' and i==0 or pattern=='middle-final' and i==n//2),next=req('hop'+str(i),method=b('PUT'),redirectCount=-99,path=b('/'+str(i))),jar=[b('cookie'+str(i))],headers=[h('X-Step',str(i))],early=[h('Link','hint'+str(i))]) for i in range(n)]
    add('limits-callback-APIs-final-request-and-reverse-history',api,count,rs)
shapes=[[],[[]],[chunk(1)],[chunk(1023)],[chunk(1024)],[chunk(1025)],[chunk(2048)],[chunk(512),chunk(512,66),b('unread')],
 [chunk(1),chunk(1023,66),b('unread')],[chunk(1023),chunk(2,66),b('unread')],[chunk(2048),b('unread')],[b('a'),[],b('after-eof')],[chunk(1,0)]*1025]
for api,count,shape,final in itertools.product(['simple','extended'],[-1,0,1,2],shapes,[False,True]):
    rs=[response(chunks=shape),response(redirect=not final,chunks=[b('last')])]
    add('flush-whole-chunks-1024-threshold-EOF-and-overshoot',api,count,rs)
tags=['content-closed','request-closed','vanished','content-timeout','request-timeout','content-too-many','request-too-many','request-invalid-status','io','script-failure','async']
for api,count,tag,phase in itertools.product(['simple','extended'],[-1,0,1,2],tags,['callback','first-read','partial-read','after-threshold','close','preclose','final-read']):
    rs=[response(),response(False,chunks=[b('final')])];extra={}
    if phase=='callback':extra=dict(callbackFailAt=0,callbackFailure=tag)
    elif phase=='first-read':rs[0]['chunks']=[tag]
    elif phase=='partial-read':rs[0]['chunks']=[b('partial'),tag]
    elif phase=='after-threshold':rs[0]['chunks']=[chunk(1024),tag]
    elif phase=='close':rs[0]['closeFailure']=tag
    elif phase=='preclose':rs[0].update(preclose=True,onClosed=tag)
    else:rs=[response(False,chunks=[b('partial'),tag])];extra=dict(consumeFinal=True)
    add('exception-boundary-'+phase,api,count,rs,**extra)
for api,preclose,consume,close,yes in itertools.product(['simple','extended'],[False,True],[False,True],[False,True],[False,True]):
    add('final-body-close-ownership-and-preclosed-redirect',api,0,[response(yes,preclose=preclose,chunks=[b('one'),b('two')])],consumeFinal=consume,closeFinal=close)
for byte,yes in itertools.product(range(256),[False,True]):
    r=req('raw\xff',method=[byte],headers=[[[byte],[byte]]],body=dict(kind='io'),onlyDifferent=True,redirectCount=0)
    add('every-byte-body-header-status-and-original-response-Show',count=0,initial=r,responses=[response(yes,chunks=[[byte]],message=[byte],headers=[[[byte],[byte]]],early=[h('Link',str(byte))],jar=[[byte]],version=[-byte,byte])],consumeFinal=not yes)
for count,show in itertools.product([-1,0,1],[False,True]):
    add('latent-cookie-expiry-not-forced-by-exception-construction',count=count,responses=[response(latentCookie=True),response(False)],withShow=show)
for tag in tags:
    add('later-callback-failure-after-history-does-not-close-final',count=4,responses=[response(),response(False)],callbackFailAt=1,callbackFailure=tag)
for api in ['simple','extended']:
    n=10000
    add('ten-thousand-hop-stack-and-order',api,n,[response(chunks=[],next=req(path=b('/'+str(i)))) for i in range(n)]+[response(False,chunks=[b('final')])],withShow=False)
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 httpRedirect/httpRedirect\'/handleClosedRead and original Show',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
