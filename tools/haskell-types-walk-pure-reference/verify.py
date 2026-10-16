#!/usr/bin/env python3
"""Source/artifact integrity and actual installed Identity dictionary replay."""
import argparse,hashlib,json,subprocess
from pathlib import Path
from capture import requests,combined,FIXTURE
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def paths():
    names=['upstream/pandoc-types/pandoc-types.cabal','upstream/pandoc-types/src/Text/Pandoc/Walk.hs','upstream/pandoc-types/src/Text/Pandoc/Generic.hs','upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/src/Text/Pandoc/Builder.hs','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocWalk.kt','core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesWalkPureDemandTest.kt',str(FIXTURE.relative_to(ROOT))]
    names += [str(p.relative_to(ROOT))for p in sorted(HERE.iterdir())if p.is_file()and p.name not in {'source-manifest.json','README.md'}]
    return sorted(set(names))
def main():
    p=argparse.ArgumentParser();p.add_argument('--record',action='store_true');p.add_argument('--check-source',action='store_true');p.add_argument('--ghc');a=p.parse_args()
    rows=[json.loads(x)for x in requests().splitlines()]
    contract=dict(original_response_count=len(rows),walk_kotlin_observation_count=sum(r['probe'].startswith('walk')for r in rows),generic_source_only_count=sum(not r['probe'].startswith('walk')for r in rows),unchanged_walk_slots=174,unchanged_walk_helpers=24,strict_callback_adapter='walkLazy',lazy_pair_adapter='Walk.lazyPair / WalkLazyPair',scope='finite representable lazy demand; not general GHC heap/bottom/infinite-spine equivalence')
    if a.record:(HERE/'source-manifest.json').write_text(json.dumps(dict(schema=1,contract=contract,sha256={name:sha(ROOT/name)for name in paths()}),indent=2)+'\n')
    manifest=json.loads((HERE/'source-manifest.json').read_text());assert manifest['contract']==contract
    for name,digest in manifest['sha256'].items():assert sha(ROOT/name)==digest,'hash mismatch: '+name
    req=(HERE/'original-requests.jsonl').read_bytes();res=(HERE/'original-responses.jsonl').read_bytes()
    assert requests()==req;assert combined(req,res)==FIXTURE.read_bytes()
    historical=[(req,'a223cd8385bd8e0fbe505606e05f6543726ae3f5d784da64c156d8cf322b8bf9'),(res,'766733d39a3dac7f7d9ec2c34168f97d6ab50b79f5af33fab2054117acb566d8'),(FIXTURE.read_bytes(),'7f6a82554614a8122e1c8b163c666e9e9eb4fc259667d2acfc7a51d0e2c5d14d')]
    for stream,digest in historical:
        prefix=b'\n'.join(stream.splitlines()[:38])+b'\n'
        assert hashlib.sha256(prefix).hexdigest()==digest,'initial38 original expectations changed'

    if a.ghc:
        ghc=Path(a.ghc);directory=subprocess.check_output([str(ghc.with_name('ghc-pkg')),'field','ghc-internal','import-dirs'],text=True).split(':',1)[1].strip()
        actual=subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/'GHC/Internal/Data/Functor/Identity.hi')])
        assert actual==(HERE/'GhcIdentity.iface.txt').read_bytes();print('Actual GHC Identity interface byte-exact PASS')
    print(f"Pure Walk source PASS: {contract['walk_kotlin_observation_count']} observations, {len(manifest['sha256'])} hashes")
if __name__=='__main__':main()
