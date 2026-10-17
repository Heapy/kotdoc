#!/usr/bin/env python3
"""Pinned count dictionary/source integrity and optional original binary replay."""
import argparse,gzip,hashlib,json,runpy,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
PRODUCTION=['PandocCounts.kt','CountEnums.kt','CountBridge.kt']
TESTS=['CountReplay.kt','CountDemandReplay.kt','CountProofFixtures.kt','OriginalTypesCountTest.kt']
def sha(data):return hashlib.sha256(data).hexdigest()
def paths():
 result=[ROOT/p for p in ['upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/test/test-pandoc-types.hs','upstream/pandoc-types/pandoc-types.cabal','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','core/src/main/kotlin/io/heapy/kotdoc/ast/Pandoc.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/GenericData.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/AstShow.kt','core/src/main/kotlin/io/heapy/kotdoc/formats/NativeWriter.kt']]
 for folder,names in [('main',PRODUCTION),('test',TESTS)]:
  for name in names:
   p=ROOT/f'core/src/{folder}/kotlin/io/heapy/kotdoc/ast/{name}';result.append(p if p.exists()else HERE/'work-in-progress'/name)
 result +=[p for p in HERE.iterdir()if p.is_file()and p.name not in {'source-manifest.json','README.md'}]
 resource=ROOT/'core/src/test/resources/pandoc-types/counts'
 if resource.exists():result +=[p for p in resource.iterdir()if p.name!='source-manifest.json']
 return sorted(set(result))
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--record',action='store_true');parser.add_argument('--check-source',action='store_true');parser.add_argument('--oracle');parser.add_argument('--demand-oracle');parser.add_argument('--ghc');parser.add_argument('--definition-interface');args=parser.parse_args()
 extract=runpy.run_path(str(HERE/'extract-ghc-source.py'))['source']
 for name in ['Num','Enum']:assert extract((HERE/f'OriginalGhc{name}.html').read_text())==(HERE/f'OriginalGhc{name}.hs').read_text()
 assert(HERE/'OriginalGhcLICENSE').read_bytes()==(ROOT/'licenses/GHC-base-BSD-3-Clause.txt').read_bytes()
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert matrix['source_method_slots']==len(matrix['slots'])==45
 request=(HERE/'requests.jsonl').read_bytes();response=(HERE/'responses.jsonl').read_bytes();demand=(HERE/'demand-native.jsonl').read_bytes()
 rows=[json.loads(x)for x in request.splitlines()];assert len(rows)==len(response.splitlines());assert len(demand.splitlines())%2==0
 ids={r['case']for r in rows}
 for slot in matrix['slots']:assert slot['original_cases']and set(slot['original_cases'])<=ids
 contract={'types':['RowHeadColumns','RowSpan','ColSpan'],'Num_methods':7,'Enum_methods':8,'method_slots':45,'finite_responses':len(rows),'demand_responses':len(demand.splitlines())//2,'bridge_responses':sum(r['operation']=='bridge'for r in rows),'Kotlin_adapter_tests':1,'range_model':'original complete finite lists or bounded ordered prefix with explicit truncation; GenericList head/tail thunks','absent_source_classes':['Real','Integral','Bounded'],'remaining':'other generic Many/count class members, full140exports/350expanded instance audit, metadata/Builder/Data.Version and JSON supplement remain open'}
 if args.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'contract':contract,'sha256':{str(p.relative_to(ROOT)):sha(p.read_bytes())for p in paths()}},indent=2)+'\n')
 manifest=json.loads((HERE/'source-manifest.json').read_text());assert manifest['contract']==contract
 for name,digest in manifest['sha256'].items():assert sha((ROOT/name).read_bytes())==digest,'source/artifact changed: '+name
 for name,data in [('requests.jsonl',request),('responses.jsonl',response),('demand-native.jsonl',demand)]:
  p=ROOT/f'core/src/test/resources/pandoc-types/counts/{name}.gz'
  if p.exists():assert gzip.decompress(p.read_bytes())==data
 if args.oracle:
  actual=subprocess.run([args.oracle,'+RTS','-M512m','-RTS'],input=request,stdout=subprocess.PIPE,check=True).stdout;assert actual==response,'original finite replay mismatch';print(f'{len(rows)} original finite responses byte-exact PASS')
 if args.demand_oracle:
  actual=subprocess.check_output([args.demand_oracle,'+RTS','-M512m','-RTS']);assert actual==demand,'original demand replay mismatch';print(f'{contract["demand_responses"]} original demand responses byte-exact PASS')
 if args.ghc:
  ghc=Path(args.ghc);directory=subprocess.check_output([str(ghc.with_name('ghc-pkg')),'field','ghc-internal','import-dirs'],text=True).split(':',1)[1].strip()
  for name in ['Num','Enum']:
   actual=subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/f'GHC/Internal/{name}.hi')]);assert actual==(HERE/f'OriginalGhc{name}.iface.txt').read_bytes(),'actual GHC dictionary mismatch: '+name
 if args.definition_interface:
  assert args.ghc
  actual=subprocess.check_output([args.ghc,'--show-iface',args.definition_interface]);assert actual==(HERE/'OriginalDefinition.iface.txt').read_bytes(),'actual Definition dictionary mismatch'
 print(f'Count integrity PASS: {len(manifest["sha256"])} hashes; {contract}')
if __name__=='__main__':main()
