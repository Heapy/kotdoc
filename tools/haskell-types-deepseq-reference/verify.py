#!/usr/bin/env python3
"""Pin original NFData sources, response bytes and Kotlin production bindings."""
import argparse,gzip,hashlib,json,subprocess,tarfile
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--record',action='store_true');p.add_argument('--check-source',action='store_true');p.add_argument('--oracle');p.add_argument('--demand-oracle');p.add_argument('--extended-oracle');p.add_argument('--ghc');a=p.parse_args()
 with tarfile.open(HERE/'deepseq-1.5.1.0.tar.gz')as t:
  for src,dest in [('Control/DeepSeq.hs','OriginalDeepSeq.hs'),('LICENSE','OriginalDeepSeqLICENSE')]:assert t.extractfile('deepseq-1.5.1.0/'+src).read()==(HERE/dest).read_bytes()
 assert (HERE/'OriginalDeepSeqLICENSE').read_bytes()==(ROOT/'licenses/deepseq-BSD-3-Clause.txt').read_bytes()
 requests=(HERE/'requests.jsonl').read_bytes();responses=(HERE/'responses.jsonl').read_bytes();demand=(HERE/'demand-native.jsonl').read_bytes();extended=(HERE/'extended-native.jsonl').read_bytes()
 assert len(requests.splitlines())==len(responses.splitlines())==308
 assert len(demand.splitlines())==1360 and len(extended.splitlines())==236
 assert len(json.loads((HERE/'demand-inputs.json').read_text()))==680 and len(json.loads((HERE/'extended-inputs.json').read_text()))==118
 matrix=json.loads((HERE/'acceptance-matrix.json').read_text());assert matrix['selected_explicit_instances']==len(matrix['instances'])==23
 source=(ROOT/'upstream/pandoc-types/src/Text/Pandoc/Definition.hs').read_text();assert sum(line.startswith('instance NFData ')for line in source.splitlines())==23
 for row in matrix['instances']:assert 'instance NFData '+row['type']+'\n' in source
 for name in ['requests.jsonl','responses.jsonl','demand-inputs.json','demand-native.jsonl','extended-native.jsonl']:
  assert gzip.decompress((ROOT/'core/src/test/resources/pandoc-types/deepseq'/f'{name}.gz').read_bytes())==(HERE/name).read_bytes()
 for binary,data,input in [(a.oracle,responses,requests),(a.demand_oracle,demand,None),(a.extended_oracle,extended,None)]:
  if binary:assert subprocess.check_output([binary,'+RTS','-M512m','-RTS'],input=input)==data,'native response bytes differ'
 if a.ghc:
  ghc=Path(a.ghc);directory=subprocess.check_output([str(ghc.with_name('ghc-pkg')),'field','deepseq','import-dirs'],text=True).split(':',1)[1].strip()
  assert subprocess.check_output([str(ghc),'--show-iface',str(Path(directory)/'Control/DeepSeq.hi')])==(HERE/'OriginalDeepSeq.iface.txt').read_bytes()
 paths=[x for x in HERE.rglob('*')if x.is_file()and 'work-in-progress'not in x.parts and '__pycache__'not in x.parts and x.name not in ['source-manifest.json','README.md']]
 paths += [ROOT/n for n in ['upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/test/test-pandoc-types.hs','licenses/deepseq-BSD-3-Clause.txt','licenses/pandoc-types-BSD-3-Clause.txt','licenses/Containers-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','vendor/text-2.1.2/src/Data/Text.hs','tools/haskell-types-generic-reference/source-manifest.json']]
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc/ast'/n for n in ['PandocDeepSeq.kt','DeepSeqData.kt','GenericDefinitionData.kt','GenericData.kt','GenericAstData.kt','PandocCounts.kt']]
 paths += [ROOT/'core/src/test/kotlin/io/heapy/kotdoc/ast'/n for n in ['DeepSeqReplay.kt','DeepSeqDemandReplay.kt','DeepSeqExtendedReplay.kt','OriginalTypesDeepSeqTest.kt']]
 paths += list((ROOT/'core/src/test/resources/pandoc-types/deepseq').glob('*'))
 if a.record:(HERE/'source-manifest.json').write_text(json.dumps({'schema':1,'explicit_NFData_instances':23,'rnf_members':23,'native_finite':308,'native_demand':680,'native_extended':118,'typed_finite_replays':308,'JVM_adapter_tests':4,'sha256':{str(x.relative_to(ROOT)):sha(x.read_bytes())for x in sorted(set(paths))}},indent=2)+'\n')
 m=json.loads((HERE/'source-manifest.json').read_text())
 for name,want in m['sha256'].items():assert sha((ROOT/name).read_bytes())==want,'source/artifact changed: '+name
 print('NFData23 source/artifact/native integrity PASS:',len(m['sha256']),'hashes')
if __name__=='__main__':main()
