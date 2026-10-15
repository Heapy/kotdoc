#!/usr/bin/env python3
"""Independent observations of actual http-client CookieJar and iproute Read APIs."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-http-cookie-reference';target=root/'core/src/test/resources/http-cookie/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=(root/'vendor/http-client-0.7.19/test-nonet/Network/HTTP/Client/CookieSpec.hs').read_bytes().replace(b'module Network.HTTP.Client.CookieSpec where',b'module OriginalCookieSpec where')
assert (folder/'OriginalCookieSpec.hs').read_bytes()==original
assert (root/'licenses/iproute-BSD-3-Clause.txt').read_bytes()==(root/'vendor/iproute-1.7.15/LICENSE').read_bytes()
with gzip.open(root/'core/src/test/resources/web-cookie/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/web-cookie/data.jsonl.gz','tools/import-http-cookie-fixtures.py','licenses/iproute-BSD-3-Clause.txt','licenses/appar-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt']
for directory in ['tools/haskell-http-cookie-reference','vendor/iproute-1.7.15','vendor/appar-0.1.8','vendor/ghc-pretty-sort-9.12.2']:
    sources += [str(f.relative_to(root)) for f in (root/directory).rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
    assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
    assert len(m['originalTests'])==15 and m['groups']==dict(collections.Counter(c['group'] for c in cs))
    print('Verified',len(cs),'original HTTP CookieJar/IP observations, all 15 original tests and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def ask(v):
    proc.stdin.write(json.dumps(v)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,v
    return json.loads(line)
original=ask(dict(mode='original-tests'));assert len(original)==15 and isinstance(original,list),original
cs=[];b=lambda s:list(s.encode('latin1'));q=10**12;now=['60000',str(123*q+1)]
def add(group,**request):cs.append(dict(group=group,request=request,expected=ask(request)))
def req(host='www.example.com',path='/a/b',**extra):return dict(host=b(host),path=b(path),**extra)
def cookie(**extra):
    return dict(dict(name=b('a'),value=b('v'),expiry=['60001','0'],domain=b('example.com'),path=b('/'),creation=['59999','0'],access=['59999','1'],persistent=False,hostOnly=False,secureOnly=False,httpOnly=False),**extra)
base=cookie()
paths=['','/','a','a/','a/b','/a','/a/','/a/b','//a','/a//','foo/bar','/foo','/foo/','/foo/bar','\0','/a\0b']
for path in paths:
    add('request-default-cookie-path',mode='default-path',request=req(path=path))
    for other in paths:add('path-match-original-remainder-and-partials',mode='path',request=b(path),cookie=b(other))
for byte in range(256):
    for path in [b('/')+[byte]+b('/tail'),[byte]+b('/tail'),b('/')+[byte]]:
        add('every-path-byte',mode='default-path',request=dict(path=path))
        add('every-path-byte',mode='path',request=path,cookie=b('/')+[byte])

hosts=['','example.com','www.example.com','EXAMPLE.COM','WWW.Example.COM','notexample.com','.example.com','example.com.','com','COM','localhost','LOCALHOST','a.localhost.','127.0.0.1','127.0.0.1.','+127.0.0.1','1.2.3.-0','1.2.3.256','[::1]','\xff.com','a\0.example.com']
domains=['','example.com','EXAMPLE.COM','.example.com','www.example.com','com','COM','localhost','127.0.0.1','0.0.1','1','[::1]','\xff.com']
for host,domain in itertools.product(hosts,domains):add('domain-match-case-IP-and-suffix-boundaries',mode='domain',host=b(host),domain=b(domain))
for byte in range(256):
    lower=byte+32 if 65<=byte<=90 or 192<=byte<=214 or 216<=byte<=222 else byte
    for host,domain in [([byte]+b('.com'),[lower]+b('.com')),(b('x.')+[byte],[lower]),([byte]+b('.com'),b('com'))]:
        add('every-domain-case-fold-byte',mode='domain',host=host,domain=domain)
parts=['0','00','0001','+0','-0','+1','-1','255','256','9223372036854775807','9223372036854775808','-9223372036854775808','1x','',' 1','0x1','+-0','0'*100+'1']
for position,part,tail in itertools.product(range(4),parts,['','.','..']):
    octets=['127','0','0','1'];octets[position]=part
    add('cookie-is-IP-signed-decimal-overflow-and-trailing-dot',mode='is-ip',bytes=b('.'.join(octets)+tail))
origins=['','example','example.','example.com','foolocalhost','1.1.1.1','::1','[::2]','127.0.0.1','127.0.0.2','127.127.127.127','[::1]','[0:0:0:0:0:0:0:1]','localhost','localhost.','a.b.c.localhost','a.b.c.localhost.','LOCALHOST','a.LOCALHOST','[::0.0.0.1]','[0:0:0:0:0:0:0.0.0.1]','[::ffff:127.0.0.1]','[::00001]','[::1%lo0]','127.00.0.1','+127.0.0.1','127.0.0.1.']
ipinputs=['::','::1','0::1','0:0::1','0:0:0:0:0:0:0:1','::0.0.0.1','0::0.0.0.1','::ffff:127.0.0.1','1::','1:2:3:4:5:6:7:8','1:2:3:4:5:6:7::8',':::1','::1:',':1','1:','(127.0.0.1)','127.0.0.1','127.00.0.1','127.0.0.256','127.0.0.18446744073709551617']
for raw in ipinputs:
    for before,after in itertools.product(['',' ','\t','\xa0'],repeat=2):
        add('iproute-Read-leading-trailing-whitespace-and-embedded',mode='ip',bytes=b(before+raw+after))
        origins.append('['+before+raw+after+']' if ':' in raw else before+raw+after)
for host,secure in itertools.product(origins,[False,True]):add('trustworthy-origin-original-and-expanded',mode='origin',secure=secure,host=b(host))
rng=random.Random(627)
for i in range(300):
    v4='.'.join(str(rng.randrange(256)) for _ in range(4))
    words=[rng.randrange(65536) for _ in range(8)];words[rng.randrange(8)]=0
    v6=':'.join(f'{x:x}' for x in words)
    embedded=':'.join(f'{x:x}' for x in words[:6])+':'+v4
    for value in [v4,v6,embedded]:add('iproute-full-numeric-Read',mode='ip',bytes=b(value))
for size in range(8):
    for letters in itertools.product('01:',repeat=size):
        value=''.join(letters)
        add('Appar-exhaustive-short-IPv6-consumed-failure',mode='ip',bytes=b(value))
        add('Appar-exhaustive-short-origin-check',mode='origin',secure=False,host=b('['+value+']'))
for value in ['::00000','::1:00000','::1:00000:','::1::','::1:::',':::1','::::1',':0:0:0:0:0:0:0:1','0:0:0:0:0:0:0:1:',
              '::12345:1','1::00000','::1:abcdx','x::1','0::1:','::0.0.0.1:', '1:2:3:4:5:6:7:8:']:
    add('Appar-long-hex-failure-and-trailing-separator',mode='ip',bytes=b(value))
    add('Appar-long-hex-origin-check',mode='origin',secure=False,host=b('['+value+']'))

for host,domain,path,http,flags in itertools.product(['www.example.com','com','COM','127.0.0.1'],[None,'','.','.example.com','EXAMPLE.COM','..example.com','com','COM','bad.com','example.com.','127.0.0.1','\xff'],['/a/b','a/',''],[False,True],['','; HttpOnly; Secure']):
    value='a=v'+('' if domain is None else '; Domain='+domain)+flags
    add('generate-cookie-domain-sanitize-PSL-host-only-default-path-and-HTTP',mode='generate',set=b(value),request=req(host,path),now=now,http=http)
for timestamp,value in itertools.product([['0','0'],['0',str(86400*q)],['0',str(86400*q+1)],['0','-1'],['-100000000000000000000','1']],
    ['a=v','a=v; Max-Age=0','a=v; Max-Age=1','a=v; Max-Age=','a=v; Max-Age=-1','a=v; Expires=Mon, 29-Jul-24 04:52:08 GMT; Max-Age=1']):
    add('cookie-expiry-default-MaxAge-Expires-priority-and-POSIX-leap-clamp',mode='generate',set=b(value),request=req(),now=timestamp,http=True)
sc=dict(name=b('a'),value=b('v'),path=None,expires=['42','1'],domain=None,maxAge=None,httpOnly=False,secure=False,sameSite=None,partitioned=False)
for picos in ['-1000000000001','-1','0','1','999999999999','1000000000001','9'*60]:
    for timestamp in [now,['0',str(86400*q+1)],['0','-1']]:
        add('direct-SetCookie-fractional-negative-MaxAge',mode='generate',setCookie=dict(sc,maxAge=picos),request=req(),now=timestamp,http=True)
for value in ['a=v; Max-Age=','a=v; Max-Age=0','a=v; Max-Age=-1','a=v','a=v; Max-Age=; Expires=Mon, 29-Jul-24 04:52:08 GMT']:
    add('MaxAge-Maybe-presence-and-short-circuit-Eq',mode='set-demand',set=b(value))
    for domain,field in itertools.product(['','; Domain=com'],['exists','name','persistent','expiry','equiv-self','equal-self','show']):
        add('generated-cookie-lazy-expiry-and-rejection',mode='generated-field',set=b(value+domain),request=req(),now=now,http=True,field=field)
    for mode in ['receive-compute','receive-evict']:
        add('receive-and-use-latent-expiry',mode=mode,set=b(value),request=req(),now=now,http=True,jar=[])

for field,value in [('name',b('b')),('value',b('w')),('expiry',['0','0']),('domain',b('EXAMPLE.COM')),('path',b('/a')),('creation',['0','0']),('access',['0','0']),('persistent',True),('hostOnly',True),('secureOnly',True),('httpOnly',True)]:
    changed=dict(base,**{field:value})
    for left,right in [(base,changed),(changed,base)]:add('equal-equivalent-and-order-every-cookie-field',mode='compare',left=left,right=right)
add('non-reflexive-cookie-order',mode='compare',left=base,right=base)
for size in range(13):
    for split in range(size+1):
        values=[cookie(name=b('a'+str(i%3)),value=b('v'+str(i)),path=b('/'+'x'*(i%3)),creation=['0',str(i%2)]) for i in range(size)]
        add('GHC-cookie-jar-ties-duplicates-and-merge-arity',mode='jar-pair',left=values[:split],right=values[split:])
for _ in range(400):
    values=[cookie(name=b('n'+str(rng.randrange(4))),value=b('v'+str(i)),domain=b(rng.choice(['example.com','EXAMPLE.COM','other.com'])),path=b('/'+'x'*rng.randrange(4)),creation=[str(rng.randrange(3)),str(rng.randrange(3))]) for i in range(rng.randrange(40))]
    split=rng.randrange(len(values)+1)
    add('GHC-cookie-jar-natural-runs-and-unlawful-tie-comparator',mode='jar-pair',left=values[:split],right=values[split:])
for a,c in [([], [base]),([base],[]),([base],[base,cookie(name=b('b'))]),([base,cookie(name=b('b'))],[base])]:
    add('cookie-jar-equality-prefix-without-length-check',mode='jar-pair',left=a,right=c)
jars=[[],[base],[dict(base,httpOnly=True)],[base,dict(base,value=b('last'),creation=['0','0'])],[cookie(name=b('b')),dict(base,domain=b('EXAMPLE.COM')),base]]
for jar in jars:
    add('remove-first-equivalent-only',mode='remove',cookie=base,jar=jar)
    for http,httponly in itertools.product([False,True],repeat=2):
        add('insert-preserves-old-creation-and-HTTPOnly-policy',mode='insert',cookie=cookie(value=b('new'),httpOnly=httponly),jar=jar,http=http)
        add('receive-preserves-old-creation-and-HTTPOnly-policy',mode='receive',set=b('a=new'+('; HttpOnly' if httponly else '')),request=req(),now=now,http=http,jar=jar)
    add('evict-expiry-equality-kept',mode='evict',jar=jar,now=base['expiry'])
for timestamp in [['60000',str(123*q)],now,['60000',str(123*q+2)],['60001','0']]:
    values=[cookie(name=b(str(i)),expiry=['60000',str(123*q+i)]) for i in range(4)]
    add('evict-before-at-after-picosecond-boundary',mode='evict',jar=values,now=timestamp)

# Cookie flags independently select/reject host-only, secure-only and HTTP-only.
for host,path,secure,http in itertools.product(['www.example.com','EXAMPLE.COM','localhost','127.0.0.1','[::1]','other.com'],['/a/b','a/b','/',''],[False,True],[False,True]):
    values=[cookie(name=b('n'+str(bits)),value=b(str(bits)),domain=b('localhost' if bits&8 else 'example.com'),path=b('/a' if bits&16 else '/'),hostOnly=bool(bits&1),secureOnly=bool(bits&2),httpOnly=bool(bits&4),creation=['0',str(bits%3)],expiry=['0','0']) for bits in range(32)]
    add('cookie-header-host-path-secure-HTTP-only-and-expired-not-evicted',mode='compute',request=req(host,path,secure=secure),jar=values,now=now,http=http)
for jar,headers,enabled in itertools.product(jars,[[],[[b('Cookie'),b('old')]],[[b('cOoKiE'),b('first')],[b('X'),b('keep')],[b('Cookie'),b('second')]]],[False,True]):
    r=req(headers=headers,cookieJar=[cookie(name=b('request-only'))] if enabled else None,body=b('body'))
    add('insert-cookie-header-removes-only-first-and-retains-request-jar',mode='insert-request',request=r,jar=jar,now=now)
for sets in [[],['a=one'],['a=one','a=two'],['a=v; Domain=com'],['a=v; Domain=example.com; Path=/a','b=w; HttpOnly'],['a=v; Max-Age=0']]:
    hs=[[b('X'),b('before')]]+[[b('SeT-CoOkIe'),b(s)] for s in sets]+[[b('Y'),b('after')]]
    for enabled in [False,True]:
        r=req(cookieJar=[cookie(name=b('request-only'))] if enabled else None,body=b('body'))
        add('response-SetCookie-fold-strip-all-and-preserve-response-jar',mode='response',request=r,headers=hs,responseJar=[cookie(name=b('response-only'))],jar=[base],now=now)
for enabled in [False,True]:add('getResponse-and-lbs-original-request-cookie-jar',mode='get-response',request=req(cookieJar=[base] if enabled else None,body=b('body')))
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 Cookies/Types, iproute-1.7.15 and GHC 9.12.2 sort',sources=hashes(),originalTests=original,cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
