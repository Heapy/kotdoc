#!/usr/bin/env python3
"""Independent native metadata replay, narrow legacy diff and source integrity."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
MAIN=['MetadataSource.kt','MetadataConversions.kt','PandocMetadata.kt','MetadataAlgebra.kt','Metadata.kt']
TEST=['MetadataReplay.kt','MetadataDemandReplay.kt','MetadataProofFixtures.kt','OriginalTypesMetadataTest.kt']
def sha(b):return hashlib.sha256(b).hexdigest()
def paths():
 result=[ROOT/p for p in ['upstream/pandoc-types/src/Text/Pandoc/Builder.hs','upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/test/test-pandoc-types.hs','upstream/pandoc-types/pandoc-types.cabal','vendor/containers-0.7/src/Data/Map/Internal.hs','vendor/text-2.1.2/src/Data/Text.hs','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/Containers-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','core/src/main/kotlin/io/heapy/kotdoc/ast/Pandoc.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocMany.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/GenericData.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/AstShow.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocRead.kt']]
 for folder,names in [('main',MAIN),('test',TEST)]:
  for name in names:
   p=ROOT/f'core/src/{folder}/kotlin/io/heapy/kotdoc/ast/{name}'
   if not p.exists()or(name=='Metadata.kt'and'PandocMetadata.docTitle'not in p.read_text()):p=HERE/'work-in-progress'/name
   result.append(p)
 result +=[p for p in HERE.iterdir()if p.is_file()and p.name not in {'source-manifest.json','README.md'}]
 resources=ROOT/'core/src/test/resources/pandoc-types/metadata'
 if resources.exists():result +=[p for p in resources.iterdir()if p.name!='source-manifest.json']
 return sorted(set(result))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--record',action='store_true');ap.add_argument('--check-source',action='store_true');ap.add_argument('--oracle');ap.add_argument('--demand-oracle');ap.add_argument('--ghc');ap.add_argument('--definition-interface');ap.add_argument('--builder-interface');a=ap.parse_args()
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert len(matrix['slots'])==25 and len(matrix['helpers'])==10
 request=(HERE/'requests.jsonl').read_bytes();response=(HERE/'responses.jsonl').read_bytes();demand=(HERE/'demand-native.jsonl').read_bytes();rows=[json.loads(x)for x in request.splitlines()];assert len(rows)==len(response.splitlines())
 ids={r['case']for r in rows}
 for slot in matrix['slots']+matrix['helpers']:assert slot['original_cases']and set(slot['original_cases'])<=ids
 before=(HERE/'OriginalMetadataBefore.kt.txt').read_text();p=next(p for p in paths()if p.name=='Metadata.kt');after=p.read_text()
 assert before[:before.index('fun docTitle(')]==after[:after.index('fun docTitle(')]
 assert before[before.index('private fun textMetadata'):]==after[after.index('private fun textMetadata'):]
 assert(HERE/'OriginalPandocTypesLICENSE').read_bytes()==(ROOT/'licenses/pandoc-types-BSD-3-Clause.txt').read_bytes()
 contract=dict(instance_dictionaries=15,instance_method_slots=25,public_helpers=10,finite_responses=len(rows),demand_responses=len(demand.splitlines())//2,legacy_helper_regressions=3,Kotlin_sharing_adapter_tests=1,Kotlin_Map_contract_tests=1,model='Pinned GHC64; explicit root/list/pair callbacks and finite ordered Map keys with lazy values; strict existing AST scalar conveniences remain distinct; not arbitrary GHC heaps/infinite maps.')
 if a.record:(HERE/'source-manifest.json').write_text(json.dumps(dict(schema=1,contract=contract,sha256={str(p.relative_to(ROOT)):sha(p.read_bytes())for p in paths()}),indent=2)+'\n')
 manifest=json.loads((HERE/'source-manifest.json').read_text());assert manifest['contract']==contract
 for p,digest in manifest['sha256'].items():assert sha((ROOT/p).read_bytes())==digest,'source/artifact changed: '+p
 for name,data in [('requests.jsonl',request),('responses.jsonl',response),('demand-native.jsonl',demand)]:
  p=ROOT/f'core/src/test/resources/pandoc-types/metadata/{name}.gz'
  if p.exists():assert gzip.decompress(p.read_bytes())==data
 if a.oracle:
  actual=subprocess.run([a.oracle,'+RTS','-M512m','-RTS'],input=request,stdout=subprocess.PIPE,check=True).stdout;assert actual==response;print(len(rows),'original finite responses byte-exact PASS')
 if a.demand_oracle:
  actual=subprocess.check_output([a.demand_oracle,'+RTS','-M512m','-RTS']);assert actual==demand;print(contract['demand_responses'],'original demand responses byte-exact PASS')
 if a.ghc:
  ghc=Path(a.ghc);directory=subprocess.check_output([str(ghc.with_name('ghc-pkg')),'field','ghc-internal','import-dirs'],text=True).split(':',1)[1].strip()
  actual=subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/'GHC/Internal/Base.hi')]);assert actual==(HERE/'OriginalBase.iface.txt').read_bytes()
  for name,p in [('Definition',a.definition_interface),('Builder',a.builder_interface)]:
   if p:assert subprocess.check_output([str(ghc),'--show-iface',p])==(HERE/f'Original{name}.iface.txt').read_bytes()
 print('Metadata integrity PASS',len(manifest['sha256']),'hashes;',contract)
if __name__=='__main__':main()
