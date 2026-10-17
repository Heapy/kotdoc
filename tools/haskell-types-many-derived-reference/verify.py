#!/usr/bin/env python3
"""Authenticate original expectations, installed Kotlin bindings and notices."""
import argparse,gzip,hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--record',action='store_true');p.add_argument('--check-source',action='store_true')
 for n in ['oracle','demand-oracle','read-oracle','parser-oracle','lex-oracle','ghc','builder-hi']:p.add_argument('--'+n)
 a=p.parse_args()
 assert (HERE/'OriginalLex.hs').read_bytes()==(ROOT/'vendor/ghc-9.12.2/GHC/Internal/Text/Read/Lex.hs').read_bytes()
 for name,v in json.loads((HERE/'historical/initial-native-prefixes.json').read_text()).items():
  assert sha(b''.join((HERE/name).read_bytes().splitlines(keepends=True)[:v['lines']]))==v['sha256'],'historical prefix '+name
 historical=json.loads((HERE/'historical/pre-lex-correction/source-manifest.json').read_text())['sha256']
 for name in ['native.jsonl','demand-native.jsonl','read-native.jsonl','read-inputs.jsonl','parser-demand-native.jsonl']:
  assert sha((HERE/name).read_bytes())==historical[str((HERE/name).relative_to(ROOT))],'old7248 expected bytes '+name
 assert sha((HERE/'historical/pre-lex-correction/ManyDerivedRead.kt').read_bytes())==historical['core/src/main/kotlin/io/heapy/kotdoc/ast/ManyDerivedRead.kt']
 assert sha((HERE/'historical/pre-lex-correction/PandocManyDerived.kt').read_bytes())==historical['core/src/main/kotlin/io/heapy/kotdoc/ast/PandocManyDerived.kt']
 assert sha((HERE/'historical/pre-lex-correction/OriginalRuntimeAttribution.txt').read_bytes())==historical[str((HERE/'OriginalRuntimeAttribution.txt').relative_to(ROOT))]
 lexical=json.loads((HERE/'historical/lexical-initial-prefix.json').read_text())
 assert sha(b''.join((HERE/'lex-demand-native.jsonl').read_bytes().splitlines(keepends=True)[:lexical['lines']]))==lexical['sha256']
 expected={'native.jsonl':4624,'demand-native.jsonl':120,'read-native.jsonl':4832,'parser-demand-native.jsonl':88,'read-inputs.jsonl':4832,'lex-demand-native.jsonl':5549}
 for name,n in expected.items():
  raw=(HERE/name).read_bytes();assert len(raw.splitlines())==n,name
  assert gzip.decompress((ROOT/'core/src/test/resources/pandoc-types/many-derived'/f'{name}.gz').read_bytes())==raw,name
 for name,binary in [('native.jsonl',a.oracle),('demand-native.jsonl',a.demand_oracle),('read-native.jsonl',a.read_oracle),('parser-demand-native.jsonl',a.parser_oracle),('lex-demand-native.jsonl',a.lex_oracle)]:
  if binary:
   request=(HERE/'read-inputs.jsonl').read_bytes()if name=='read-native.jsonl'else None
   assert subprocess.check_output([binary,'+RTS','-M512m','-RTS'],input=request)==(HERE/name).read_bytes(),'native bytes '+name
 if a.ghc and a.builder_hi:assert subprocess.check_output([a.ghc,'--show-iface',a.builder_hi])==(HERE/'OriginalBuilder.iface.txt').read_bytes()
 for source,target in [('OriginalGhcPrimLICENSE','GHC-prim-LICENSE.txt'),('OriginalRuntimeAttribution.txt','GHC-Read-Show-Attribution.txt')]:assert (HERE/source).read_bytes()==(ROOT/'licenses'/target).read_bytes()
 attribution=(HERE/'OriginalRuntimeAttribution.txt').read_text()
 for phrase in ['Koen Claessen','2002','1992','1994']:assert phrase in attribution
 names=['OriginalReadP.hs','OriginalReadPrec.hs','OriginalRead.hs','OriginalShow.hs','OriginalClasses.hs','OriginalSequenceInternal.hs','OriginalLex.hs']
 for i,name in enumerate(names):
  block=attribution.split(name+'\n',1)[1]
  if i+1<len(names):block=block.split(names[i+1]+'\n',1)[0]
  assert block.strip() in (HERE/name).read_text(),'exact original attribution '+name
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert matrix['source_instances']==4 and sum(len(x['members'])for x in matrix['instances'])==matrix['class_members']==16
 assert matrix['original_observations']['total']==12797 and matrix['expected_new_junit_cases']==12802
 paths=[x for x in HERE.rglob('*')if x.is_file()and 'work-in-progress'not in x.parts and '__pycache__'not in x.parts and x not in [HERE/'source-manifest.json',HERE/'README.md']]
 paths += [ROOT/n for n in ['upstream/pandoc-types/src/Text/Pandoc/Builder.hs','upstream/pandoc-types/test/test-pandoc-types.hs','vendor/ghc-9.12.2/GHC/Internal/Text/Read/Lex.hs','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/GHC-prim-LICENSE.txt','licenses/GHC-Read-Show-Attribution.txt','licenses/Containers-BSD-3-Clause.txt']]
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc/ast'/n for n in ['PandocManyDerived.kt','ManyDerivedRead.kt','ManyReadLex.kt','PandocMany.kt','GenericData.kt','GenericRepData.kt','PandocRead.kt']]
 paths += [ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast'/n for n in ['ManyDerivedProof.kt','ManyDerivedInitialReplay.kt','ManyDerivedDemandReplay.kt','ManyDerivedReadReplay.kt','ManyDerivedParserReplay.kt','ManyDerivedLexReplay.kt','OriginalTypesManyDerivedTest.kt']]
 paths += list((ROOT/'core/src/test/resources/pandoc-types/many-derived').glob('*'))
 if a.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'source_instances':4,'class_members':16,'original_observations':12797,'jvm_adapter_checks':4,'integrity_checks':1,'sha256':{str(x.relative_to(ROOT)):sha(x.read_bytes())for x in sorted(set(paths))}},indent=2)+'\n')
 m=json.loads((HERE/'source-manifest.json').read_text())
 for name,want in m['sha256'].items():assert sha((ROOT/name).read_bytes())==want,'source/artifact '+name
 print('Many generic derived original/source integrity PASS:',len(m['sha256']),'hashes;12797 original observations')
if __name__=='__main__':main()
