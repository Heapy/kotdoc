#!/usr/bin/env python3
"""Independent complete-original common proxy branch observations, not invented upstream tests."""
import argparse, collections, gzip, hashlib, itertools, json, random, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-http-proxy-reference'
target = root / 'core/src/test/resources/http-proxy/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle'); p.add_argument('--check-source', action='store_true')
args = p.parse_args()
source = (root / 'vendor/http-client-0.7.19/Network/HTTP/Proxy.hs').read_bytes()
expected = source.replace(b'module Network.HTTP.Proxy', b'module OriginalProxy').replace(
    b'Network.HTTP.Client.Request (', b'Network.HTTP.Client.Internal (').replace(
    b'Network.HTTP.Client.Types   (', b'Network.HTTP.Client.Internal (').replace(
    b'System.Environment          (getEnvironment)', b'ProxyEnvironment            (getEnvironment)')
assert (folder / 'OriginalProxy.hs').read_bytes() == expected
assert (root / 'licenses/http-proxy-BSD-3-Clause.txt').read_bytes() == source.split(b'{-\n', 1)[1].split(b'\n-}', 1)[0] + b'\n'
with gzip.open(root / 'core/src/test/resources/http-loop/data.jsonl.gz', 'rt') as f:
    parent = json.loads(next(f))
