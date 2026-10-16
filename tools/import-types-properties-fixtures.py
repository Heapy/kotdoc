#!/usr/bin/env python3
"""Capture unchanged original Arbitrary + Data.Generics expectations; no Kotlin oracle."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'core/src/test/resources/pandoc-types'
SOURCES = [
    'upstream/pandoc-types/test/test-pandoc-types.hs',
    *['upstream/pandoc-types/src/Text/Pandoc/' + x + '.hs' for x in ['Arbitrary', 'Definition', 'Walk', 'Builder', 'Generic']],
    'licenses/pandoc-types-BSD-3-Clause.txt',
    'licenses/QuickCheck-BSD-3-Clause.txt',
    'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-case.json',
    'vendor/quickcheck-2.16.0.0/Arbitrary.hs',
    'vendor/quickcheck-2.16.0.0/Gen.hs',
    'tools/import-types-properties-fixtures.py',
    *['tools/haskell-types-properties-reference/' + x for x in ['TypesPropertiesReference.hs', 'kotdoc-types-properties-reference.cabal', 'cabal.project', 'cabal.project.freeze', 'README.md']],
]

def hashes():
    return {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in SOURCES}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--oracle')
    parser.add_argument('--check-source', action='store_true')
    args = parser.parse_args()
    manifest = DEST / 'properties-manifest.json'
    if args.check_source:
        saved = json.loads(manifest.read_text())
        assert saved['sources'] == hashes(), 'source drift; regenerate original oracle'
        raw = gzip.decompress((DEST / 'properties.jsonl.gz').read_bytes())
        shrink = gzip.decompress((DEST / 'shrink.jsonl.gz').read_bytes())
        assert hashlib.sha256(shrink).hexdigest() == saved['shrinkSha256']
        assert len(shrink.splitlines()) == saved['shrinkCases'] == 184
        assert hashlib.sha256(raw).hexdigest() == saved['observationsSha256']
        rows = [json.loads(x) for x in raw.splitlines()]
        assert len(rows) == saved['cases'] == 500
        assert all(r['originalPredicates'] is True for r in rows)
        print('Original types properties: source hashes, payload, 500 replays, 12 original predicates verified')
        return
    if not args.oracle:
        parser.error('--oracle required for capture')
    raw = subprocess.check_output([args.oracle])
    shrink = subprocess.check_output([args.oracle, 'shrink'])
    assert len(shrink.splitlines()) == 184
    rows = [json.loads(x) for x in raw.splitlines()]
    assert len(rows) == 500 and all(r['originalPredicates'] is True for r in rows)
    DEST.mkdir(parents=True, exist_ok=True)
    (DEST / 'properties.jsonl.gz').write_bytes(gzip.compress(raw, mtime=0))
    (DEST / 'shrink.jsonl.gz').write_bytes(gzip.compress(shrink, mtime=0))
    manifest.write_text(json.dumps({'shrinkCases': 184, 'shrinkSha256': hashlib.sha256(shrink).hexdigest(), 'cases': len(rows), 'predicates': 12, 'generator': 'unchanged Text.Pandoc.Arbitrary; QuickCheck 2.16.0.0 mkQCGen; seeds 1..500, sizes cycling 0/1/3/8/30; Pandoc original resize 8', 'observationsSha256': hashlib.sha256(raw).hexdigest(), 'sources': hashes()}, indent=2) + '\n')
    print(f'Captured {len(rows)} original replays / {len(rows) * 12} predicate observations')

if __name__ == '__main__':
    main()
