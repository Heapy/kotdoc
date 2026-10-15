#!/usr/bin/env python3
"""Independent observations of the actual http-client redirection helper."""
import argparse,collections,gzip,hashlib,itertools,json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-http-redirect-reference';target=root/'core/src/test/resources/http-redirect/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
with gzip.open(root/'core/src/test/resources/http-cookie/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/http-cookie/data.jsonl.gz','tools/import-http-redirect-fixtures.py']
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
    assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
    assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
    print('Verified',len(cs),'original HTTP redirect observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def ask(v):
    proc.stdin.write(json.dumps(v)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,v
    return json.loads(line)
cs=[];b=lambda s:list(s.encode('latin1'));h=lambda name,value:[b(name),b(value)]
def req(host='example.com',**extra):return dict(host=b(host),**extra)
def step(location='/next',status=301,jar=None,headers=None):return dict(status=status,headers=headers if headers is not None else [h('Location',location)],jar=[] if jar is None else jar)
def add(group,original=None,request=None,steps=None):
    r=dict(original=req() if original is None else original,request=req() if request is None else request,steps=[step()] if steps is None else steps)
    cs.append(dict(group=group,request=r,expected=ask(r)))
strict=dict(kind='strict',bytes=b('payload\0\xff'))
initial=req(path=b('/a/b'),query=b('?old=1'),method=b('POST'),body=strict,headers=[h('Content-Type','text/plain'),h('X-Keep','v')])
for status in [-(2**63)]+list(range(-1,701))+[2**63-1]:
    add('every-status-and-Int-boundary',request=initial,steps=[step(status=status)])
locations=['','/','next','../next','../../next','./','..','//other.com/path','http://example.com','https://example.com','HTTP://EXAMPLE.COM:80/a',
 'http://example.com:81/a','https://example.com:443/a','?new=2','#fragment','next?q#frag','/a/../b','/a/%2e%2e/b','/a//b','//example.com',
 'http://u:p@example.com/a','http://u%3Aa:p%40ss@other.com','//u:p@example.com','http://user@example.com','http://:pass@example.com',
 'http://example.com:','http://example.com:-1','http://example.com:9223372036854775808','http://example.com:18446744073709551616',
 'ftp://example.com','mailto:a@example.com','data:text/plain,hello','http://','http://:80','http:///foo','http://[::1]/a','http://[::gg]/',
 'http://[::1]:8080/a','/with space',' /leading','/a\nb','/a\rb','/a\tb','/\0x','/\xff','/\xe9','/%','/%0','/%zz','/a\\b',
 '://bad','a:b','a%3Ab','///many','//other.com:8080/x','http://a@b:c@other.com','http://a:b:c@other.com','http://a%40b:p@example.com']
for status,location in itertools.product([200,299,300,301,302,303,304,305,306,307,308,399,400],locations):
    add('Location-resolution-escaping-auth-invalid-and-fragments',request=initial,steps=[step(location,status)])
for method,body,status,enabled in itertools.product(['GET','HEAD','POST','PUT','DELETE','get','\0\xff'],[
 strict,dict(kind='lazy',chunks=[[],b('abc'),[],b('\xff\0'),[]]),dict(kind='builder',length=-7),dict(kind='stream',length=100),dict(kind='chunked'),dict(kind='io')],
 [300,301,302,303,304,307,308,399],[False,True]):
    r=dict(initial,method=b(method),body=body,cookieJar=[b('current')] if enabled else None)
    add('method-body-constructors-and-cookie-enablement',request=r,steps=[step(status=status,jar=[b('received'),b('received')])])
for path,query,secure,location in itertools.product(['','/','/a/b','a/b','//a//b','/a/../b','/a\0b'],['','x=1','?x=1'],[False,True],['','next','../c','/c','?x=2','#frag','//other.com/c']):
    add('raw-base-path-query-and-scheme',request=req(path=b(path),query=b(query),secure=secure,port=80),steps=[step(location)])
header_sets=[[],[h('Authorization','original')],[h('aUtHoRiZaTiOn','mixed'),h('Authorization','second')],
 [h('X','first'),h('x','second'),h('Y','keep')],[h('Authorization','a'),h('Cookie','c'),h('Content-Type','t'),h('X','x')],
 [h('Content-Type','first'),h('content-type','second'),h('CONTENT-LENGTH','99')],
 [h('\xc0','latin1-first'),h('\xe0','latin1-second'),h('\xd7','times'),h('\xf7','divide')]]
policies=[dict(kind='none'),dict(kind='all'),dict(kind='names',names=[b('Authorization'),b('Content-Type'),b('\xe0')]),dict(kind='original',names=[b('Authorization')])]
hosts=[('example.com','example.com','example.com'),('example.com','other.com','example.com'),('example.com','other.com','third.com'),
 ('example.com','example.com','EXAMPLE.COM'),('example.com','EXAMPLE.COM','example.com'),('EXAMPLE.COM','example.com','EXAMPLE.COM'),('example.com','example.com','other.com')]
for (first,current,last),hs,policy,only,status in itertools.product(hosts,header_sets,policies,[False,True],[301,302,303,307]):
    r=req(current,headers=hs,strip=policy,onlyDifferent=only,method=b('POST'),body=strict)
    original=req(first,headers=list(reversed(hs))+[h('X-Original','keep')],strip=dict(kind='all'),onlyDifferent=not only,cookieJar=None)
    add('original-vs-current-host-policy-headers-and-merge',original=original,request=r,steps=[step('http://'+last+'/next',status)])
for byte,only,last in itertools.product(range(256),[False,True],['example.com','other.com']):
    lower=byte+32 if 65<=byte<=90 or 192<=byte<=214 or 216<=byte<=222 else byte
    hs=[[[byte],b('first')],[[lower],b('second')]]
    r=req(headers=hs,strip=dict(kind='names',names=[[lower]]),onlyDifferent=only)
    add('every-HeaderName-byte-CI-predicate-and-nub',original=req(headers=hs),request=r,steps=[step('http://'+last+'/')])
for byte,template in itertools.product(range(256),['/a{}b','http://a{}b/path','http://user:{}@example.com','?q={}']):
    add('every-Location-byte-Char8-escaping',request=initial,steps=[step(template.format(chr(byte)),303 if byte%2 else 307)])
for hs in [[],[h('X','none')],[h('Location','ftp://example.com'),h('location','/valid')],[h('lOcAtIoN','/first'),h('Location','/second')],[h('Location',''),h('location','/later')]]:
    for status in [200,301,302,307,400]:add('first-Location-only-case-insensitive',request=initial,steps=[step(status=status,headers=hs)])
for policy,only in itertools.product(policies,[False,True]):
    r=req(headers=[h('Authorization','secret'),h('X','first'),h('x','second'),h('Content-Type','type')],strip=policy,onlyDifferent=only,method=b('POST'),body=dict(kind='io'),cookieJar=[b('initial')])
    for chain in [['http://other.com/a','http://third.com/b','http://example.com/c'],['/a','/b','/c'],['http://EXAMPLE.COM/a','http://example.com/b'],
                  ['http://u:p@other.com/a','http://example.com/b','http://other.com/c'],['http://example.com:81/a','https://example.com/b'],['/a','ftp://bad','/never']]:
        for codes in [[301,307,308],[302,303,301]]:
            add('multi-hop-strip-restore-body-auth-and-cookie-chain',original=r,request=r,steps=[step(loc,codes[i%3],[b('hop'+str(i))]) for i,loc in enumerate(chain)])
for status,location,headers in [(200,'/x',None),(400,'/x',None),(301,'x',[]),(301,'ftp://bad',None),(301,'http://example.com:-1',None),(301,'/%zz',None),(301,'/valid',None)]:
    add('predicate-demand-after-status-Location-and-URI-validation',request=req(headers=[h('X','v')],strip=dict(kind='fail')),steps=[step(location,status,headers=headers)])
for status in [301,302,303,307]:
    add('unrelated-request-fields-preserved',request=req(port=1234,secure=True,path=b('/a'),query=b('?q'),headers=[h('X','v')],body=dict(kind='io'),method=b('PUT'),redirectCount=-1,rawBody=True,version=[2,7],proxy=dict(host=b('proxy'),port=-80),proxyConnect=False,onlyDifferent=True),steps=[step('/b',status)])
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 getRedirectedRequest with frozen Network.URI/CookieJar',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
