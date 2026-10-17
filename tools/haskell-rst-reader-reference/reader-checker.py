#!/usr/bin/env python3
"""Finite read-only full RST reader source/payload/profile/original-code checker."""
import argparse,hashlib,json,pathlib,subprocess
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];FIX=ROOT/'core/src/test/resources/io/heapy/kotdoc/rst-reader'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def source():
 m=json.loads((HERE/'reader-manifest.json').read_text())
 for name,pin in m['sourcePins'].items():assert sha(ROOT/name)==pin,name
 for name,dep in m.get('historicalDependencyPins',{}).items():
  assert sha(ROOT/dep['snapshot'])==dep['sha256'],name
  blob=subprocess.run(['git','show',dep['commit']+':'+name],cwd=ROOT,capture_output=True,check=True).stdout
  assert hashlib.sha256(blob).hexdigest()==dep['sha256'],name
  if sha(ROOT/name)!=dep['sha256']:print('Historical packaged dependency differs from current checkout:',name,flush=True)
 for name,pin in m['runtimePins'].items():
  p=pathlib.Path(name)
  if p.exists():assert sha(p)==pin,name
 original=(ROOT/'upstream/pandoc/src/Text/Pandoc/Readers/RST.hs').read_text().replace('module Text.Pandoc.Readers.RST ( readRST ) where','module FullSourceRstReader where').replace('import Text.Pandoc.URI','import SourceUriPosix').replace('import Text.Pandoc.CSV','import SourceCSV')
 assert original==(HERE/'FullSourceRstReader.hs').read_text(),'exact complete original module transform'
 count=0
 for name,pin in m['fixtures'].items():
  p=FIX/name;assert sha(p)==pin['sha256'],name;rows=p.read_text().splitlines();assert len(rows)==pin['records'],name;count+=len(rows)
 assert count==m['kotlinCaseCount']
 print('Read-only full reader source/tool/fixture/runtime pins PASS;',count,'independent Kotlin case bindings',flush=True)
 return m
def verify(oracle,original,callbacks):
 m=source();count=0
 for name in m['fixtures']:
  if name=='original37.jsonl':continue
  rows=[json.loads(l)for l in (FIX/name).read_text().splitlines()]
  p=subprocess.run([str(callbacks if name=='callback-observations.jsonl' else oracle)],input=''.join(json.dumps(r['request'],ensure_ascii=False)+'\n'for r in rows).encode(),capture_output=True,check=True)
  actual=[json.loads(l)for l in p.stdout.splitlines()];assert actual==[r['expected']for r in rows],name;count+=len(rows)
  print(name,len(rows),'native PASS',flush=True)
 p=subprocess.run([str(original),'--original-tests'],capture_output=True,check=True)
 assert [json.loads(l)for l in p.stdout.splitlines()]==[json.loads(l)for l in (FIX/'original37.jsonl').read_text().splitlines()]
 print(count,'expanded original observations and37 unchanged original assertions PASS',flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser();g=p.add_mutually_exclusive_group(required=True);g.add_argument('--check-source',action='store_true');g.add_argument('--verify-oracle',action='store_true');p.add_argument('--oracle',type=pathlib.Path,default=pathlib.Path('/tmp/kotdoc-rst-reader/full-oracle'));p.add_argument('--callback-oracle',type=pathlib.Path,default=pathlib.Path('/tmp/kotdoc-rst-reader/callback-oracle'));p.add_argument('--original-tests',type=pathlib.Path,default=pathlib.Path('/tmp/kotdoc-rst-reader/oracle'));a=p.parse_args();source()if a.check_source else verify(a.oracle,a.original_tests,a.callback_oracle)
