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
        'lost-pandoc-types-test': lambda x: x['pandoc_types'].pop(),
        'false-mapping': lambda x: next(r for r in x['pandoc_types'] if r['status'] == 'unmapped').update(status='mapped'),
    }
    for name, mutate in mutations.items():
        candidate = json.loads(json.dumps(original))
        mutate(candidate)
        target.write_text(json.dumps(candidate, indent=2, ensure_ascii=False) + '\n')
        result = run(target)
        assert result.returncode != 0 and 'Inventory differs' in result.stderr, (name, result.stderr)
        print('PASS rejected ' + name, flush=True)
