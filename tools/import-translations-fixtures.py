#!/usr/bin/env python3
"""Independent observations from unchanged Pandoc Translations.Types/GHC/text/Aeson."""
import argparse, atexit, collections, gzip, hashlib, itertools, json, os, random, re, subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/translations/data.jsonl.gz'
parser = argparse.ArgumentParser()
parser.add_argument('--oracle', default=os.environ.get('KOTDOC_TRANSLATIONS_REFERENCE'))
parser.add_argument('--check-source', action='store_true')
args = parser.parse_args()
source = (root / 'upstream/pandoc/src/Text/Pandoc/Translations/Types.hs').read_text()
declaration = source.split('data Term =', 1)[1].split('deriving', 1)[0]
terms = re.findall(r'\b[A-Z][A-Za-z]+\b', declaration)
assert len(terms) == 22
files = sorted((root / 'upstream/pandoc/data/translations').glob('*.yaml'))
sources = ['tools/import-translations-fixtures.py', 'upstream/pandoc/src/Text/Pandoc/Translations/Types.hs',
           'licenses/pandoc-GPL-2.0.md', 'licenses/GHC-base-BSD-3-Clause.txt',
           'licenses/text-BSD-2-Clause.txt', 'licenses/Containers-BSD-3-Clause.txt',
           'licenses/Aeson-BSD-3-Clause.txt', 'vendor/containers-syntax-0.7/src/Data/Map/Internal.hs',
           'vendor/containers-syntax-0.7/LICENSE',
           'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json']
sources += [str(f.relative_to(root)) for f in files]
for folder in ['tools/haskell-translations-reference', 'vendor/ghc-9.12.2',
               'vendor/ghc-translations-9.12.2', 'vendor/text-2.1.2', 'vendor/aeson-pretty-dependency-2.2.3.0']:
    sources += sorted(str(f.relative_to(root)) for f in (root / folder).rglob('*') if f.is_file())

def hashes():
    # Git stores a symlink's target spelling, not the followed file's contents.
    # Both the alias and its original target occur in the complete file inventory.
    return {f: hashlib.sha256(os.readlink(root / f).encode() if (root / f).is_symlink()
                             else (root / f).read_bytes()).hexdigest() for f in sorted(sources)}

def digest(cases):
    h = hashlib.sha256()
    for c in cases:
        h.update(json.dumps(c, sort_keys=True, separators=(',', ':')).encode())
    return h.hexdigest()

if args.check_source:
    with gzip.open(target, 'rt') as stream:
        manifest = json.loads(next(stream)); cases = [json.loads(line) for line in stream]
    assert manifest['sources'] == hashes()
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert manifest['terms'] == terms
    assert manifest['operations'] == dict(collections.Counter(c['request'][0] for c in cases))
    originals = [c for c in cases if c.get('original')]
    assert [(c['path'], c['request'][1]) for c in originals] == [
        (str(f.relative_to(root)), list(map(ord, f.read_text()))) for f in files]
    assert set(c['request'][1][1] and ''.join(map(chr, c['request'][1][1])) for c in cases
               if c.get('validTerm')) == set(terms)
    print(f'Verified {len(cases)} original Translations.Types observations, {len(terms)} terms, '
          f'{len(files)} unchanged language files and {len(sources)} source hashes')
    raise SystemExit

