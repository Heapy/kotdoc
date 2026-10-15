#!/usr/bin/env python3
"""Capture original cookie-0.5.1 behavior and original Arbitrary/property witnesses."""
import argparse
import collections
import gzip
import hashlib
import itertools
import json
from pathlib import Path
import random
import subprocess

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-web-cookie-reference'
target = root / 'core/src/test/resources/web-cookie/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle')
p.add_argument('--original-tests')
p.add_argument('--check-source', action='store_true')
args = p.parse_args()
original = (root / 'vendor/cookie-0.5.1/test/Spec.hs').read_text().replace('module Main where', 'module OriginalCookieSpec where')
assert (folder / 'OriginalCookieSpec.hs').read_text() == original
assert (root / 'licenses/cookie-MIT.txt').read_bytes() == (root / 'vendor/cookie-0.5.1/LICENSE').read_bytes()
with gzip.open(root / 'core/src/test/resources/public-suffix/data.jsonl.gz', 'rt') as f:
    parent = json.loads(next(f))
sources = list(parent['sources']) + ['core/src/test/resources/public-suffix/data.jsonl.gz',
    'tools/import-web-cookie-fixtures.py', 'licenses/cookie-MIT.txt', 'licenses/Time-BSD-2-Clause.txt']
for directory in ['tools/haskell-web-cookie-reference', 'vendor/cookie-0.5.1', 'vendor/time-1.14',
                  'vendor/text-2.1.2', 'vendor/ghc-unicode-9.12.2']:
    sources += [str(f.relative_to(root)) for f in (root / directory).rglob('*') if f.is_file()]
