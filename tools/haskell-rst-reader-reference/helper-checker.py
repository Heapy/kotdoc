#!/usr/bin/env python3
"""Finite read-only low-level RST helper provenance and original-code replay."""
import argparse,gzip,hashlib,json,pathlib,subprocess
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];FIX=ROOT/'core/src/test/resources/rst-reader-helpers'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def rows(name):return[json.loads(l)for l in gzip.decompress((FIX/(name+'.jsonl.gz')).read_bytes()).splitlines()]
def source():
 m=json.loads((HERE/'helper-manifest.json').read_text())
 for name,pin in m['pins'].items():assert sha(ROOT/name)==pin,name
 for name,pin in m['runtimePins'].items():
  p=pathlib.Path(name)
  if p.exists():assert sha(p)==pin,name
 src=(ROOT/'upstream/pandoc/src/Text/Pandoc/CSV.hs').read_text().replace('module Text.Pandoc.CSV','module SourceCSV');assert src==(HERE/'SourceCSV.hs').read_text()
 original=(HERE/'original-gridtables/test/test-gridtables.hs').read_text().replace('module Main (main) where','module Main (main) where\nimport System.IO.Unsafe(unsafePerformIO)\nimport qualified Data.Aeson as A\nimport qualified Data.ByteString.Lazy.Char8 as B').replace('parse\' p = runParser p () "<test input>"','parse\' p text = unsafePerformIO $ do\n  B.putStrLn $ B.pack "__INPUT__" <> A.encode text\n  pure $ runParser p () "<test input>" text')
 assert (HERE/'OriginalGridTests.hs').read_text()=='{-# LANGUAGE OverloadedStrings #-}\n'+original
 p=json.loads((HERE/'gridtables-provenance.json').read_text())
 for name,pin in p['sourcePins'].items():assert sha(HERE/name)==pin,name
 print('Read-only helper/source/test/tool/payload/runtime profile pins passed',flush=True)
def verify(oracle,original):
 source();n=0
 for name in ['observations','postgrid','deep','original-raw']:
  rs=rows(name);p=subprocess.run([str(oracle)],input=('\n'.join(json.dumps(r['request'],ensure_ascii=False)for r in rs)+'\n').encode(),capture_output=True,check=True);assert[json.loads(l)for l in p.stdout.splitlines()]==[r['expected']for r in rs],name;n+=len(rs)
 p=subprocess.run([str(original),'--hide-successes','--num-threads=1'],capture_output=True,check=True);assert b'All 27 tests passed'in p.stdout
 print(n,'independent native observations and all27 original gridtables assertions passed',flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser();g=p.add_mutually_exclusive_group(required=True);g.add_argument('--check-source',action='store_true');g.add_argument('--verify-oracle',action='store_true');p.add_argument('--oracle',type=pathlib.Path,default=pathlib.Path('/tmp/kotdoc-rst-reader/helper-oracle'));p.add_argument('--original-tests',type=pathlib.Path,default=pathlib.Path('/tmp/kotdoc-rst-reader/original-grid-tests'));a=p.parse_args();source()if a.check_source else verify(a.oracle,a.original_tests)
