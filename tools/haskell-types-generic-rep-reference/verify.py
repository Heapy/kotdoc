#!/usr/bin/env python3
"""Verify independent original Generic response bytes and current Kotlin bindings."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--record',action='store_true');p.add_argument('--check-source',action='store_true')
 for name in ['oracle','demand-oracle','direct-oracle','custom-oracle','ghc']:p.add_argument('--'+name)
 a=p.parse_args()
 assert (HERE/'OriginalCompilerLICENSE').read_bytes()==(ROOT/'licenses/GHC-Compiler-BSD.txt').read_bytes()
 assert (HERE/'OriginalGenericsLICENSE').read_bytes()==(ROOT/'licenses/GHC-base-BSD-3-Clause.txt').read_bytes()
 assert (HERE/'OriginalGenericsCopyright.txt').read_bytes()==(ROOT/'licenses/GHC-Generics-Attribution.txt').read_bytes()
 source=(HERE/'OriginalGenerics.hs').read_text();deriv=(HERE/'OriginalDerivGenerics.hs').read_text()
 copyright=source[source.index('-- Module      :  GHC.Internal.Generics'):source.index('-- @since base-4.6.0.0')]+deriv[deriv.index('{-\n(c)'):deriv.index('-- | The deriving code')]
 assert copyright==(HERE/'OriginalGenericsCopyright.txt').read_text()
 census=json.loads((ROOT/'tools/haskell-types-public-api-reference/expanded-instances.json').read_text());assert sum(r['class_name']=='Generic'for r in census['instances'])==24
 requests=(HERE/'requests.jsonl').read_bytes();responses=(HERE/'responses.jsonl').read_bytes();assert len(requests.splitlines())==len(responses.splitlines())==85
 schema=json.loads((ROOT/'tools/haskell-types-generic-reference/definition-schema.json').read_text())
 expected={(d['name'],c['name'])for d in schema['types']for c in d['constructors']}|{('Many','Many')}
 observed=set()
 def collect(x,d):
  if x.get('node')=='C1':observed.add((d,x['name']))
  for v in x.values():
   if isinstance(v,dict):collect(v,d)
 for line in responses.splitlines():
  value=json.loads(line)['from'];collect(value,value['name'])
 assert expected==observed and len(observed)==78
 old=json.loads((HERE/'historical/pre-dispatch-source-manifest.json').read_text())['sha256']
 assert sha((HERE/'historical/pre-dispatch-PandocGenericRep.kt').read_bytes())==old['core/src/main/kotlin/io/heapy/kotdoc/ast/PandocGenericRep.kt']
 for name in ['requests.jsonl','responses.jsonl','demand-inputs.json','demand-native.jsonl','direct-inputs.json','direct-native.jsonl']:
  assert sha((HERE/name).read_bytes())==old[str((HERE/name).relative_to(ROOT))]
 assert sha(b''.join((HERE/'custom-native.jsonl').read_bytes().splitlines(keepends=True)[:80]))==old[str((HERE/'custom-native.jsonl').relative_to(ROOT))]
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert matrix['source_instances']==len(matrix['instances'])==24 and matrix['source_Generic_methods']==48
 for prefix,count,binary in [('demand',820,a.demand_oracle),('direct',773,a.direct_oracle),('custom',56,a.custom_oracle)]:
  raw=(HERE/(prefix+'-native.jsonl')).read_bytes();assert len(raw.splitlines())==2*count
  if prefix!='custom':assert len(json.loads((HERE/(prefix+'-inputs.json')).read_text()))==count
  if binary:assert subprocess.check_output([binary,'+RTS','-M512m','-RTS'])==raw,'original '+prefix+' responses changed'
 if a.oracle:assert subprocess.check_output([a.oracle,'+RTS','-M512m','-RTS'],input=requests)==responses
 if a.ghc:
  ghc=Path(a.ghc);directory=subprocess.check_output([str(ghc.with_name('ghc-pkg')),'field','ghc-internal','import-dirs'],text=True).split(':',1)[1].strip()
  assert subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/'GHC/Internal/Generics.hi')])==(HERE/'OriginalGenerics.iface.txt').read_bytes()
 for name in ['requests.jsonl','responses.jsonl','demand-inputs.json','demand-native.jsonl','direct-inputs.json','direct-native.jsonl','custom-native.jsonl']:
  assert gzip.decompress((ROOT/'core/src/test/resources/pandoc-types/generic-rep'/f'{name}.gz').read_bytes())==(HERE/name).read_bytes()
 paths=[x for x in HERE.rglob('*')if x.is_file()and 'work-in-progress'not in x.parts and '__pycache__'not in x.parts and x.name not in ['source-manifest.json','README.md']]
 paths += [ROOT/n for n in ['upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/src/Text/Pandoc/Builder.hs','upstream/pandoc-types/test/test-pandoc-types.hs','tools/haskell-types-generic-reference/definition-schema.json','tools/haskell-types-public-api-reference/expanded-instances.json','licenses/GHC-base-BSD-3-Clause.txt','licenses/GHC-Compiler-BSD.txt','licenses/GHC-Generics-Attribution.txt','licenses/pandoc-types-BSD-3-Clause.txt']]
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc/ast'/n for n in ['PandocGenericRep.kt','GenericRepData.kt','GenericDefinitionData.kt','GenericData.kt','GenericAstData.kt','PandocDeepSeq.kt','DeepSeqData.kt','PandocMany.kt']]
 paths += [ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast'/n for n in ['GenericRepReplay.kt','GenericRepDemandReplay.kt','GenericRepDirectReplay.kt','GenericRepCustomReplay.kt','GenericRepShow.kt','GenericRepFixtures.kt','OriginalTypesGenericRepTest.kt']]
 paths += list((ROOT/'core/src/test/resources/pandoc-types/generic-rep').glob('*'))
 if a.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'source_Generic_instances':24,'from_to_members':48,'associated_Rep_types':24,'original_rows':1734,'JVM_adapter_checks':3,'sha256':{str(x.relative_to(ROOT)):sha(x.read_bytes())for x in sorted(set(paths))}},indent=2)+'\n')
 for name,want in json.loads((HERE/'source-manifest.json').read_text())['sha256'].items():assert sha((ROOT/name).read_bytes())==want,'source/artifact changed: '+name
 print('Generic24 derived Rep original/source integrity PASS:',len(json.loads((HERE/'source-manifest.json').read_text())['sha256']),'hashes')
if __name__=='__main__':main()
