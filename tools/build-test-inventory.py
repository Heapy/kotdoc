#!/usr/bin/env python3
"""Conservative, source-backed test inventory. No upstream executables are run."""
import argparse
import ast
import hashlib
import gzip
import importlib.util
import sys
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/test-inventory.json'
PT = 'upstream/pandoc-types/test/test-pandoc-types.hs'
AST = 'core/src/test/kotlin/io/heapy/kotdoc/ast/'
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('test_inventory_extraction', ROOT / 'tools/extract-test-inventory.py')
extraction = importlib.util.module_from_spec(spec)
spec.loader.exec_module(extraction)
census_spec = importlib.util.spec_from_file_location('test_inventory_census', ROOT / 'tools/census-test-inventory.py')
census = importlib.util.module_from_spec(census_spec)
census_spec.loader.exec_module(census)
runtime_spec = importlib.util.spec_from_file_location('runtime_test_inventory', ROOT / 'tools/runtime-test-inventory.py')
runtime = importlib.util.module_from_spec(runtime_spec)
runtime_spec.loader.exec_module(runtime)
dependency_spec = importlib.util.spec_from_file_location('dependency_test_inventory', ROOT / 'tools/dependency-test-inventory.py')
dependency = importlib.util.module_from_spec(dependency_spec)
dependency_spec.loader.exec_module(dependency)
calibre_spec = importlib.util.spec_from_file_location('calibre_test_inventory', ROOT / 'tools/calibre-test-inventory.py')
calibre = importlib.util.module_from_spec(calibre_spec)
calibre_spec.loader.exec_module(calibre)


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
        related_mapping = None
        if name.startswith(('p_walk ', 'p_walkList ', 'p_query ', 'p_queryList ')):
            related_mapping = m
            path = AST + 'OriginalTypesPropertiesTest.kt'
            assert '@TestFactory fun originalWalkPredicates()' in (ROOT / path).read_text()
            callback = name.split(' ', 1)[1]
            prefix = 'p_walk' if name.startswith('p_walk') else 'p_query'
            m = dict(path=path, sha256=digest(path), method='originalWalkPredicates',
                     dynamic_case_pattern=prefix + ' ' + callback + ' seed=<original-seed> size=<original-size>',
                     trial_count=500, evidence_ref='local:611/original-walk-and-shrink',
                     production=dict(path='core/src/main/kotlin/io/heapy/kotdoc/ast/PandocVisitor.kt', sha256=digest('core/src/main/kotlin/io/heapy/kotdoc/ast/PandocVisitor.kt'), entrypoints=['walk']),
                     command='./gradlew :core:test --tests io.heapy.kotdoc.ast.OriginalTypesPropertiesTest')
            gap = 'All twelve original predicates replay on 500 unchanged-source generated documents with independent Data.Generics expectations and original foldr/tails adapters. Full generator/shrinker decision/distribution, QCGen parity and broader partial/deep acceptance remain pending. These are 500 trials per source property, not 500 source tests.'
        records.append(dict(id='pandoc-types/property/' + name, name=name, expression=expr.strip(),
                            source=PT, source_sha256=digest(PT), kind='source-property',
                            status='partial' if m else 'unmapped', kotlin=m, related_earlier_mapping=related_mapping,
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


def checkpoint_evidence():
    """Recorded independent evidence is provenance, never additional source tests."""
    pt_path = 'core/src/test/resources/pandoc-types/properties-manifest.json'
    pt = json.loads((ROOT / pt_path).read_text())
    crypto_path = 'core/src/test/resources/crypto-p256-words/data.jsonl.gz'
    with gzip.open(ROOT / crypto_path, 'rt') as stream:
        crypto = json.loads(next(stream))
    for manifest in (pt, crypto):
        for path, expected in manifest['sources'].items():
            if digest(path) != expected:
                raise ValueError('Checkpoint source hash drift: ' + path)
    def assets(paths):
        return [dict(path=p, sha256=digest(p)) for p in paths]
    commit='cdf2b84906e91b1baad740738564855677966acb'
    submodule_pins={}
    def committed_bytes(path,revision=commit):
        for folder in ('upstream/pandoc-types','upstream/pandoc','upstream/calibre'):
            if path.startswith(folder+'/'):
                if (revision,folder) not in submodule_pins:
                    submodule_pins[(revision,folder)]=subprocess.check_output(['git','rev-parse',revision+':'+folder],cwd=ROOT,text=True).strip()
                return subprocess.check_output(['git','show',submodule_pins[(revision,folder)]+':'+path[len(folder)+1:]],cwd=ROOT/folder)
        return subprocess.check_output(['git','show',revision+':'+path],cwd=ROOT)
    generator_manifest_path='core/src/test/resources/pandoc-types/quickcheck-manifest.json'
    generator_manifest=json.loads(committed_bytes(generator_manifest_path))
    historical_sources=[]
    for path,expected in generator_manifest['sources'].items():
        actual=hashlib.sha256(committed_bytes(path)).hexdigest()
        if actual!=expected:raise ValueError('Historical generator source hash differs: '+path)
        historical_sources.append(dict(path=path,sha256=actual))
    generator_artifacts=[generator_manifest_path,'core/src/test/resources/pandoc-types/quickcheck.jsonl.gz',
                         'core/src/test/resources/pandoc-types/no-shrink.jsonl.gz',AST+'OriginalQuickCheck.kt',
                         AST+'OriginalQuickCheckTest.kt',AST+'OriginalTypesShrink.kt',AST+'OriginalTypesPropertiesTest.kt',AST+'OriginalTypesShrinkTest.kt']
    historical_generator=dict(id='local:611/seeded-generator-and-noop-shrink-cdf2b849',
        kind='historical-property-trials-and-test-support-observations',recorded_commit=commit,
        recorded_kotlin_checks=7375,check_components=dict(walk_trials=6000,ordered_shrink_checks=185,quickcheck_operation_checks=420,seeded_pandoc_comparisons=500,implicit_empty_shrink_checks=270),
        operation_observations=generator_manifest['cases'],implicit_empty_shrink_observations=generator_manifest['noShrinkCases'],
        artifacts_from_recorded_commit=[dict(path=p,sha256=hashlib.sha256(committed_bytes(p)).hexdigest()) for p in generator_artifacts],
        independent_sources_from_recorded_commit=historical_sources,
        expectation_origin='Unchanged-source QuickCheck/splitmix/random and original no-op shrink captures; 500 original generator inputs. Sources and artifact hashes read from the recorded Git commit, not concurrent checkout work.',
        fresh_execution_in_this_checkpoint=False,
        gap='Historical finite seeded replay does not establish all seeds/all nineteen generator-instance boundaries, full shrinker lazy/deep/partial acceptance, table/Figure/JSON original-generator replays or Read/ordering. Not additional registered upstream source tests.')
    domain_commit='43db34fe4fd20707b4891e81378c059acdc09d22'
    domain_path='core/src/test/resources/pandoc-types/generator-domain-manifest.json'
    domain_manifest=json.loads(committed_bytes(domain_path,domain_commit))
    domain_sources=[]
    for path,expected in domain_manifest['sources'].items():
        actual=hashlib.sha256(committed_bytes(path,domain_commit)).hexdigest()
        if actual!=expected:raise ValueError('Historical domain source hash differs: '+path)
        domain_sources.append(dict(path=path,sha256=actual))
    domain_artifacts=[domain_path,'core/src/test/resources/pandoc-types/standalone-generators.jsonl.gz',
                      'core/src/test/resources/pandoc-types/generator-helpers.jsonl.gz',
                      AST+'OriginalTypesGeneratorDomainTest.kt',AST+'OriginalSeededTypesArbitrary.kt',
                      'docs/pandoc-types-porting-matrix.md']
    historical_domain=dict(id='local:611/standalone-generator-domain-43db34fe',
        kind='historical-generator-domain-observations',recorded_commit=domain_commit,
        recorded_kotlin_checks=17613,preceding_check_count=7375,
        standalone_generator_observations=domain_manifest['standaloneCases'],helper_domain_observations=domain_manifest['helperCases'],
        instances=19,payloads=domain_manifest['payloads'],
        artifacts_from_recorded_commit=[dict(path=p,sha256=hashlib.sha256(committed_bytes(p,domain_commit)).hexdigest()) for p in domain_artifacts],
        independent_sources_from_recorded_commit=domain_sources,
        expectation_origin='Nineteen unchanged-source seeded generator instances and private helpers loaded in original GHC module context; accepted commit blobs, never current checkout expectations.',
        fresh_execution_in_this_checkpoint=False,
        gap='Finite source-generated observations are not additional upstream source tests, universal seed/domain/shrinker fidelity, table/Figure/JSON property closure or Read/ordering parity.')
    return [historical_domain,historical_generator,dict(id='local:610/p256-word-observations', kind='oracle-observation-corpus',
                 recorded_commit='ad16fb3980bf56b6d405da745f1f07910a2599f2',
                 observations=crypto['cases'], semantic_sha256=crypto['caseHash'],
                 artifacts=assets([crypto_path, 'tools/p256-words-reference/README.md',
                                   'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoP256Words.kt',
                                   'core/src/test/kotlin/io/heapy/kotdoc/crypto/CryptoP256WordsTest.kt']),
                 source_test_coverage='Not counted as a complete original P256Spec port or new upstream source leaves.',
                 recorded_kotlin_checks=79946, fresh_execution_in_this_checkpoint=False,
                 artifact_snapshot_role='Current checkout hashes; historical recorded_commit/counts do not assert these files are byte-identical to that commit or freshly validated.',
                 gap='The historical word checkpoint does not discharge field headers/point engine/public P256 or remaining original tests; this is its evidence boundary, not a claim about current global implementation status.'),
            dict(id='local:611/original-walk-and-shrink', kind='property-trials-and-shrink-observations',
                 recorded_commit='665c5daf970f0981b2851fc4d50fc35ad0dd004a', source_properties=pt['predicates'],
                 generated_inputs=pt['cases'], assertion_trials=pt['predicates'] * pt['cases'],
                 ordered_shrink_observations=pt['shrinkCases'], recorded_kotlin_checks=6185,
                 semantic_sha256=pt['observationsSha256'], shrink_sha256=pt['shrinkSha256'],
                 artifacts_from_recorded_commit=[dict(path=p,sha256=hashlib.sha256(committed_bytes(p,'665c5daf970f0981b2851fc4d50fc35ad0dd004a')).hexdigest()) for p in [pt_path, 'core/src/test/resources/pandoc-types/properties.jsonl.gz',
                                   'core/src/test/resources/pandoc-types/shrink.jsonl.gz',
                                   AST + 'OriginalTypesPropertiesTest.kt', AST + 'OriginalTypesShrinkTest.kt',
                                   AST + 'OriginalTypesShrink.kt', AST + 'OriginalTypesArbitrary.kt',
                                   'tools/haskell-types-properties-reference/README.md']],
                 expectation_origin='Unchanged pinned Text.Pandoc.Arbitrary inputs; independent Data.Generics.everywhere/everything; original ordered shrink outputs.',
                 fresh_execution_in_this_checkpoint=False,
                 artifact_snapshot_role='Immutable Git blobs from recorded_commit; concurrent checkout artifacts are not attributed to this historical result.',
                 gap='Historical 6185-check evidence boundary: full generator/shrinker acceptance, nine no-op shrink APIs, broader Unicode/deep/partial coverage, other properties and Read/ordering were not discharged here. The separate cdf2b849 record adds finite seeded/no-op evidence; this old gap is not current global status.')]


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
    return dict(schema_version=6, generator=dict(path='tools/build-test-inventory.py', sha256=digest('tools/build-test-inventory.py')), scope='Source registry snapshot, complete pandoc-types main registrations, Command and computed Old source leaves, and Lua Haskell/script-suite registrations, finite nested Lua registrations, a Haskell module/site census and original native runtime Tasty census; NOT all conditional profiles, complete expectation attribution or port coverage.',
                counting_units=dict(source_test='Registered predicate/example; paired examples retain two assertion names.', trial='Generated property input, not another source test.', observation='Oracle capture, not another source test.', registry='Suite/factory/import candidate, not a source test count.'),
                pins=pins, roots=roots, sources=[dict(path=p, sha256=digest(p)) for p in sorted(paths)],
                registry=sorted(entries, key=lambda r: r['id']), pandoc_types=pt_inventory(),
                extraction_provenance=[extraction.source('tools/extract-test-inventory.py'), extraction.source('docs/test-inventory-command-blocks.json'), census.source('tools/census-test-inventory.py'), census.source('tools/capture-test-inventory-lua.lua'), census.source('docs/test-inventory-lua-trees.json'), runtime.source_file('tools/runtime-test-inventory.py'), runtime.source_file('tools/prepare-test-inventory-runtime.py'), runtime.source_file('tools/test-inventory-runtime-reference/RuntimeInventory.hs'), runtime.source_file('docs/test-inventory-runtime.json'), dependency.source('tools/dependency-test-inventory.py'), dependency.source('tools/test-inventory-dependency-reference/CabalInventory.hs'), dependency.source('docs/test-inventory-dependency-cabal.json'), calibre.source('tools/calibre-test-inventory.py')],
                pandoc_command=extraction.command_inventory(), pandoc_old=extraction.old_inventory(), pandoc_lua=extraction.lua_inventory(),
                checkpoint_evidence=checkpoint_evidence(), pandoc_haskell_census=census.haskell_inventory(),
                dependency_roots=dependency.inventory(), calibre_source_registry=calibre.inventory(),
                pandoc_runtime=runtime.runtime_inventory(extraction.command_inventory(),extraction.old_inventory()),
                pandoc_lua_nested=census.lua_leaves(extraction.lua_inventory()),
                separate_requirements=[dict(id=x, status='unmapped', gap=g) for x, g in [
                    ('pandoc-types/generators-shrinkers', 'Audit every Arbitrary instance/generator/shrinker independently; Kotlin seeded samples do not port QuickCheck semantics.'),
                    ('pandoc-types/Read', 'Derived Read and lexical/partial contracts require separate acceptance beyond JSON examples.'),
                    ('pandoc-types/Eq-Ord', 'Existing comparison corpora require a separately reviewed mapping; observations are not registered test counts.'),
                    ('pandoc/all-leaves', 'Command/Old and finite nested Lua registrations remain unmapped. Original native-profile runtime Tasty census establishes helper/list/comprehension multiplicities. Other OS/build/conditional profiles, precise generated-leaf expectation attribution and every executable mapping remain required.'),
                    ('dependencies/all-leaves', 'All retained vendor Cabal roots are reconciled against original Cabal conditional AST; exact frozen-release absences and embedded/non-Cabal candidates are explicit. Runtime leaves, expectation/factory closure and Kotlin mappings remain pending.'),
                    ('calibre/formats-and-tests', 'Primary group/factory append declarations, embedded source methods and builtin directional registry matrix reconciled. Runtime availability/factory closure, inherited/generated leaves, fixture expectations, RapydScript and executable mappings remain pending; no calibre implementation started.')]])


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
    print(f"Verified {len(result['sources'])} source/assets, {len(result['registry'])} registry candidates, 81 pandoc-types source tests (56 paired examples; 137 assertions/property registrations). {len(result['pandoc_command'])} Command goldens, {len(result['pandoc_old'])} Old registrations, {len(result['pandoc_lua']['registrations'])} Lua registrations. {len(result['pandoc_haskell_census']['modules'])} remaining Haskell module roots and {len(result['pandoc_lua_nested']['leaves'])} nested Lua registrations; {len(result['pandoc_runtime']['leaves'])} native-profile Tasty leaves; other profiles, precise expectation attribution and global scope remain incomplete.")


if __name__ == '__main__':
    main()
