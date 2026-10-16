#!/usr/bin/env python3
"""Exercise inventory drift rejection using temporary snapshots, without editing sources."""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFIER = ROOT / 'tools/build-test-inventory.py'
original = json.loads((ROOT / 'docs/test-inventory.json').read_text())


def run(path):
    return subprocess.run([sys.executable, str(VERIFIER), '--snapshot', str(path)],
                          cwd=ROOT, capture_output=True, text=True)


with tempfile.TemporaryDirectory(prefix='kotdoc-test-inventory-') as folder:
    target = Path(folder) / 'snapshot.json'
    target.write_text(json.dumps(original, indent=2, ensure_ascii=False) + '\n')
    result = run(target)
    assert result.returncode == 0, result.stderr
    print('PASS canonical snapshot', flush=True)
    mutations = {
        'lost-source': lambda x: x['sources'].pop(),
        'source-hash-drift': lambda x: x['sources'][0].update(sha256='0' * 64),
        'lost-registration': lambda x: x['registry'].pop(),
        'lost-dependency-root': lambda x: x['dependency_roots']['roots'].pop(),
        'lost-dependency-suite': lambda x: x['dependency_roots']['cabal_test_suites'].pop(),
        'lost-frozen-dependency': lambda x: x['dependency_roots']['frozen_dependencies'].pop(),
        'lost-embedded-signal': lambda x: x['dependency_roots']['embedded_and_main_signals'].pop(),
        'lost-runtime-leaf': lambda x: x['pandoc_runtime']['leaves'].pop(),
        'changed-runtime-path': lambda x: x['pandoc_runtime']['leaves'][0]['components'].append('invented'),
        'lost-haskell-module': lambda x: x['pandoc_haskell_census']['modules'].pop(),
        'lost-haskell-site': lambda x: next(m for m in x['pandoc_haskell_census']['modules'] if m['registration_sites'])['registration_sites'].pop(),
        'lost-haskell-reference': lambda x: x['pandoc_haskell_census']['module_registrations'].pop(),
        'lost-nested-lua-leaf': lambda x: x['pandoc_lua_nested']['leaves'].pop(),
        'changed-lua-callback-span': lambda x: x['pandoc_lua_nested']['leaves'][0]['registration_span'].update(first_line=1),
        'lost-command-leaf': lambda x: x['pandoc_command'].pop(),
        'changed-command-stdout': lambda x: x['pandoc_command'][0].update(expected_stdout='invented'),
        'lost-old-leaf': lambda x: x['pandoc_old'].pop(),
        'changed-old-expected-hash': lambda x: x['pandoc_old'][0]['expectation_origin'].update(sha256='0' * 64),
        'lost-lua-registration': lambda x: x['pandoc_lua']['registrations'].pop(),
        'miscounted-oracle-evidence': lambda x: next(r for r in x['checkpoint_evidence'] if r['kind']=='oracle-observation-corpus').update(observations=1),
        'lost-pandoc-types-test': lambda x: x['pandoc_types'].pop(),
        'false-mapping': lambda x: next(r for r in x['pandoc_types'] if r['status'] == 'partial').update(status='mapped'),
    }
    for name, mutate in mutations.items():
        candidate = json.loads(json.dumps(original))
        mutate(candidate)
        target.write_text(json.dumps(candidate, indent=2, ensure_ascii=False) + '\n')
        result = run(target)
        assert result.returncode != 0 and 'Inventory differs' in result.stderr, (name, result.stderr)
        print('PASS rejected ' + name, flush=True)
