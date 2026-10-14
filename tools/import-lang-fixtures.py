#!/usr/bin/env python3
"""Unchanged unicode-collation Lang, original tests, GHC, Parsec and Binary output."""
import argparse, atexit, collections, gzip, hashlib, itertools, json, os, random, struct, subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/lang/data.jsonl.gz'
parser = argparse.ArgumentParser()
parser.add_argument('--oracle', default=os.environ.get('KOTDOC_LANG_REFERENCE'))
parser.add_argument('--check-source', action='store_true')
args = parser.parse_args()
unit = (root / 'vendor/unicode-collation-0.1.3.7/test/unit.hs').read_text()
a = unit.index('emptyLang ::'); b = unit.index('\nlangParseTest ::', a)
assert unit[a:b] in (root / 'tools/haskell-lang-reference/OriginalLangExamples.hs').read_text()
sources = ['tools/import-lang-fixtures.py', 'upstream/pandoc/pandoc.cabal',
           'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json',
           'licenses/Unicode-collation-BSD-2-Clause.txt', 'licenses/Binary-BSD-3-Clause.txt',
           'licenses/Parsec-BSD-2-Clause.txt', 'licenses/text-BSD-2-Clause.txt', 'licenses/GHC-base-BSD-3-Clause.txt']
for folder in ['tools/haskell-lang-reference', 'vendor/unicode-collation-0.1.3.7',
               'vendor/parsec-3.1.18.0', 'vendor/text-2.1.2', 'vendor/binary-syntax-0.8.9.3',
               'vendor/ghc-9.12.2', 'vendor/ghc-pretty-sort-9.12.2']:
    sources += sorted(str(f.relative_to(root)) for f in (root / folder).rglob('*') if f.is_file())
