#!/usr/bin/env python3
"""Capture actual http-client request behavior, including unchanged original specs."""
import argparse, collections, gzip, hashlib, itertools, json, random, subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-http-request-reference'
target = root / 'core/src/test/resources/http-request/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle')
p.add_argument('--check-source', action='store_true')
args = p.parse_args()
for name in ['Request', 'RequestBody']:
    original = (root / f'vendor/http-client-0.7.19/test-nonet/Network/HTTP/Client/{name}Spec.hs').read_text()
    adapted = original.replace(f'module Network.HTTP.Client.{name}Spec where', f'module Original{name}Spec where')
    assert (folder / f'Original{name}Spec.hs').read_text() == adapted
sources = ['tools/import-http-request-fixtures.py', 'upstream/pandoc/src/Text/Pandoc/Class/IO/HTTP.hs']
for directory in ['vendor/http-client-0.7.19', 'vendor/http-types-0.12.6', 'vendor/bytestring-http-0.12.2.0', 'vendor/blaze-builder-0.4.4.1', 'vendor/case-insensitive-1.2.1.0', 'vendor/network-uri-2.6.4.2', 'tools/haskell-http-request-reference']:
    sources += [str(f.relative_to(root)) for f in (root / directory).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts]
def hashes(): return {s: hashlib.sha256((root / s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cases): return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes()
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert manifest['groups'] == dict(collections.Counter(c['group'] for c in cases))
    assert len(manifest['originalTests']) == 26
    print('Verified', len(cases), 'http-client request observations, all 26 original tests and', len(manifest['sources']), 'source hashes')
    raise SystemExit
assert args.oracle
proc = subprocess.Popen([args.oracle], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def ask(request):
    proc.stdin.write(json.dumps(request) + '\n'); proc.stdin.flush()
    line = proc.stdout.readline(); assert line, request
    return json.loads(line)
original = ask(dict(mode='original-tests'))
assert isinstance(original, list) and len(original) == 26, original
cases = []
def add(group, **request):
    expected = ask(request)
    if isinstance(expected, dict) and 'error' in expected:
        assert ''.join(map(chr, expected['error'])).startswith('InvalidUrlException '), (request, expected)
    cases.append(dict(group=group, request=request, expected=expected))
cp = lambda s: list(map(ord, s))
b = lambda s: list(s.encode('latin1'))
hdr = lambda n, v: [b(n), b(v)]
def url(group, s, **extra): add(group, url=cp(s), **extra)
def uri(scheme='http:', auth=('', 'example.com', ''), path='', query='', fragment=''):
    return [cp(scheme), None if auth is None else [cp(x) for x in auth], cp(path), cp(query), cp(fragment)]
original_urls = ['http://example.com', 'httP://example.com', 'HttP://example.com', 'HttPs://example.com', 'ftp://example.com', 'http://agent:topsecret@example.com', 'http://user:pass@example.com', 'http://example.com/', 'https://example.com/', 'https://example.com:80/', 'http://example.org', 'http://agent:secret@example.com', 'http://agent007:shakenNotStirred@example.com', 'http://%2F%3F%23%5B%5D%21%24%26%27%28%29%2A%2B%2C%3B%3D:therealpassword@example.com', 'http://therealusername:%3F%23%5B%5D%21%24%26%27%28%29%2A%2B%2C%3B%3D%2F@example.com']
for s in original_urls:
    for mode in ['parse', 'throw']: url('original-test-urls', s, mode=mode)
for scheme in ['http:', 'httP:', 'HttP:', 'HttPs:', 'ftp:']:
    add('original-test-uri-conversion', mode='uri', uri=uri(scheme))
add('original-redaction', mode='default', patch=dict(headers=[hdr('Authorization', 'secret')]))
url('original-proxy-auth', 'http://example.org', mode='throw', ops=[['proxy-basic', b('user'), b('pass')]])
bodies = [['bs', b('data')], ['lbs', [b('data')]], ['builder', 4, b('data')], ['stream', 4, [b('data')]], ['chunked', [b('data')]]]
for body, expect in itertools.product(bodies, [False, True]):
    url('original-request-builder-bodies', 'http://localhost', mode='throw', body=body, patch=dict(method=b('PUT'), path=b('foo'), headers=[hdr('Expect', '100-continue')] if expect else []))
for method in ['', 'GET', 'POST', 'PUT', 'HEAD', 'OPTIONS', 'get', 'GEt', 'GET ', '1', 'Ä']:
    for s in ['', 'http://example.com/a b', 'https://user:pass@[::1]:443/a?b#c', 'file:///tmp/test', 'not-a-url']:
        url('method-prefix-and-invalid-url', method + ' ' + s)
for s in ['', ':', '//example.com', 'http:', 'http:/x', 'http:///x', 'http://', 'http://a:', 'http://a:-1', 'http://a:65536', 'http://a:00080', 'http://a:9223372036854775808', 'http://a:18446744073709551616', 'http://a:18446744073709551617', 'http://[::1]/', 'http://[::ffff:1.2.3.4]/', 'http://bücher.example/中文', 'http://a/%zz', 'http://a/%', 'http://a/\\b', 'http://a/?x=+%20#f', 'http://u@example.com', 'http://u:@example.com', 'http://:p@example.com', 'http://u:p:q@example.com', 'http://%C3%A9:%F0%9F%98%80@example.com']:
    url('url-encoding-authority-and-port-boundaries', s)
for c in list(range(256)) + [0x300, 0x130, 0x3a3, 0x1f600, 0xd800, 0x10ffff]:
    for template in ['http://a/{c}', 'http://{c}:pass@a/', 'http://a/?q={c}']:
        url('all-byte-url-escaping-and-unicode', template.format(c=chr(c)))
ports = ['', ':', ':0', ':80', ':443', ':65536', ':0x50', ':0XfF', ':0o120', ':00080', ': 80 ', ':(80)', ':(-0)', ':- 0', ':+80', ':-1', ':9223372036854775807', ':9223372036854775808', ':18446744073709551616', ':18446744073709551617', ':(0x50)', ':((80))', ':80\n', ':1_000', ':0b10', ':--comment\n80', ':80 --comment', ':\u00a080', ':\x0080', ':80x', ':80.0']
for port in ports:
    for scheme in ['http:', 'https:', 'HTTPS:', 'ftp:', '']:
        add('arbitrary-uri-and-read-int-syntax', mode='uri', uri=uri(scheme, ('u:p@', 'EXAMPLE.com', port)))
for scheme, auth, path in itertools.product(['http:', 'https:', 'ftp:'], [None, ('', '', ''), ('u:p@', '[::1]', ':80')], ['', '/', 'relative', 'a?b']):
    add('arbitrary-uri-components', mode='uri', uri=uri(scheme, auth, path, 'query'))
for query in ['', '?', 'x', '?x', '??x']:
    for path in ['', '/', 'a', '//a']:
        add('get-uri-and-wire-path-query-normalization', mode='default', patch=dict(path=b(path), query=b(query)))
for relative in [uri('', None, '../b'), uri('', None, '', '?query', '#frag'), uri('https:', ('n:p@', 'new', ':81'), '/new'), uri('', ('', 'another', ''), '/a')]:
    url('relative-uri-and-auth-retention', 'http://u:p@a/base/old?q', ops=[['relative', relative]])
for n in range(256):
    add('all-latin1-header-case-and-byte-show', mode='default', patch=dict(headers=[[[n], [n]], [[n], b('two')]], redact=[[n]]))
header_sets = [[], [hdr('Host', 'other')], [hdr('HOST', '')], [hdr('Accept-Encoding', '')], [hdr('accept-encoding', ' ')], [hdr('Accept-Encoding', ''), hdr('ACCEPT-ENCODING', 'gzip')], [hdr('Accept-Encoding', 'br'), hdr('Accept-Encoding', '')], [hdr('Content-Length', '99'), hdr('Transfer-Encoding', 'identity')], [hdr('X-A', '1'), hdr('x-a', '2')], [hdr('Expect', '100-continue')], [hdr('expect', '100-Continue')], [hdr('Expect', ''), hdr('Expect', '100-continue')]]
for hs, method, body in itertools.product(header_sets, ['GET', 'HEAD', 'POST'], [['bs', []], ['bs', b('data')], ['chunked', [b('a'), b('bc')]]]):
    add('header-order-duplicates-and-automatic-headers', mode='default', patch=dict(headers=hs, method=b(method)), body=body)
for secure, port, direct in itertools.product([False, True], [80, 443, 81, -1, 0], [False, True]):
    add('proxy-origin-and-connect-modes', mode='default', patch=dict(secure=secure, port=port), ops=[['proxy', b('proxy'), 3128]] + ([['proxy-direct']] if direct else []))
for v, timeout in itertools.product([[1, 0], [1, 1], [2, 0], [3, 9], [-1, -2]], [['default'], ['none'], ['micro', 0], ['micro', -1], ['micro', 1000]]):
    add('request-show-version-timeout', mode='default', patch=dict(version=v, timeout=timeout, redirectCount=-1))
for enc, mime, raw, strategy in itertools.product(['gzip', 'GZIP', 'gzip, deflate', ''], ['', 'application/x-tar', 'application/x-tar; charset=x', 'APPLICATION/X-TAR'], [False, True], range(3)):
    add('gzip-exact-header-and-mime-predicate', mode='default', patch=dict(rawBody=raw, decompress=strategy), responseHeaders=[hdr('Content-Encoding', enc), hdr('Content-Type', mime)])
for n in range(256):
    items = [[[n], [255-n]], [b('space key'), b('a+b c&d=e')], [[], []]]
    add('all-query-bytes-and-form-encoding', mode='default', ops=[['form', items], ['query', items + [[b('novalue'), None]]]])
    add('all-partial-query-bytes', mode='default', ops=[['partial', [[[n], [[True, [n]], [False, [n]]]], [b('empty'), []]]]])
for user, password in itertools.product([[], b('user'), list(range(256))], [[], b('pass'), list(range(256))]):
    add('auth-prepending-duplicates-and-redaction', mode='default', ops=[['basic', user, password], ['proxy-basic', user, password], ['bearer', user]])
for kind in ['stream', 'chunked']:
    for chunks in [[], [b('a')], [b('a'), [], b('ignored')], [b('a'), b('bc')]]:
        for length in [-1, 0, 1, 3, 4]:
            for expect in [False, True]:
                body = [kind, length, chunks] if kind=='stream' else [kind, chunks]
                add('stream-termination-size-errors-and-expect', mode='default', body=body, patch=dict(headers=[hdr('Expect', '100-continue')] if expect else []))
for body in bodies:
    add('io-body-construction', mode='default', body=['io', ['io', body]])
for n in [0, 1, 127, 128, 4096, 32751, 32752, 32753, 65504, 75000]:
    add('file-size-progress-and-reopened-provider', mode='file', data=[i%256 for i in range(n)])
for n in [127, 128, 4095, 4096, 32751, 32752, 32753, 65504]:
    for kind in ['bs', 'lbs', 'builder']:
        data = [i%256 for i in range(n)]
        body = ['lbs', [data]] if kind=='lbs' else ['builder', n, data] if kind=='builder' else ['bs', data]
        add('builder-buffer-boundaries', mode='default', body=body)
# Native callback traces distinguish popper failures from write failures and
# verify which header/body writes are covered by onRequestBodyException.
for body, expect, hook, fail in itertools.product(
    bodies + [['stream', 2, [b('a'), dict(error='io')]], ['stream', 2, [b('a'), dict(error='runtime')]], ['stream', 3, [b('a')]], ['io-error']],
    [False, True], ['default', 'swallow', 'throw'], [-1, 0, 1, 2, 3]):
    add('body-exception-hook-scope-and-popper-encapsulation', mode='default', body=body,
        patch=dict(headers=[hdr('Expect', '100-continue')] if expect else []),
        faults=True, hook=hook, failWrite=fail)
for n in range(256):
    lower=n+32 if 65<=n<=90 or 192<=n<=214 or 216<=n<=222 else n
    add('all-latin1-case-insensitive-header-redaction', mode='default',
        patch=dict(headers=[[[n], b('private')]], redact=[[lower]]))
for n in [8160, 8161, 32670, 32671, 32672, 32752, 65504]:
    data=[i%256 for i in range(n)]
    for expect in [False, True]:
        for body in [['bs',data], ['lbs',[data[:4000],data[4000:]]], ['builder',n,data]]:
            add('blaze-copy-insert-and-buffer-split-boundaries',mode='default',body=body,
                patch=dict(headers=[hdr('Expect','100-continue')] if expect else []))
        add('large-header-and-path-buffer-segmentation',mode='default',
            patch=dict(path=[47]+data,headers=[[b('X-Long'),data]]))
for port in ['- (0)', '(- (0))', '(+0)', '(-0)', '(--1)', '(0)  ', '(0x0)', '0B10', '0b10',
             '0x', '0x-1', '-0x0', '0x 0', '\x850', '\xa00', '\u200b0', '\u20280',
             '0\x85', '--a\n0', '0{-a-}', '0 --x', '(0) --x']:
    add('ghc-read-int-lexical-boundaries',mode='uri',uri=uri(auth=('', 'example.com', ':'+port)))
for n in [0,1,32752,75000]:
    add('file-provider-reopening-and-repeatable-bodies',mode='file',data=[i%256 for i in range(n)],times=3)
for after in [[],b('short'),list(range(256))*300]:
    add('file-size-snapshot-and-subsequent-changes',mode='file',data=b('Hello, world!'),afterData=after)
for n in range(256):
    url('all-percent-decoded-auth-bytes',f'http://%{n:02X}:%{255-n:02X}@example.com')
for ops in [[['query',[]]], [['form',[]]], [['partial',[]]], [['check']], [['check'],['ignore']], [['ignore'],['check']]]:
    add('empty-queries-and-status-policy-switches',mode='default',ops=ops)
for hs in [[hdr('Content-Encoding','br'),hdr('CONTENT-ENCODING','gzip')],
           [hdr('content-encoding','gzip'),hdr('Content-Type','application/x-tar'),hdr('CONTENT-TYPE','text/plain')],
           [hdr('content-encoding','gzip'),hdr('Content-Type','text/plain'),hdr('CONTENT-TYPE','application/x-tar')]]:
    add('gzip-membership-and-first-mime-header',mode='default',responseHeaders=hs)
proc.stdin.close(); assert proc.wait() == 0
manifest = dict(cases=len(cases), sources=hashes(), caseHash=digest(cases), originalTests=original, groups=dict(collections.Counter(c['group'] for c in cases)))
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as rawfile, gzip.GzipFile(fileobj=rawfile, mode='wb', mtime=0) as z:
    z.write(('\n'.join(json.dumps(x, separators=(',', ':')) for x in [manifest] + cases) + '\n').encode())
print('Captured', len(cases), 'observations;', manifest['caseHash'])