def hashes():
    return {s: hashlib.sha256((root / s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
original_names = ['parse/render cookies', 'parse/render SetCookie', 'parse/render cookies text',
                  'parseCookies', 'parseQuotedCookies', 'parseQuotedSetCookie', 'year 24', 'year 69', 'year 70']
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes() and manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert manifest['originalTests'] == original_names and manifest['originalPropertyInputs'] == 3000
    assert manifest['groups'] == dict(collections.Counter(c['group'] for c in cases))
    assert sum('property' in c for c in cases) == 3000
    print('Verified', len(cases), 'original cookie observations, nine original test families, 3,000 original property inputs and', len(manifest['sources']), 'source hashes')
    raise SystemExit

assert args.oracle and args.original_tests
result = subprocess.run([args.original_tests, '--quickcheck-replay=938', '--quickcheck-tests=1000'],
                        capture_output=True, text=True, check=True)
assert 'All 9 tests passed' in result.stdout and result.stdout.count('+++ OK, passed 1000 tests.') == 3
for name in original_names:
    assert name + ':' in result.stdout, name
print(result.stdout, flush=True)
proc = subprocess.Popen([args.oracle], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def ask(value):
    proc.stdin.write(json.dumps(value) + '\n'); proc.stdin.flush()
    line = proc.stdout.readline(); assert line, value
    return json.loads(line)
cases = []
def add(group, property=None, **request):
    case = dict(group=group, request=request, expected=ask(request))
    if property is not None:
        case['property'] = property
    cases.append(case)
b = lambda s: list(s.encode('latin1'))
points = lambda s: list(map(ord, s))
add('original-default-set-cookie', mode='default')
inputs = ask(dict(mode='property-inputs', count=1000))
for kind, mode in [('cookies', 'render'), ('text', 'render-text'), ('set', 'set')]:
    assert len(inputs[kind]) == 1000
    for data in inputs[kind]:
        assert data['holds'] is True
        add('original-property-' + kind, property=kind,
            mode=mode, **({'cookie': data['input']} if kind == 'set' else {'cookies': data['input']}))

for text in ['', ';', ';;', 'a=a1;b=b2; c=c3', 'a="a1";b="b2"; c="c3"', 'a="a1"',
             'a', ' a ', ' a = b ', '\ta=\tb', 'a=b=c', 'a=b;', ';a=b;;', 'a=""', 'a="',
             'a="b', 'a=b"', 'a="b""', 'a="b;c"', '"a"="b"', '\r\nA=x\0y']:
    add('literal-cookie-header-separators-quotes-and-whitespace', mode='parse', bytes=b(text))
for byte in range(256):
    for data in [[byte], b(' ')+[byte]+b('=')+[byte]+b(';'), b('a="')+[byte]+b('"; b=')+[byte]]:
        add('every-cookie-header-byte', mode='parse', bytes=data)
for text in ['💡=é', '\ufffd=值', 'a=\ud800', '\udfff=\U0010ffff']:
    pairs = [[points(a), points(c)] for a,c in [text.split('=', 1)]]
    add('Unicode-text-builder-and-surrogate-normalization', mode='render-text', cookies=pairs)
for data in [[0xc2], [0xe0, 0xa0], [0xed, 0xa0, 0x80], [0xf0, 0x90], [0xf4, 0x90, 0x80, 0x80], list(range(256))]:
    add('lenient-UTF8-invalid-sequences', mode='parse', bytes=b('name=')+data)

flags = ['Path=/a', 'path=', 'Path="/a"', 'Domain=.example.com', 'Domain=', 'HttpOnly', 'HTTPONLY=no',
         'Secure', 'secure=false', 'Partitioned', 'partitioned=no', 'SameSite=Lax', 'SameSite=Strict',
         'SameSite=None', 'SameSite=lax', 'SameSite="Lax"', 'SameSite=Lax ', 'SameSite =Lax',
         'Max-Age=', 'Max-Age=0', 'Max-Age=01', 'Max-Age=-1', 'Max-Age=+1', 'Max-Age=1.5',
         'Max-Age= 1', 'Max-Age=1 ', 'Max-Age="1"', 'Max-Age='+'9'*200,
         'Expires=Mon, 29-Jul-24 04:52:08 GMT', 'Expires=', 'Unknown=unused']
for name, value, flag in itertools.product(['a', ' a ', '\ta', ''], ['b', '"b"', ' "b" ', ''], flags):
    add('set-cookie-attributes-and-name-value-boundaries', mode='set', bytes=b(name+'='+value+'; '+flag))
for left, right in itertools.product(flags, flags):
    add('set-cookie-first-duplicate-and-attribute-order', mode='set', bytes=b('a=b; '+left+'; '+right))
for byte in range(256):
    for prefix in ['a=', 'a=b; Path=', 'a=b; Max-Age=', 'a=b; SameSite=']:
        add('every-set-cookie-attribute-byte', mode='set', bytes=b(prefix)+[byte])
for field, copy in itertools.product(['name', 'maxAge', 'eq-name-different', 'eq-self', 'clear-max-age', 'clear-max-age-then-eq'], [False, True]):
    add('empty-max-age-field-demand-record-update-and-Eq', mode='set-field', bytes=b('a=b; Max-Age='), field=field, copy=copy)

base = dict(name=b('name'), value=b('value'), path=None, expires=None, maxAge=None,
            domain=None, httpOnly=False, secure=False, sameSite=None, partitioned=False)
for bits in range(128):
    c = dict(base, path=b('/path') if bits&1 else None, expires=['0', '0'] if bits&2 else None,
             maxAge='1000000000001' if bits&4 else None, domain=b('example.com') if bits&8 else None,
             httpOnly=bool(bits&16), secure=bool(bits&32), partitioned=bool(bits&64), sameSite=['Lax','Strict','None'][bits%3])
    add('all-set-cookie-render-flags-and-field-order', mode='set', cookie=c)
for picos in ['-1000000000001', '-1000000000000', '-1', '0', '1', '999999999999', '1000000000001', '9'*100]:
    add('max-age-rational-floor-and-Show', mode='set', cookie=dict(base, maxAge=picos))
for name in base:
    replacement = {'name':b('other'), 'value':b('other'), 'path':b('/'), 'expires':['0','0'],
        'maxAge':'1', 'domain':b('example'), 'httpOnly':True, 'secure':True, 'sameSite':'Lax', 'partitioned':True}[name]
    add('derived-Eq-each-set-cookie-field', mode='equal', left=base, right=dict(base, **{name:replacement}))
add('derived-Eq-identical', mode='equal', left=base, right=base)

date = 'Mon, 29-Jul-2024 04:52:08 GMT'
for position in range(len(date)):
    for replacement in [' ', '\t', '\n', '\xa0', '\x85', '0', '9', '-', '+', ':', ',', '/', 'x']:
        add('date-single-position-grammar-mutations', mode='date', bytes=b(date[:position]+replacement+date[position+1:]))
for ws in [' ', '  ', '\t', '\r\n', '\v', '\f', '\xa0', '\x85', '']:
    for before, after in itertools.product(['', ws], repeat=2):
        add('date-locale-literals-case-and-whitespace', mode='date', bytes=b(before+date.lower().replace(' ', ws)+after))
for year, month, day in itertools.product([-401,-400,-100,-4,-1,0,1,4,24,69,70,99,100,400,1600,1900,1969,1970,2000,2024,2100,2400], ['Jan','Feb','Apr','Dec'], [0,1,28,29,30,31,32]):
    add('date-proleptic-calendar-validity-and-year-fuzz', mode='date', bytes=b(f'Mon, {day:02}-{month}-{year} 23:59:60 GMT'))
for year in ['000024','-0000','+2024','- 1','  2024',' -1','1'*100,'-'+'1'*100]:
    add('date-unbounded-signed-and-padded-year', mode='date', bytes=b(f'Tue, 01-Jan-{year} 00:00:00 GMT'))
for hour, minute, second in itertools.product([0,1,12,23,24,99], [0,1,59,60,99], [0,1,59,60,61,99]):
    add('date-clock-ranges-and-leap-second-conversion', mode='date', bytes=b(f'Fri, 01-Mar-2024 {hour:02}:{minute:02}:{second:02} GMT'))
q=10**12
for day, picos in itertools.product([-10**50,-678942,-678941,-678575,-1,0,1,40587,10**50],
                                   [-10**50,-86400*q-1,-q,-1,0,1,1234567890123,86399*q+999999999999,86400*q,86400*q+1,86401*q,10**50]):
    add('date-format-and-Show-unbounded-day-and-raw-DiffTime', mode='format-date', time=[str(day),str(picos)])
for seconds, fraction in itertools.product([2**63-1,2**63,2**64-10,2**64-9,2**64-1,2**64,2**64+1], [0,1,q-1]):
    add('date-format-machine-Int-second-overflow-and-sign-padding', mode='format-date', time=['0',str((seconds+86340)*q+fraction)])
rng=random.Random(626)
for _ in range(1000):
    day=rng.randrange(-200000,100000);picos=rng.randrange(86401*q)
    time=[str(day),str(picos)]
    expected=ask(dict(mode='format-date',time=time))
    add('original-formatted-date-parse-and-fuzz', mode='date', bytes=expected['bytes'])

proc.stdin.close(); assert proc.wait()==0
manifest=dict(upstream='cookie-0.5.1 and time-1.14', sources=hashes(), originalTests=original_names,
              originalPropertyInputs=3000, originalQuickCheckReplay=938, originalQuickCheckTests=1000,
              cases=len(cases), caseHash=digest(cases), groups=dict(collections.Counter(c['group'] for c in cases)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for value in [manifest]+cases:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in manifest.items() if k!='sources'},indent=2))
