#!/usr/bin/env python3
"""Capture the unchanged doctemplates Context/Val instances; never invoke Kotlin."""
import argparse, collections, gzip, hashlib, json, os, random, subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/templates/values.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--check-source', action='store_true')
p.add_argument('--oracle', default=os.environ.get('KOTDOC_DOCTEMPLATES_REFERENCE'))
args = p.parse_args()
sources = ['tools/import-template-value-fixtures.py', 'upstream/pandoc/stack.yaml',
           'licenses/Doctemplates-BSD-3-Clause.txt', 'vendor/emojis-0.1.5/src/Text/emojis.inc']
for folder in ['vendor/doctemplates-0.11.1', 'vendor/doclayout-0.6', 'tools/haskell-doctemplates-reference']:
    sources += sorted(str(p.relative_to(root)) for p in (root / folder).rglob('*')
                      if p.is_file() and 'dist-newstyle' not in p.parts)
hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources}

def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':'))
                                  for c in cases).encode()).hexdigest()

if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f))
        cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert sorted(c['original'] for c in cases if 'original' in c) == sorted(
        p.name for p in (root / 'vendor/doctemplates-0.11.1/test').glob('*.test'))
    assert manifest['operations'] == dict(collections.Counter(c['op'] for c in cases))
    print(f'Verified {len(cases)} template-value observations and {len(hashes)} source files')
    raise SystemExit
assert args.oracle, '--oracle is required for independent captures'

inputs = []
def add(op, value, **metadata):
    inputs.append(dict(op=op, input=value, **metadata))

# Every original golden's actual JSON context is fed to the original instances.
for path in sorted((root / 'vendor/doctemplates-0.11.1/test').glob('*.test')):
    context, _, _ = path.read_text().split('\n.\n')
    def unique(pairs):
        assert len(dict(pairs)) == len(pairs)
        return dict(pairs)
    json.loads(context, object_pairs_hook=unique)
    add('json-text', context, original=path.name)

texts = ['', '\n', 'a\n', '\na\n\n', '\t x\r\n y', 'a b c', '中😀é', '\u0000', '𝄞\uffff']
for value in [None, False, True, 0, -1, 42, 'x', [], {}, [None], [True, False],
              ['a', 'b'], [['a'], ['b']], {'x': None}, {'z': 1, 'a': 'a'},
              {'\uffff': 1, '😀': 2, '\ue000': 3, '𝄞': 4}] + texts:
    add('json', value)
for text in texts:
    for kind in ['text', 'string']:
        add('conversion', [kind, text])
for xs in [[], texts, ['a', 'b'], [''], ['\n']]:
    for kind in ['texts', 'strings']:
        add('conversion', [kind, xs])
for boolean in [False, True]:
    add('conversion', ['bool', boolean])
for pairs in [{}, {'z': 'last', 'a': 'first', '😀': 'wide', '\uffff': 'bmp'}]:
    add('conversion', ['map', pairs])

# Decimal strings avoid Python/JSON binary-float rounding of the oracle input.
numbers = ['0', '-0', '0.0', '-0.0', '1.0', '1e23', '9223372036854775808',
           '-9223372036854775809', '9007199254740993', '1e1000', '-1e1000',
           '1e-1000', '-1e-1000', '0.1000000000000000055511151231257827021181583404541015625',
           '2.2250738585072014e-308', '4.9406564584124654e-324',
           '2.4703282292062327e-324', '-2.4703282292062328e-324',
           '1' + '0' * 309 + '.1', '-1' + '0' * 309 + '.1']
for exponent in [-1000, -400, -325, -324, -323, -308, -100, -23, -8, -7, -6, -1, 0, 1, 6, 7, 22, 23, 100, 308, 309, 400, 1000]:
    for coefficient in ['0', '-0', '1', '-1', '1.5', '9.999999999999999', '12345678901234567890123456789.125']:
        numbers.append(coefficient + 'e' + str(exponent))
rng = random.Random(111064)
for _ in range(5000):
    digits = ''.join(str(rng.randrange(10)) for _ in range(rng.randrange(1, 90)))
    digits = str(rng.randrange(1, 10)) + digits
    cut = rng.randrange(1, len(digits))
    numbers.append(rng.choice(['', '-']) + digits[:cut] + '.' + digits[cut:] + 'e' + str(rng.randrange(-400, 401)))
for number in numbers:
    add('json-text', number)

docs = texts + [['empty'], ['raw', 99, 'mismatched'], ['raw', -4, 'negative'],
               ['hsep', ['abc', 'de', 'fgh']], ['vcat', ['a', 'b', '']],
               ['prefix', '> ', ['hsep', ['a', 'b', 'c']]], ['bold', 'styled'],
               ['link', 'https://x', 'linked'], ['before', 'x'], ['after', 'x'],
               ['block', 4, [[[None, 'a'], ['url', 'bc']], [[None, 'd']]]],
               ['cooked', 7, [[None, 'one'], ['url', 'two'], [None, '']]],
               ['concat', ['raw', 12, 'a'], ['hsep', ['b', 'c', 'd']]]]
vals = [['simple', d] for d in docs] + [['null'], ['bool', False], ['bool', True],
        ['list', []], ['map', []], ['json', []], ['json', {}]]
for value in vals:
    add('value', value)
    add('value', ['list', [value]])
    add('value', ['map', [['z', value], ['a', value], ['😀', value], ['\uffff', value]]])
for a in vals[:8]:
    for b in vals:
        add('value', ['list', [a, b]])

keys = ['a', 'z', '', 'it', 'foo.bar', '\ue000', '\uffff', '𝄞', '😀']
def random_value(depth):
    kind = rng.randrange(6 if depth else 3)
    if kind == 0: return rng.choice(vals)
    if kind == 1: return ['json', rng.choice([0, 1, False, None, texts])]
    if kind == 2: return ['simple', rng.choice(docs)]
    if kind == 3: return ['list', [random_value(depth-1) for _ in range(rng.randrange(5))]]
    if kind == 4: return ['map', [[rng.choice(keys), random_value(depth-1)] for _ in range(rng.randrange(5))]]
    return ['list', [random_value(depth-1)]]
for _ in range(1000):
    add('value', random_value(4))
for _ in range(300):
    add('union', [['map', [[rng.choice(keys), random_value(2)] for _ in range(rng.randrange(8))]]
                  for _ in range(2)])
# Actual Context union left bias and absent versus present NullVal.
add('union', [['map', [['x', ['null']], ['missing', ['bool', True]]]],
              ['map', [['x', ['simple', 'right']], ['y', ['bool', False]]]]])

payload = '\n'.join(json.dumps([c['op'], c['input']], ensure_ascii=True) for c in inputs) + '\n'
result = subprocess.run([args.oracle], input=payload, text=True, capture_output=True, check=True)
lines = result.stdout.splitlines()
assert len(lines) == len(inputs), (len(lines), len(inputs), result.stderr)
cases = []
for request, output in zip(inputs, lines):
    expected = json.loads(output)
    assert 'exception' not in expected, (request, expected)
    cases.append(dict(request, expected=expected))
manifest = dict(upstream='doctemplates-0.11.1', cases=len(cases), caseHash=digest(cases),
                operations=dict(collections.Counter(c['op'] for c in cases)), sources=hashes)
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', mtime=0, filename='') as gz:
    gz.write(('\n'.join(json.dumps(c, ensure_ascii=False, separators=(',', ':')) for c in [manifest] + cases) + '\n').encode())
print(f'Captured {len(cases)} template-value observations, {manifest["operations"]}')
