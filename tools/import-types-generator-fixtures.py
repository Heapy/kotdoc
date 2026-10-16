#!/usr/bin/env python3
"""Original QuickCheck seed/split/combinator observations, independent of Kotlin."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'core/src/test/resources/pandoc-types'
SOURCES = ['tools/import-types-generator-fixtures.py',
           'licenses/QuickCheck-BSD-3-Clause.txt', 'licenses/splitmix-BSD-3-Clause.txt', 'licenses/random-LICENSE.txt',
           *['vendor/quickcheck-2.16.0.0/' + n + '.hs' for n in ['Gen', 'Arbitrary', 'Random']],
           *['vendor/random-1.3.1/' + n + '.hs' for n in ['Internal', 'Random']], 'vendor/splitmix-0.1.3.2/SplitMix.hs',
           'upstream/pandoc-types/src/Text/Pandoc/Arbitrary.hs',
           'upstream/pandoc-types/src/Text/Pandoc/Definition.hs',
           'upstream/pandoc-types/src/Text/Pandoc/Builder.hs',
           'core/src/test/resources/pandoc-types/properties-manifest.json',
           'core/src/test/resources/pandoc-types/properties.jsonl.gz',
           *['tools/haskell-types-generator-reference/' + n for n in ['QuickCheckReference.hs', 'NoShrinkReference.hs', 'README.md', 'kotdoc-types-generator-reference.cabal', 'cabal.project', 'cabal.project.freeze']]]
def hashes():
    return {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in SOURCES}
def main():
    p = argparse.ArgumentParser(); p.add_argument('--oracle'); p.add_argument('--no-shrink-oracle'); p.add_argument('--check-source', action='store_true'); a = p.parse_args()
    dest = DEST / 'quickcheck.jsonl.gz'; manifest = DEST / 'quickcheck-manifest.json'
    if a.check_source:
        m = json.loads(manifest.read_text()); raw = gzip.decompress(dest.read_bytes())
        assert m['sources'] == hashes()
        assert m['sha256'] == hashlib.sha256(raw).hexdigest()
        assert m['cases'] == len(raw.splitlines()) == 420
        no_shrink = gzip.decompress((DEST / 'no-shrink.jsonl.gz').read_bytes())
        assert m['noShrinkSha256'] == hashlib.sha256(no_shrink).hexdigest()
        assert m['noShrinkCases'] == len(no_shrink.splitlines()) == 270
        assert all(json.loads(line)['expected'] == [] for line in no_shrink.splitlines())
        print('Original QuickCheck source/payload hashes and 420 seed/size cases verified'); return
    if not a.oracle or not a.no_shrink_oracle: p.error('--oracle and --no-shrink-oracle are required')
    raw = subprocess.check_output([a.oracle]); assert len(raw.splitlines()) == 420
    no_shrink = subprocess.check_output([a.no_shrink_oracle]); assert len(no_shrink.splitlines()) == 270
    assert all(json.loads(line)['expected'] == [] for line in no_shrink.splitlines())
    (DEST / 'no-shrink.jsonl.gz').write_bytes(gzip.compress(no_shrink, mtime=0))
    dest.write_bytes(gzip.compress(raw, mtime=0))
    manifest.write_text(json.dumps({'noShrinkCases': 270, 'noShrinkSha256': hashlib.sha256(no_shrink).hexdigest(), 'cases': 420, 'sha256': hashlib.sha256(raw).hexdigest(), 'sources': hashes()}, indent=2) + '\n')
    print('Captured 420 original seeded QuickCheck operation cases')
if __name__ == '__main__': main()
