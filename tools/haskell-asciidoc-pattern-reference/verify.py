#!/usr/bin/env python3
"""Read-only additive source/expectation verification; never regenerates values."""
import argparse,gzip,hashlib,json,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
HERE=pathlib.Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
manifest=json.loads((HERE/'current-source.json').read_text())
for name,pin in manifest['files'].items():
 assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==pin,name
old=json.loads((ROOT/'tools/haskell-asciidoc-writer-reference/provenance.json').read_text())['files']
bind=json.loads((HERE/'historical-source-bindings.json').read_text())
for name,pin in old.items():
 assert hashlib.sha256((ROOT/bind.get(name,name)).read_bytes()).hexdigest()==pin,name
for fixture,count in [('patterns',30),('adjacent',8)]:
 rows=[json.loads(x) for x in gzip.open(HERE/'fixtures'/f'{fixture}.jsonl.gz','rt')]
 assert len(rows)==count
 if a.verify_oracle:
  main='PatternOracle.hs' if fixture=='patterns' else 'PatternExtraOracle.hs'
  with tempfile.TemporaryDirectory(prefix='asciidoc-pattern-') as tmp:
   binary=pathlib.Path(tmp)/'oracle'
   subprocess.run(['python3',str(ROOT/'tools/haskell-asciidoc-writer-reference/build-cached.py'),'--main',str(HERE/main),'--output',str(binary)],check=True)
   actual=[json.loads(x) for x in subprocess.check_output([str(binary)],text=True).splitlines()]
   assert actual==rows,fixture
print('PASS current sources, historical source snapshots, unchanged original expectations, 38 additive controls'+(' and original oracle replay' if a.verify_oracle else ''))
