#!/usr/bin/env python3
"""Validate the finite Walk source matrix and byte-exact original response replay."""
import argparse,gzip,hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
FIXTURES=ROOT/'core/src/test/resources/pandoc-types/walk-closure'
SOURCE_ONLY={'meta-skips-blocks','meta-value-skips-blocks'}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def payload(name):return gzip.decompress((FIXTURES/(name+'.gz')).read_bytes())
def matrix():
    requests=[json.loads(line) for line in payload('walk-requests.jsonl').splitlines()]
    dictionaries=json.loads((HERE/'source-dictionaries.json').read_text())
    concrete=json.loads((HERE/'concrete-dictionaries.json').read_text())
    slots=[]
    for dictionary in dictionaries:
        index=dictionary['index']
        concrete_row=next((r for r in concrete if r['source_index']==index),None)
        for method in ['walk','walkM','query']:
            def matches(r):
                if r.get('method')!=method:return False
                if concrete_row:return not r.get('helper') and not r.get('generic') and r.get('target')==concrete_row['target'] and r.get('root')==concrete_row['root']
                if not r.get('generic'):return False
                pair=r['root'].startswith('pair')
                return (index==1)==pair
            ids=[r['id'] for r in requests if matches(r)]
            assert ids,(index,method)
            slots.append(dict(source_index=index,method=method,source_header=dictionary['source_header'],request_ids=ids))
    helpers=[]
    for name in exports():
        contexts=json.loads((HERE/'helper-contexts.json').read_text())
        method='query' if name.startswith('query') else 'walkM'
        keys={(r['target'],r['root']) for r in contexts if r['query_helper' if method=='query' else 'walk_helper']==name}
        ids=[r['id'] for r in requests if r.get('helper') and r['method']==method and (r['target'],r['root']) in keys]
        assert ids,name
        helpers.append(dict(source_export=name,kotlin_entrypoint=name.replace("M'",'MPrime').replace("'",'Prime'),request_ids=ids))
    assert len(slots)==174 and len(helpers)==24
    return dict(dictionary_count=58,method_slot_count=174,helper_export_count=24,
                original_response_count=len(requests),kotlin_observation_count=sum(r.get('probe')not in SOURCE_ONLY for r in requests),
                source_only_probes=sorted(SOURCE_ONLY),slots=slots,helpers=helpers)
def exports():
    source=(ROOT/'upstream/pandoc-types/src/Text/Pandoc/Walk.hs').read_text()
    module=source[source.index('module Text.Pandoc.Walk'):source.index('import Control.Monad')]
    return re.findall(r'^  , ((?:query\w+|walk\w+M)\'?)$',module,re.M)
def files():
    paths=[
      'upstream/pandoc-types/pandoc-types.cabal','upstream/pandoc-types/src/Text/Pandoc/Walk.hs','upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/src/Text/Pandoc/Builder.hs',
      'core/src/main/kotlin/io/heapy/kotdoc/ast/Pandoc.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocRead.kt',
      'core/src/main/kotlin/io/heapy/kotdoc/ast/AstShow.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/UnicodeText.kt',
      'core/src/main/kotlin/io/heapy/kotdoc/ast/PandocWalk.kt','core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesWalkClosureTest.kt',
      'core/src/test/resources/pandoc-types/read-examples.jsonl','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/Transformers-BSD-3-Clause.txt',
      'vendor/transformers-future-0.6.1.2/Control/Monad/Trans/State/Strict.hs',
      'vendor/transformers-future-0.6.1.2/Control/Monad/Trans/Except.hs',
    ]
    paths += [str(p.relative_to(ROOT)) for p in sorted(HERE.iterdir()) if p.is_file() and p.name not in {'source-manifest.json','README.md'}]
    paths += [str(p.relative_to(ROOT)) for p in sorted(FIXTURES.iterdir()) if p.is_file()]
    return sorted(set(paths))
def main():
    p=argparse.ArgumentParser();p.add_argument('--record',action='store_true');p.add_argument('--check-source',action='store_true');p.add_argument('--binary');p.add_argument('--ghc');a=p.parse_args()
    if a.record:
        data=dict(schema=1,source_contract=matrix(),sha256={name:sha(ROOT/name)for name in files()})
        (HERE/'source-manifest.json').write_text(json.dumps(data,indent=2)+'\n')
    manifest=json.loads((HERE/'source-manifest.json').read_text())
    assert manifest['source_contract']==matrix(),'source/operation matrix changed'
    for name,expected in manifest['sha256'].items():assert sha(ROOT/name)==expected,'source hash mismatch: '+name
    original=(ROOT/'upstream/pandoc-types/src/Text/Pandoc/Walk.hs').read_bytes()
    assert original==(HERE/'OriginalWalk.hs').read_bytes()
    clauses=re.findall(r'(?ms)^instance\b.*?(?=^instance\b|^--|^\w|\Z)',original.decode())
    rows=json.loads((HERE/'source-dictionaries.json').read_text());assert len(clauses)==58
    assert [r['source_clause']for r in rows]==clauses
    from capture import requests
    generated=''.join(json.dumps(r,ensure_ascii=True,separators=(',',':'))+'\n'for r in requests()).encode()
    assert generated==payload('walk-requests.jsonl'),'request generator changed'
    responses=payload('walk-responses.jsonl').splitlines();reqs=payload('walk-requests.jsonl').splitlines();assert len(reqs)==len(responses)
    assert len({json.loads(r)['id']for r in reqs})==len(reqs)
    for request,response in zip(reqs,responses):
        r=json.loads(request);v=json.loads(response)
        if r.get('probe')in SOURCE_ONLY:assert v=={'exception':'blocks'}
    if a.ghc:
        ghc=Path(a.ghc);package=ghc.with_name('ghc-pkg')
        directory=subprocess.check_output([str(package),'field','ghc-internal','import-dirs'],text=True).split(':',1)[1].strip()
        actual=subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/'GHC/Internal/Data/Monoid.hi')])
        assert actual==(HERE/'GhcMonoid.iface.txt').read_bytes(),'actual GHC Monoid interface differs'
        print('Actual GHC First/Last dictionary interface byte-exact PASS')
    if a.binary:
        result=subprocess.run([a.binary],input=payload('walk-requests.jsonl'),capture_output=True,check=True)
        assert result.stdout==payload('walk-responses.jsonl'),'original response replay differs'
        print(f'Original byte-exact replay PASS: {len(responses)} responses')
    c=manifest['source_contract'];print(f"Walk source PASS: {c['method_slot_count']} method slots, {c['helper_export_count']} helpers, {c['kotlin_observation_count']} Kotlin observations; {len(manifest['sha256'])} hashes")
if __name__=='__main__':main()
