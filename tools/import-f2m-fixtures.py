#!/usr/bin/env python3
"""Independent observations from pinned crypton, including every active F2mSpec property."""
import argparse, collections, gzip, hashlib, importlib.util, itertools, json, random, subprocess, tempfile, sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.set_int_max_str_digits(0)  # Trusted generated exponents intentionally exceed 4300 decimal digits.
folder = root / 'tools/haskell-f2m-reference'
target = root / 'core/src/test/resources/crypto-f2m/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle'); p.add_argument('--check-source', action='store_true'); p.add_argument('--verify-oracle', action='store_true')
a = p.parse_args()
spec = importlib.util.spec_from_file_location('f2m_properties', root / 'tools/extract-f2m-properties.py')
props = importlib.util.module_from_spec(spec); spec.loader.exec_module(props)
assert (folder / 'OriginalF2mProperties.hs').read_text() == props.module()
vendor = root / 'vendor/crypton-2.0.1'
pin = json.loads((vendor / 'SOURCE.json').read_text())
assert pin['sha256'] == '9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
assert set(pin['files']) == {str(f.relative_to(vendor)) for f in vendor.rglob('*') if f.is_file() and f.name != 'SOURCE.json'}
for path, sha in pin['files'].items(): assert hashlib.sha256((vendor / path).read_bytes()).hexdigest() == sha, path
notice = root / 'licenses/crypton-BSD-3-Clause.txt'
assert notice.read_bytes() == (vendor / 'LICENSE').read_bytes()
sources = [Path(__file__).resolve(), root / 'tools/extract-f2m-properties.py', notice]
sources += [f for f in folder.rglob('*') if f.is_file()]
sources += [f for f in vendor.rglob('*') if f.is_file()]
def hashes(): return {str(f.relative_to(root)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs): return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cs).encode()).hexdigest()
def groups(cs): return dict(collections.Counter(c['group'] for c in cs))
if a.check_source or a.verify_oracle:
    with gzip.open(target, 'rt') as f: manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes()
    assert manifest['caseHash'] == digest(cases) and manifest['cases'] == len(cases) and manifest['groups'] == groups(cases)
    assert manifest['properties'] == [x[0] for x in props.properties()]
    if a.check_source:
        print('Verified', len(cases), 'F2m observations, all 29 property expressions and', len(manifest['sources']), 'source hashes')
        raise SystemExit
