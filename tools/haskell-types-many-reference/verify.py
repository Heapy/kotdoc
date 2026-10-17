#!/usr/bin/env python3
"""Validate pinned Many sources and optionally replay the actual original binaries."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
PRODUCTION=['PandocMany.kt','ManyAlgebra.kt','ManyTraversable.kt','ManyRepetition.kt','ManySpecialized.kt']
TESTS=['ManyProofFixtures.kt','ManyReplay.kt','ManyDemandReplay.kt','OriginalTypesManyTest.kt']
STREAMS=['core','construction','algebra','numeric','traversal']
def sha(data):return hashlib.sha256(data).hexdigest()
def paths():
 result=[ROOT/'upstream/pandoc-types/src/Text/Pandoc'/name for name in ['Builder.hs','Definition.hs']]
 result += [ROOT/name for name in ['upstream/pandoc-types/test/test-pandoc-types.hs','upstream/pandoc-types/pandoc-types.cabal','licenses/pandoc-types-BSD-3-Clause.txt','licenses/Containers-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','vendor/text-2.1.2/src/Data/Text.hs','core/src/main/kotlin/io/heapy/kotdoc/layout/Attributed.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/Builder.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/Pandoc.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocRead.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/AstShow.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/GenericData.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/GenericMonad.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocWalk.kt']]
 for folder,names in [('main',PRODUCTION),('test',TESTS)]:
  for name in names:
   installed=ROOT/f'core/src/{folder}/kotlin/io/heapy/kotdoc/ast/{name}'
   result.append(installed if installed.exists()else HERE/'work-in-progress'/name)
 result += [p for p in HERE.iterdir()if p.is_file()and p.name not in {'source-manifest.json','README.md'}]
 resources=ROOT/'core/src/test/resources/pandoc-types/many'
 if resources.exists():result += [p for p in resources.iterdir()if p.name!='source-manifest.json']
 return sorted(set(result))
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--record',action='store_true');parser.add_argument('--check-source',action='store_true');parser.add_argument('--oracle');parser.add_argument('--demand-oracle');parser.add_argument('--ghc');parser.add_argument('--builder-interface')
 args=parser.parse_args()
 assert (HERE/'OriginalSequenceInternal.hs').read_bytes()==(ROOT/'tools/haskell-types-generic-reference/OriginalSequenceInternal.hs').read_bytes()
 assert (HERE/'OriginalContainersLICENSE').read_bytes()==(ROOT/'licenses/Containers-BSD-3-Clause.txt').read_bytes()
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert len(matrix['slots'])==matrix['scope_slots']==42
 counts={name:len((HERE/(name+'-requests.jsonl')).read_bytes().splitlines())for name in STREAMS}
 demand=(HERE/'demand-native.jsonl').read_bytes();assert len(demand.splitlines())%2==0
 contract={'slots':42,'Functor':2,'Foldable':17,'Traversable':4,'specialized_algebra':13,'helpers_and_representation':6,'source_rows':counts,'demand_rows':len(demand.splitlines())//2,'absent_source_instances':['Applicative Many','Alternative Many','Monad Many','NFData Many'],'scope':'finite source dictionaries and explicit thunk/list/effect adapters; not all Many Data/Eq/Ord/Read/Show/Typeable/Generic members or arbitrary GHC heaps'}
 if args.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'contract':contract,'sha256':{str(p.relative_to(ROOT)):sha(p.read_bytes())for p in paths()}},indent=2)+'\n')
 manifest=json.loads((HERE/'source-manifest.json').read_text());assert manifest['contract']==contract
 for name,digest in manifest['sha256'].items():assert sha((ROOT/name).read_bytes())==digest,'source/artifact changed: '+name
 for name in STREAMS:
  request=(HERE/(name+'-requests.jsonl')).read_bytes();response=(HERE/(name+'-responses.jsonl')).read_bytes();assert len(request.splitlines())==len(response.splitlines())
  for suffix,data in [('requests',request),('responses',response)]:
   resource=ROOT/f'core/src/test/resources/pandoc-types/many/{name}-{suffix}.jsonl.gz'
   if resource.exists():assert gzip.decompress(resource.read_bytes())==data
  if args.oracle:
   actual=subprocess.run([args.oracle,'+RTS','-M512m','-RTS'],input=request,stdout=subprocess.PIPE,check=True).stdout
   assert actual==response,'original raw replay mismatch: '+name
   print(f'{name}: {counts[name]} original responses byte-exact PASS')
 resource=ROOT/'core/src/test/resources/pandoc-types/many/demand-native.jsonl.gz'
 if resource.exists():assert gzip.decompress(resource.read_bytes())==demand
 if args.demand_oracle:
  actual=subprocess.check_output([args.demand_oracle,'+RTS','-M512m','-RTS']);assert actual==demand,'demand raw replay mismatch'
  print(f'demand: {contract["demand_rows"]} original responses byte-exact PASS')
 if args.ghc:
  ghc=Path(args.ghc);directory=subprocess.check_output([str(ghc.with_name('ghc-pkg')),'field','ghc-internal','import-dirs'],text=True).split(':',1)[1].strip()
  for module,name in [('Data/Foldable','Foldable'),('Data/Traversable','Traversable'),('Data/Functor','Functor'),('Data/Semigroup/Internal','SemigroupInternal'),('Data/Functor/Utils','FunctorUtils'),('Base','Base')]:
   actual=subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/('GHC/Internal/'+module+'.hi'))]);assert actual==(HERE/('Original'+name+'.iface.txt')).read_bytes(),'GHC interface mismatch: '+module
 if args.builder_interface:
  assert args.ghc,'--builder-interface needs --ghc'
  actual=subprocess.check_output([args.ghc,'--show-iface',args.builder_interface]);assert actual==(HERE/'OriginalBuilder.iface.txt').read_bytes(),'Builder interface mismatch'
 print(f'Many source/artifact integrity PASS: {len(manifest["sha256"])} hashes; {contract}')
if __name__=='__main__':main()
