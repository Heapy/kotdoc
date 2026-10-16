#!/usr/bin/env python3
"""Pinned source integrity and optional independent raw original replay; no build."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
PRODUCTION=['GenericData.kt','GenericMonad.kt','GenericDefinitionData.kt','GenericAstData.kt','PandocGeneric.kt']
TESTS=['GenericProofFixtures.kt','GenericCoreShow.kt','GenericCoreReplay.kt','GenericConstructorReplay.kt','GenericDemandReplay.kt','GenericDeepReplay.kt','OriginalTypesGenericTest.kt']
STREAMS=['core','constructor','target','demand','deep']
def sha(data):return hashlib.sha256(data).hexdigest()
def paths():
 result=[ROOT/'upstream/pandoc-types/src/Text/Pandoc'/name for name in ['Generic.hs','Definition.hs','Builder.hs']]
 result += [ROOT/'upstream/pandoc-types/pandoc-types.cabal',ROOT/'upstream/pandoc-types/test/test-pandoc-types.hs']
 result += [ROOT/name for name in ['licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/syb-0.7.4-LICENSE.txt','licenses/syb-0.7.4-NOTICE.txt','vendor/text-2.1.2/src/Data/Text.hs','vendor/containers-0.7/src/Data/Map/Internal.hs','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocRead.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/AstShow.kt']]
 for folder,names in [('main',PRODUCTION),('test',TESTS)]:
  for name in names:
   installed=ROOT/f'core/src/{folder}/kotlin/io/heapy/kotdoc/ast/{name}'
   result.append(installed if installed.exists() else HERE/'work-in-progress'/name)
 result += [p for p in HERE.iterdir()if p.is_file()and p.name not in {'source-manifest.json','README.md'}]
 resources=ROOT/'core/src/test/resources/pandoc-types/generic'
 if resources.exists():result += [p for p in resources.iterdir()if p.name!='source-manifest.json']
 return sorted(set(result))
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--record',action='store_true');parser.add_argument('--check-source',action='store_true');parser.add_argument('--ghc')
 for name in STREAMS:parser.add_argument('--'+name+'-oracle')
 args=parser.parse_args()
 assert (ROOT/'licenses/syb-0.7.4-LICENSE.txt').read_bytes()==(HERE/'OriginalSybLICENSE').read_bytes()
 assert (ROOT/'licenses/syb-0.7.4-NOTICE.txt').read_bytes()==b''.join((HERE/'OriginalSybSchemes.hs').read_bytes().splitlines(keepends=True)[:17])
 schema=json.loads((HERE/'definition-schema.json').read_text())
 assert len(schema['types'])==23 and sum(len(t['constructors'])for t in schema['types'])==77
 assert len(schema['aliases'])==5
 counts={name:len((HERE/(name+'-requests.jsonl')).read_bytes().splitlines())for name in STREAMS}
 contract={'functions':['bottomUp','topDown','bottomUpM','queryWith'],'definition_data_types':23,'definition_constructors':77,'aliases':5,'many_specializations':2,'source_request_counts':counts,'scope':'finite explicit Data dictionaries and thunk adapters; not full Data/Typeable/Generic/NFData class-member or arbitrary GHC heap equivalence'}
 if args.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'contract':contract,'sha256':{str(p.relative_to(ROOT)):sha(p.read_bytes())for p in paths()}},indent=2)+'\n')
 manifest=json.loads((HERE/'source-manifest.json').read_text());assert manifest['contract']==contract
 for name,digest in manifest['sha256'].items():assert sha((ROOT/name).read_bytes())==digest,'source/artifact changed: '+name
 for name,item in json.loads((HERE/'preserved-prefixes.json').read_text()).items():
  prefix=b''.join((HERE/name).read_bytes().splitlines(keepends=True)[:item['rows']]);assert sha(prefix)==item['sha256'],'original prefix changed: '+name
 for name in STREAMS:
  request=(HERE/(name+'-requests.jsonl')).read_bytes();response=(HERE/(name+'-responses.jsonl')).read_bytes()
  assert len(request.splitlines())==len(response.splitlines())
  resource=ROOT/f'core/src/test/resources/pandoc-types/generic/{name}-responses.jsonl.gz'
  if resource.exists():assert gzip.decompress(resource.read_bytes())==response
  oracle=getattr(args,name+'_oracle')
  if oracle:
   actual=subprocess.run([oracle,'+RTS','-M512m','-RTS'],input=request,stdout=subprocess.PIPE,check=True).stdout
   assert actual==response,'native raw replay mismatch: '+name
   print(f'{name}: {counts[name]} original responses byte-exact PASS')
 if args.ghc:
  ghc=Path(args.ghc);directory=subprocess.check_output([str(ghc.with_name('ghc-pkg')),'field','ghc-internal','import-dirs'],text=True).split(':',1)[1].strip()
  for source,dest in [('GHC/Internal/Data/Data.hi','GhcData.iface.txt'),('GHC/Internal/Data/Typeable/Internal.hi','GhcTypeable.iface.txt')]:
   actual=subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/source)])
   assert actual==(HERE/dest).read_bytes(),'actual GHC interface mismatch: '+source
  print('Actual installed GHC Data/Typeable interfaces byte-exact PASS')
 print(f'Generic source/artifact integrity PASS: {len(manifest["sha256"])} hashes, {counts}')
if __name__=='__main__':main()