else:
    cases = []
    def add(group, op, **kw):
        request = dict(op=op, **{k: str(v) for k, v in kw.items()})
        cases.append(dict(group=group, request=request))
    for n in range(65536): add('original-exhaustive-GF16-square-roots', 'sqrt', f=65581, a=n)
    fields = [-3, -1, 0, 1, 2, 3, 7, 11, 65581, (1 << 20) - 1, (1 << 163) | (1 << 7) | (1 << 6) | (1 << 3) | 1]
    values = [-2, -1, 0, 1, 2, 3, 7, 255, 65535, 1 << 64, (1 << 257) - 1]
    for op, f, x in itertools.product(['mod', 'square', 'pow', 'mul'], fields, values):
        bs = [-2, -1, 0, 1, 2, 3, 16, 257] if op == 'pow' else values if op == 'mul' else [0]
        for b in bs: add('invalid-moduli-signs-zero-one-and-unreduced-values', op, f=f, a=x, b=b)
    for op, x, y in itertools.product(['add', 'rawSquare'], values, values): add('signed-XOR-and-raw-square-bit-spreading', op, a=x, b=y)
    for op, f, x in itertools.product(['inv', 'div', 'quadratic', 'sqrt'], fields, values):
        if op == 'sqrt' and f < 2: continue  # Original does not terminate for degree-zero moduli.
        if op in ['inv', 'div'] and (f < 0 or x < 0): continue  # Undefined negative-input GCD need not terminate.
        for part in (['constructor', 'value'] if op != 'sqrt' else ['value']):
            add('inverse-nonunits-zero-modulus-lazy-Maybe-and-quadratic-trace', op, f=f, a=x, b=x, part=part)
    bottoms = ['bottom:f', 'bottom:a', 'bottom:b']
    for op, f, x, y, part in itertools.product(['mod', 'mul', 'square', 'pow', 'add', 'rawSquare'],
                                               [-1, 0, 1, 11, bottoms[0]], [-1, 0, 2, bottoms[1]], [-1, 0, 2, bottoms[2]], ['ignore', 'value']):
        add('guard-order-short-circuit-and-bottom-arguments', op, f=f, a=x, b=y, part=part)
    for op, f, x, y, part in itertools.product(['inv', 'div', 'quadratic', 'sqrt'],
                                               [0, 1, 2, 11, bottoms[0]], [0, 1, 2, bottoms[1]], [0, 1, 2, bottoms[2]], ['ignore', 'constructor', 'value']):
        if op == 'sqrt' and isinstance(f, int) and f < 2: continue
        add('Maybe-constructor-versus-payload-and-ignored-dividend', op, f=f, a=x, b=y, part=part)
    for f, x, y, part in itertools.product([0, 1, 2, 7, 11], [-1, 0, 1, 'bottom:a'],
                                          [0, 1, 2, 3, 'bottom:b'], ['constructor', 'value']):
        add('mapped-division-guards-before-inverse-payload', 'div', f=f, a=x, b=y, part=part)
    for bits, f, x, dense in itertools.product([512, 2048, 8192, 16384], [2, 11, 65581], [0, 1, 2, 65535], [False, True]):
        add('deep-exponent-stacks-without-JVM-recursion', 'pow', f=f, a=x, b=(1 << bits) - int(dense))
    rng = random.Random(0xF2A2026)
    sizes = [1, 2, 8, 16, 32, 64, 113, 163, 233, 283, 409, 571, 1024, 2048]
    for i in range(256):
        bits = rng.choice(sizes)
        f = rng.getrandbits(bits) | (1 << bits) | 1
        x, y = rng.getrandbits(bits * 2), rng.getrandbits(bits * 2)
        for op in ['mod', 'mul', 'square', 'rawSquare', 'sqrt', 'inv', 'div', 'quadratic', 'pow']:
            add('large-sparse-dense-reducible-and-irreducible-polynomials', op, f=f, a=x, b=(rng.getrandbits(12) if op == 'pow' else y))
    for index in range(29):
        for i in range(1 if index == 17 else 128):
            bits = [1, 2, 4, 8, 16, 32, 64][i % 7]
            m = rng.getrandbits(bits) + 1
            xs = [rng.getrandbits(bits) for _ in range(3)]
            if index < 5: xs = [x * rng.choice([-1, 1]) for x in xs]
            add('all-original-active-property-expressions', 'property', index=index, m=m, a=xs[0], b=xs[1], c=xs[2])
assert a.oracle, '--oracle required'
with tempfile.TemporaryFile() as inp, tempfile.TemporaryFile() as out:
    inp.write(''.join(json.dumps(c['request'], separators=(',', ':')) + '\n' for c in cases).encode()); inp.seek(0)
    subprocess.run([a.oracle], stdin=inp, stdout=out, check=True, timeout=600); out.seek(0)
    results = [json.loads(line) for line in out]
assert len(results) == len(cases)
if a.verify_oracle:
    for i, (c, result) in enumerate(zip(cases, results)): assert c['expected'] == result, (i, c, result)
    print('Original crypton verified all', len(cases), 'F2m observations'); raise SystemExit
for c, result in zip(cases, results):
    c['expected'] = result
    if c['request']['op'] == 'property': assert result == {'property': True}, c
manifest = dict(cases=len(cases), sources=hashes(), groups=groups(cases), caseHash=digest(cases), properties=[x[0] for x in props.properties()])
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as f:
    with gzip.GzipFile(filename='', mode='wb', fileobj=f, mtime=0) as z:
        z.write(('\n'.join(json.dumps(c, separators=(',', ':')) for c in [manifest] + cases) + '\n').encode())
print('Generated', len(cases), 'F2m observations; SHA256', manifest['caseHash'])
