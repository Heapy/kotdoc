#!/usr/bin/env python3
"""Independent Builder observations and explicit current integration bindings."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
def digest(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--record',action='store_true');p.add_argument('--check-source',action='store_true');p.add_argument('--oracle');p.add_argument('--demand-oracle');a=p.parse_args()
 requests=(HERE/'requests.jsonl').read_bytes();responses=(HERE/'responses.jsonl').read_bytes();demand=(HERE/'demand-native.jsonl').read_bytes()
 assert len(requests.splitlines())==len(responses.splitlines())==17630
 assert len(demand.splitlines())==540
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert len(matrix['rows'])==50
 if a.oracle:assert subprocess.check_output([a.oracle],input=requests)==responses,'original finite replay differs'
 if a.demand_oracle:assert subprocess.check_output([a.demand_oracle,'+RTS','-M512m','-RTS'])==demand,'original demand replay differs'
 for name,data in [('requests.jsonl',requests),('responses.jsonl',responses),('demand-native.jsonl',demand)]:
  assert gzip.decompress((ROOT/'core/src/test/resources/pandoc-types/builders'/f'{name}.gz').read_bytes())==data
 paths=[ROOT/'upstream/pandoc-types/src/Text/Pandoc'/n for n in ['Builder.hs','Definition.hs']]
 paths += [ROOT/'upstream/pandoc-types/test/test-pandoc-types.hs',ROOT/'licenses/pandoc-types-BSD-3-Clause.txt',ROOT/'licenses/Containers-BSD-3-Clause.txt',ROOT/'tools/haskell-types-generic-reference/OriginalSequenceInternal.hs']
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc/ast'/n for n in ['BuilderStreams.kt','PandocBuilders.kt','ManySplit.kt','Builder.kt','PandocMany.kt','GenericData.kt','GenericDefinitionData.kt','GenericAstData.kt'] if (ROOT/'core/src/main/kotlin/io/heapy/kotdoc/ast'/n).exists()]
 paths += [ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast'/n for n in ['OriginalTypesBuildersTest.kt','BuilderReplay.kt','BuilderDemandReplay.kt','LegacyBuilderMismatch.kt']]
 paths += [x for x in HERE.rglob('*') if x.is_file() and 'work-in-progress' not in x.parts and x.name not in ['source-manifest.json','README.md']]
 paths += list((ROOT/'core/src/test/resources/pandoc-types/builders').glob('*'))
 if a.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'finite':17630,'demand':270,'public':50,'source_instances_total':350,'sha256':{str(x.relative_to(ROOT)):digest(x.read_bytes()) for x in sorted(set(paths))}},indent=2)+'\n')
 for path,want in json.loads((HERE/'source-manifest.json').read_text())['sha256'].items():assert digest((ROOT/path).read_bytes())==want,'source/artifact changed: '+path
 print('Builder source/artifact/original fixture integrity PASS')
if __name__=='__main__':main()