sources = list(parent['sources']) + ['core/src/test/resources/http-loop/data.jsonl.gz',
    'tools/import-http-proxy-fixtures.py', 'licenses/http-proxy-BSD-3-Clause.txt',
    'vendor/text-2.1.2/src/Data/Text/Internal/Fusion/CaseMapping.hs',
    'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-lowercase.json']
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes():
    return {s: hashlib.sha256((root / s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        m = json.loads(next(f)); cs = [json.loads(x) for x in f]
    assert m['sources'] == hashes() and m['cases'] == len(cs) and m['caseHash'] == digest(cs)
    assert m['groups'] == dict(collections.Counter(c['group'] for c in cs))
    print('Verified', len(cs), 'original HTTP environment proxy observations and', len(m['sources']), 'source hashes')
    raise SystemExit
assert args.oracle
proc = subprocess.Popen([args.oracle], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def ask(v):
    proc.stdin.write(json.dumps(v) + '\n'); proc.stdin.flush()
    line = proc.stdout.readline(); assert line, v
    return json.loads(line)
cs = []
b = lambda s: list(s.encode('latin1'))
def add(group, env, hosts=None, name='http_proxy', api='env', **extra):
    points = lambda s: list(map(ord,s))
    environment = lambda es: [[points(k),points(v)] for k,v in es]
    if 'afterEnv' in extra: extra['afterEnv'] = environment(extra['afterEnv'])
    if extra.get('override') is not None: extra['override'] = points(extra['override'])
    r = dict(api=api, name=points(name), env=environment(env), hosts=[b('example.com')] if hosts is None else hosts, **extra)
    cs.append(dict(group=group, request=r, expected=ask(r)))
urls = ['', 'cache', 'cache:3128', 'http://cache', 'http://cache/', 'http://cache:0',
    'http://cache:', 'http://cache:080', 'http://cache:65536', 'http://cache:9223372036854775807',
    'http://cache:9223372036854775808', 'http://cache:18446744073709551616', 'http://cache:' + '9' * 200,
    'http://cache:+80', 'http://cache:-1', 'http://cache: 80', 'http://cache:0x50', 'http://cache:80x',
    'HTTP://cache', 'https://cache', 'ftp://cache', '//cache', 'http:', 'http://', 'http://:80',
    'http:///path', 'http://cache/path', 'http://cache//', 'http://cache?', 'http://cache#',
    'http://cache?x', 'http://cache#x', ' cache ', '\tcache', 'cache\n', 'http://[::1]:3128/',
    'http://[v1.host]:3128', 'http://[::1]', 'http://[broken]', 'http://a%20b', 'http://a_b',
    'http://user:password@cache', 'user:password@cache:3128', 'http://user@cache', 'http://:@cache',
    'http://user:@cache', 'http://:password@cache', 'http://u:p:q@cache', 'http://u%3Ar:p%40x@cache',
    'http://%FF:%C4%80@cache', 'http://%F0%9F%98%80:%00@cache', 'http://u@x:p@cache',
    'http://café', 'http://cache:８０', 'http://cache:١٢', 'http://cache:80/']
for url, bypass in itertools.product(urls, [None, '', 'example.com', ' ', 'example.com, ']):
    env = [('http_proxy', url)] + ([] if bypass is None else [('no_proxy', bypass)])
    add('URI-scheme-authority-port-auth-and-construction-before-bypass', env,
        [b('example.com'), b('elsewhere'), [], b('example.com')])
entries = [[], [('HTTP_PROXY','one'),('http_proxy','two')], [('http_proxy','one'),('http_proxy','two')],
    [('HTTP_PROXY','one'),('Http_Proxy','two')], [('Http_Proxy','one'),('HTTP_PROXY','two')],
    [('http_proxy',''),('HTTP_PROXY','one')], [('HTTP_PROXY','one'),('Http_Proxy','')],
    [('no_proxy','example.com'),('NO_PROXY','elsewhere'),('HTTP_PROXY','cache')],
    [('NO_PROXY','example.com'),('No_Proxy','elsewhere'),('HTTP_PROXY','cache')]]
for env, name in itertools.product(entries, ['http_proxy','HTTP_PROXY','Http_Proxy','https_proxy','custom','']):
    add('exact-first-folded-last-duplicate-case-precedence-custom-name', env, [b('example.com'),b('elsewhere')], name)
suffixes = ['', ',', ',,', 'example.com', '.example.com', '..example.com', 'EXAMPLE.COM',
    ' example.com', '   example.com', 'example.com ', '\texample.com', 'example.com\t', ' ', '  ',
    '*', '*.example.com', 'example.com, ', ' ,example.com', ',example.com,,',
    'example.com, elsewhere', 'nope, ', 'example.com:80', '.', 'com', 'localhost', '127.0.0.1',
    'é', 'É', 'İ', 'K', '\x00', '\u012c', '😀', 'example.com\n']
hosts = ['', 'example.com', 'EXAMPLE.COM', 'www.example.com', '.example.com', '..example.com',
    'badexample.com', 'example.com.', 'elsewhere', 'example.com:80', 'localhost', '127.0.0.1',
    '.', 'com', '*', 'é', 'É', 'i', 'k', '\x00']
for suffix in suffixes:
    add('no-proxy-boundary-leading-space-literal-wildcard-lazy-partial-errors',
        [('http_proxy','u:p@cache:3128'),('no_proxy',suffix)], list(map(b, hosts)))
for cp in range(256):
    add('every-Char8-host-and-suffix-simple-lowercase',
        [('http_proxy','cache'),('no_proxy',chr(cp))], [[cp], [ord('x'),ord('.'),cp], []])
lower = json.loads((root / 'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-lowercase.json').read_text())['lower']
for cp, mapped in list(lower.items()) + [(f'{cp:x}',chr(cp)) for cp in [0x10d50,0x10d65,0x1c89,0xa7cb,0xa7cc,0xa7da,0xa7dc,0xd800,0xdfff,0x10ffff]]:
    c = chr(int(cp,16)); folded = '\ufffd' if 0xd800<=ord(c)<=0xdfff else mapped
    add('exact-text-2.1.2-full-case-expansion-and-surrogate-pack', [(c+'_PROXY','cache')], name=folded+'_proxy')
    add('Unicode-simple-lower-before-Char8-pack', [('http_proxy','cache'),('no_proxy',c)], [[n] for n in sorted({ord(c)&255, ord(mapped[0])&255, 0, 65, 97})])
request = lambda host='example.com', **kw: dict(host=b(host), headers=[[b('Proxy-Authorization'),b('old')],[b('X-Test'),b('value')],[b('proxy-authorization'),b('second')]], **kw)
for secure, override, fallback, url, bypass, initial in itertools.product(
    [False,True], [None,'custom','HTTP_PROXY'], ['request','none','use'],
    [None,'cache:80','u:p@cache:3128','cache',''], [None,'example.com',' '], [False,True]):
    env = [] if url is None else [('http_proxy',url),('https_proxy',url),('custom',url)]
    if bypass is not None: env.append(('no_proxy',bypass))
    add('non-Windows-helper-default-override-fallback-and-auth-header-order', env,
        [request(**(dict(proxy=dict(host=b('existing'),port=-42)) if initial else {})), request('elsewhere'),request('')],
        api='helper', secure=secure, override=override, fallback=fallback,
        fallbackProxy=dict(host=b('fallback'),port=9999))
for api in ['env','helper']:
    add('environment-captured-once-before-applying-modifier', [('http_proxy','u:p@cache'),('no_proxy','example.com')],
        [b('example.com'),b('elsewhere')] if api=='env' else [request(),request('elsewhere')], api=api,
        afterEnv=[('http_proxy','bad/path'),('no_proxy',' ')], fallback='request')
for c in ['\x00','\n','\t','"','\\','é','😀','\ud800']:
    add('invalid-variable-Text-Show-and-precedence', [('x'+c,'bad/path'+c)], name='x'+c)
rng = random.Random(719)
for _ in range(400):
    selected = rng.sample(suffixes,rng.randrange(1,5))
    env = [(rng.choice(['http_proxy','HTTP_PROXY','Http_Proxy']),rng.choice(urls)) for _ in range(rng.randrange(1,5))]
    env += [(rng.choice(['no_proxy','NO_PROXY','No_Proxy']),','.join(selected))]
    add('deterministic-mixed-precedence-and-short-circuit-combinations',env,list(map(b,rng.sample(hosts,5))))
proc.stdin.close(); assert proc.wait()==0
m = dict(upstream='http-client-0.7.19 complete original Network.HTTP.Proxy common/non-Windows branch',
    sources=hashes(), cases=len(cs), caseHash=digest(cs), groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f, gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for value in [m]+cs: gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
