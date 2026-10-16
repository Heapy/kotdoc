#!/usr/bin/env python3
"""Conservative, source-backed test inventory. No upstream executables are run."""
import argparse
import ast
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/test-inventory.json'
PT = 'upstream/pandoc-types/test/test-pandoc-types.hs'
AST = 'core/src/test/kotlin/io/heapy/kotdoc/ast/'


def digest(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def tracked(folder):
    return subprocess.check_output(['git', '-C', str(ROOT / folder), 'ls-files', '-z']).decode().split('\0')[:-1]


def mapping(file, method, dynamic=None):
    path = AST + file + '.kt'
    text = (ROOT / path).read_text()
    assert 'fun `' + method + '`' in text, (path, method)
    production = {'UpstreamJsonTest': ('PandocJson.kt', ['encode/toJson (typed dispatch)', 'decode/fromJson (typed dispatch)']),
                  'TablesTest': ('Tables.kt', ['simpleTable'] if method == 'upstream table sanitisation' else ['normalize'] if method == 'upstream table normalization example' else ['normalizeHead/normalizeBody/normalizeFoot']),
                  'PandocJsonTest': ('PandocJson.kt', ['encode', 'decode']),
                  'BuilderTest': ('Builder.kt', ['simpleFigureWith']),
                  'PandocVisitorTest': ('PandocVisitor.kt', ['walk'])}[file]
    production_path = 'core/src/main/kotlin/io/heapy/kotdoc/ast/' + production[0]
    return dict(path=path, sha256=digest(path), method=method, dynamic_case=dynamic,
                production=dict(path=production_path, sha256=digest(production_path), entrypoints=production[1]),
                command='./gradlew :core:test --tests io.heapy.kotdoc.ast.' + file)


def pt_inventory():
    text = (ROOT / PT).read_text()
    registration = text[text.index('tests :: [Test]'):]
    properties = re.findall(r'testProperty "([^"]+)"\s+([^\n]+)', registration)
    examples = re.findall(r'testEncodeDecode "([^"]+)" (t_\w+)', registration)
    assert len(properties) == 23 and len(examples) == 56
    records = []
    for name, expr in properties:
        m = None
        gap = 'Original QuickCheck generator/shrinker and full predicate replay not yet mapped.'
        walks = {
            'p_walk inlineTrans': 'upstream inline transformation agrees with independent generic traversal',
            'p_walk blockTrans': 'upstream block transformation agrees with independent generic traversal',
            'p_walk metaValueTrans': 'upstream metadata value transformation agrees with generic traversal',
            'p_walk metaTrans': 'metadata walk transforms top-level map only',
            'p_query inlineQuery': 'inline query matches generic query including metadata captions cells and citation affixes',
            'p_query blockQuery': 'block query matches generic header query',
            'p_query metaValueQuery': 'metadata query matches generic strings and map size',
            'p_query metaQuery': 'metadata query matches generic strings and map size',
        }
        if name in walks:
            m = mapping('PandocVisitorTest', walks[name])
            gap = 'Related translated transformation/query checked with independent generic JSON traversal over fixtures; original QuickCheck generator/shrinker and generic Haskell predicate replay missing. Unicode uses JVM Locale.ROOT here.'
        elif name == 'round-trip':
            m = mapping('PandocJsonTest', 'upstream roundtrip property with reproducible recursive documents')
            gap = 'Same roundtrip predicate with 500 Kotlin-generated documents; original generator/shrinker not ported.'
        elif name.startswith('p_tableNorm'):
            which = next(x for x in ('Head', 'Body', 'Foot') if x in name).lower()
            suffix = ('normalization is idempotent' if 'Idempotent' in name else
                      'normalization preserves cell subset' if 'IsSubset' in name else 'first row' + ('s' if which == 'body' else '') + ' ha' + ('ve' if which == 'body' else 's') + ' correct width')
            m = mapping('TablesTest', 'upstream ' + which + ' ' + suffix)
            gap = 'Translated predicate uses Kotlin seeded samples; complete upstream generator/shrinker and shrinking semantics remain pending.'
        elif name.startswith('p_figureRepresentation'):
            m = mapping('BuilderTest', 'upstream figure representation property')
            gap = 'Translated predicate uses 500 Kotlin samples; original arbitrary inline generator/shrinker not ported.'
        records.append(dict(id='pandoc-types/property/' + name, name=name, expression=expr.strip(),
                            source=PT, source_sha256=digest(PT), kind='source-property',
                            status='partial' if m else 'unmapped', kotlin=m,
                            expectation_origin=PT + ' (predicate); src/Text/Pandoc/Arbitrary.hs (generators)', gap=gap))
    for name, symbol in examples:
        records.append(dict(id='pandoc-types/json/' + symbol, name=name, expression=symbol,
                            source=PT, source_sha256=digest(PT), kind='paired-literal-example',
                            assertions=['Encoding', 'Decoding'], status='mapped',
                            kotlin=mapping('UpstreamJsonTest', 'upstream exact JSON encoding and typed decoding', [symbol + ' encoding', symbol + ' decoding']),
                            expectation_origin=PT + ':' + symbol + '; original literal JSON imported by tools/import-pandoc-json-fixtures.py; independently translated typed values in UpstreamJsonTest.kt', gap=''))
    for symbol, name, method in [('t_tableSan', 'table sanitisation', 'upstream table sanitisation'),
                                 ('t_tableNormExample', 'table normalization example', 'upstream table normalization example')]:
        assert re.search(r'^' + symbol + r' = testCase "' + name + '"', text, re.M)
        records.append(dict(id='pandoc-types/literal/' + symbol, name=name, expression=symbol,
                            source=PT, source_sha256=digest(PT), kind='literal-assertion', status='mapped',
                            kotlin=mapping('TablesTest', method), expectation_origin=PT + ':' + symbol + ' (independent literal expected table)', gap=''))
    assert len(records) == 81
    return sorted(records, key=lambda r: r['id'])


def build():
    pins = {}
    paths = set()
    entries = []
    def entry(path, name, kind, line=1, gap='Leaf extraction and Kotlin mapping not yet audited.'):
        entries.append(dict(id=kind + '/' + path + '/' + name,
                            source=path, source_sha256=digest(path), line=line, name=name, kind=kind,
                            status='unmapped', kotlin=None, expectation_origin=path,
                            execution_command=None, gap=gap))
    for folder in ('upstream/pandoc', 'upstream/pandoc-types', 'upstream/calibre'):
        pins[folder] = subprocess.check_output(['git', 'rev-parse', 'HEAD:' + folder], cwd=ROOT, text=True).strip()
        actual = subprocess.check_output(['git', '-C', str(ROOT / folder), 'rev-parse', 'HEAD'], text=True).strip()
        assert actual == pins[folder], 'Submodule checkout differs from root pin: ' + folder
        for rel in tracked(folder):
            path = folder + '/' + rel
            if (ROOT / path).is_file() and (folder != 'upstream/calibre' or path.endswith(('.py', '.pyj'))):
                if folder != 'upstream/calibre' and ('/test/' in path or path.endswith('.cabal')):
                    paths.add(path)
                elif folder == 'upstream/calibre':
                    text = (ROOT / path).read_text(errors='replace')
                    if re.search(r'\b(?:def (?:find_tests|test_\w+)|unittest|doctest)\b', text) or path in (folder + '/setup/test.py', folder + '/src/calibre/utils/run_tests.py') or '/conversion/plugins/' in path or path.endswith(('/customize/ui.py', '/customize/builtins.py')):
                        paths.add(path)
    # Dependency cabal declarations and complete test roots are preserved as source assets.
    for path in sorted((ROOT / 'vendor').rglob('*')):
        if path.is_file() and (path.suffix == '.cabal' or 'test' in [x.lower() for x in path.relative_to(ROOT / 'vendor').parts[:-1]] or 'tests' in [x.lower() for x in path.relative_to(ROOT / 'vendor').parts[:-1]]):
            paths.add(str(path.relative_to(ROOT)))
    paths.update([PT, 'upstream/pandoc-types/src/Text/Pandoc/Arbitrary.hs',
                  'upstream/pandoc-types/src/Text/Pandoc/Definition.hs',
                  'tools/import-pandoc-json-fixtures.py',
                  'core/src/test/resources/pandoc-types/json-fixtures.json'])
    for path in sorted(paths):
        p = ROOT / path
        if p.suffix not in ('.cabal', '.hs', '.py', '.lua', '.pyj'):
            continue
        text = p.read_text(errors='replace')
        if p.suffix == '.cabal':
            for m in re.finditer(r'^test-suite\s+([^\s]+)', text, re.M | re.I):
                entry(path, m[1], 'cabal-test-suite', text[:m.start()].count('\n') + 1)
        if p.suffix == '.hs':
            for m in re.finditer(r'^import\s+(?:qualified\s+)?(Tests(?:\.\w+)+)', text, re.M):
                entry(path, m[1], 'haskell-test-import', text[:m.start()].count('\n') + 1,
                      'Import registration candidate, not proof of reachable leaf execution; audit tree registration and CPP flags.')
        if p.suffix == '.py':
            try:
                tree = ast.parse(text)
            except SyntaxError:
                entry(path, 'parse-required', 'python-discovery-gap', gap='Python AST unavailable; manually audit this source.')
                continue
            def qualified_nodes(node, prefix=''):
                for child in ast.iter_child_nodes(node):
                    scope = isinstance(child, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef))
                    qualified = prefix + child.name if scope else prefix
                    if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        yield child, qualified
                    yield from qualified_nodes(child, qualified + '.' if scope else prefix)
            for node, qualified in qualified_nodes(tree):
                if node.name.startswith('test_') or node.name in ('find_tests', 'suite', 'test_suite', 'load_tests'):
                    entry(path, qualified, 'python-source-test-candidate' if node.name.startswith('test_') else 'python-suite-factory', node.lineno,
                          'Static candidate includes nested/conditional tests; reconcile with original runtime registry before counting source tests.')
    ids = [e['id'] for e in entries]
    assert len(ids) == len(set(ids)), 'Duplicate registry identity requires explicit disambiguation'
    # Explicit roots prevent tests outside the primary Haskell executable being forgotten.
    roots = [dict(path=p, sha256=digest(p), role=role) for p, role in [
        ('upstream/pandoc/test/test-pandoc.hs', 'Main Tasty registration, Command, Old, reader/writer/common suites'),
        ('upstream/pandoc/test/Tests/Command.hs', 'Markdown parser selects every top-level CodeBlock; stdout/stderr/status expectations'),
        ('upstream/pandoc/test/Tests/Old.hs', 'Computed legacy golden matrix; helper expansion still required'),
        ('upstream/pandoc/pandoc-lua-engine/test/test-pandoc-lua-engine.hs', 'Separate Lua filters/modules/custom readers/writers'),
        (PT, 'Complete 81 source tests below; 56 JSON examples have two assertions each'),
        ('upstream/calibre/setup/test.py', 'Calibre group registry and separate RapydScript runner'),
        ('upstream/calibre/src/calibre/utils/run_tests.py', 'Conditional runtime suites, including embedded find_tests'),
        ('upstream/calibre/src/calibre/customize/ui.py', 'Input/output format enumeration, aliases and enabled plugins'),
        ('upstream/calibre/src/calibre/customize/builtins.py', 'Built-in conversion plugin registry')]]
    return dict(schema_version=1, generator=dict(path='tools/build-test-inventory.py', sha256=digest('tools/build-test-inventory.py')), scope='Source registry snapshot plus complete pandoc-types main source-test enumeration; NOT complete global leaf inventory or port coverage.',
                counting_units=dict(source_test='Registered predicate/example; paired examples retain two assertion names.', trial='Generated property input, not another source test.', observation='Oracle capture, not another source test.', registry='Suite/factory/import candidate, not a source test count.'),
                pins=pins, roots=roots, sources=[dict(path=p, sha256=digest(p)) for p in sorted(paths)],
                registry=sorted(entries, key=lambda r: r['id']), pandoc_types=pt_inventory(),
                separate_requirements=[dict(id=x, status='unmapped', gap=g) for x, g in [
                    ('pandoc-types/generators-shrinkers', 'Audit every Arbitrary instance/generator/shrinker independently; Kotlin seeded samples do not port QuickCheck semantics.'),
                    ('pandoc-types/Read', 'Derived Read and lexical/partial contracts require separate acceptance beyond JSON examples.'),
                    ('pandoc-types/Eq-Ord', 'Existing comparison corpora require a separately reviewed mapping; observations are not registered test counts.'),
                    ('pandoc/all-leaves', 'Expand computed Old matrices, exact command AST blocks, Lua and CPP-dependent suite trees; map every assertion.'),
                    ('dependencies/all-leaves', 'Cabal declarations/test trees are source discovery only; embedded doctests and non-cabal runners require audit.'),
                    ('calibre/formats-and-tests', 'Reconcile runtime suite IDs, embedded tests, standalone/doctest/RapydScript runners and directional additional-format matrix; map Kotlin evidence.')]])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='Explicitly refresh the reviewable snapshot; never creates expected conversion output.')
    parser.add_argument('--snapshot', type=Path, default=OUTPUT, help='Snapshot to verify or explicitly refresh (default: docs/test-inventory.json).')
    args = parser.parse_args()
    result = build()
    data = json.dumps(result, indent=2, ensure_ascii=False) + '\n'
    if args.write:
        args.snapshot.write_text(data)
    elif not args.snapshot.exists() or args.snapshot.read_text() != data:
        raise SystemExit('Inventory differs: source paths/hashes, registrations, pins or mappings changed. Review and refresh with --write.')
    print(f"Verified {len(result['sources'])} source/assets, {len(result['registry'])} registry candidates, 81 pandoc-types source tests (56 paired examples; 137 assertions/property registrations). Global leaf inventory remains incomplete.")


if __name__ == '__main__':
    main()
