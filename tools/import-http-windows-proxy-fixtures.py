#!/usr/bin/env python3
"""Observe complete original Windows proxy algorithms with registry/environment effects."""
import argparse, collections, gzip, hashlib, itertools, json, random, subprocess, tarfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-http-windows-proxy-reference'
target = root / 'core/src/test/resources/http-windows-proxy/data.jsonl.gz'
p = argparse.ArgumentParser(); p.add_argument('--oracle'); p.add_argument('--check-source', action='store_true'); args = p.parse_args()
source = (root / 'vendor/http-client-0.7.19/Network/HTTP/Proxy.hs').read_bytes()
expected = source.replace(b'module Network.HTTP.Proxy', b'#define mingw32_HOST_OS 1\n#define MIN_VERSION_Win32(x,y,z) 1\nmodule OriginalWindowsProxy').replace(
    b'httpProtocol,', b'httpProtocol, parseProxy, parseWindowsProxy, registryProxyString, systemProxy,').replace(
    b'Network.HTTP.Client.Request (', b'Network.HTTP.Client.Internal (').replace(b'Network.HTTP.Client.Types   (', b'Network.HTTP.Client.Internal (').replace(
    b'System.Environment          (getEnvironment)', b'ProxyEffects                (getEnvironment)').replace(b'System.Win32.Registry', b'ProxyRegistry').replace(b'System.Win32.Types', b'ProxyRegistryTypes')
assert (folder / 'OriginalWindowsProxy.hs').read_bytes() == expected
projection = (root / 'tools/haskell-http-proxy-reference/HttpProxyReference.hs').read_text().split('run :: Value')[0].replace('module Main where', 'module ProxyProjection where').replace('import OriginalProxy\n', '').replace('import ProxyEnvironment (environment)\n', '')
projection = projection.replace(' | otherwise = error $ "unclassified: " ++ show e', ' | Just io <- fromException e :: Maybe IOError = object ["kind" .= ("io"::String),"message" .= ioeGetErrorString io]\n | Just ThreadKilled <- fromException e = object ["kind" .= ("async"::String)]\n | otherwise = error $ "unclassified: " ++ show e').replace('import System.IO\n', 'import System.IO\nimport System.IO.Error (ioeGetErrorString)\n')
assert (folder / 'ProxyProjection.hs').read_text() == projection
with gzip.open(root / 'core/src/test/resources/http-proxy/data.jsonl.gz', 'rt') as f:
    parent = json.loads(next(f)); previous = [json.loads(x) for x in f]