assert args.oracle, '--oracle required'
proc = subprocess.Popen([args.oracle], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def cleanup():
    if proc.poll() is None:
        proc.terminate(); proc.wait()
atexit.register(cleanup)
cases = []
def add(*req, **flags):
    proc.stdin.write(json.dumps(req, ensure_ascii=True) + '\n'); proc.stdin.flush()
    line = proc.stdout.readline()
    assert line, req
    cases.append(dict(request=req, expected=json.loads(line), **flags))
    if len(cases) % 10000 == 0:
        print('Captured', len(cases), 'Translations.Types observations', flush=True)

def points(s): return list(map(ord, s)) if isinstance(s, str) else s
def string(s): return ['s', points(s)]
def obj(rows): return ['o', [[points(k), v] for k, v in rows]]
def table(rows): return obj([(k, string(v)) for k, v in rows])
precisions = [-2**63, -1, 0, 10, 11, 2**63-1]
rng = random.Random(0x20172024)
invalids = [['z'], ['b', True], ['b', False], ['n', 1], ['n', -0.1], ['a', []], obj([])]
for term in terms:
    add('termJSON', string(term), validTerm=True)
    for bad in [term.lower(), term.upper(), ' '+term, term+' ', term+'x', '']:
        add('termJSON', string(bad))
for value in invalids: add('termJSON', value)
for cp in [*range(256), *range(0xd800, 0xe000), *[rng.randrange(0x110000) for _ in range(1500)]]:
    add('termJSON', string([cp, 49]), rawError=True)
for a, b, p in itertools.product(range(22), range(22), precisions): add('relation', a, b, p)
for op, i, j, k in itertools.product(['from', 'fromTo', 'fromThen', 'fromThenTo'], range(22), range(22), range(22)):
    add('enum', op, i, j, k, 50)
for op, tag in itertools.product(['toEnum', 'succ', 'pred'], [-2**63, -1, *range(23), 2**63-1]):
    add('enum', op, tag, 0, 21, 30)
add('enum', 'bounds', 0, 0, 21, 30)
for term, pre, post, p in itertools.product(terms + ['Figurex', 'figure', '_Figure'],
        ['', ' ', '(', ' ( ( ', '{-x-}', '--x\n'], ['', ' ', ' ) ) ', '\x00', ';', '--x', "'", 'x'], [-1, 0, 11, 2**63-1]):
    add('read', p, points(pre+term+post))
for depth in [1, 10, 1000, 10000]: add('read', 2**63-1, points('('*depth+'Abstract'+')'*depth+' rest'), deep=True)
for cp in [*range(256), *range(0xd800, 0xe000), *[rng.randrange(0x110000) for _ in range(1500)]]:
    add('read', 0, [*points('Figure'), cp, 49], raw=True)
list_values = ['', '[]', '[Abstract]', '[Figure,To]', '[(Abstract), (To)]',
               '[Abstract,]', '[,Abstract]', '[figure]', '[Abstract To]', '[[]]',
               '[Figure {-x-},To]', '[Figure,--x\nTo]', '['+','.join(terms)+']']
for s, pre, post in itertools.product(list_values, ['', ' ', '(', ' ( ( ', '{-x-}'], ['', ' ', ' ) ) ', '\x00', 'x']):
    add('readList', points(pre+s+post))
for depth in [1, 10, 1000, 10000]:
    add('readList', points('('*depth+'[Figure,To]'+')'*depth+' rest'), deep=True)
for invalid, p in itertools.product(invalids[:-1] + [string('x')], precisions): add('translations', invalid, p)
text_values = ['', ' ', ' x ', '漢🙂', 'a\n b\t', '\x0eH1', '\x00', '\u0085x\u0085', '\u200bx\u200b',
               [0xd800], [0xdc00], [0xd800, 0xdc00], [0x10000]]
for term, text, p in itertools.product(terms, text_values, [0, 11]):
    add('translations', table([(term, text)]), p)
for cp in [*range(256), *range(0xd800, 0xe000), *[rng.randrange(0x110000) for _ in range(1500)]]:
    add('translations', table([('Figure', [cp, 120, cp])]), 0, strip=True)
for key in ['', 'ZZ', 'AA', 'Figure ', 'figure', '図1', '🙂1', '\x0eH', '\x00', [0xd800], [0xd800,0xdc00]]:
    add('translations', obj([('Table', ['z']), (key, string('x'))]), 0, validationOrder=True)
for term, invalid in itertools.product(terms, invalids):
    add('translations', obj([(term, invalid)]), 0)
for rows in [[('Figure','first'),('Figure','last')], [('To','z'),('Abstract','a'),('Figure','f')],
             [(t,' '+t+' ') for t in terms], [(t,t) for t in terms[::-1]]]:
    for p in precisions: add('translations', table(rows), p)
for _ in range(500):
    rows = [(rng.choice(terms), rng.choice(text_values)) for _ in range(rng.randrange(25))]
    add('translations', table(rows), rng.choice(precisions), random=True)
tables = [table([]), table([('Figure','left')]), table([('Figure','right'),('Table','table')]),
          table([('Figure','')]), table([(t,t) for t in terms]), table([('Figure',[0xd800,0xdc00])])]
for a, b, p in itertools.product(tables, tables, precisions): add('combine', 'append', [a,b], p, '1')
for n in range(5):
    for ts in itertools.product(tables, repeat=n):
        add('combine', 'concat', ts, 0, '1')
        if ts: add('combine', 'sconcat', ts, 11, '1')
for t, n, p in itertools.product(tables, [-10**100, -1, 0, 1, 2, 2**63-1, 10**100], precisions):
    add('combine', 'stimes', [t], p, str(n))
for f in files: add('yaml', points(f.read_text()), 0, original=True, path=str(f.relative_to(root)))
manifest = dict(cases=len(cases), sources=hashes(), terms=terms, caseHash=digest(cases),
                operations=dict(collections.Counter(c['request'][0] for c in cases)),
                originalFiles=len(files), oracle='Unchanged Pandoc 3.12.1 Translations.Types, GHC 9.12.2, '
                'text 2.1.2, containers 0.7, Aeson 2.2.3.0; original YAML 0.11.11.2 for data-file transport only')
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', mtime=0) as z:
    for c in [manifest]+cases:
        z.write((json.dumps(c, ensure_ascii=True, separators=(',', ':'))+'\n').encode())
print('Captured', len(cases), 'Translations.Types observations; SHA', manifest['caseHash'], flush=True)
