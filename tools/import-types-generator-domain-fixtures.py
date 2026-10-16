#!/usr/bin/env python3
"""Standalone19 and private generator helpers: execute unchanged original source."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'core/src/test/resources/pandoc-types'
PROJECT = 'tools/haskell-types-generator-reference'
SOURCES = ['tools/import-types-generator-domain-fixtures.py',
           *[PROJECT + '/' + n for n in ['StandaloneGeneratorsReference.hs', 'PrivateGeneratorObservations.expr', 'kotdoc-types-generator-reference.cabal', 'cabal.project', 'cabal.project.freeze']],
           *['upstream/pandoc-types/src/Text/Pandoc/' + n + '.hs' for n in ['Arbitrary', 'Definition', 'Builder']],
           *['vendor/quickcheck-2.16.0.0/' + n + '.hs' for n in ['Arbitrary', 'Gen', 'Random']],
           'vendor/splitmix-0.1.3.2/SplitMix.hs', 'vendor/random-1.3.1/Internal.hs',
           'licenses/pandoc-types-BSD-3-Clause.txt', 'licenses/QuickCheck-BSD-3-Clause.txt',
           'licenses/splitmix-BSD-3-Clause.txt', 'licenses/random-LICENSE.txt']
def hashes():
    return {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in SOURCES}
def validate(standalone, helpers):
    a=[json.loads(x) for x in standalone.splitlines()]; b=[json.loads(x) for x in helpers.splitlines()]
    assert len(a) == 5700 and len(b) == 4538
    assert len({r['kind'] for r in a}) == 19
    assert all(sum(r['kind']==k for r in a)==300 for k in {r['kind'] for r in a})
    assert len({json.dumps(r['expected']) for r in b if r['kind']=='attr'}) == 18
    constructors=[r for r in b if r['witness'].startswith('constructor:')]
    assert len(constructors)==56
    assert sum(r['kind']=='inline' for r in constructors)==20
    assert sum(r['kind']=='block' for r in constructors)==14
    witnesses={r['witness']:r for r in b if r['witness'] and not r['witness'].startswith('constructor:')}
    assert set(witnesses)=={'char32','char127','char128','char9999','length0','length8'}
    for name,r in witnesses.items():
        if name.startswith('char'): assert chr(int(name[4:])) in r['expected']
        else: assert len(r['expected']) == int(name[6:])
def main():
    p=argparse.ArgumentParser(); p.add_argument('--oracle'); p.add_argument('--cabal',default='cabal')
    p.add_argument('--builddir',default='/tmp/kotdoc-types-generator-build'); p.add_argument('--check-source',action='store_true'); args=p.parse_args()
    manifest=DEST/'generator-domain-manifest.json'
    if args.check_source:
        saved=json.loads(manifest.read_text()); assert saved['sources']==hashes()
        values=[]
        for name in ['standalone-generators','generator-helpers']:
            raw=gzip.decompress((DEST/(name+'.jsonl.gz')).read_bytes())
            assert saved['payloads'][name]==hashlib.sha256(raw).hexdigest(); values.append(raw)
        validate(*values); print('Original19 standalone generators5700 + private/domain4538, source and payload hashes verified'); return
    if not args.oracle: p.error('--oracle is required')
    standalone=subprocess.check_output([args.oracle])
    expression=(ROOT/PROJECT/'PrivateGeneratorObservations.expr').read_text()
    helpers=subprocess.check_output([args.cabal,'exec','-v0','--project-dir='+PROJECT,'--builddir='+args.builddir,'--',
        'ghc','-ignore-dot-ghci','upstream/pandoc-types/src/Text/Pandoc/Arbitrary.hs','-XOverloadedStrings','-e',expression],cwd=ROOT)
    validate(standalone,helpers)
    payloads={}
    for name,raw in [('standalone-generators',standalone),('generator-helpers',helpers)]:
        (DEST/(name+'.jsonl.gz')).write_bytes(gzip.compress(raw,mtime=0)); payloads[name]=hashlib.sha256(raw).hexdigest()
    manifest.write_text(json.dumps({'standaloneCases':5700,'helperCases':4538,'payloads':payloads,'sources':hashes()},indent=2)+'\n')
    print('Captured5700 original standalone instance replays +4538 unchanged-source private helper/domain observations')
if __name__=='__main__': main()
