#!/usr/bin/env python3
"""Authenticate original Data evidence, the source member matrix and Kotlin binding."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def sha(value):return hashlib.sha256(value).hexdigest()
CORPORA={'metadata':24,'demand':233,'finite':92,'custom':36,'support':25,'carrier':39,'numeric':508,'sharing':26,'metadata-partial':8,'cast':12}
EXES={'metadata':'metadata','demand':'demand','finite':'finite','custom':'custom','support':'support','carrier':'carrier','numeric':'numeric','sharing':'sharing','metadata-partial':'metadata-partial','cast':'cast'}
LICENSES={'OriginalDataLICENSE':'GHC-base-BSD-3-Clause.txt','OriginalCompilerLICENSE':'GHC-Compiler-BSD.txt','OriginalIntegerLICENSE':'ghc-bignum-BSD-3-Clause.txt','OriginalContainersLICENSE':'Containers-BSD-3-Clause.txt','OriginalTextLICENSE':'text-BSD-2-Clause.txt','OriginalPandocTypesLICENSE':'pandoc-types-BSD-3-Clause.txt','OriginalAttribution.txt':'GHC-Data-Attribution.txt'}
def main():
 p=argparse.ArgumentParser();p.add_argument('--record',action='store_true');p.add_argument('--check-source',action='store_true');p.add_argument('--oracle-build');p.add_argument('--ghc');p.add_argument('--data-hi');p.add_argument('--definition-hi');p.add_argument('--builder-hi');a=p.parse_args()
 for name,old in list(json.loads((HERE/'historical-prefixes.json').read_text()).items())+list(json.loads((HERE/'pre-list-custom-prefix.json').read_text()).items()):
  assert sha(b''.join((HERE/name).read_bytes().splitlines(keepends=True)[:old['lines']]))==old['sha256'],'original prefix '+name
 for name,count in CORPORA.items():
  filename=name+'-native.jsonl';raw=(HERE/filename).read_bytes();assert len(raw.splitlines())==count,filename
  assert gzip.decompress((ROOT/'core/src/test/resources/pandoc-types/data'/f'{filename}.gz').read_bytes())==raw,filename
  if a.oracle_build:
   binary=Path(a.oracle_build)/('types-data-'+EXES[name]+'-reference')/'opt/build'/('types-data-'+EXES[name]+'-reference')/('types-data-'+EXES[name]+'-reference')
   request=(HERE/(name+'-requests.jsonl')).read_bytes()if name in ['finite','numeric']else None
   assert subprocess.check_output([str(binary),'+RTS','-M512m','-RTS'],input=request)==raw,'native bytes '+name
 for name in ['finite-requests.jsonl','numeric-requests.jsonl']:
  assert gzip.decompress((ROOT/'core/src/test/resources/pandoc-types/data'/f'{name}.gz').read_bytes())==(HERE/name).read_bytes()
 assert gzip.decompress((ROOT/'core/src/test/resources/pandoc-types/data/constructor-responses.jsonl.gz').read_bytes())==(ROOT/'tools/haskell-types-generic-reference/constructor-responses.jsonl').read_bytes()
 for source,target in LICENSES.items():assert (HERE/source).read_bytes()==(ROOT/'licenses'/target).read_bytes(),target
 attribution=(HERE/'OriginalAttribution.txt').read_text()
 for source in ['OriginalData.hs','OriginalDerivGenerate.hs','OriginalReal.hs','OriginalFloat.hs','OriginalInteger.hs']:
  block=attribution.split(source+'\n',1)[1].split('\n\n',1)[0].strip();assert block in (HERE/source).read_text(),'attribution '+source
 for flag,target in [(a.data_hi,'OriginalData.iface.txt'),(a.definition_hi,'OriginalDefinition.iface.txt'),(a.builder_hi,'OriginalBuilder.iface.txt')]:
  if flag:assert a.ghc and subprocess.check_output([a.ghc,'--show-iface',flag])==(HERE/target).read_bytes(),target
 old=json.loads((HERE/'historical/pre-naming/source-manifest.json').read_text())['sha256']
 assert sha((HERE/'historical/pre-naming/PandocData.kt').read_bytes())==old['core/src/main/kotlin/io/heapy/kotdoc/ast/PandocData.kt']
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert len(matrix['rows'])==336
 assert len({(r['module'],r['instance'])for r in matrix['rows']})==24
 census=ROOT/matrix['census']['path'];assert sha(census.read_bytes())==matrix['census']['sha256']
 assert json.loads(census.read_text())['source_instance_count']==350
 for r in matrix['rows']:assert r['class_contract'].startswith('OriginalData.hs:')and r['finite_evidence']and r['demand_evidence']
 paths=[x for x in HERE.rglob('*')if x.is_file()and 'work-in-progress'not in x.parts and '__pycache__'not in x.parts and x not in [HERE/'source-manifest.json',HERE/'README.md']]
 paths += [ROOT/n for n in ['upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/src/Text/Pandoc/Builder.hs','upstream/pandoc-types/test/test-pandoc-types.hs','vendor/text-2.1.2/src/Data/Text.hs','vendor/containers-0.7/src/Data/Map/Internal.hs','tools/haskell-types-generic-reference/OriginalSequenceInternal.hs','tools/haskell-types-generic-reference/constructor-responses.jsonl','tools/haskell-types-generic-reference/definition-schema.json']]
 paths += [ROOT/'licenses'/n for n in LICENSES.values()]
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc/ast'/n for n in ['PandocData.kt','DataDictionaries.kt','GenericData.kt','GenericDefinitionData.kt','GenericRepData.kt','PandocGenericRep.kt','DeepSeqData.kt','MetadataSource.kt','PandocMany.kt','ManyRepetition.kt']]
 paths += list((ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast').glob('Data*Proof.kt'))+[ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast/DataProofFiles.kt',ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesDataTest.kt',ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast/GenericCoreShow.kt']
 paths += list((ROOT/'core/src/test/resources/pandoc-types/data').glob('*'))
 if a.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'source_instances':24,'members':336,'original_expected_rows':1003,'original_comparisons':2659,'jvm_only_assertions':2,'junit_cases':11,'sha256':{str(x.relative_to(ROOT)):sha(x.read_bytes())for x in sorted(set(paths))}},indent=2)+'\n')
 manifest=json.loads((HERE/'source-manifest.json').read_text())
 for name,want in manifest['sha256'].items():assert sha((ROOT/name).read_bytes())==want,'source/artifact '+name
 print('Data source/original integrity PASS:',len(manifest['sha256']),'hashes;24 source instances/336 members;1003 native rows/2659 comparisons')
if __name__=='__main__':main()