def hashes():
    return {f: hashlib.sha256(os.readlink(root / f).encode() if (root / f).is_symlink()
                             else (root / f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cases):
    h = hashlib.sha256()
    for c in cases: h.update(json.dumps(c, sort_keys=True, separators=(',', ':')).encode())
    return h.hexdigest()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes()
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert manifest['operations'] == dict(collections.Counter(c['request'][0] for c in cases))
    original = [c for c in cases if c['request'][0] == 'original']
    assert [c['request'][1] for c in original] == list(range(8))
    for c in original:
        e = c['expected']
        assert e['parsed']['success']['fields'] == e['expected'] == e['roundtrip']['success']['fields']
    print(f'Verified {len(cases)} original Lang observations, all 16 original language-test '
          f'expectations and {len(sources)} source hashes')
    raise SystemExit
assert args.oracle, '--oracle required'
proc = subprocess.Popen([args.oracle], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def cleanup():
    if proc.poll() is None: proc.terminate(); proc.wait()
atexit.register(cleanup)
cases = []
def add(*req, **flags):
    proc.stdin.write(json.dumps(req, ensure_ascii=True)+'\n'); proc.stdin.flush()
    line = proc.stdout.readline(); assert line, req
    c = dict(request=req, expected=json.loads(line), **flags); cases.append(c)
    if len(cases) % 10000 == 0: print('Captured',len(cases),'Lang observations',flush=True)
    return c['expected']
def ps(s): return list(map(ord,s)) if isinstance(s,str) else s
def lang(language='en', script=None, region=None, variants=(), extensions=(), private=()):
    return [ps(language), None if script is None else ps(script), None if region is None else ps(region),
            [ps(v) for v in variants], [[ps(k),[[ps(a),ps(b)] for a,b in vs]] for k,vs in extensions], [ps(p) for p in private]]
rng = random.Random(0x1472021)
for n in range(8): add('original',n)
seeds = ['', 'en', 'EN-US', 'sr_Latn_RS', 'es-419', 'de-CH-1996', 'en--with garbage',
         'en with garbage', 'en-u-kr-latin-digit', 'en-U-CO-PHONEBK', 'x-foo', 'i-klingon',
         'en-x-ab-123', 'en-u-co-phonebk-co-trad', 'en-u-co-PHONEBK', 'en-a-foo-b-bar',
         'en-abc-def-ghi-jkl-Latn-US', 'abcd-Abcd-419-1996-u-co-phonebk', 'en-u-ABC-co-PHONEBK',
         'en-u-co-u-co-phonebk', 'en-US.UTF-8', 'en-', '-en', 'en__US', 'en-123', 'en-12', 'en-1234']
for s in seeds:
    add('parse',ps(s)); add('fromString',ps(s))
tokens = ['', 'a','aa','aaa','aaaa','aaaaa','aaaaaaaa','aaaaaaaaa','en','EN','US','USA','Latn','LATN','Cyrl','419','1234',
          '12345','phonebk','PHONEBK','u','U','co','CO','x','X','kr','true','foo','000','!','漢','ß','🙂']
for base, mid, last, separator in itertools.product(['en','abc','abcd','abcdefghi',''],tokens,tokens,['-','_']):
    add('parse',ps(separator.join([base,mid,last])), grammar=True)
for _ in range(2000):
    s = rng.choice(['-','_']).join(rng.choices(tokens,k=rng.randrange(1,20)))
    add('parse',ps(s),random=True)
for cp in [*range(256),*range(0xd800,0xe000),*[rng.randrange(0x110000) for _ in range(1500)]]:
    for p in [[cp,101,110], [101,110,cp,85,83]]: add('parse',p,raw=True)
    add('fromString',[cp,101,110],raw=True)
for n in [0,1,3,10,1000,10000]:
    add('parse',ps('en'+('-abc'*n)),long=True)
    add('parse',ps('en-u-co'+('-abc'*n)),long=True)
for code in sorted((root/'vendor/unicode-collation-0.1.3.7/data/tailorings').glob('*.txt')):
    add('parse',ps(code.stem),tailoring=code.name)

texts = ['', 'en','EN','US','Latn','x', 'a\n\x00\x0eH1', '漢🙂', '\uffff', '\U00010000', '\U0010ffff',
         [0xd800],[0xdc00],[0xd800,0xdc00],[0xfffd,0xfffd]]
base = lang(); records = [base]
for i in range(6):
    if i == 0: values = [ps(v) for v in texts]
    elif i in [1,2]: values = [None,*[ps(v) for v in texts]]
    elif i in [3,5]: values = [[],*[ [ps(v)] for v in texts], [ps(v) for v in texts], [ps(v) for v in texts[::-1]]]
    else:
        values = [
            [], [[ps('u'),[]]], [[ps('u'),[[ps('co'),ps('phonebk')]]]],
            *[[[ps(v),[[ps('co'),ps('x')]]]] for v in texts],
            *[[[ps('u'),[[ps(v),ps('x')]]]] for v in texts],
            *[[[ps('u'),[[ps('co'),ps(v)]]]] for v in texts],
            [[ps('u'),[[ps('co'),ps('first')],[ps('co'),ps('last')]]]],
            [[ps('u'),[]],[ps('u'),[[ps('co'),ps('phonebk')]]]],
            [[ps('u'),[[ps('co'),ps('phonebk')]]],[ps('u'),[]]],
        ]
    for v in values:
        r = [*base]; r[i] = v; records.append(r)
for a,b in itertools.product(records,repeat=2): add('relation',a,b)
encoded = []
for r in records:
    for p in [-2**63,-1,0,10,11,2**63-1]:
        e = add('record',r,p)
        if p == 0: encoded.append(e['encoded'])

# Every absence/exact/different script-region-collation combination; evaluate
# competing candidates and stable ties, not only singleton membership.
for script,region,col in itertools.product([None,'','Latn'],[None,'','US'],[None,'','phonebk']):
    q = lang(script=script,region=region,extensions=[] if col is None else [('u',[('co',col)])])
    candidates = [lang(script=s,region=r,extensions=[] if c is None else [('u',[('co',c)])])
                  for s,r,c in itertools.product([None,script,'different'],[None,region,'different'],[None,col,'different'])]
    for a,b in itertools.product(candidates,repeat=2): add('lookup',q,[[a,1],[b,2]],ranking=True)
for q in records:
    add('lookup',q,[[r,n] for n,r in enumerate(records)],fields=True)
    add('lookup',q,[[r,n] for n,r in reversed(list(enumerate(records)))],fields=True)
q = lang(script='Latn',region='US',extensions=[('u',[('co','phonebk')])])
for r in records:
    add('lookup',q,[[r,1],[q,2],[q,3]],stable=True)
    add('lookup',q,[[q,3],[q,2],[r,1]],stable=True)
    add('lookup',r,[[lang(language='EN'),1],[lang(language='de'),2]],language=True)

# Complete valid byte vectors, every truncation, trailers, all Maybe tag bytes,
# malformed UTF-8 and signed length boundaries at each nested source location.
for h in dict.fromkeys(encoded):
    add('decode',h)
    add('decode',h+'deadbeef',trailer=True)
    for i in range(len(h)//2): add('decode',h[:i*2],truncated=True)
some = bytes.fromhex(add('record',lang(script='Latn',region='US'),0)['encoded'])
script_at=10;region_at=23
assert some[script_at]==some[region_at]==1
for byte in range(256):
    for at in [script_at,region_at]:
        data=bytearray(some);data[at]=byte;add('decode',data.hex(),maybeTag=True)
for h in dict.fromkeys(encoded[:15]):
    data=bytes.fromhex(h)
    for _ in range(20):
        changed=bytearray(data);changed[rng.randrange(len(data))]=rng.randrange(256)
        add('decode',changed.hex(),mutated=True)
def length(n):return struct.pack('>q',n)
def text_bytes(bs):return length(len(bs))+bs
empty_tail=b'\0\0'+length(0)*3
invalid_utf8=[bytes([b]) for b in range(128,256)]+[b'\xc0\x80',b'\xed\xa0\x80',b'\xf4\x90\x80\x80',b'\xf0\x90',b'\xe2\x82']
for bs in invalid_utf8:
    for data in [text_bytes(bs)+empty_tail,
                 text_bytes(b'en')+b'\x01'+text_bytes(bs)+b'\0'+length(0)*3,
                 text_bytes(b'en')+b'\0\0'+length(1)+text_bytes(bs)+length(0)*2,
                 text_bytes(b'en')+b'\0\0'+length(0)+length(1)+text_bytes(b'u')+length(1)+text_bytes(b'co')+text_bytes(bs)+length(0)]:
        add('decode',data.hex(),utf8=True)
for n in [-2**63,-10,-1,0,1,2,2**31-1,2**63-1]:
    for data in [length(n)+b'en'+empty_tail,
                 text_bytes(b'en')+b'\0\0'+length(n)+length(0)*10,
                 text_bytes(b'en')+b'\0\0'+length(0)+length(n)+length(0)*10,
                 text_bytes(b'en')+b'\0\0'+length(0)*2+length(n)+length(0)*10]:
        add('decode',data.hex(),lengthBoundary=True)
manifest = dict(cases=len(cases),sources=hashes(),caseHash=digest(cases),
    operations=dict(collections.Counter(c['request'][0] for c in cases)),
    originalTests=16, packageArchiveSHA256='ec7f75d635486d760bf45d2093ff380b1dad4146ceee39107d66cd00af713c57',
    oracle='Unchanged unicode-collation 0.1.3.7 Lang, original langPairs tests, GHC 9.12.2, '
           'Parsec 3.1.18.0, text 2.1.2 and Binary 0.8.9.3')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
    for c in [manifest]+cases:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cases),'Lang observations; SHA',manifest['caseHash'],flush=True)