sources = list(parent['sources']) + ['core/src/test/resources/http-proxy/data.jsonl.gz', 'tools/import-http-windows-proxy-fixtures.py']
for pkg, sha, notice in [('Win32-2.14.2.1','69d15a9fb4ef718353aaf8700a64c5885743d4f34a94f7da273fa12584df0315','Win32-BSD-3-Clause.txt'),
                         ('safe-0.3.21','2c81fea42bbe79ec488774043c1ec54f30efba059df9a4f3681ce83d85007f01','safe-BSD-3-Clause.txt')]:
    archive = root / 'vendor' / (pkg + '.tar.gz')
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == sha
    with tarfile.open(archive) as t:
        original = {m.name: t.extractfile(m).read() for m in t.getmembers() if m.isfile()}
    actual = {str(f.relative_to(root / 'vendor')): f.read_bytes() for f in (root / 'vendor' / pkg).rglob('*') if f.is_file()}
    assert actual == original, pkg
    assert (root / 'licenses' / notice).read_bytes() == (root / 'vendor' / pkg / 'LICENSE').read_bytes()
    sources += [str(archive.relative_to(root)), 'licenses/' + notice] + ['vendor/' + name for name in original]
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes(): return {s: hashlib.sha256((root / s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs): return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target, 'rt') as f: m = json.loads(next(f)); cs = [json.loads(x) for x in f]
    assert m['sources'] == hashes() and m['cases'] == len(cs) and m['caseHash'] == digest(cs)
    assert m['groups'] == dict(collections.Counter(c['group'] for c in cs))
    print('Verified', len(cs), 'original Windows proxy observations and', len(m['sources']), 'source hashes'); raise SystemExit
assert args.oracle
proc = subprocess.Popen([args.oracle], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def ask(v):
    proc.stdin.write(json.dumps(v) + '\n'); proc.stdin.flush()
    line = proc.stdout.readline(); assert line, v
    return json.loads(line)
cs = []; points = lambda s: list(map(ord,s)); b = lambda s: list(s.encode('latin1'))
def add(group, api, text='', server='http=cache:3128;https=secure:443', overrideValue='', hosts=None, env=None, **extra):
    r = dict(api=api,text=points(text),env=[] if env is None else [[points(k),points(v)] for k,v in env],
             server=points(server),overrideValue=points(overrideValue),hosts=[b('example.com')] if hosts is None else hosts,**extra)
    cs.append(dict(group=group,request=r,expected=ask(r)))
urls = ['', 'cache', 'cache:3128', '//cache', 'http://cache', 'https://cache', 'HTTP://cache',
    'http://cache/', 'http://cache/path', 'http://cache?query', 'http://cache#fragment', 'http://cache/path?x',
    'http://cache:', 'http://cache:0', 'http://cache:65536', 'http://cache:080', 'http://cache:9223372036854775808',
    'http://cache:18446744073709551616', 'http://cache:'+'9'*200, 'http://cache:-1', 'http://cache:+80',
    'http://cache:0x50', 'http://cache:80x', 'http://', 'http://:80', 'http:', 'http:///path',
    'http://[::1]:3128', 'http://[v1.host]', 'http://u@cache', 'http://u:p@cache', 'http://:@cache',
    'http://u:@cache', 'http://:p@cache', 'http://u:p:q@cache', 'http://u%3Ar:p%40@cache',
    'http://%FF:%C4%80@cache', 'http://%F0%9F%98%80:%00@cache', 'http://@cache', 'http://u@@cache',
    'http://u@a:p@cache', 'http://café', 'http://cache:８０', 'http://cache:١٢', 'cache:80/path',
    ' cache ', 'http://a\ud800', 'ftp://cache', 'https://u:p@cache:']
for secure, url in itertools.product([False,True],urls):
    add('absolute-URI-selection-scheme-port-auth-path-query-fragment', 'parse', text=url, secure=secure)
strings = ['', ';', 'cache:80', 'http=cache', 'https=cache', 'http://cache', 'https://cache',
    'http=one;http=two', 'https=one;http=two;https=three', ';http=one;;', 'HTTP=cache', ' http=cache',
    'http=cache ', 'http=', 'http==cache', 'http://u:p@cache/path?x', 'http=cache#x', 'https=https://cache',
    'http=http://cache', '=cache', 'http:cache', 'http=cache;https=secure', 'http=\ud800', 'https=']
for secure, s in itertools.product([False,True],strings):
    add('semicolon-protocol-equals-conversion-first-case-sensitive-selection','select',text=s,secure=secure)
    add('registry-strings-selected-protocol-validation-and-error-Show','system',server=s,secure=secure)
for secure, enabled, valueType, failures in itertools.product([False,True],[0,1,2,2**31,2**32-1],[0,1,4,5],
    [{},{'open':'io'},{'dword':'io'},{'server':'io'},{'override':'io'},{'close':'io'},
     {'open':'fatal'},{'dword':'fatal'},{'server':'fatal'},{'override':'fatal'},{'close':'fatal'},
     {'open':'async'},{'dword':'async'},{'server':'async'},{'override':'async'},{'close':'async'},
     {'server':'fatal','close':'io'},{'server':'io','close':'fatal'},{'server':'async','close':'fatal'}]):
    add('registry-DWORD-type-nonzero-bracket-errors-and-close-precedence','registry',secure=secure,enabled=enabled,valueType=valueType,failures=failures)
hosts = list(map(b,['','localhost','LOCALHOST','127.0.0.1','127.0.0.2','example.com','www.example.com','example','ample','EXAMPLE.COM','other','*']))
for secure, exc in itertools.product([False,True],['','<local>','<LOCAL>','example.com','*.example.com','myexample.comsuffix','LOCALHOST','127.0.0.1','*','😀','é','x\x00y',' localhost ;example.com']):
    add('literal-host-substring-localhost-and-local-marker-no-pattern-expansion','system',secure=secure,overrideValue=exc,hosts=hosts+[[n] for n in range(256)])
request = lambda host='example.com': dict(host=b(host),proxy=dict(host=b('existing'),port=-42),headers=[[b('Proxy-Authorization'),b('old')],[b('x'),b('y')],[b('proxy-authorization'),b('second')]])
for secure, envValue, noProxy, server, fallback in itertools.product([False,True],[None,'env','u:p@env','bad/path'],[None,'example.com',' '],
    ['http=cache;https=secure','http=u:p@cache;https=u:p@secure','http=cache#fragment;https=cache#fragment',''],['request','none','use']):
    env=[] if envValue is None else [('http_proxy',envValue),('https_proxy',envValue)]
    if noProxy is not None: env.append(('no_proxy',noProxy))
    add('environment-before-registry-eager-construction-fallback-and-auth-order','helper',env=env,server=server,secure=secure,
        hosts=[request(),request('other'),request('localhost'),request('')],fallback=fallback,fallbackProxy=dict(host=b('fallback'),port=9))
for row in previous:
    if row['request']['api']=='helper':
        r=dict(row['request'],server=points('http=user:pass@windows:80;https=secure'),overrideValue=points('<local>'))
        cs.append(dict(group='all-preceding-environment-helper-inputs-with-Windows-secondary-selection',request=r,expected=ask(r)))
for key, tag in itertools.product(['environment','open','dword','server','override','close'],['io','fatal','async']):
    add('combined-helper-effect-failures-and-environment-error-before-registry','helper',env=[('http_proxy','env')],
        failures={key:tag},hosts=[request()],fallback='request')
for api in ['system','helper']:
    add('registry-and-env-snapshots-not-reread-on-application',api,env=[('http_proxy','env'),('no_proxy','example.com')],
        hosts=[request(),request('other')] if api=='helper' else [b('example.com'),b('other')],fallback='request',
        after=dict(env=[],server=points('http=changed'),overrideValue=points('example.com'),enabled=0))
rng=random.Random(71932)
for _ in range(350):
    s=';'.join(rng.choices(strings,k=rng.randrange(1,5)))
    add('deterministic-combined-registry-strings','system',server=s,secure=rng.choice([False,True]),hosts=hosts)
proc.stdin.close(); assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 complete original Windows CPP branch with registry effects; Win32 2.14.2.1 binding reference; safe 0.3.21',
       sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for value in [m]+cs: gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
