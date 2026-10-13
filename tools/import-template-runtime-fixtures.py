#!/usr/bin/env python3
"""Observe unchanged doctemplates rendering; no Kotlin or replacement renderer."""
import argparse, collections, gzip, hashlib, json, os, random, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/templates/runtime.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--check-source', action='store_true')
p.add_argument('--oracle', default=os.environ.get('KOTDOC_DOCTEMPLATES_REFERENCE'))
args = p.parse_args()
sources = ['tools/import-template-runtime-fixtures.py', 'upstream/pandoc/stack.yaml',
           'licenses/Doctemplates-BSD-3-Clause.txt', 'vendor/emojis-0.1.5/src/Text/emojis.inc',
           'vendor/text-2.1.3/CaseMapping.hs']
for folder in ['vendor/doctemplates-0.11.1', 'vendor/doclayout-0.6', 'tools/haskell-doctemplates-reference']:
    sources += sorted(str(p.relative_to(root)) for p in (root / folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        m = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert m['sources'] == hashes and m['cases'] == len(cases) and m['caseHash'] == digest(cases)
    assert m['operations'] == dict(collections.Counter(c['op'] for c in cases))
    assert sorted(c['original'] for c in cases if 'original' in c and c['op']=='runtime') == sorted(p.name for p in (root / 'vendor/doctemplates-0.11.1/test').glob('*.test'))
    assert len([c for c in cases if 'originalUnit' in c]) == 9
    assert len([c for c in cases if c.get('stringOriginal')]) == 31
    assert len([c for c in cases if c.get('roman') and c['op']=='runtime']) == 4001
    print(f'Verified {len(cases)} original template-runtime observations and {len(hashes)} source files')
    raise SystemExit
assert args.oracle, '--oracle is required'
def native(requests):
    result = subprocess.run([args.oracle], input='\n'.join(json.dumps(x) for x in requests) + '\n', text=True,
                            capture_output=True, check=True, cwd=root / 'vendor/doctemplates-0.11.1')
    lines = [json.loads(x) for x in result.stdout.splitlines()]
    assert len(lines) == len(requests), (len(lines), len(requests), result.stderr)
    for request, output in zip(requests, lines):
        assert not isinstance(output, dict) or not ({'error', 'exception'} & output.keys()), (request, output)
    return lines
cases = []
def compiled(path, text, context, expected=None, variables=None, **metadata):
    output, = native([['compile', [path, text, context]]])
    if expected is not None: assert output['rendered']['renders'][0 if 'width' not in metadata else 4][0] == expected, (path, output)
    if variables is not None: assert output['variables'] == variables
    cases.append(dict(op='runtime', input=[output['template'], context], expected=output, **metadata))
for path in sorted((root / 'vendor/doctemplates-0.11.1/test').glob('*.test')):
    j, text, expected = path.read_text().split('\n.\n')
    compiled('test/' + path.with_suffix('.txt').name, text + '\n', ['json', json.loads(j)], expected, original=path.name)

# Nine successful original unit cases. Six parser diagnostic cases stay pending.
compiled('foo', '$-- hi', ['json', {}], '', originalUnit='comment with no newline')
compiled('foo', 'not breakable and$~$ this is breakable\nok? $foo$$~$', ['json', {'foo': '42'}],
         'not breakable and\nthis is\nbreakable\nok? 42', width=10, originalUnit='reflow')
compiled('foo', '$foo/nowrap$\n$foo$', ['map', [['foo', ['simple', ['hsep', ['hello', 'this', 'is', 'a', 'test', 'of', 'the', 'wrapping']]]]]],
         'hello this is a test of the wrapping\nhello this\nis a test\nof the\nwrapping', width=10, originalUnit='nowrap pipe')
compiled('foo', '$one/alpha$ $twentysix/alpha$ $twentyseven/alpha$ $zero/alpha$',
         ['json', {'one': 1, 'twentysix': 26, 'twentyseven': 27, 'zero': 0}], 'a z a 0', originalUnit='alpha pipe')
for name, text, variables in [
    ('getVariables', '$b$$a$$if(foo.bar)$$b$$else$$baz$$endif$', ['b', 'a', 'foo.bar', 'baz']),
    ('loop-bound variables', '$for(employee)$$employee.name$: $it.salary$ $company$$sep$$separator$$endfor$', ['employee', 'company', 'separator']),
    ('bracketed loop', '$items[, ]$', ['items']),
    ('nested loops', '$for(a)$$for(a.b)$$it$$a.c$$x$$endfor$$endfor$', ['a', 'x']),
    ('it outside loops', '$it$', ['it'])]:
    compiled('', text, ['json', {}], variables=variables, originalUnit=name)

requests = []
def add(op, value, **metadata): requests.append(dict(op=op, input=value, **metadata))
def var(parts=('x',), pipes=()): return [list(parts), list(pipes)]
def interp(parts=('x',), pipes=()): return ['interpolate', var(parts, pipes)]
def literal(d): return ['literal', d]
def cat(*templates):
    result = ['empty']
    for t in reversed(templates): result = ['append', t, result]
    return result
def runtime(t, v, **metadata): add('runtime', [t, ['map', [['x', v]]]], **metadata)

strings = ['', 'a', 'a\n', '\na\n\n', ' x\t\r\ny ', 'ßİﬃσς𐐨', '中😀é', '\u0000', 'a b c d', '001', '27', '0', '-1', '+1', '١٢']
docs = strings + [['empty'], ['space'], ['cr'], ['newline'], ['blanklines', 2], ['raw', 0, ''],
    ['raw', -4, 'negative'], ['hsep', ['hello', 'this', 'wraps']],
    ['prefix', '> ', ['hsep', ['abc', 'def', 'ghi']]], ['bold', 'styled'], ['link', 'https://x', 'linked'],
    ['block', 4, [[[None, 'ab'], ['url', 'cd']], [[None, 'ef']]]],
    ['cooked', 7, [[None, 'ßa'], ['url', '😀b'], [None, '']]],
    ['concat', 'a', ['concat', ['newline'], ['newline']]], ['before', ['newline']], ['flush', ['vcat', ['a', 'b']]]]
vals = [['simple', d] for d in docs] + [['null'], ['bool', False], ['bool', True], ['list', []], ['map', []],
    ['json', ['a', False, None, {'k': 'v'}]], ['json', {'z': 1, 'a': 'first', '😀': False, '\uffff': None}],
    ['list', [['null'], ['list', [['bool', False], ['list', [['simple', 'yes']]]]]]]]
pipes = ['pairs', 'uppercase', 'lowercase', 'length', 'reverse', 'first', 'last', 'rest', 'allbutlast', 'chomp', 'alpha', 'roman', 'nowrap']
pipes += [['block', a, n, l, r] for a in range(3) for n in [-2, 0, 1, 3, 8] for l, r in [('', ''), ('|', '|'), ('😀', '中'), ('', '>')]]
# Interpolation, truthiness and binding of the same piped value are all observed.
for pipe in [None] + pipes:
    ps = [] if pipe is None else [pipe]
    t = cat(interp(pipes=ps), literal('|'), ['conditional', var(pipes=ps), literal('yes'), literal('no')],
            literal('|'), ['iterate', var(pipes=ps), cat(interp(('it',)), interp(('it', 'key')), interp(('it', 'value')), interp()), literal(',')])
    for v in vals: runtime(t, v)

for n in range(4001): runtime(interp(pipes=['roman']), ['json', n], roman=True)
for n in ['00001', '00000', '26', '27', str(2**63-1), str(2**63), str(2**64-1), str(2**64), str(2**64+27),
          '9'*1000, '1 '*4, ' 1', '1\n', '1.0', '１２', '١', '\u0967']:
    for pipe in ['alpha', 'roman']: runtime(interp(pipes=[pipe]), ['simple', n])
for first in pipes[:13]:
    for second in pipes[:13]:
        for v in vals[::5]: runtime(interp(pipes=[first, second]), v)

rng = random.Random(0xD0C111)
parts = [[], ['x'], ['x', 'a'], ['x', 'key'], ['x', 'value'], ['x', 'a', 'b'], ['it'], ['it', 'key'], ['it', 'value'], ['missing'], ['x.a'], ['a'], ['😀'], ['\uffff']]
def random_var(): return var(rng.choice(parts), rng.sample(pipes, rng.randrange(3)))
def random_template(depth):
    if depth == 0: return rng.choice([['empty'], literal(rng.choice(docs)), ['interpolate', random_var()]])
    kind = rng.randrange(8)
    if kind == 0: return literal(rng.choice(docs))
    if kind == 1: return ['interpolate', random_var()]
    if kind == 2: return ['conditional', random_var(), random_template(depth-1), random_template(depth-1)]
    if kind == 3: return ['iterate', random_var(), random_template(depth-1), random_template(depth-1)]
    if kind == 4: return ['nested', random_template(depth-1)]
    if kind == 5: return ['partial', rng.sample(pipes, rng.randrange(3)), random_template(depth-1)]
    return [rng.choice(['concat', 'append']), random_template(depth-1), random_template(depth-1)]
for _ in range(2500):
    t = random_template(4)
    v = rng.choice(vals)
    context = ['map', [['x', v], ['it', rng.choice(vals)], ['a', rng.choice(vals)],
                       ['😀', rng.choice(vals)], ['\uffff', rng.choice(vals)]]]
    add('runtime', [t, context])

# Column-sensitive cases distinguish evaluation from final document concatenation.
for v in vals:
    for separator in [literal('xx'), ['nested', literal('a\nb')], ['partial', ['length'], literal('long')]]:
        runtime(cat(literal('abc'), ['iterate', var(), ['nested', literal('1\n2')], separator],
                    ['nested', literal('end\nnext')]), v)
    runtime(cat(literal('abc'), ['partial', ['length'], literal('abcdefgh')], ['nested', literal('x\ny')]), v)
    runtime(['iterate', var(('missing',), [['block', 0, 2, '', '']]),
             cat(interp(('missing',)), literal('|'), interp(('it',))), literal(',')], v)

# Int64 state wrap and the original lazy empty-nest guard: no giant allocation.
for n in [-2**63, -2**63+1, -1, 0, 1, 2**63-2, 2**63-1]:
    runtime(cat(literal(['raw', n, 'x']), ['nested', ['empty']]), ['null'])
    runtime(cat(literal(['raw', n, 'x']), literal(['raw', 1, 'y']),
                ['nested', ['empty']]), ['null'])
    if n < 0:
        runtime(cat(literal(['raw', n, 'x']), ['nested', literal('a\nb')]), ['null'])

variables = [var(p, fs) for p in parts for fs in [[], ['length'], ['uppercase', 'reverse']]]
for _ in range(300): add('variable', [rng.choice(variables), rng.choice(variables)])
order_pipes = pipes[:13] + [['block', a, n, l, r] for a in range(3) for n in [-2**63, -1, 0, 2**63-1]
                          for l, r in [('', ''), ('😀', ''), ('\uffff', 'z'), ('a', '😀'), ('a', '\uffff')]]
add('pipe-order', rng.sample(order_pipes, len(order_pipes)))

# Exercise the genuine original String target, not a Text render converted afterwards.
for case in cases[:]:
    requests.append(dict(op='runtime-string', input=case['input'], stringOriginal=True))
for request in requests[:]:
    if request['op']=='runtime' and rng.randrange(13)==0:
        requests.append(dict(op='runtime-string', input=request['input']))

for start in range(0, len(requests), 128):
    batch = requests[start:start+128]
    for request, output in zip(batch, native([[c['op'], c['input']] for c in batch])):
        cases.append(dict(request, expected=output))
manifest = dict(upstream='doctemplates-0.11.1', cases=len(cases), caseHash=digest(cases),
                operations=dict(collections.Counter(c['op'] for c in cases)), sources=hashes)
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', mtime=0, filename='') as gz:
    gz.write(('\n'.join(json.dumps(c, ensure_ascii=False, separators=(',', ':')) for c in [manifest] + cases) + '\n').encode())
print(f'Captured {len(cases)} original template-runtime observations: {manifest["operations"]}')
